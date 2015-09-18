package io.vertx.ext.eventbus.bridge.tcp.impl.protocol;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;

public class FrameHelper {

  private FrameHelper() {}

  public static void sendFrame(String type, JsonObject headers, JsonObject body, WriteStream<Buffer> stream) {
    final JsonObject payload = new JsonObject().put("type", type);

    if (headers != null) {
      payload.put("headers", headers);
    }

    if (body != null) {
      payload.put("body", body);
    }

    // encode
    byte[] data = payload.encode().getBytes();

    stream.write(Buffer.buffer().appendInt(data.length).appendBytes(data));
  }

  public static void sendFrame(String type, JsonObject body, WriteStream<Buffer> stream) {
    sendFrame(type, null, body, stream);
  }

  public static void sendFrame(String type, WriteStream<Buffer> stream) {
    sendFrame(type, null, null, stream);
  }
}
