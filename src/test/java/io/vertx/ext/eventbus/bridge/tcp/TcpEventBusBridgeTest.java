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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class TcpEventBusBridgeTest {

  private Vertx vertx;
  private volatile Handler<BridgeEvent> eventHandler = event -> event.complete(true);

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    final Async async = context.async();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));

    vertx.setPeriodic(1000, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    TcpEventBusBridge bridge = TcpEventBusBridge.create(
            vertx,
            new BridgeOptions()
                    .addInboundPermitted(new PermittedOptions().setAddress("hello"))
                    .addInboundPermitted(new PermittedOptions().setAddress("echo"))
                    .addInboundPermitted(new PermittedOptions().setAddress("test"))
                    .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
                    .addOutboundPermitted(new PermittedOptions().setAddress("test"))
                    .addOutboundPermitted(new PermittedOptions().setAddress("ping")), new NetServerOptions(), event -> eventHandler.handle(event));

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

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {
      FrameHelper.sendFrame("send", "test", new JsonObject().put("value", "vert.x"), socket);
    }));
  }

  @Test
  public void testSendVoidStringMessage(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    vertx.eventBus().consumer("test", (Message<Object> msg) -> {
      context.assertTrue(msg.body() instanceof String);
      context.assertEquals("I'm not a JSON Object", msg.body());
      client.close();
      async.complete();
    });

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {
      FrameHelper.sendFrame("send", "test", "I'm not a JSON Object", socket);
    }));
  }

  @Test
  public void testNoHandlers(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();

        context.assertEquals("err", frame.getString("type"));
        context.assertEquals("#backtrack", frame.getString("address"));

        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("send", "test", "#backtrack", new JsonObject().put("value", "vert.x"), socket);
    }));
  }

  @Test
  public void testErrorReply(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      msg.fail(0, "oops!");
    });

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();

        context.assertEquals("err", frame.getString("type"));
        context.assertEquals("#backtrack", frame.getString("address"));

        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("send", "test", "#backtrack", new JsonObject().put("value", "vert.x"), socket);
    }));
  }

  @Test
  public void testSendsFromOtherSideOfBridge(TestContext context) {
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals(true, frame.getBoolean("send"));
        context.assertEquals("hi", frame.getJsonObject("body").getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", "ping", null, socket);
    }));

  }

  @Test
  public void testSendMessageWithReplyBacktrack(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals(true, frame.getBoolean("send"));
        context.assertEquals("Hello vert.x", frame.getJsonObject("body").getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("send", "hello", "#backtrack", new JsonObject().put("value", "vert.x"), socket);
    }));
  }

  @Test
  public void testSendMessageWithReplyBacktrackTimeout(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    // This does not reply and will provoke a timeout
    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> { /* Nothing! */ } );

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        context.assertEquals("err", frame.getString("type"));
        context.assertEquals("TIMEOUT", frame.getString("failureType"));
        context.assertEquals(-1, frame.getInteger("failureCode"));
        context.assertEquals("#backtrack", frame.getString("address"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      JsonObject headers = new JsonObject().put("timeout", 100L);

      FrameHelper.sendFrame("send", "test", "#backtrack", headers, null, new JsonObject().put("value", "vert.x"), socket);
    }));
  }

  @Test
  public void testSendMessageWithDuplicateReplyID(TestContext context) {
    // replies must always return to the same origin

    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      vertx.eventBus().consumer("third-party-receiver", msg -> context.fail());

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        client.close();
        async.complete();
      });

      socket.handler(parser);


      FrameHelper.sendFrame("send", "hello", "third-party-receiver", new JsonObject().put("value", "vert.x"), socket);
    }));
  }

  @Test
  public void testRegister(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      // 1 reply will arrive
      // MESSAGE for echo
      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();

        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals(false, frame.getBoolean("send"));
        context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", "echo", null, socket);

      // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
      // remote consumer

      FrameHelper.sendFrame("publish", "echo", new JsonObject().put("value", "Vert.x"), socket);
    }));

  }

  @Test
  public void testUnRegister(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    final String address = "test";
    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      // 2 replies will arrive:
      //   1). message published to test
      //   2). err of NO_HANDLERS because of consumer for 'test' is unregistered.
      final AtomicBoolean unregistered = new AtomicBoolean(false);
      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        if (unregistered.get()) {
          // consumer on 'test' has been unregistered, send message will fail.
          context.assertEquals("err", frame.getString("type"));
          context.assertEquals("#backtrack", frame.getString("address"));
          context.assertEquals("NO_HANDLERS", frame.getString("failureType"));
          context.assertEquals("No handlers for address test", frame.getString("message"));
          client.close();
          async.complete();
        } else {
          // got message, then unregister the handler
          context.assertNotEquals("err", frame.getString("type"));
          context.assertEquals(false, frame.getBoolean("send"));
          context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
          unregistered.compareAndSet(false, true);
          FrameHelper.sendFrame("unregister", address, null, socket);
          FrameHelper.sendFrame("send", address, "#backtrack", new JsonObject().put("value", "This will fail anyway!"), socket);
        }
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", address, null, socket);
      FrameHelper.sendFrame("publish", address, new JsonObject().put("value", "Vert.x"), socket);
    }));

  }

  @Test
  public void testReplyFromClient(TestContext context) {
    // Send a request from java and get a response from the client
    NetClient client = vertx.createNetClient();
    final Async async = context.async();
    final String address = "test";
    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        if ("message".equals(frame.getString("type"))) {
          context.assertEquals(true, frame.getBoolean("send"));
          context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
          FrameHelper.sendFrame("send", frame.getString("replyAddress"), new JsonObject().put("value", "You got it"), socket);
        }
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", address, null, socket);

      // There is now way to know that the register actually happened, wait a bit before sending.
      vertx.setTimer( 500L, timerId -> {
          vertx.eventBus().<JsonObject>request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
              context.assertTrue(respMessage.succeeded());
              context.assertEquals("You got it", respMessage.result().body().getString("value"));
              client.close();
              async.complete();
          });
        });

    }));

  }

  @Test
  public void testReplyStringMessageFromClient(TestContext context) {
    // Send a request from java and get a response from the client
    NetClient client = vertx.createNetClient();
    final Async async = context.async();
    final String address = "test";
    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        if ("message".equals(frame.getString("type"))) {
          context.assertEquals(true, frame.getBoolean("send"));
          context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
          FrameHelper.sendFrame("send", frame.getString("replyAddress"), "You got it", socket);
        }
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", address, null, socket);

      // There is now way to know that the register actually happened, wait a bit before sending.
      vertx.setTimer( 500L, timerId -> {
        vertx.eventBus().<JsonObject>request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
          context.assertTrue(respMessage.succeeded());
          context.assertEquals("You got it", respMessage.result().body());
          client.close();
          async.complete();
        });
      });

    }));

  }

  @Test
  public void testFailFromClient(TestContext context) {
    // Send a request from java and get a response from the client
    NetClient client = vertx.createNetClient();
    final Async async = context.async();
    final String address = "test";
    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {

      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();
        if ("message".equals(frame.getString("type"))) {
          context.assertEquals(true, frame.getBoolean("send"));
          context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
          FrameHelper.writeFrame(new JsonObject().put("type","send").put("address",frame.getString("replyAddress")).put("failureCode", 1234).put("message", "ooops!"), socket);
        }
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", address, null, socket);

      // There is now way to know that the register actually happened, wait a bit before sending.
      vertx.setTimer( 500L, timerId -> {
          vertx.eventBus().request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
              context.assertTrue(respMessage.failed());
              context.assertEquals("ooops!", respMessage.cause().getMessage());
              client.close();
              async.complete();
          });
        });

    }));

  }

  @Test
  public void testSendPing(TestContext context) {
    NetClient client = vertx.createNetClient();
    final Async async = context.async();
    // MESSAGE for ping
    final FrameParser parser = new FrameParser(parse -> {
      context.assertTrue(parse.succeeded());
      JsonObject frame = parse.result();
      context.assertEquals("pong", frame.getString("type"));
      client.close();
      async.complete();
    });
    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {
    socket.handler(parser);
      FrameHelper.sendFrame("register", "echo", null, socket);
      FrameHelper.sendFrame("ping", socket);
    }));
  }

  @Test
  public void testNoAddress(TestContext context) {
    NetClient client = vertx.createNetClient();
    final Async async = context.async();
    final AtomicBoolean errorOnce = new AtomicBoolean(false);
    final FrameParser parser = new FrameParser(parse -> {
      context.assertTrue(parse.succeeded());
      JsonObject frame = parse.result();
      if (!errorOnce.compareAndSet(false, true)) {
        context.fail("Client gets error message twice!");
      } else {
        context.assertEquals("err", frame.getString("type"));
        context.assertEquals("missing_address", frame.getString("message"));
        vertx.setTimer(200, l -> {
          client.close();
          async.complete();
        });
      }
    });
    client.connect(7000, "localhost", context.asyncAssertSuccess(socket -> {
      socket.handler(parser);
      FrameHelper.sendFrame("send", socket);
    }));
  }

}
