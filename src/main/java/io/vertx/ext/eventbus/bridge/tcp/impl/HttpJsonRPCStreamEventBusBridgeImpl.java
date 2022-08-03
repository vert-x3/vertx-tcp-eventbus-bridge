package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCBridgeOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class HttpJsonRPCStreamEventBusBridgeImpl extends JsonRPCStreamEventBusBridgeImpl<HttpServerRequest> {

  // http client cannot reply in the same request in which it originally received
  // a response so the replies map should be persistent across http request
  final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

  public HttpJsonRPCStreamEventBusBridgeImpl(Vertx vertx, JsonRPCBridgeOptions options, Handler<BridgeEvent<HttpServerRequest>> bridgeEventHandler) {
    super(vertx, options, bridgeEventHandler);
  }

  @Override
  public void handle(HttpServerRequest socket) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CREATED, null, socket),
      // on success
      () -> {
        final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();

        socket.exceptionHandler(t -> {
          log.error(t.getMessage(), t);
          registry.values().forEach(MessageConsumer::unregister);
          registry.clear();
        }).handler(buffer -> {
          // TODO: handle content type

          // TODO: body may be an array (batching)
          final JsonObject msg = new JsonObject(buffer);

          if (this.isInvalid(msg)) {
            return;
          }

          final String method = msg.getString("method");
          final Object id = msg.getValue("id");
          HttpServerResponse response = socket
            .response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .endHandler(handler -> {
              registry.values().forEach(MessageConsumer::unregister);
              // normal close, trigger the event
              checkCallHook(() -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CLOSED, null, socket));
              registry.clear();
            });
          Consumer<JsonObject> writer;
          if (method.equals("register")) {
            response.setChunked(true);
            writer = payload -> response.write(payload.encode());
          } else {
            writer = payload -> response.end(payload.encode());
          }
          dispatch(writer, method, id, msg, registry, replies);
        });
      },
      // on failure
      () ->  socket.response().setStatusCode(500).setStatusMessage("Internal Server Error").end());
  }

  // TODO: Discuss. Currently we are only adding such methods because SSE doesn't have a body, maybe we could
  //  instead mandate some query params in the request to signal SSE. but bodyHandler is not invoked
  //  in that case so how to handle the request. endHandler or check query params first before applying
  //  bodyHandler ?
  public void handleSSE(HttpServerRequest socket, Object id, JsonObject msg) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CREATED, null, socket),
      () -> {
        final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();

        socket.exceptionHandler(t -> {
          log.error(t.getMessage(), t);
          registry.values().forEach(MessageConsumer::unregister);
          registry.clear();
        });

        HttpServerResponse response = socket.response().setChunked(true).putHeader(HttpHeaders.CONTENT_TYPE,
          "text/event-stream").endHandler(handler -> {
          checkCallHook(() -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CLOSED, null, socket));
          registry.values().forEach(MessageConsumer::unregister);
          registry.clear();
        });

        Consumer<JsonObject> writer = payload -> {
          JsonObject result = payload.getJsonObject("result");
          if (result != null) {
            String address = result.getString("address");
            if (address != null) {
              response.write("event: " + address + "\n");
              response.write("data: " + payload.encode() + "\n\n");
            }
          }
        };
        register(writer, id, msg, registry, replies);
      },
      () ->  socket.response().setStatusCode(500).setStatusMessage("Internal Server Error").end()
    );
  }


}
