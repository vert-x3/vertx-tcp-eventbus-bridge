package test;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;

public class TcpEventBusBridgeEchoServer {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    EventBus eb = vertx.eventBus();

    eb.consumer("hello", (Message<JsonObject> msg) -> {
      msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value")));
    });

    eb.consumer("echo",
        (Message<JsonObject> msg) -> msg.reply(msg.body()));

    eb.consumer("echo2",
                (Message<JsonObject> msg) -> {
                  if ("send".equals(msg.body().getString("response_type"))) {
                    eb.send("echo2_response", msg.body());
                  } else {
                    eb.publish("echo2_response", msg.body());
                  }
                });

    TcpEventBusBridge bridge = TcpEventBusBridge.create(
        vertx,
        new BridgeOptions()
            .addInboundPermitted(new PermittedOptions().setAddress("hello"))
            .addInboundPermitted(new PermittedOptions().setAddress("echo"))
            .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
            .addInboundPermitted(new PermittedOptions().setAddress("echo2"))
            .addOutboundPermitted(new PermittedOptions().setAddress("echo2_response")));

    bridge.listen(7000, res -> System.out.println("Ready"));
  }
}
