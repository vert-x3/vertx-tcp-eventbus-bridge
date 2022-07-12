package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCBridgeOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TCPJsonRPCStreamEventBusBridgeImpl extends JsonRPCStreamEventBusBridgeImpl<NetSocket> {

  public TCPJsonRPCStreamEventBusBridgeImpl(Vertx vertx, JsonRPCBridgeOptions options, Handler<BridgeEvent<NetSocket>> bridgeEventHandler) {
    super(vertx, options, bridgeEventHandler);
  }

  @Override
  public void handle(NetSocket socket) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CREATED, null, socket),
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
            checkCallHook(() -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CLOSED, null, socket));
            registry.clear();
          })
          .handler(
            // create a protocol parser
            new StreamParser()
              .exceptionHandler(t -> {
                log.error(t.getMessage(), t);
              })
              .handler(buffer -> {
                // TODO: handle content type

                // TODO: body may be an array (batching)
                final JsonObject msg = new JsonObject(buffer);
                if (this.isInvalid(msg)) {
                  return;
                }
                final String method = msg.getString("method");
                final Object id = msg.getValue("id");

                dispatch(
                  socket::write,
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
}
