/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.eventbus.bridge.tcp.impl.protocol;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;

/**
 * Helper class to format and send frames over a socket
 * @author Paulo Lopes
 */
public class FrameHelper {

  private FrameHelper() {}

  public static void sendFrame(String type, String address, String replyAddress, JsonObject headers, JsonObject body, WriteStream<Buffer> handler) {
    final JsonObject payload = new JsonObject().put("type", type);

    if (address != null) {
      payload.put("address", address);
    }

    if (replyAddress != null) {
      payload.put("replyAddress", replyAddress);
    }

    if (headers != null) {
      payload.put("headers", headers);
    }

    if (body != null) {
      payload.put("body", body);
    }

    // encode
    byte[] data = payload.encode().getBytes();

    handler.write(Buffer.buffer().appendInt(data.length).appendBytes(data));
  }

  public static void sendFrame(String type, String address, String replyAddress, JsonObject body, WriteStream<Buffer> handler) {
    sendFrame(type, address, replyAddress, null, body, handler);
  }

  public static void sendFrame(String type, String address, JsonObject body, WriteStream<Buffer> handler) {
    sendFrame(type, address, null, null, body, handler);
  }

  public static void sendFrame(String type, WriteStream<Buffer> handler) {
    sendFrame(type, null, null, null, null, handler);
  }

  public static void sendFrame(String type, ReplyException failure, WriteStream<Buffer> handler) {
    final JsonObject payload = new JsonObject()
        .put("type", type)
        .put("failureCode", failure.failureCode())
        .put("failureType", failure.failureType().name())
        .put("message", failure.getMessage());

    // encode
    byte[] data = payload.encode().getBytes();

    handler.write(Buffer.buffer().appendInt(data.length).appendBytes(data));
  }

  public static void sendErrFrame(String message, WriteStream<Buffer> handler) {
    final JsonObject payload = new JsonObject()
        .put("type", "err")
        .put("message", message);

    // encode
    byte[] data = payload.encode().getBytes();

    handler.write(Buffer.buffer().appendInt(data.length).appendBytes(data));
  }
}
