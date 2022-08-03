package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
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
            writer = payload -> {
              response.write("event: " + payload.getJsonObject("result").getString("address") + "\n");
              response.write("data: " + payload.encode() + "\n\n");
            };
          } else {
            writer = payload -> response.end(payload.encode());
          }
          dispatch(writer, method, id, msg, registry, replies);
        });
      },
      // on failure
      () ->  socket.response().setStatusCode(500).setStatusMessage("Internal Server Error").end());
  }

  // TODO: discuss implications of accepting response here. bridge events may not be emitted.
  //  but if accepting request cannot use handler as the request is usually empty and handler is
  //  not invoked until data has been read. also same thing for other cases
  public void handleSSE(HttpServerResponse socket, JsonObject msg) {
    final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();

    socket.exceptionHandler(t -> {
      log.error(t.getMessage(), t);
      registry.values().forEach(MessageConsumer::unregister);
      registry.clear();
    });
    if (this.isInvalid(msg)) {
      return;
    }

    HttpServerResponse response = socket
      .setChunked(true)
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
      .endHandler(handler -> {
        registry.values().forEach(MessageConsumer::unregister);
        registry.clear();
      });

    final String method = msg.getString("method");
    if (!method.equalsIgnoreCase("register")) {
      log.error("Invalid method for SSE!");
      return;
    }

    final Object id = msg.getValue("id");
    Consumer<JsonObject> writer = payload -> {
      // TODO: Should we use id or address for event name?
      response.write("event: " + payload.getJsonObject("result").getString("address") + "\n");
      response.write("data: " + payload.encode() + "\n\n");
    };
    register(writer, id, msg, registry, replies);
  }


}
