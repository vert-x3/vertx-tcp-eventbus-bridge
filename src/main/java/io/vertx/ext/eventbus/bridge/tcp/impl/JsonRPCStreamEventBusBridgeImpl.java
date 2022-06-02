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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.*;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCStreamEventBusBridge;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.JsonRPCHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract TCP EventBus bridge. Handles all common socket operations but has no knowledge on the payload.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class JsonRPCStreamEventBusBridgeImpl implements JsonRPCStreamEventBusBridge {

  private static final Logger log = LoggerFactory.getLogger(JsonRPCStreamEventBusBridgeImpl.class);
  private static final JsonObject EMPTY = new JsonObject(Collections.emptyMap());

  private final EventBus eb;

  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final BridgeOptions options;
  private final Handler<BridgeEvent> bridgeEventHandler;


  public JsonRPCStreamEventBusBridgeImpl(Vertx vertx, BridgeOptions options, Handler<BridgeEvent> eventHandler) {
    this.eb = vertx.eventBus();
    this.options = options != null ? options : new BridgeOptions();
    this.bridgeEventHandler = eventHandler;
  }

  private void dispatch(WriteStream<Buffer> socket, String method, Object id, JsonObject msg, Map<String, MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {

    switch (method) {
      case "send":
        checkCallHook(
          () -> new BridgeEventImpl(BridgeEventType.SEND, msg, null),
          () -> send(socket, id, msg, registry, replies),
          () -> JsonRPCHelper.error(id, -32040, "access_denied", socket)
        );
        break;
      case "publish":
        checkCallHook(
          () -> new BridgeEventImpl(BridgeEventType.SEND, msg, null),
          () -> publish(socket, id, msg, registry, replies),
          () -> JsonRPCHelper.error(id, -32040, "access_denied", socket)
        );
        break;
      case "register":
        checkCallHook(
          () -> new BridgeEventImpl(BridgeEventType.REGISTER, msg, null),
          () -> register(socket, id, msg, registry, replies),
          () -> JsonRPCHelper.error(id, -32040, "access_denied", socket)
        );
        break;
      case "unregister":
        checkCallHook(
          () -> new BridgeEventImpl(BridgeEventType.UNREGISTER, msg, null),
          () -> unregister(socket, id, msg, registry, replies),
          () -> JsonRPCHelper.error(id, -32040, "access_denied", socket)
        );
        break;
      case "ping":
        JsonRPCHelper.response(id, "pong", socket);
        break;
      default:
        JsonRPCHelper.error(id, -32601, "unknown_method", socket);
        break;
    }
  }

  private void unregister(WriteStream<Buffer> socket, Object id, JsonObject msg, Map<String, MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {
    final JsonObject params = msg.getJsonObject("params", EMPTY);
    final String address = params.getString("address");

    if (address == null) {
      JsonRPCHelper.error(id, -32602, "invalid_parameters", socket);
      return;
    }

    if (checkMatches(false, address)) {
      MessageConsumer<?> consumer = registry.remove(address);
      if (consumer != null) {
        consumer.unregister();
        if (id != null) {
          // ack
          JsonRPCHelper.response(id, EMPTY, socket);
        }
      } else {
        JsonRPCHelper.error(id, -32044, "unknown_address", socket);
      }
    } else {
      JsonRPCHelper.error(id, -32040, "access_denied", socket);
    }
  }

  private void register(WriteStream<Buffer> socket, Object id, JsonObject msg, Map<String, MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {
    final JsonObject params = msg.getJsonObject("params", EMPTY);
    final String address = params.getString("address");

    if (address == null) {
      JsonRPCHelper.error(id, -32602, "invalid_parameters", socket);
      return;
    }

    if (checkMatches(false, address)) {
      registry.put(address, eb.<JsonObject>consumer(address, res1 -> {
        // save a reference to the message so tcp bridged messages can be replied properly
        if (res1.replyAddress() != null) {
          replies.put(res1.replyAddress(), res1);
        }

        final JsonObject responseHeaders = new JsonObject();

        // clone the headers from / to
        for (Map.Entry<String, String> entry : res1.headers()) {
          responseHeaders.put(entry.getKey(), entry.getValue());
        }

        JsonRPCHelper.response(
          id,
          new JsonObject()
            .put("address", res1.address())
            .put("replyAddress", res1.replyAddress())
            .put("headers", responseHeaders)
            .put("isSend", res1.isSend())
            .put("body", res1.body()),
          socket);
      }));
      checkCallHook(
        () -> new BridgeEventImpl(BridgeEventType.REGISTERED, msg, null),
        () -> {
          if (id != null) {
            // ack
            JsonRPCHelper.response(id, EMPTY, socket);
          }
        });
    } else {
      JsonRPCHelper.error(id, -32040, "access_denied", socket);
    }
  }

  private void publish(WriteStream<Buffer> socket, Object id, JsonObject msg, Map<String, MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {
    final JsonObject params = msg.getJsonObject("params", EMPTY);
    final String address = params.getString("address");

    if (address == null) {
      JsonRPCHelper.error(id, -32602, "invalid_parameters", socket);
      return;
    }

    if (checkMatches(true, address)) {
      final JsonObject body = params.getJsonObject("body");
      final DeliveryOptions deliveryOptions = parseMsgHeaders(new DeliveryOptions(), params.getJsonObject("headers"));

      eb.publish(address, body, deliveryOptions);
      if (id != null) {
        // ack
        JsonRPCHelper.response(id, EMPTY, socket);
      }
    } else {
      JsonRPCHelper.error(id, -32040, "access_denied", socket);
    }
  }

  private void send(WriteStream<Buffer> socket, Object id, JsonObject msg, Map<String, MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {
    final JsonObject params = msg.getJsonObject("params", EMPTY);
    final String address = params.getString("address");

    if (address == null) {
      JsonRPCHelper.error(id, -32602, "invalid_parameters", socket);
      return;
    }

    if (checkMatches(true, address, replies)) {
      final JsonObject body = params.getJsonObject("body");
      final DeliveryOptions deliveryOptions = parseMsgHeaders(new DeliveryOptions(), params.getJsonObject("headers"));

      if (id != null) {
        // id is not null, it is a request from TCP endpoint that will wait for a response
        eb.<JsonObject>request(address, body, deliveryOptions, request -> {
          if (request.failed()) {
            JsonRPCHelper.error(id, (ReplyException) request.cause(), socket);
          } else {
            final Message<JsonObject> response = request.result();
            final JsonObject responseHeaders = new JsonObject();

            // clone the headers from / to
            for (Map.Entry<String, String> entry : response.headers()) {
              responseHeaders.put(entry.getKey(), entry.getValue());
            }

            if (response.replyAddress() != null) {
              replies.put(response.replyAddress(), response);
            }

            JsonRPCHelper.response(
              id,
              new JsonObject()
                .put("headers", responseHeaders)
                .put("id", response.replyAddress())
                // TODO: why?
                .put("send", true)
                .put("body", response.body()),
              socket);
          }
        });
      } else {
        // no reply address it might be a response, a failure or a request that does not need a response
        if (replies.containsKey(address)) {
          // address is registered, it is not a request
          final JsonObject error = params.getJsonObject("error");
          if (error == null) {
            // No error block, it is a response
            replies.get(address).reply(body, deliveryOptions);
          } else {
            // error block, fail the original response
            replies.get(address).fail(error.getInteger("failureCode"), error.getString("message"));
          }
        } else {
          // it is a request that does not expect a response
          eb.send(address, body, deliveryOptions);
        }
      }
      // replies are a one time off operation
      replies.remove(address);
    } else {
      JsonRPCHelper.error(id, -32040, "access_denied", socket);
    }
  }

  @Override
  public void handle(NetSocket socket) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl(BridgeEventType.SOCKET_CREATED, null, socket),
      // on success
      () -> {
        final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();
        final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

        socket
          .exceptionHandler(t -> {
            log.error(t.getMessage(), t);
            registry.values().forEach(MessageConsumer::unregister);
            registry.clear();
          })
          .endHandler(v -> {
            registry.values().forEach(MessageConsumer::unregister);
            // normal close, trigger the event
            checkCallHook(() -> new BridgeEventImpl(BridgeEventType.SOCKET_CLOSED, null, socket));
            registry.clear();
          })
          .handler(
            // create a protocol parser
            new StreamParser()
              .exceptionHandler(t -> {
                log.error(t.getMessage(), t);
              })
              .handler((contentType, body) -> {
                // TODO: handle content type

                // TODO: body may be an array (batching)
                final JsonObject msg = new JsonObject(body);

                // validation
                if (!"2.0".equals(msg.getString("jsonrpc"))) {
                  log.error("Invalid message: " + msg);
                  return;
                }

                final String method = msg.getString("method");
                if (method == null) {
                  log.error("Invalid method: " + msg.getString("method"));
                  return;
                }

                final Object id = msg.getValue("id");
                if (id != null) {
                  if (!(id instanceof String) && !(id instanceof Integer) && !(id instanceof Long)) {
                    log.error("Invalid id: " + msg.getValue("id"));
                    return;
                  }
                }

                dispatch(
                  socket,
                  method,
                  id,
                  msg,
                  registry,
                  replies);
              }));
      },
      // on failure
      socket::close
    );
  }

  public void handle(WebSocketBase socket) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl(BridgeEventType.SOCKET_CREATED, null, null),
      // on success
      () -> {
        final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();
        final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

        socket
          .exceptionHandler(t -> {
            log.error(t.getMessage(), t);
            registry.values().forEach(MessageConsumer::unregister);
            registry.clear();
          })
          .endHandler(v -> {
            registry.values().forEach(MessageConsumer::unregister);
            // normal close, trigger the event
            checkCallHook(() -> new BridgeEventImpl(BridgeEventType.SOCKET_CLOSED, null, null));
            registry.clear();
          })
          .frameHandler(frame -> {
            // TODO: this could be an [], in this case, after parsing, we should loop and call for each element the
            //       code bellow.

            // One idea from vs-jsonrpcstream was the use of content-types, so define how the message was formated
            // by default json (like in the spec) but microsoft was suggesting messagepack as alternative. I'm not
            // sure if we should implement this. The TCP parser was accounting for it, but is it a good idea? maybe not?

            final JsonObject msg = new JsonObject(frame.binaryData());

            // validation
            if (!"2.0".equals(msg.getString("jsonrpc"))) {
              log.error("Invalid message: " + msg);
              return;
            }

            final String method = msg.getString("method");
            if (method == null) {
              log.error("Invalid method: " + msg.getString("method"));
              return;
            }

            final Object id = msg.getValue("id");
            if (id != null) {
              if (!(id instanceof String) && !(id instanceof Integer) && !(id instanceof Long)) {
                log.error("Invalid id: " + msg.getValue("id"));
                return;
              }
            }

            // TODO: we should wrap the socket in order to override the "write" method to write a text frame
            // TODO: the current WriteStream assumes binary frames which are harder to handle on the browser
            // TODO: maybe we could make this configurable (binary/text)

            // if we create a wraper, say an interface:
            // interface SocketWriter { write(Buffer buff) }
            // then we can create specific implementation wrappers for all kinds of sockets, netSocket, webSocket (binary or text)

            // given that the wraper is at the socket level (it's not that heavy in terms of garbage collection, 1 extra object per connection.
            // And a connection is long lasting, not like HTTP

            dispatch(
              socket,
              method,
              id,
              msg,
              registry,
              replies);
          });
      },
      // on failure
      socket::close
    );
  }

  private void checkCallHook(Supplier<BridgeEvent> eventSupplier) {
    checkCallHook(eventSupplier, null, null);
  }

  private void checkCallHook(Supplier<BridgeEvent> eventSupplier, Runnable okAction) {
    checkCallHook(eventSupplier, okAction, null);
  }

  private void checkCallHook(Supplier<BridgeEvent> eventSupplier, Runnable okAction, Runnable rejectAction) {
    if (bridgeEventHandler == null) {
      if (okAction != null) {
        okAction.run();
      }
    } else {
      BridgeEvent event = eventSupplier.get();
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
              log.debug("Bridge handler prevented: " + event.toString());
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

  private boolean checkMatches(boolean inbound, String address, Map<String, Message<JsonObject>> replies) {
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
}
