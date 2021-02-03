/*
 * Copyright 2021 Red Hat, Inc.
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
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper.sendErrFrame;
import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper.sendFrame;

/**
 * The EventBus Bridge Handler to handle all socket operations.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 * @author <a href="mailto:lgao@redhat.com">Lin Gao</a>
 */
class TcpEventBusBridgeHandler implements Handler<NetSocket> {

  private static final Logger log = LoggerFactory.getLogger(TcpEventBusBridgeHandler.class);

  private final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();
  private final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

  private final TcpEventBusBridgeImpl bridge;
  private final EventBus eventBus;
  private final Handler<BridgeEvent> bridgeEventHandler;

  TcpEventBusBridgeHandler(TcpEventBusBridgeImpl bridge, EventBus eventBus, Handler<BridgeEvent> bridgeEventHandler) {
    this.bridge = bridge;
    this.eventBus = eventBus;
    this.bridgeEventHandler = bridgeEventHandler;
  }

  @Override
  public void handle(NetSocket socket) {

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
            log.error("msg does not have address: " + msg.toString());
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

  private void doSendOrPub(NetSocket socket, String address, JsonObject msg, Map<String,
    MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {
    final JsonObject body = msg.getJsonObject("body");
    final JsonObject headers = msg.getJsonObject("headers");

    // default to message
    final String type = msg.getString("type", "message");
    DeliveryOptions deliveryOptions = parseMsgHeaders(new DeliveryOptions(), headers);

    switch (type) {
      case "send":
        if (bridge.checkMatches(true, address, replies)) {
          final String replyAddress = msg.getString("replyAddress");

          if (replyAddress != null) {
            // reply address is not null, it is a request from TCP endpoint that will wait for a response
            eventBus.request(address, body, deliveryOptions, (AsyncResult<Message<JsonObject>> res1) -> {
              if (res1.failed()) {
                sendErrFrame(address, replyAddress, (ReplyException) res1.cause(), socket);
              } else {
                final Message<JsonObject> response = res1.result();
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
              eventBus.send(address, body, deliveryOptions);
            }
          }
          // replies are a one time off operation
          replies.remove(address);
        } else {
          sendErrFrame("access_denied", socket);
        }
        break;
      case "publish":
        if (bridge.checkMatches(true, address)) {
          eventBus.publish(address, body, deliveryOptions);
        } else {
          sendErrFrame("access_denied", socket);
        }
        break;
      case "register":
        if (bridge.checkMatches(false, address)) {
          registry.put(address, eventBus.consumer(address, (Message<JsonObject> res1) -> {
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
        if (bridge.checkMatches(false, address)) {
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
