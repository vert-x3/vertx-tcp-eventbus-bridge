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
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.StreamParser;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.JsonRPCHelper;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.JsonRPCHelper.*;

@RunWith(VertxUnitRunner.class)
public class HttpJsonRPCStreamEventBusBridgeImplTest {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  private final Handler<BridgeEvent<HttpServerRequest>> eventHandler = event -> event.complete(true);

  @Before
  public void before(TestContext should) {
    final Async test = should.async();
    final Vertx vertx = rule.vertx();

    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));

    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));

    vertx.setPeriodic(1000, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    vertx
      .createHttpServer()
      .requestHandler(JsonRPCStreamEventBusBridge.httpSocketHandler(
        vertx,
        new JsonRPCBridgeOptions()
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
    final WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      client.close();
      test.complete();
    });

    request(
      "send",
      new JsonObject().put("address", "test").put("body", new JsonObject().put("value", "vert.x")),
      buffer -> client.post(7000, "localhost", "/").sendBuffer(buffer)
    );

  }

  @Test(timeout = 10_000L)
  public void testNoHandlers(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    final WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    request(
      "send",
      "#backtrack",
      new JsonObject()
        .put("address", "test")
        .put("body", new JsonObject().put("value", "vert.x")),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
          JsonObject frame = handler.bodyAsJsonObject();

          should.assertTrue(frame.containsKey("error"));
          should.assertFalse(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          client.close();
          test.complete();
        })
        .onFailure(should::fail)
    );
  }

  @Test(timeout = 10_000L)
  public void testErrorReply(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    final WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      msg.fail(0, "oops!");
    });

    request(
      "send",
      "#backtrack",
      new JsonObject()
        .put("address", "test")
        .put("body", new JsonObject().put("value", "vert.x")),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
          JsonObject frame = handler.bodyAsJsonObject();
          should.assertTrue(frame.containsKey("error"));
          should.assertFalse(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          client.close();
          test.complete();
        })
        .onFailure(should::fail)
    );

  }

  @Test(timeout = 10_000L)
  public void testSendsFromOtherSideOfBridge(TestContext should) {
    final Vertx vertx = rule.vertx();
    WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    final AtomicBoolean ack = new AtomicBoolean(false);

    request(
      "register",
      "#backtrack",
      new JsonObject()
        .put("address", "ping"),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
          JsonObject frame = handler.bodyAsJsonObject();

          if (!ack.getAndSet(true)) {
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
          } else {
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));

            JsonObject result = frame.getJsonObject("result");

            should.assertEquals("hi", result.getJsonObject("body").getString("value"));
            client.close();
            test.complete();
          }
        })
        .onFailure(should::fail)
    );

  }

  @Test(timeout = 10_000L)
  public void testSendMessageWithReplyBacktrack(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    request(
      "send",
      "#backtrack",
      new JsonObject()
        .put("address", "hello")
        .put("body", new JsonObject().put("value", "vert.x")),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
          JsonObject frame = handler.bodyAsJsonObject();

          should.assertFalse(frame.containsKey("error"));
          should.assertTrue(frame.containsKey("result"));
          should.assertEquals("#backtrack", frame.getValue("id"));

          JsonObject result = frame.getJsonObject("result");

          should.assertEquals("Hello vert.x", result.getJsonObject("body").getString("value"));
          client.close();
          test.complete();
        })
        .onFailure(should::fail)
    );
  }

  @Test(timeout = 10_000L)
  public void testSendMessageWithReplyBacktrackTimeout(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    // This does not reply and will provoke a timeout
    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> { /* Nothing! */ });

    JsonObject headers = new JsonObject().put("timeout", 100L);

    request(
      "send",
      "#backtrack",
      new JsonObject()
        .put("address", "test")
        .put("headers", headers)
        .put("body", new JsonObject().put("value", "vert.x")),
            buffer -> client
      .post(7000, "localhost", "/")
      .sendBuffer(buffer)
      .onSuccess(handler -> {
        JsonObject frame = handler.bodyAsJsonObject();

        should.assertTrue(frame.containsKey("error"));
        should.assertFalse(frame.containsKey("result"));
        should.assertEquals("#backtrack", frame.getValue("id"));

        JsonObject error = frame.getJsonObject("error");

        should.assertEquals("Timed out after waiting 100(ms) for a reply. address: __vertx.reply.1, repliedAddress: test", error.getString("message"));
        should.assertEquals(-1, error.getInteger("code"));

        client.close();
        test.complete();
      })
      .onFailure(should::fail)
    );

  }

  @Test(timeout = 10_000L)
  public void testSendMessageWithDuplicateReplyID(TestContext should) {
    // replies must always return to the same origin

    final Vertx vertx = rule.vertx();
    WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    vertx.eventBus().consumer("third-party-receiver", msg -> should.fail());

    request(
      "send",
      "third-party-receiver",
      new JsonObject()
        .put("address", "hello")
        .put("body", new JsonObject().put("value", "vert.x")),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
          JsonObject frame = handler.bodyAsJsonObject();
          client.close();
          test.complete();
        })
        .onFailure(should::fail)
    );
  }

  @Test(timeout = 10_000L)
  public void testRegister(TestContext should) {
    // Send a request and get a response
    final Vertx vertx = rule.vertx();
    WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    final AtomicInteger messageCount = new AtomicInteger(0);

    request(
      "register",
      "#backtrack",
      new JsonObject().put("address", "echo"),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
            JsonObject frame = handler.bodyAsJsonObject();
            // 2 messages will arrive
            // 1) ACK for register message
            // 2) MESSAGE for echo
            if (messageCount.get() == 0) {
              // ACK for register message
              should.assertFalse(frame.containsKey("error"));
              should.assertTrue(frame.containsKey("result"));
              should.assertEquals("#backtrack", frame.getValue("id"));
              // increment message count so that next time ACK for publish is expected
              should.assertTrue(messageCount.compareAndSet(0, 1));
            } else {
              // reply for echo message
              should.assertFalse(frame.containsKey("error"));
              should.assertTrue(frame.containsKey("result"));
              should.assertEquals("#backtrack", frame.getValue("id"));

              JsonObject result = frame.getJsonObject("result");

              should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));
              client.close();
              test.complete();
            }
          })
        .onFailure(should::fail)
    );

    // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
    // remote consumer

    request(
      "publish",
      "#backtrack",
      new JsonObject()
        .put("address", "echo")
        .put("body", new JsonObject().put("value", "Vert.x")),
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
            JsonObject frame = handler.bodyAsJsonObject();
            // ACK for publish message
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));
          })
        .onFailure(should::fail)
    );

  }

//  @Test(timeout = 10_000L)
//  public void testUnRegister(TestContext should) {
//    // Send a request and get a response
//    final Vertx vertx = rule.vertx();
//    WebClient client = WebClient.create(vertx);
//    final Async test = should.async();
//
//    final String address = "test";
//    // 4 replies will arrive:
//    //   1). ACK for register
//    //   2). ACK for publish
//    //   3). message published to test
//    //   4). err of NO_HANDLERS because of consumer for 'test' is unregistered.
//    final AtomicInteger messageCount = new AtomicInteger(0);
//    final AtomicInteger messageCount2 = new AtomicInteger(0);
//    final StreamParser parser = new StreamParser()
//      .exceptionHandler(should::fail)
//      .handler(body -> {
//        JsonObject frame = new JsonObject(body);
//
//        if (messageCount.get() == 0) {
//          // ACK for register message
//          should.assertFalse(frame.containsKey("error"));
//          should.assertTrue(frame.containsKey("result"));
//          should.assertEquals("#backtrack", frame.getValue("id"));
//          // increment message count so that next time ACK for publish is expected
//          should.assertTrue(messageCount.compareAndSet(0, 1));
//        }
//        else if (messageCount.get() == 1) {
//          // got message, then unregister the handler
//          should.assertFalse(frame.containsKey("error"));
//          JsonObject result = frame.getJsonObject("result");
//          should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));
//
//          request(
//            "unregister",
//            "#backtrack",
//            new JsonObject().put("address", address),
//            buffer -> client
//              .post(7000, "localhost", "/")
//              .sendBuffer(buffer)
//              .onSuccess(handler -> {
//                  JsonObject frame2 = handler.bodyAsJsonObject();
//                  if (messageCount2.get() == 0) {
//                    // ACK for publish message
//                    should.assertFalse(frame2.containsKey("error"));
//                    should.assertTrue(frame2.containsKey("result"));
//                    should.assertEquals("#backtrack", frame2.getValue("id"));
//                    // increment message count so that next time reply for echo message is expected
//                    should.assertTrue(messageCount2.compareAndSet(0, 1));
//                  } else {
//                    // ACK for unregister message
//                    should.assertFalse(frame.containsKey("error"));
//                    should.assertTrue(frame.containsKey("result"));
//                    should.assertEquals("#backtrack", frame.getValue("id"));
//                    // increment message count so that next time error reply for send message is expected
//                    should.assertTrue(messageCount.compareAndSet(3, 4));
//
//                    request(
//                      "send",
//                      "#backtrack",
//                      new JsonObject()
//                        .put("address", address)
//                        .put("body", new JsonObject().put("value", "This will fail anyway!")),
//                      socket
//                    );
//                  }
//                })
//              .onFailure(should::fail)
//          );
//        } else {
//          // TODO: Check error handling of bridge for consistency
//          // consumer on 'test' has been unregistered, send message will fail.
//          should.assertTrue(frame.containsKey("error"));
//          JsonObject error = frame.getJsonObject("error");
//          should.assertEquals(-1, error.getInteger("code"));
//          should.assertEquals("No handlers for address test", error.getString("message"));
//
//          client.close();
//          test.complete();
//        }
//      });
//
//    request(
//      "register",
//      "#backtrack",
//      new JsonObject()
//        .put("address", address),
//      buffer -> client
//        .post(7000, "localhost", "/")
//        .sendBuffer(buffer)
//        .onSuccess(handler -> {
//            JsonObject frame = handler.bodyAsJsonObject();
//            // ACK for publish message
//            should.assertFalse(frame.containsKey("error"));
//            should.assertTrue(frame.containsKey("result"));
//            should.assertEquals("#backtrack", frame.getValue("id"));
//            // increment message count so that next time reply for echo message is expected
//            should.assertTrue(messageCount.compareAndSet(1, 2));
//          })
//        .onFailure(should::fail)
//    );
//
//    request(
//      "publish",
//      "#backtrack",
//      new JsonObject()
//        .put("address", address)
//        .put("body", new JsonObject().put("value", "Vert.x")),
//      socket::end
//    );
//  }
//
//  @Test(timeout = 10_000L)
//  public void testReplyFromClient(TestContext should) {
//    // Send a request from java and get a response from the client
//    final Vertx vertx = rule.vertx();
//    WebClient client = WebClient.create(vertx);
//    final Async test = should.async();
//    final String address = "test";
//
//    final AtomicBoolean ack = new AtomicBoolean(false);
//    final StreamParser parser = new StreamParser()
//      .exceptionHandler(should::fail)
//      .handler(body -> {
//        JsonObject frame = new JsonObject(body);
//
//        if (!ack.getAndSet(true)) {
//          should.assertFalse(frame.containsKey("error"));
//          should.assertTrue(frame.containsKey("result"));
//          should.assertEquals("#backtrack", frame.getValue("id"));
//        } else {
//          JsonObject result = frame.getJsonObject("result");
//          should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));
//
//          request(
//            "send",
//            "#backtrack",
//            new JsonObject()
//              .put("address", result.getString("replyAddress"))
//              .put("body", new JsonObject().put("value", "You got it")),
//            socket::end
//          );
//        }
//      });
//
//    request(
//      "register",
//      "#backtrack",
//      new JsonObject()
//        .put("address", address),
//      buffer -> client
//        .post(7000, "localhost", "/")
//        .sendBuffer(buffer)
//        .onSuccess(handler -> {
//            JsonObject frame = handler.bodyAsJsonObject();
//            // ACK for publish message
//            should.assertFalse(frame.containsKey("error"));
//            should.assertTrue(frame.containsKey("result"));
//            should.assertEquals("#backtrack", frame.getValue("id"));
//          })
//        .onFailure(should::fail)
//    );
//
//    // There is no way to know that the register actually happened, wait a bit before sending.
//    vertx.setTimer(500L, timerId -> {
//      vertx.eventBus().<JsonObject>request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
//        should.assertTrue(respMessage.succeeded());
//        should.assertEquals("You got it", respMessage.result().body().getString("value"));
//        client.close();
//        test.complete();
//      });
//    });
//
//  }
//
//  @Test(timeout = 10_000L)
//  public void testFailFromClient(TestContext should) {
//    // Send a request from java and get a response from the client
//    final Vertx vertx = rule.vertx();
//
//    WebClient client = WebClient.create(vertx);
//    final Async test = should.async();
//    final String address = "test";
//    client.connect(7000, "localhost", should.asyncAssertSuccess(socket -> {
//
//      final AtomicBoolean ack = new AtomicBoolean(false);
//      final StreamParser parser = new StreamParser()
//        .exceptionHandler(should::fail)
//        .handler(body -> {
//          JsonObject frame = new JsonObject(body);
//          if (!ack.getAndSet(true)) {
//            should.assertFalse(frame.containsKey("error"));
//            should.assertTrue(frame.containsKey("result"));
//            should.assertEquals("#backtrack", frame.getValue("id"));
//          } else {
//            JsonObject result = frame.getJsonObject("result");
//            should.assertEquals("Vert.x", result.getJsonObject("body").getString("value"));
//
//            request(
//              "send",
//              null,
//              new JsonObject()
//                .put("address", result.getString("replyAddress"))
//                .put("error", new JsonObject().put("failureCode", 1234).put("message", "ooops!")),
//              socket::end
//            );
//          }
//        });
//
//      socket.handler(parser);
//
//      request(
//        "register",
//        "#backtrack",
//        new JsonObject()
//          .put("address", address),
//        socket::end
//      );
//
//      // There is now way to know that the register actually happened, wait a bit before sending.
//      vertx.setTimer(500L, timerId -> {
//        vertx.eventBus().request(address, new JsonObject().put("value", "Vert.x"), respMessage -> {
//          should.assertTrue(respMessage.failed());
//          should.assertEquals("ooops!", respMessage.cause().getMessage());
//          client.close();
//          test.complete();
//        });
//      });
//    }));
//  }

  @Test(timeout = 10_000L)
  public void testSendPing(TestContext should) {
    final Vertx vertx = rule.vertx();
    WebClient client = WebClient.create(vertx);
    final Async test = should.async();

    request(
      "ping",
      "#backtrack",
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
            JsonObject frame = handler.bodyAsJsonObject();
            should.assertFalse(frame.containsKey("error"));
            should.assertTrue(frame.containsKey("result"));
            should.assertEquals("#backtrack", frame.getValue("id"));

            should.assertEquals("pong", frame.getString("result"));
            client.close();
            test.complete();
          })
        .onFailure(should::fail)
    );
  }

  @Test(timeout = 10_000L)
  public void testNoAddress(TestContext should) {
    final Vertx vertx = rule.vertx();

    WebClient client = WebClient.create(vertx);
    final Async test = should.async();
    final AtomicBoolean errorOnce = new AtomicBoolean(false);

    request(
      "send",
      "#backtrack",
      buffer -> client
        .post(7000, "localhost", "/")
        .sendBuffer(buffer)
        .onSuccess(handler -> {
          JsonObject frame = handler.bodyAsJsonObject();
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
        })
        .onFailure(should::fail)
    );
  }

}
