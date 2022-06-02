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

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Helper class to format and send frames over a socket
 *
 * @author Paulo Lopes
 */
public class JsonRPCHelper {

  private JsonRPCHelper() {
  }

  // TODO: Should we refactor this helpers to return the buffer with the encoded message and let the caller perform
  //       the write? This would allow the caller to differentiate from a binary write from a text write?
  //       The same applies to all methods on this helper class
  public static void request(String method, Object id, JsonObject params, MultiMap headers, WriteStream<Buffer> handler) {

    final JsonObject payload = new JsonObject().put("jsonrpc", "2.0");

    if (method == null) {
      throw new IllegalStateException("method cannot be null");
    }

    payload.put("method", method);

    if (id != null) {
      payload.put("id", id);
    }

    if (params != null) {
      payload.put("params", params.copy());
    }

    // write
    if (headers != null) {
      headers.forEach(entry -> {
        handler.write(
          Buffer.buffer(entry.getKey()).appendString(": ").appendString(entry.getValue()).appendString("\r\n")
        );
      });
      // end of headers
      handler.write(Buffer.buffer("\r\n"));
    }

    handler.write(payload.toBuffer().appendString("\r\n"));
  }

  public static void request(String method, Object id, JsonObject params, WriteStream<Buffer> handler) {
    request(method, id, params, null, handler);
  }

  public static void request(String method, Object id, WriteStream<Buffer> handler) {
    request(method, id, null, null, handler);
  }

  public static void request(String method, WriteStream<Buffer> handler) {
    request(method, null, null, null, handler);
  }

  public static void request(String method, JsonObject params, WriteStream<Buffer> handler) {
    request(method, null, params, null, handler);
  }

  public static void response(Object id, Object result, WriteStream<Buffer> handler) {
    final JsonObject payload = new JsonObject()
      .put("jsonrpc", "2.0")
      .put("id", id)
      .put("result", result);

    handler.write(payload.toBuffer().appendString("\r\n"));
  }

  public static void error(Object id, Number code, String message, WriteStream<Buffer> handler) {
    final JsonObject payload = new JsonObject()
      .put("jsonrpc", "2.0")
      .put("id", id);

    final JsonObject error = new JsonObject();
    payload.put("error", error);

    if (code != null) {
      error.put("code", code);
    }

    if (message != null) {
      error.put("message", message);
    }

    handler.write(payload.toBuffer().appendString("\r\n"));
  }

  public static void error(Object id, ReplyException failure, WriteStream<Buffer> handler) {
    error(id, failure.failureCode(), failure.getMessage(), handler);
  }

  public static void error(Object id, String message, WriteStream<Buffer> handler) {
    error(id, -32000, message, handler);
  }
}
