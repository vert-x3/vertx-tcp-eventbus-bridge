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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

@RunWith(VertxUnitRunner.class)
public class TcpEventBusBridgeTest {

  Vertx vertx;
  TcpEventBusBridge bridge;

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    final Async async = context.async();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));

    bridge = TcpEventBusBridge.create(
        vertx,
        new BridgeOptions()
            .addInboundPermitted(new PermittedOptions().setAddress("hello"))
            .addInboundPermitted(new PermittedOptions().setAddress("echo"))
            .addInboundPermitted(new PermittedOptions().setAddress("test"))
            .addOutboundPermitted(new PermittedOptions().setAddress("echo")));

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

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      client.close();
      async.complete();
    });

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      FrameHelper.sendFrame("send", "test", new JsonObject().put("value", "vert.x"), buffer -> {
    	  socket.write(Buffer.buffer().appendInt(buffer.length()).appendBuffer(buffer));
      });
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

      socket.handler(parser::handle);

      FrameHelper.sendFrame("send", "hello", TcpEventBusBridge.REPLY_BACKTRACK, new JsonObject().put("value", "vert.x"), buffer -> {
    	socket.write(Buffer.buffer().appendInt(buffer.length()).appendBuffer(buffer));
      });
    });
  }

  @Test
  public void testSendMessageWithReplyThirdParty(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      vertx.eventBus().consumer("third-party-receiver", 
      	(Message<Buffer> msg) -> {
      	  JsonObject frame = msg.body().toJsonObject();
          context.assertNotEquals("err", frame.getString("type"));
          context.assertEquals("Hello vert.x", frame.getJsonObject("body").getString("value"));
          client.close();
          async.complete();
  		});
  
      FrameHelper.sendFrame("send", "hello", "third-party-receiver", new JsonObject().put("value", "vert.x"), buffer -> {
    	socket.write(Buffer.buffer().appendInt(buffer.length()).appendBuffer(buffer));
      });
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

      socket.handler(parser::handle);

      FrameHelper.sendFrame("register", "echo", null, buffer -> {
    	  socket.write(Buffer.buffer().appendInt(buffer.length()).appendBuffer(buffer));
      });

      // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
      // remote consumer

      FrameHelper.sendFrame("publish", "echo", new JsonObject().put("value", "Vert.x"), buffer -> {
    	  socket.write(Buffer.buffer().appendInt(buffer.length()).appendBuffer(buffer));
      });
    });

  }
}
