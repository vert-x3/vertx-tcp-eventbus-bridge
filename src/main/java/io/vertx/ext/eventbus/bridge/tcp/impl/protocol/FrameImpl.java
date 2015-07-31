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
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.eventbus.bridge.tcp.Action;
import io.vertx.ext.eventbus.bridge.tcp.Frame;

import java.util.Map;

/**
 * A Frame represents a Procotol Data Unit.
 */
public class FrameImpl implements Frame {

  // A frame must always be created for a specific action
  private final Action action;
  // Headers are optional but behave most like the HTTP headers
  // for simplicity multiple headers are listed multiple times
  private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
  // Body of the message is a buffer but can be handled as other types
  // based on the content-type header
  private Buffer body;

  public FrameImpl(Action action) {
    this.action = action;
  }

  @Override
  public Action action() {
    return action;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public Frame setBody(Object body) {
    if (body != null) {
      if (body instanceof String) {
        this.body = Buffer.buffer((String) body);
        addHeader("Content-Type", "text/plain");
      } else if (body instanceof Buffer) {
        this.body = (Buffer) body;
      } else if (body instanceof JsonObject) {
        this.body = Buffer.buffer(((JsonObject) body).encode());
        addHeader("Content-Type", "application/json");
      } else {
        throw new RuntimeException("Don't know how to handle: " + body.getClass().getName());
      }
    }

    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> R getBody() {
    if (body != null) {
      String contentType = headers.get("Content-Type");

      if (contentType != null) {
        switch (contentType) {
          case "application/json":
            return (R) new JsonObject(body.toString());
          case "text/plain":
            return (R) body.toString();
        }
      }
    }

    return (R) body;
  }

  @Override
  public JsonObject toJSON() {
    if (body != null) {
      return new JsonObject(body.toString());
    }

    return null;
  }

  @Override
  public void write(WriteStream stream) {
    Buffer payload = Buffer.buffer();

    payload.appendString(action.name());
    payload.appendByte((byte) '\n');

    if (body != null) {
      headers.set("Content-Length", Long.toString(body.length()));
    } else {
      headers.set("Content-Length", "0");
    }

    for (Map.Entry<String, String> entry : headers) {
      payload.appendString(entry.getKey());
      payload.appendString(": ");
      payload.appendString(escape(entry.getValue()));
      payload.appendByte((byte) '\n');
    }

    payload.appendByte((byte) '\n');
    if (body != null) {
      payload.appendBuffer(body);
    }
    payload.appendByte((byte) '\0');
    stream.write(payload);
  }

  @Override
  public Frame addHeader(String key, String value) {
    headers.add(key, value);
    return this;
  }

  public static String escape(String value) {
    return value.replaceAll("\\\\", "\\\\").replaceAll(":", "\\c").replaceAll("\n", "\\n").replaceAll("\r", "\\r");
  }

  public static String unescape(String value) {
    return value.replaceAll("\\\\r", "\r").replaceAll("\\\\n", "\n").replaceAll("\\\\c", ":").replaceAll("\\\\\\\\", "\\\\");
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb
        .append(action.toString())
        .append(System.lineSeparator());

    for (Map.Entry<String, String> entry : headers) {
      sb.append(entry.getKey());
      sb.append(": ");
      sb.append(escape(entry.getValue()));
      sb.append(System.lineSeparator());
    }
    sb.append(System.lineSeparator());

    sb.append(getBody().toString());
    sb.append("^@");

    return sb.toString();
  }
}
