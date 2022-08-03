package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

import static io.vertx.ext.eventbus.bridge.tcp.impl.protocol.JsonRPCHelper.request;

public class InteropWebSocketServer extends AbstractVerticle {

  // To test just run this application from the IDE and then open the browser on http://localhost:8080
  // later we can also automate this with a vert.x web client, I'll show you next week how to bootstrap it.
  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new InteropWebSocketServer());
  }

  @Override
  public void start(Promise<Void> start) {
    // just to have some messages flowing around
    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));
    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));
    vertx.setPeriodic(1000L, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));

    // once we fix the interface we can avoid the casts
    Handler<HttpServerRequest> bridge = JsonRPCStreamEventBusBridge.httpSocketHandler(
        vertx,
        new JsonRPCBridgeOptions()
          .addInboundPermitted(new PermittedOptions().setAddress("hello"))
          .addInboundPermitted(new PermittedOptions().setAddress("echo"))
          .addInboundPermitted(new PermittedOptions().setAddress("test"))
          .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
          .addOutboundPermitted(new PermittedOptions().setAddress("test"))
          .addOutboundPermitted(new PermittedOptions().setAddress("ping")),
      null
    );

    WebClient client = WebClient.create(vertx);

    vertx
      .createHttpServer()
      .requestHandler(req -> {
        // this is where any http request will land
        // serve the base HTML application
        if ("/".equals(req.path())) {
          req.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "text/html")
            .sendFile("ws.html");
        } else if ("/jsonrpc".equals(req.path())){
          bridge.handle(req);
        } else if ("/jsonrpc-sse".equals(req.path())) {
          HttpServerResponse resp = req.response().setChunked(true).putHeader("Content-Type", "text/event-stream");
          request(
            "register",
            (int) (Math.random() * 100_000),
            new JsonObject().put("address", "ping"),
            buffer -> client
              .post(8080, "localhost", "/jsonrpc")
              .as(BodyCodec.pipe(resp))
              .sendBuffer(buffer)
          );
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
