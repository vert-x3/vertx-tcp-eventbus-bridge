package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCBridgeOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WebsocketJsonRPCStreamEventBusBridgeImpl extends JsonRPCStreamEventBusBridgeImpl<WebSocketBase> {

  public WebsocketJsonRPCStreamEventBusBridgeImpl(Vertx vertx, JsonRPCBridgeOptions options, Handler<BridgeEvent<WebSocketBase>> bridgeEventHandler) {
    super(vertx, options, bridgeEventHandler);
  }

  @Override
  public void handle(WebSocketBase socket) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CREATED, null, null),
      // on success
      () -> {
        final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();
        final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

        Consumer<JsonObject> consumer;
        if (options.getWebsocketsTextAsFrame()) {
          consumer = payload -> socket.writeTextMessage(payload.encode());
        } else {
          consumer = payload -> socket.writeBinaryMessage(payload.toBuffer());
        }

        socket
          .exceptionHandler(t -> {
            log.error(t.getMessage(), t);
            registry.values().forEach(MessageConsumer::unregister);
            registry.clear();
          })
          .endHandler(v -> {
            registry.values().forEach(MessageConsumer::unregister);
            // normal close, trigger the event
            checkCallHook(() -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CLOSED, null, null));
            registry.clear();
          })
          .frameHandler(frame -> {
            // TODO: this could be an [], in this case, after parsing, we should loop and call for each element the
            //       code bellow.

            // One idea from vs-jsonrpcstream was the use of content-types, so define how the message was formated
            // by default json (like in the spec) but microsoft was suggesting messagepack as alternative. I'm not
            // sure if we should implement this. The TCP parser was accounting for it, but is it a good idea? maybe not?

            // not handling CLOSE frames here, endHandler will be invoked on the socket later
            // ping frames are automatically handled by websockets so safe to ignore here
            if (frame.isClose() || frame.isPing()) {
              return;
            }

            final JsonObject msg = new JsonObject(frame.binaryData());
            if (this.isInvalid(msg)) {
              return;
            }

            final String method = msg.getString("method");
            final Object id = msg.getValue("id");

            // TODO: we should wrap the socket in order to override the "write" method to write a text frame
            // TODO: the current WriteStream assumes binary frames which are harder to handle on the browser
            // TODO: maybe we could make this configurable (binary/text)

            // if we create a wraper, say an interface:
            // interface SocketWriter { write(Buffer buff) }
            // then we can create specific implementation wrappers for all kinds of sockets, netSocket, webSocket (binary or text)

            // given that the wraper is at the socket level (it's not that heavy in terms of garbage collection, 1 extra object per connection.
            // And a connection is long lasting, not like HTTP

            dispatch(
              consumer,
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

}
