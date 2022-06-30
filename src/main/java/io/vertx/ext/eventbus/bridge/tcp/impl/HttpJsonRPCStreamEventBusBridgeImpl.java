package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.json.schema.JsonSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpJsonRPCStreamEventBusBridgeImpl extends JsonRPCStreamEventBusBridgeImpl<HttpServerRequest> {

  public HttpJsonRPCStreamEventBusBridgeImpl(Vertx vertx, BridgeOptions options, Handler<BridgeEvent<HttpServerRequest>> bridgeEventHandler) {
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
        final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

        socket.exceptionHandler(t -> {
          log.error(t.getMessage(), t);
          registry.values().forEach(MessageConsumer::unregister);
          registry.clear();
        }).endHandler(v -> {
          registry.values().forEach(MessageConsumer::unregister);
          // normal close, trigger the event
          checkCallHook(() -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CLOSED, null, socket));
          registry.clear();
        }).handler(buffer -> {
          // TODO: handle content type

          // TODO: body may be an array (batching)
          final JsonObject msg = new JsonObject(buffer);
          System.out.println(msg);

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
          HttpServerResponse response = socket
            .response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
          dispatch(response::end, method, id, msg, registry, replies);
        });
      },
      // on failure
      socket::end);
  }
}
