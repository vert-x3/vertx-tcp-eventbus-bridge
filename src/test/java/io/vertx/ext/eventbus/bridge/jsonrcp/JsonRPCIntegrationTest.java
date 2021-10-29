/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.eventbus.bridge.jsonrcp;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCStreamEventBusBridge;
import io.vertx.ext.eventbus.bridge.tcp.impl.StreamParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.JsonRPCHelper.request;

@RunWith(VertxUnitRunner.class)
public class JsonRPCIntegrationTest {

  String id() {
    return UUID.randomUUID().toString();
  }

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  private volatile Handler<BridgeEvent> eventHandler = event -> event.complete(true);

  @Before
  public void before(TestContext should) {
    final Async test = should.async();
    final Vertx vertx = rule.vertx();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));

    vertx.setPeriodic(1000, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    vertx.createNetServer()
      .connectHandler(JsonRPCStreamEventBusBridge.create(
        vertx,
        new BridgeOptions()
          .addInboundPermitted(new PermittedOptions().setAddress("hello"))
          .addInboundPermitted(new PermittedOptions().setAddress("echo"))
          .addInboundPermitted(new PermittedOptions().setAddress("test"))
          .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
          .addOutboundPermitted(new PermittedOptions().setAddress("test"))
          .addOutboundPermitted(new PermittedOptions().setAddress("ping")),
        event -> eventHandler.handle(event)))
      .listen(7000, res -> {
        should.assertTrue(res.succeeded());
        test.complete();
      });
  }

  // client send message not expecting a response
  @Test
  public void testClientSendMessageNotExpectingResponse(TestContext context) {
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = context.async();
    final String address = "test";

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {
      final StreamParser parser = new StreamParser()
        .exceptionHandler(context::fail)
        .handler((mineType, body) ->{

          JsonObject frame = new JsonObject(body);

          if ("message".equals(frame.getString("send"))) {
            context.assertEquals(true, frame.getBoolean("send"));
            context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));

            request(
              "send",
              "#backtrack",
              new JsonObject()
                .put("address", frame.getString("replyAddress"))
                .put("body", new JsonObject().put("value", "You got it")),
              socket);
          }
        });


        socket.handler(parser);

        request(
          "register",
          "#backtrack",
          new JsonObject()
            .put("address", address),
          socket);

        // There is now way to know that the register actually happened, wait a bit before sending.
        vertx.setTimer(500L, timerId -> {
          vertx.eventBus().<JsonObject>request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
            context.assertTrue(respMessage.failed());
            client.close();
            test.complete();
          });
        });
      }));
  }

  // client sends a message expecting a response
  // client subscribes to a channel, server sends a reply
  // client subscribes to a channel, server publishes a reply
  // client subscribes to a channel, server sends multiple replies'
  // client unsubscribes a channel, server sends a message, and it is not received on the client
  // client send ping and expect pong

}
