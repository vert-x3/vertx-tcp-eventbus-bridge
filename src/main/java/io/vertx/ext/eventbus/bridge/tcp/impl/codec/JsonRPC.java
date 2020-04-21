package io.vertx.ext.eventbus.bridge.tcp.impl.codec;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.eventbus.bridge.tcp.impl.ParserHandler;

public class JsonRPC implements ParserHandler {
  @Override
  public void handle(String contentType, Buffer body) {
    // TODO: handle content type
    try {
      JsonObject json = new JsonObject(body);
    } catch (RuntimeException e) {

    }
  }
}
