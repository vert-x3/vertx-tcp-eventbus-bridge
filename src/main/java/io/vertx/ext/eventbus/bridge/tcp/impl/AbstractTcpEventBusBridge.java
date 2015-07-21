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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.ext.eventbus.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;

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
 *
 * @param <T> the payload type.
 */
public abstract class AbstractTcpEventBusBridge<T> implements TcpEventBusBridge {

  final EventBus eb;
  final NetServer server;
  final Buffer delim;

  private final Map<String, Pattern> compiledREs = new HashMap<>();

  private final List<PermittedOptions> inboundPermitted = new ArrayList<>();
  private final List<PermittedOptions> outboundPermitted = new ArrayList<>();

  public AbstractTcpEventBusBridge(Vertx vertx, NetServerOptions options, String delim) {
    this.eb = vertx.eventBus();
    this.delim = Buffer.buffer(delim);

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


  abstract protected void sendError(NetSocket socket, String message);

  abstract protected void sendFailure(NetSocket socket, ReplyException failure);

  abstract protected void sendOk(NetSocket socket);

  abstract protected void sendMessage(NetSocket socket, String address, String replyAddress, MultiMap headers, T value);

  abstract protected BridgeMessage<T> parse(Buffer buffer);

  private void handler(NetSocket socket) {

    final Map<String, MessageConsumer<T>> registry = new HashMap<>();

    final RecordParser parser = RecordParser.newDelimited(delim, buffer -> {

      BridgeMessage<T> request;

      try {
        request = parse(buffer);
      } catch (RuntimeException e) {
        sendError(socket, e.getMessage());
        return;
      }

      if (request.type == null) {
        sendError(socket, "type_required");
        return;
      }

      if (request.address == null) {
        sendError(socket, "address_required");
        return;
      }

      switch (request.type) {
        case "sendMessage":
          if (checkMatches(true, request.address)) {
            if (request.replyAddress != null) {
              eb.send(request.address, request.body, (AsyncResult<Message<T>> res) -> {
                if (res.failed()) {
                  sendFailure(socket, (ReplyException) res.cause());
                } else {
                  sendMessage(socket,
                      request.replyAddress, res.result().replyAddress(), res.result().headers(), res.result().body());
                }
              });
            } else {
              eb.send(request.address, request.body);
              sendOk(socket);
            }
          } else {
            sendError(socket, "access_denied");
          }
          break;
        case "publish":
          if (checkMatches(true, request.address)) {
            eb.publish(request.address, request.body);
            sendOk(socket);
          } else {
            sendError(socket, "access_denied");
          }
          break;
        case "register":
          if (checkMatches(false, request.address)) {
            registry.put(request.address, eb.consumer(request.address,
                msg -> sendMessage(socket, msg.address(), msg.replyAddress(), msg.headers(), msg.body())));
            sendOk(socket);
          }
          break;
        case "unregister":
          if (checkMatches(false, request.address)) {
            MessageConsumer<T> consumer = registry.remove(request.address);
            if (consumer != null) {
              consumer.unregister();
              sendOk(socket);
            } else {
              sendError(socket, "unknown_address");
            }
          }
          break;
        default:
          sendError(socket, "unknown_type");
          break;
      }
    });

    socket.handler(parser::handle);

    socket.exceptionHandler(t -> {
      registry.values().forEach(MessageConsumer<T>::unregister);
      registry.clear();
      socket.close();
    });

    socket.endHandler(v -> {
      registry.values().forEach(MessageConsumer<T>::unregister);
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
