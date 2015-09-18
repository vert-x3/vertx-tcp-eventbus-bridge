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
package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.eventbus.bridge.tcp.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper.*;
/**
 * Abstract TCP EventBus bridge. Handles all common socket operations but has no knowledge on the payload.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class TcpEventBusBridgeImpl implements TcpEventBusBridge {

  private static final Logger log = LoggerFactory.getLogger(TcpEventBusBridgeImpl.class);

  private static final JsonObject EMPTY = new JsonObject();

  final EventBus eb;
  final NetServer server;

  private final Map<String, Pattern> compiledREs = new HashMap<>();

  private final List<PermittedOptions> inboundPermitted = new ArrayList<>();
  private final List<PermittedOptions> outboundPermitted = new ArrayList<>();

  public TcpEventBusBridgeImpl(Vertx vertx, NetServerOptions options) {
    this.eb = vertx.eventBus();

    server = vertx.createNetServer(options);
    server.connectHandler(this::handler);
  }

  @Override
  public TcpEventBusBridge addInboundPermitted(PermittedOptions permitted) {
    inboundPermitted.add(permitted);
    return this;
  }

  @Override
  public TcpEventBusBridge addOutboundPermitted(PermittedOptions permitted) {
    outboundPermitted.add(permitted);
    return this;
  }

  @Override
  public TcpEventBusBridge listen() {
    server.listen();
    return this;
  }

  @Override
  public TcpEventBusBridge listen(int port) {
    server.listen(port);
    return this;
  }

  @Override
  public TcpEventBusBridge listen(int port, String address) {
    server.listen(port, address);
    return this;
  }

  @Override
  public TcpEventBusBridge listen(Handler<AsyncResult<TcpEventBusBridge>> handler) {
    server.listen(res -> {
      if (res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(this));
      }
    });
    return this;
  }

  @Override
  public TcpEventBusBridge listen(int port, String address, Handler<AsyncResult<TcpEventBusBridge>> handler) {
    server.listen(port, address, res -> {
      if (res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(this));
      }
    });
    return this;
  }

  @Override
  public TcpEventBusBridge listen(int port, Handler<AsyncResult<TcpEventBusBridge>> handler) {
    server.listen(port, res -> {
      if (res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(this));
      }
    });
    return this;
  }

  private void handler(NetSocket socket) {

    final Map<String, MessageConsumer<?>> registry = new HashMap<>();

    // create a protocol parser
    final FrameParser parser = new FrameParser(res -> {
      if (res.failed()) {
        // could not parse the message properly
        log.error(res.cause());
        return;
      }

      final JsonObject msg = res.result();

      // short reference
      final JsonObject headers = msg.getJsonObject("headers", EMPTY);
      final String address = headers.getString("address");
      final JsonObject body = msg.getJsonObject("body");

      if (address == null) {
        sendFrame("err", new JsonObject().put("message", "address_required"), null, socket);
        return;
      }

      // default to message
      final String type = msg.getString("type", "message");

      switch (type) {
        case "message":
          if (checkMatches(true, address)) {
            final String replyAddress = headers.getString("replyAddress");

            if (replyAddress != null) {
              eb.send(address, body, (AsyncResult<Message<JsonObject>> res1) -> {
                if (res1.failed()) {
                  final ReplyException failure = (ReplyException) res1.cause();

                  sendFrame("message",
                      new JsonObject()
                          .put("failureCode", failure.failureCode())
                          .put("failureType", failure.failureType().name())
                          .put("message", failure.getMessage()),
                      null,
                      socket);
                } else {
                  final JsonObject responseHeaders = new JsonObject();

                  // clone the headers from / to
                  for (Map.Entry<String, String> entry : res1.result().headers()) {
                    responseHeaders.put(entry.getKey(), entry.getValue());
                  }

                  sendFrame("message",
                      responseHeaders
                          .put("address", replyAddress)
                          .put("replyAddress", res1.result().replyAddress()),
                      res1.result().body(),
                      socket);
                }
              });
            } else {
              eb.send(address, body);
              sendFrame("ok", socket);
            }
          } else {
            sendFrame("err", new JsonObject().put("message", "access_denied"), null, socket);
          }
          break;
        case "publish":
          if (checkMatches(true, address)) {
            eb.publish(address, body);
            sendFrame("ok", socket);
          } else {
            sendFrame("err", new JsonObject().put("message", "access_denied"), null, socket);
          }
          break;
        case "register":
          if (checkMatches(false, address)) {
            registry.put(address, eb.consumer(address, (Message<JsonObject> res1) -> {
              final JsonObject responseHeaders = new JsonObject();

              // clone the headers from / to
              for (Map.Entry<String, String> entry : res1.headers()) {
                responseHeaders.put(entry.getKey(), entry.getValue());
              }

              sendFrame("message",
                  responseHeaders
                      .put("address", res1.address())
                      .put("replyAddress", res1.replyAddress()),
                  res1.body(),
                  socket);
            }));

            sendFrame("ok", socket);
          } else {
            sendFrame("err", new JsonObject().put("message", "access_denied"), null, socket);
          }
          break;
        case "unregister":
          if (checkMatches(false, address)) {
            MessageConsumer<?> consumer = registry.remove(address);
            if (consumer != null) {
              consumer.unregister();
              sendFrame("ok", socket);
            } else {
              sendFrame("err", new JsonObject().put("message", "unknown_address"), null, socket);
            }
          } else {
            sendFrame("err", new JsonObject().put("message", "access_denied"), null, socket);
          }
          break;
        default:
          sendFrame("err", new JsonObject().put("message", "unknown_type"), null, socket);
          break;
      }
    });

    socket.handler(parser::handle);

    socket.exceptionHandler(t -> {
      registry.values().forEach(MessageConsumer::unregister);
      registry.clear();
      socket.close();
    });

    socket.endHandler(v -> {
      registry.values().forEach(MessageConsumer::unregister);
      registry.clear();
    });
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    server.close(handler);
  }

  @Override
  public void close() {
    server.close();
  }

  private boolean checkMatches(boolean inbound, String address) {

    List<PermittedOptions> matches = inbound ? inboundPermitted : outboundPermitted;

    for (PermittedOptions matchHolder : matches) {
      String matchAddress = matchHolder.getAddress();
      String matchRegex;
      if (matchAddress == null) {
        matchRegex = matchHolder.getAddressRegex();
      } else {
        matchRegex = null;
      }

      boolean addressOK;
      if (matchAddress == null) {
        addressOK = matchRegex == null || regexMatches(matchRegex, address);
      } else {
        addressOK = matchAddress.equals(address);
      }

      if (addressOK) {
        return true;
      }
    }

    return false;
  }

  private boolean regexMatches(String matchRegex, String address) {
    Pattern pattern = compiledREs.get(matchRegex);
    if (pattern == null) {
      pattern = Pattern.compile(matchRegex);
      compiledREs.put(matchRegex, pattern);
    }
    Matcher m = pattern.matcher(address);
    return m.matches();
  }
}
