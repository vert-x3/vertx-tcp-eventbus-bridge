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
package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.eventbus.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(VertxUnitRunner.class)
public class TcpEventBusBridgeTest {

  Vertx vertx;
  TcpEventBusBridge bridge;

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    final Async async = context.async();

    vertx.eventBus().consumer("hello",
        (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    vertx.eventBus().consumer("echo",
        (Message<JsonObject> msg) -> msg.reply(msg.body()));

    bridge = TcpEventBusBridge.create(vertx);
    bridge
        .addInboundPermitted(new PermittedOptions().setAddress("hello"))
        .addInboundPermitted(new PermittedOptions().setAddress("echo"))
        .addOutboundPermitted(new PermittedOptions().setAddress("echo"));

    bridge.listen(7000, res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testSendVoidMessage(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      final FrameParser parser = new FrameParser(frame -> {
        context.assertNotEquals(Action.ERROR, frame.action());
        client.close();
        async.complete();
      });

      socket.handler(parser::handle);

      Frame.create(Action.MESSAGE)
          .addHeader("Address", "hello")
          .setBody(new JsonObject().put("value", "vert.x"))
          .write(socket);
    });
  }

  @Test
  public void testSendMessageWithReply(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      final FrameParser parser = new FrameParser(frame -> {
        context.assertNotEquals(Action.ERROR, frame.action());
        context.assertEquals("Hello vert.x", frame.toJSON().getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser::handle);

      Frame.create(Action.MESSAGE)
          .addHeader("Address", "hello")
          .addHeader("Reply-Address", UUID.randomUUID().toString())
          .setBody(new JsonObject().put("value", "vert.x"))
          .write(socket);
    });
  }

  @Test
  public void testRegister(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      // 3 replies will arrive
      // OK for REGISTER
      // OK for PUBLISH
      // MESSAGE for echo
      AtomicInteger cnt = new AtomicInteger(3);

      final FrameParser parser = new FrameParser(frame -> {
        context.assertNotEquals(Action.ERROR, frame.action());
        if (cnt.decrementAndGet() == 0) {
          context.assertNotEquals(Action.MESSAGE, frame.action());
          context.assertEquals("Vert.x", frame.toJSON().getString("value"));
          client.close();
          async.complete();
        }
      });

      socket.handler(parser::handle);

      Frame.create(Action.REGISTER)
          .addHeader("Address", "echo")
          .write(socket);

      // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
      // remote consumer

      Frame.create(Action.PUBLISH)
          .addHeader("Address", "echo")
          .setBody(new JsonObject().put("value", "Vert.x"))
          .write(socket);
    });

  }
}
