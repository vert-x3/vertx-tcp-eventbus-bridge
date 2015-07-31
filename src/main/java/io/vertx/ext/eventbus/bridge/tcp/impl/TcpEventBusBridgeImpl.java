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
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.eventbus.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;
import io.vertx.ext.eventbus.bridge.tcp.Action;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameImpl;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract TCP EventBus bridge. Handles all common socket operations but has no knowledge on the payload.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class TcpEventBusBridgeImpl implements TcpEventBusBridge {

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
    final FrameParser parser = new FrameParser(frame -> {

      if (frame.headers().get("Address") == null) {
        new FrameImpl(Action.ERROR)
            .setBody("address_required")
            .write(socket);
        return;
      }

      final String address = frame.headers().get("Address");


      switch (frame.action()) {
        case MESSAGE:
          if (checkMatches(true, address)) {
            final String replyAddress = frame.headers().get("Reply-Address");

            if (replyAddress != null) {
              eb.send(address, frame.getBody(), (AsyncResult<Message<Object>> res) -> {
                if (res.failed()) {
                  final ReplyException failure = (ReplyException) res.cause();

                  new FrameImpl(Action.MESSAGE)
                      .setBody(new JsonObject()
                          .put("failureCode", failure.failureCode())
                          .put("failureType", failure.failureType().name())
                          .put("message", failure.getMessage()))
                      .write(socket);
                } else {
                  new FrameImpl(Action.MESSAGE)
                      .addHeader("Address", replyAddress)
                      .addHeader("Reply-Address", res.result().replyAddress());

                  // clone the headers from / to
                  for (Map.Entry<String, String> entry : res.result().headers()) {
                    frame.addHeader(entry.getKey(), entry.getValue());
                  }

                  frame
                      .setBody(res.result().body())
                      .write(socket);
                }
              });
            } else {
              eb.send(address, frame.getBody());

              new FrameImpl(Action.OK)
                  .write(socket);
            }
          } else {
            new FrameImpl(Action.ERROR)
                .setBody("access_denied")
                .write(socket);
          }
          break;
        case PUBLISH:
          if (checkMatches(true, address)) {
            eb.publish(address, frame.getBody());
            new FrameImpl(Action.OK)
                .write(socket);

          } else {
            new FrameImpl(Action.ERROR)
                .setBody("access_denied")
                .write(socket);
          }
          break;
        case REGISTER:
          if (checkMatches(false, frame.headers().get("Address"))) {
            registry.put(address, eb.consumer(address, msg -> {

              new FrameImpl(Action.MESSAGE)
                  .addHeader("Address", msg.address())
                  .addHeader("Reply-Address", msg.replyAddress());

              // clone the headers from / to
              for (Map.Entry<String, String> entry : msg.headers()) {
                frame.addHeader(entry.getKey(), entry.getValue());
              }

              frame
                  .setBody(msg.body())
                  .write(socket);
            }));

            new FrameImpl(Action.OK)
                .write(socket);
          } else {
            new FrameImpl(Action.ERROR)
                .setBody("access_denied")
                .write(socket);
          }
          break;
        case UNREGISTER:
          if (checkMatches(false, address)) {
            MessageConsumer<?> consumer = registry.remove(address);
            if (consumer != null) {
              consumer.unregister();
              new FrameImpl(Action.OK)
                  .write(socket);
            } else {
              new FrameImpl(Action.ERROR)
                  .setBody("unknown_address")
                  .write(socket);
            }
          } else {
            new FrameImpl(Action.ERROR)
                .setBody("access_denied")
                .write(socket);
          }
          break;
        default:
          new FrameImpl(Action.ERROR)
              .setBody("unknown_action")
              .write(socket);
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
