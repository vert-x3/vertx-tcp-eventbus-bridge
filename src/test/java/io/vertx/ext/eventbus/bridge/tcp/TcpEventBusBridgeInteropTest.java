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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

@RunWith(VertxUnitRunner.class)
public class TcpEventBusBridgeInteropTest {

  private Vertx vertx;

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    final Async async = context.async();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    TcpEventBusBridge bridge = TcpEventBusBridge.create(
            vertx,
            new BridgeOptions()
                    .addInboundPermitted(new PermittedOptions())
                    .addOutboundPermitted(new PermittedOptions()));

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
  public void testInteropWithPlainJava(TestContext context) {
    final Async async = context.async();

    // register a local consumer
    final EventBus eb = vertx.eventBus();

    eb.consumer("io.vertx", msg -> {
      // assert that headers are received
      context.assertEquals("findAll", msg.headers().get("action"));
      // assert body is not null
      context.assertNotNull(msg.body());
      msg.reply(msg.body());
    });

    // run some plain java (blocking)
    vertx.executeBlocking(f -> {
      try {
        JsonObject headers = new JsonObject();
        headers.put("action", "findAll");

        JsonObject body = new JsonObject();
        body.put("model", "news");

        JsonObject protocol = new JsonObject();
        protocol.put("type", "send");
        protocol.put("headers", headers);
        protocol.put("body", body);
        protocol.put("address", "io.vertx");
        protocol.put("replyAddress", "durp");


        Buffer buffer = Buffer.buffer();
        buffer.appendInt(protocol.encode().getBytes().length);
        buffer.appendString(protocol.encode());

        Socket clientSocket = new Socket("localhost", 7000);

        DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
        output.write(buffer.getBytes());

        DataInputStream input = new DataInputStream(clientSocket.getInputStream());

        int bytesLength = input.readInt();
        byte[] bytes = new byte[bytesLength];
        for(int i = 0; i < bytesLength; i++) {
          bytes[i] = input.readByte();
        }

        input.close();
        output.close();
        clientSocket.close();

        JsonObject reply = new JsonObject(new String(bytes));

        // assert that the body is the same we sent
        context.assertEquals(body, reply.getJsonObject("body"));

        f.complete();

      } catch (IOException e) {
        f.fail(e);
      }
    }, res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @Test
  public void testSendMessageWithReplyBacktrack(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals("Hello vert.x", frame.getJsonObject("body").getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("send", "hello", "#backtrack", new JsonObject().put("value", "vert.x"), socket);
    });
  }

  @Test
  public void testSendMessageWithDuplicateReplyID(TestContext context) {
    // replies must always return to the same origin

    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      vertx.eventBus().consumer("third-party-receiver", msg -> context.fail());

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        client.close();
        async.complete();
      });

      socket.handler(parser);


      FrameHelper.sendFrame("send", "hello", "third-party-receiver", new JsonObject().put("value", "vert.x"), socket);
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

      // 1 reply will arrive
      // MESSAGE for echo
      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();

        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", "echo", null, socket);

      // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
      // remote consumer

      FrameHelper.sendFrame("publish", "echo", new JsonObject().put("value", "Vert.x"), socket);
    });

  }
}
