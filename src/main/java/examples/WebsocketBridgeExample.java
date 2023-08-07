package examples;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCBridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCStreamEventBusBridge;

public class WebsocketBridgeExample extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new WebsocketBridgeExample());
  }

  @Override
  public void start(Promise<Void> start) {
    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));
    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));
    vertx.setPeriodic(1000L, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    JsonRPCBridgeOptions options = new JsonRPCBridgeOptions()
      .addInboundPermitted(new PermittedOptions().setAddress("hello"))
      .addInboundPermitted(new PermittedOptions().setAddress("echo"))
      .addInboundPermitted(new PermittedOptions().setAddress("ping"))
      .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
      .addOutboundPermitted(new PermittedOptions().setAddress("hello"))
      .addOutboundPermitted(new PermittedOptions().setAddress("ping"))
      // if set to false, then websockets messages are received on frontend as binary frames
      .setWebsocketsTextAsFrame(true);

    Handler<WebSocketBase> bridge = JsonRPCStreamEventBusBridge.webSocketHandler(vertx, options, null);

    vertx
      .createHttpServer()
      .requestHandler(req -> {
        // this is where any http request will land
        if ("/jsonrpc".equals(req.path())) {
          // we switch from HTTP to WebSocket
          req.toWebSocket()
            .onFailure(err -> {
              err.printStackTrace();
              req.response().setStatusCode(500).end(err.getMessage());
            })
            .onSuccess(bridge::handle);
        } else {
          // serve the base HTML application
          if ("/".equals(req.path())) {
            req.response()
              .putHeader(HttpHeaders.CONTENT_TYPE, "text/html")
              .sendFile("ws.html");
          } else {
            // 404 all the rest
            req.response().setStatusCode(404).end("Not Found");
          }
        }
      })
      .listen(8080)
      .onFailure(start::fail)
      .onSuccess(server -> {
        System.out.println("Server listening at http://localhost:8080");
        start.complete();
      });
  }

  public void example1(Vertx vertx) {

    vertx
      .createHttpServer()
      .requestHandler(req -> {
        // this is where any http request will be handled

        // perform a protocol upgrade
        req.toWebSocket()
          .onFailure(err -> {
            err.printStackTrace();
            req.response().setStatusCode(500).end(err.getMessage());
          })
          .onSuccess(ws -> {
            JsonRPCBridgeOptions options = new JsonRPCBridgeOptions()
              .addInboundPermitted(new PermittedOptions().setAddress("in"))
              .addOutboundPermitted(new PermittedOptions().setAddress("out"))
              // if set to false, then websockets messages are received
              // on frontend as binary frames
              .setWebsocketsTextAsFrame(true);

            JsonRPCStreamEventBusBridge.webSocketHandler(vertx, options).handle(ws);
          });
      })
      .listen(8080)
      .onSuccess(server -> {
        // server is ready!
      });

  }
}
