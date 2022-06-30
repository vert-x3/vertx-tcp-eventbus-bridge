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
package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.HttpJsonRPCStreamEventBusBridgeImpl;
import io.vertx.ext.eventbus.bridge.tcp.impl.JsonRPCStreamEventBusBridgeImpl;
import io.vertx.ext.eventbus.bridge.tcp.impl.TCPJsonRPCStreamEventBusBridgeImpl;
import io.vertx.ext.eventbus.bridge.tcp.impl.WebsocketJsonRPCStreamEventBusBridgeImpl;

/**
 * JSONRPC stream EventBus bridge for Vert.x
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */

// TODO: "extends Handler<NetSocket>" was a bad idea because it locks the implementation to TCP sockets. Instead we
//       should have explicit methods that either handle a NetSocket or a WebSocketBase:
//       handle(NetSocket socket)  handle(WebSocketBase socket)
//       or: return a handler, e.g.:
//         Handler<WebSocketBase> webSocketHandler();
//         Handler<NetSocket> netSocketHandler();

// How about generifying this interface as in JsonRPCStreamEventBusBridge<T> extends Handler<T> ?
// similarly create a base class for the impl and concrete impls for each protocol.
@VertxGen
public interface JsonRPCStreamEventBusBridge {

  static Handler<NetSocket> netSocketHandler(Vertx vertx) {
    return netSocketHandler(vertx, null, null);
  }

  static Handler<NetSocket> netSocketHandler(Vertx vertx, BridgeOptions options) {
    return netSocketHandler(vertx, options, null);
  }

  static Handler<NetSocket> netSocketHandler(Vertx vertx, BridgeOptions options, Handler<BridgeEvent<NetSocket>> eventHandler) {
    return new TCPJsonRPCStreamEventBusBridgeImpl(vertx, options, eventHandler);
  }

  static Handler<WebSocketBase> webSocketHandler(Vertx vertx) {
    return webSocketHandler(vertx, null, null, false);
  }

  static Handler<WebSocketBase> webSocketHandler(Vertx vertx, BridgeOptions options) {
    return webSocketHandler(vertx, options, null, false);
  }
  static Handler<WebSocketBase> webSocketHandler(Vertx vertx, BridgeOptions options, Handler<BridgeEvent<WebSocketBase>> eventHandler) {
    return new WebsocketJsonRPCStreamEventBusBridgeImpl(vertx, options, eventHandler, false);
  }

  static Handler<WebSocketBase> webSocketHandler(Vertx vertx, BridgeOptions options, Handler<BridgeEvent<WebSocketBase>> eventHandler, boolean useText) {
    return new WebsocketJsonRPCStreamEventBusBridgeImpl(vertx, options, eventHandler, useText);
  }

  static Handler<HttpServerRequest> httpSocketHandler(Vertx vertx) {
    return httpSocketHandler(vertx, null, null);
  }

  static Handler<HttpServerRequest> httpSocketHandler(Vertx vertx, BridgeOptions options) {
    return httpSocketHandler(vertx, options, null);
  }

  static Handler<HttpServerRequest> httpSocketHandler(Vertx vertx, BridgeOptions options, Handler<BridgeEvent<HttpServerRequest>> eventHandler) {
    return new HttpJsonRPCStreamEventBusBridgeImpl(vertx, options, eventHandler);
  }
}
