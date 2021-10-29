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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper.sendErrFrame;
import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper.sendFrame;

/**
 * Abstract TCP EventBus bridge. Handles all common socket operations but has no knowledge on the payload.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class TcpEventBusBridgeImpl implements TcpEventBusBridge {

  private static final Logger log = LoggerFactory.getLogger(TcpEventBusBridgeImpl.class);

  private final EventBus eb;
  private final NetServer server;

  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final BridgeOptions options;
  private final Handler<BridgeEvent> bridgeEventHandler;


  public TcpEventBusBridgeImpl(Vertx vertx, BridgeOptions options, NetServerOptions netServerOptions, Handler<BridgeEvent> eventHandler) {
    this.eb = vertx.eventBus();
    this.options = options != null ? options : new BridgeOptions();
    this.bridgeEventHandler = eventHandler;

    server = vertx.createNetServer(netServerOptions == null ? new NetServerOptions() : netServerOptions);
    server.connectHandler(this::handler);
  }

  @Override
  public Future<TcpEventBusBridge> listen() {
    return server.listen().map(this);
  }

  @Override
  public Future<TcpEventBusBridge> listen(int port) {
    return server.listen(port).map(this);
  }

  @Override
  public Future<TcpEventBusBridge> listen(int port, String address) {
    return server.listen(port, address).map(this);
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

  private void doSendOrPub(NetSocket socket, String address, JsonObject msg, Map<String,
    MessageConsumer<?>> registry, Map<String, Message<?>> replies) {
    final Object body = msg.getValue("body");
    final JsonObject headers = msg.getJsonObject("headers");


    // default to message
    final String type = msg.getString("type", "message");
    DeliveryOptions deliveryOptions = parseMsgHeaders(new DeliveryOptions(), headers);

    switch (type) {
      case "send":
        if (checkMatches(true, address, replies)) {
          final String replyAddress = msg.getString("replyAddress");

          if (replyAddress != null) {
            // reply address is not null, it is a request from TCP endpoint that will wait for a response
            eb.request(address, body, deliveryOptions, (AsyncResult<Message<Object>> res1) -> {
              if (res1.failed()) {
                sendErrFrame(address, replyAddress, (ReplyException) res1.cause(), socket);
              } else {
                final Message<?> response = res1.result();
                final JsonObject responseHeaders = new JsonObject();

                // clone the headers from / to
                for (Map.Entry<String, String> entry : response.headers()) {
                  responseHeaders.put(entry.getKey(), entry.getValue());
                }

                if (response.replyAddress() != null) {
                  replies.put(response.replyAddress(), response);
                }

                sendFrame("message", replyAddress, response.replyAddress(), responseHeaders, true, response.body(), socket);
              }
            });
          } else {
            // no reply address it might be a response, a failure or a request that does not need a response
            if (replies.containsKey(address)) {
              // address is registered, it is not a request
              Integer failureCode = msg.getInteger("failureCode");
              if ( failureCode == null ) {
                //No failure code, it is a response
                replies.get(address).reply(body, deliveryOptions);
              } else {
                //Failure code, fail the original response
                replies.get(address).fail(msg.getInteger("failureCode"), msg.getString("message"));
              }
            } else {
              // it is a request that does not expect a response
              eb.send(address, body, deliveryOptions);
            }
          }
          // replies are a one time off operation
          replies.remove(address);
        } else {
          sendErrFrame("access_denied", socket);
        }
        break;
      case "publish":
        if (checkMatches(true, address)) {
          eb.publish(address, body, deliveryOptions);
        } else {
          sendErrFrame("access_denied", socket);
        }
        break;
      case "register":
        if (checkMatches(false, address)) {
          registry.put(address, eb.consumer(address, (Message<Object> res1) -> {
            // save a reference to the message so tcp bridged messages can be replied properly
            if (res1.replyAddress() != null) {
              replies.put(res1.replyAddress(), res1);
            }

            final JsonObject responseHeaders = new JsonObject();

            // clone the headers from / to
            for (Map.Entry<String, String> entry : res1.headers()) {
              responseHeaders.put(entry.getKey(), entry.getValue());
            }

            sendFrame("message", res1.address(), res1.replyAddress(), responseHeaders, res1.isSend(), res1.body(), socket);
          }));
          checkCallHook(() -> new BridgeEventImpl(BridgeEventType.REGISTERED, msg, socket), null, null);
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
      case "ping":
        sendFrame("pong", socket);
        break;
      default:
        sendErrFrame("unknown_type", socket);
        break;
    }
  }

  private void handler(NetSocket socket) {

    final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();
    final Map<String, Message<?>> replies = new ConcurrentHashMap<>();

    // create a protocol parser
    final FrameParser parser = new FrameParser(res -> {
      if (res.failed()) {
        // could not parse the message properly
        log.error(res.cause());
        return;
      }

      final JsonObject msg = res.result();

      // short reference

      // default to message
      final String type = msg.getString("type", "message");
      final String address = msg.getString("address");
      BridgeEventType eventType = parseType(type);
      checkCallHook(() -> new BridgeEventImpl(eventType, msg, socket),
        () -> {
          if (eventType != BridgeEventType.SOCKET_PING && address == null) {
            sendErrFrame("missing_address", socket);
            log.error("msg does not have address: " + msg);
            return;
          }
          doSendOrPub(socket, address, msg, registry, replies);
        },
        () -> sendErrFrame("blocked by bridgeEvent handler", socket));
    });

    socket.handler(parser);

    socket.exceptionHandler(t -> {
      log.error(t.getMessage(), t);
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
  public Future<Void> close() {
    return server.close();
  }

  private void checkCallHook(Supplier<BridgeEventImpl> eventSupplier, Runnable okAction, Runnable rejectAction) {
    if (bridgeEventHandler == null) {
      if (okAction != null) {
        okAction.run();
      }
    } else {
      BridgeEventImpl event = eventSupplier.get();
      bridgeEventHandler.handle(event);
      event.future().onComplete(res -> {
        if (res.succeeded()) {
          if (res.result()) {
            if (okAction != null) {
              okAction.run();
            }
          } else {
            if (rejectAction != null) {
              rejectAction.run();
            } else {
              log.debug("Bridge handler prevented send or pub");
            }
          }
        } else {
          log.error("Failure in bridge event handler", res.cause());
        }
      });
    }
  }

  private boolean checkMatches(boolean inbound, String address) {
    return checkMatches(inbound, address, null);
  }

  private boolean checkMatches(boolean inbound, String address, Map<String, Message<?>> replies) {
    // special case, when dealing with replies the addresses are not in the inbound/outbound list but on
    // the replies registry
    if (replies != null && inbound && replies.containsKey(address)) {
      return true;
    }

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

  private DeliveryOptions parseMsgHeaders(DeliveryOptions options, JsonObject headers) {
    if (headers == null)
      return options;

    Iterator<String> fnameIter = headers.fieldNames().iterator();
    String fname;
    while (fnameIter.hasNext()) {
      fname = fnameIter.next();
      if ("timeout".equals(fname)) {
          options.setSendTimeout(headers.getLong(fname));
      } else if ("localOnly".equals(fname)) {
          options.setLocalOnly(headers.getBoolean(fname));
      } else if ("codecName".equals(fname)) {
          options.setCodecName(headers.getString(fname));
      } else {
        options.addHeader(fname, headers.getString(fname));
      }
    }

    return options;
  }

  private static BridgeEventType parseType(String typeStr) {
    switch (typeStr) {
      case "ping":
        return BridgeEventType.SOCKET_PING;
      case "register":
        return BridgeEventType.REGISTER;
      case "unregister":
          return BridgeEventType.UNREGISTER;
      case "publish":
        return BridgeEventType.PUBLISH;
      case "send":
        return BridgeEventType.SEND;
      default:
        throw new IllegalArgumentException("Invalid frame type " + typeStr);
    }
  }
}
