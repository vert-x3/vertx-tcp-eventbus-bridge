package examples;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCBridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.JsonRPCStreamEventBusBridge;

public class HttpBridgeExample extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new HttpBridgeExample());
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
      .addOutboundPermitted(new PermittedOptions().setAddress("ping"));

    Handler<HttpServerRequest> bridge = JsonRPCStreamEventBusBridge.httpSocketHandler(vertx, options, null);

    vertx
      .createHttpServer()
      .requestHandler(req -> {
        if ("/".equals(req.path())) {
          req.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "text/html")
            .sendFile("http.html");
        } else if ("/jsonrpc".equals(req.path())){
          bridge.handle(req);
        } else {
          req.response().setStatusCode(404).end("Not Found");
        }
      })
      .listen(8080)
      .onFailure(start::fail)
      .onSuccess(server -> {
        System.out.println("Server listening at http://localhost:8080");
        start.complete();
      });

  }
}
