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
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;

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

  final EventBus eb;
  final NetServer server;

  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final BridgeOptions options;

  public TcpEventBusBridgeImpl(Vertx vertx, BridgeOptions options, NetServerOptions netServerOptions) {
    this.eb = vertx.eventBus();
    this.options = options != null ? options : new BridgeOptions();

    server = vertx.createNetServer(netServerOptions == null ? new NetServerOptions() : netServerOptions);
    server.connectHandler(this::handler);
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
      final String address = msg.getString("address");
      final JsonObject body = msg.getJsonObject("body");

      // default to message
      final String type = msg.getString("type", "message");

      if ("ping".equals(type)) {
        // discard
        return;
      }

      if (address == null) {
        sendErrFrame("address_required", socket);
        return;
      }

      switch (type) {
        case "send":
          if (checkMatches(true, address)) {
            final String replyAddress = msg.getString("replyAddress");

            if (replyAddress != null) {
              eb.send(address, body, (AsyncResult<Message<JsonObject>> res1) -> {
                if (res1.failed()) {
                  sendFrame("message", (ReplyException) res1.cause(), socket);
                } else {
                  final Message<JsonObject> response = res1.result();
                  final JsonObject responseHeaders = new JsonObject();

                  // clone the headers from / to
                  for (Map.Entry<String, String> entry : response.headers()) {
                    responseHeaders.put(entry.getKey(), entry.getValue());
                  }

                  sendFrame("message", replyAddress, response.replyAddress(), responseHeaders, response.body(), socket);
                }
              });
            } else {
              eb.send(address, body);
            }
          } else {
            sendErrFrame("access_denied", socket);
          }
          break;
        case "publish":
          if (checkMatches(true, address)) {
            eb.publish(address, body);
          } else {
            sendErrFrame("access_denied", socket);
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

              sendFrame("message", res1.address(), res1.replyAddress(), responseHeaders, res1.body(), socket);
            }));
          } else {
            sendErrFrame("access_denied", socket);
          }
          break;
        case "unregister":
          if (checkMatches(false, address)) {
            MessageConsumer<?> consumer = registry.remove(address);
            if (consumer != null) {
              consumer.unregister();
            } else {
              sendErrFrame("unknown_address", socket);
            }
          } else {
            sendErrFrame("access_denied", socket);
          }
          break;
        default:
          sendErrFrame("unknown_type", socket);
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

    List<PermittedOptions> matches = inbound ? options.getInboundPermitteds() : options.getOutboundPermitteds();

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
