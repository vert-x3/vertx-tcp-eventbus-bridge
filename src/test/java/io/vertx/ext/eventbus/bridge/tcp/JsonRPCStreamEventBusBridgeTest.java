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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.StreamParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.JsonRPCHelper.*;

@RunWith(VertxUnitRunner.class)
public class JsonRPCStreamEventBusBridgeTest {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  private final Handler<BridgeEvent<NetSocket>> eventHandler = event -> event.complete(true);

  @Before
  public void before(TestContext should) {
    final Async test = should.async();
    final Vertx vertx = rule.vertx();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));

    vertx.setPeriodic(1000, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    vertx.createNetServer()
      .connectHandler(JsonRPCStreamEventBusBridge.netSocketHandler(
        vertx,
        new BridgeOptions()
          .addInboundPermitted(new PermittedOptions().setAddress("hello"))
          .addInboundPermitted(new PermittedOptions().setAddress("echo"))
          .addInboundPermitted(new PermittedOptions().setAddress("test"))
          .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
          .addOutboundPermitted(new PermittedOptions().setAddress("test"))
          .addOutboundPermitted(new PermittedOptions().setAddress("ping")),
        eventHandler))
      .listen(7000, res -> {
        should.assertTrue(res.succeeded());
        test.complete();
      });
  }

  @Test(timeout = 10_000L)
  public void testSendVoidMessage(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      client.close();
      test.complete();
    });

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {
      request("send", new JsonObject().put("address", "test").put("body", new JsonObject().put("value", "vert.x")), socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testNoHandlers(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final StreamParser parser = new StreamParser()
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          should.assertTrue(frame.containsKey("error"));
          should.assertFalse(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          client.close();
          test.complete();
        }).exceptionHandler(should::fail);

      socket.handler(parser);

      request(
        "send",
        "#backtrack",
        new JsonObject()
          .put("address", "test")
          .put("body", new JsonObject().put("value", "vert.x")),
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testErrorReply(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      msg.fail(0, "oops!");
    });

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          should.assertTrue(frame.containsKey("error"));
          should.assertFalse(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          client.close();
          test.complete();
        });

      socket.handler(parser);

      request(
        "send",
        "#backtrack",
        new JsonObject()
          .put("address", "test")
          .put("body", new JsonObject().put("value", "vert.x")),
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testSendsFromOtherSideOfBridge(TestContext should) {
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final AtomicBoolean ack = new AtomicBoolean(false);

      // 2 replies will arrive:
      //   1). acknowledge register
      //   2). greeting
      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          if (!ack.getAndSet(true)) {
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
          } else {
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));

            JsonObject result = frame.getJsonObject("result");

            should.assertEquals(true, result.getBoolean("isSend"));
            should.assertEquals("hi", result.getJsonObject("body").getString("value"));
            client.close();
            test.complete();
          }
        });

      socket.handler(parser);

      request(
        "register",
        "#backtrack",
        new JsonObject()
          .put("address", "ping"),
        socket);
    }));

  }

  @Test(timeout = 10_000L)
  public void testSendMessageWithReplyBacktrack(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          should.assertFalse(frame.containsKey("error"));
          should.assertTrue(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          JsonObject result = frame.getJsonObject("result");

          should.assertEquals(true, result.getBoolean("send"));
          should.assertEquals("Hello vert.x", result.getJsonObject("body").getString("value"));
          client.close();
          test.complete();
        });

      socket.handler(parser);

      request(
        "send",
        "#backtrack",
        new JsonObject()
          .put("address", "hello")
          .put("body", new JsonObject().put("value", "vert.x")),
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testSendMessageWithReplyBacktrackTimeout(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    // This does not reply and will provoke a timeout
    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> { /* Nothing! */ });

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          should.assertTrue(frame.containsKey("error"));
          should.assertFalse(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          JsonObject error = frame.getJsonObject("error");

          should.assertEquals("Timed out after waiting 100(ms) for a reply. address: __vertx.reply.1, repliedAddress: test", error.getString("message"));
          should.assertEquals(-1, error.getInteger("code"));

          client.close();
          test.complete();
        });

      socket.handler(parser);

      JsonObject headers = new JsonObject().put("timeout", 100L);

      request(
        "send",
        "#backtrack",
        new JsonObject()
          .put("address", "test")
          .put("headers", headers)
          .put("body", new JsonObject().put("value", "vert.x")),
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testSendMessageWithDuplicateReplyID(TestContext should) {
    // replies must always return to the same origin

    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      vertx.eventBus().consumer("third-party-receiver", msg -> should.fail());

      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);
          client.close();
          test.complete();
        });

      socket.handler(parser);


      request(
        "send",
        "third-party-receiver",
        new JsonObject()
          .put("address", "hello")
          .put("body", new JsonObject().put("value", "vert.x")),
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testRegister(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {
      final AtomicInteger messageCount = new AtomicInteger(0);

      // 3 messages will arrive
      // 1) ACK for register message
      // 2) ACK for publish message
      // 3) MESSAGE for echo
      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          if (messageCount.get() == 0) {
            // ACK for register message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
            // increment message count so that next time ACK for publish is expected
            should.assertTrue(messageCount.compareAndSet(0, 1));
          }
          else if (messageCount.get() == 1) {
            // ACK for publish message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
            // increment message count so that next time reply for echo message is expected
            should.assertTrue(messageCount.compareAndSet(1, 2));
          } else {
            // reply for echo message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));

            JsonObject result = frame.getJsonObject("result");

            should.assertEquals(false, result.getBoolean("isSend"));
            should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));
            client.close();
            test.complete();
          }
        });

      socket.handler(parser);

      request(
        "register",
        "#backtrack",
        new JsonObject()
          .put("address", "echo"),
        socket);

      // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
      // remote consumer

      request(
        "publish",
        "#backtrack",
        new JsonObject()
          .put("address", "echo")
          .put("body", new JsonObject().put("value", "Vert.x")),
        socket);
    }));

  }

  @Test(timeout = 10_000L)
  public void testUnRegister(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();

    final String address = "test";
    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      // 4 replies will arrive:
      //   1). ACK for register
      //   2). ACK for publish
      //   3). message published to test
      //   4). err of NO_HANDLERS because of consumer for 'test' is unregistered.
      final AtomicInteger messageCount = new AtomicInteger(0);
      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          if (messageCount.get() == 0) {
            // ACK for register message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
            // increment message count so that next time ACK for publish is expected
            should.assertTrue(messageCount.compareAndSet(0, 1));
          }
          else if (messageCount.get() == 1) {
            // ACK for publish message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
            // increment message count so that next time reply for echo message is expected
            should.assertTrue(messageCount.compareAndSet(1, 2));
          } else if (messageCount.get() == 2) {
            // got message, then unregister the handler
            should.assertFalse(frame.containsKey("error"));
            JsonObject result = frame.getJsonObject("result");
            should.assertEquals(false, result.getBoolean("isSend"));
            should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));

            // increment message count so that next time ACK for unregister is expected
            should.assertTrue(messageCount.compareAndSet(2, 3));

            request("unregister", "#backtrack", new JsonObject().put("address", address), socket);
          } else if (messageCount.get() == 3) {
            // ACK for unregister message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
            // increment message count so that next time error reply for send message is expected
            should.assertTrue(messageCount.compareAndSet(3, 4));

            request(
              "send",
              "#backtrack",
              new JsonObject()
                .put("address", address)
                .put("body", new JsonObject().put("value", "This will fail anyway!")),
              socket);
          } else {
            // TODO: Check error handling of bridge for consistency
            // consumer on 'test' has been unregistered, send message will fail.
            should.assertTrue(frame.containsKey("error"));
            JsonObject error = frame.getJsonObject("error");
            should.assertEquals(-1, error.getInteger("code"));
            should.assertEquals("No handlers for address test", error.getString("message"));

            client.close();
            test.complete();
          }
        });

      socket.handler(parser);

      request(
        "register",
        "#backtrack",
        new JsonObject()
          .put("address", address),
        socket);

      request(
        "publish",
        "#backtrack",
        new JsonObject()
          .put("address", address)
          .put("body", new JsonObject().put("value", "Vert.x")),
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testReplyFromClient(TestContext should) {
    // Send a request from java and get a response from the client
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();
    final String address = "test";
    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final AtomicBoolean ack = new AtomicBoolean(false);
      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);

          if (!ack.getAndSet(true)) {
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
          } else {
            JsonObject result = frame.getJsonObject("result");
            should.assertTrue(result.getBoolean("isSend"));
            should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));

            request(
              "send",
              "#backtrack",
              new JsonObject()
                .put("address", result.getString("replyAddress"))
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
          should.assertTrue(respMessage.succeeded());
          should.assertEquals("You got it", respMessage.result().body().getString("value"));
          client.close();
          test.complete();
        });
      });

    }));

  }

  @Test(timeout = 10_000L)
  public void testFailFromClient(TestContext should) {
    // Send a request from java and get a response from the client
    final Vertx vertx = rule.vertx();

    NetClient client = vertx.createNetClient();
    final Async test = should.async();
    final String address = "test";
    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {

      final AtomicBoolean ack = new AtomicBoolean(false);
      final StreamParser parser = new StreamParser()
        .exceptionHandler(should::fail)
        .handler(body -> {
          JsonObject frame = new JsonObject(body);
          if (!ack.getAndSet(true)) {
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
          } else {
            JsonObject result = frame.getJsonObject("result");
            should.assertTrue(result.getBoolean("isSend"));
            should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));

            request(
              "send",
              null,
              new JsonObject()
                .put("address", result.getString("replyAddress"))
                .put("error", new JsonObject().put("failureCode", 1234).put("message", "ooops!")),
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
        vertx.eventBus().request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
          should.assertTrue(respMessage.failed());
          should.assertEquals("ooops!", respMessage.cause().getMessage());
          client.close();
          test.complete();
        });
      });
    }));
  }

  @Test(timeout = 10_000L)
  public void testSendPing(TestContext should) {
    final Vertx vertx = rule.vertx();
    NetClient client = vertx.createNetClient();
    final Async test = should.async();
    // MESSAGE for ping
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        JsonObject frame = new JsonObject(body);

        should.assertFalse(frame.containsKey("error"));
        should.assertTrue(frame.containsKey("result"));
        should.assertEquals("#backtrack", frame.getValue("id"));

        should.assertEquals("pong", frame.getString("result"));
        client.close();
        test.complete();
      });

    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {
      socket.handler(parser);
      request(
        "ping",
        "#backtrack",
        socket);
    }));
  }

  @Test(timeout = 10_000L)
  public void testNoAddress(TestContext should) {
    final Vertx vertx = rule.vertx();

    NetClient client = vertx.createNetClient();
    final Async test = should.async();
    final AtomicBoolean errorOnce = new AtomicBoolean(false);
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        JsonObject frame = new JsonObject(body);
        if (!errorOnce.compareAndSet(false, true)) {
          should.fail("Client gets error message twice!");
        } else {
          should.assertTrue(frame.containsKey("error"));
          should.assertEquals("invalid_parameters", frame.getJsonObject("error").getString("message"));
          vertx.setTimer(200, l -> {
            client.close();
            test.complete();
          });
        }
      });
    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {
      socket.handler(parser);
      request(
        "send",
        "#backtrack",
        socket);
    }));
  }

}
