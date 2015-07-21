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
package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;

import java.util.Map;

/**
 * Simple implementation of Json payload. Protocol assumes json strings without line breaks as full messages.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class JsonObjectTcpEventBusBridgeImpl extends AbstractTcpEventBusBridge<JsonObject> {

  public JsonObjectTcpEventBusBridgeImpl(Vertx vertx, NetServerOptions options) {
    super(vertx, options, "\n");
  }

  @Override
  protected void sendError(NetSocket socket, String message) {
    socket.write(new JsonObject()
        .put("type", "err")
        .put("body", message).encode());

    socket.write("\n");
  }

  @Override
  protected void sendFailure(NetSocket socket, ReplyException failure) {
    socket.write(new JsonObject()
        .put("type", "message")
        .put("failureCode", failure.failureCode())
        .put("failureType", failure.failureType().name())
        .put("message", failure.getMessage()).encode());

    socket.write("\n");
  }


  @Override
  protected void sendOk(NetSocket socket) {
    JsonObject response = new JsonObject();

    response
        .put("type", "ok");

    socket.write(response.encode());
    socket.write("\n");
  }

  @Override
  protected void sendMessage(NetSocket socket, String address, String replyAddress, MultiMap headers, JsonObject value) {
    JsonObject response = new JsonObject();

    response
        .put("type", "message")
        .put("address", address);

    if (replyAddress != null) {
      response
          .put("replyAddress", replyAddress);
    }

    response
        .put("body", value);

    socket.write(response.encode());
    socket.write("\n");
  }

  @Override
  @SuppressWarnings("unchecked")
  protected BridgeMessage<JsonObject> parse(Buffer buffer) {
    Map<String, Object> request = (Map<String, Object>) JsonCodec.decodeValue(buffer.toString("UTF-8"), Map.class);

    String type = (String) request.get("type");
    String address = (String) request.get("address");
    String replyAddress = (String) request.get("replyAddress");
    JsonObject body = new JsonObject((Map<String, Object>) request.get("body"));

    return new BridgeMessage<>(type, address, replyAddress, null, body);
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

//    vertx.setPeriodic(5000l, t -> {
//      vertx.eventBus().publish("hello", new JsonObject().put("value", "me"));
//    });

    TcpEventBusBridge.create(vertx);

    vertx.setPeriodic(10000l, t -> {
      vertx.eventBus().send("echo", new JsonObject(), reply -> {
        System.out.println(reply.result().body());
      });
    });
  }

}
