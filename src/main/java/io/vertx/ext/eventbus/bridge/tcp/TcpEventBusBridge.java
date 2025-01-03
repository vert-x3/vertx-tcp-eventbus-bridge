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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.TcpEventBusBridgeImpl;

/**
 * TCP EventBus bridge for Vert.x
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public interface TcpEventBusBridge {

  static TcpEventBusBridge create(Vertx vertx) {
    return create(vertx, null, null);
  }

  static TcpEventBusBridge create(Vertx vertx, BridgeOptions options) {
    return create(vertx, options, null);
  }

  static TcpEventBusBridge create(Vertx vertx, BridgeOptions options, NetServerOptions netServerOptions) {
    return new TcpEventBusBridgeImpl(vertx, options, netServerOptions,null);
  }

  static TcpEventBusBridge create(Vertx vertx, BridgeOptions options, NetServerOptions netServerOptions,Handler<BridgeEvent> eventHandler) {
    return new TcpEventBusBridgeImpl(vertx, options, netServerOptions,eventHandler);
  }

  /**
   * Start listening on the port and host as configured in the {@link io.vertx.core.net.NetServerOptions} used when creating the server.
   *
   * @return a future of the result
   */
  Future<TcpEventBusBridge> listen();

  /**
   * Start listening on the specified port and host, ignoring port and host configured in the {@link io.vertx.core.net.NetServerOptions} used when creating the server.
   *
   * @param port the tcp port
   * @param address the local address
   *
   * @return a future of the result
   */
  Future<TcpEventBusBridge> listen(int port, String address);

  /**
   * Start listening on the specified port and host "0.0.0.0", ignoring port and host configured in the {@link io.vertx.core.net.NetServerOptions} used when creating the server.
   *
   * @param port the TCP port
   *
   * @return a future of the result
   */
  Future<TcpEventBusBridge> listen(int port);

  /**
   * Start listening on the specified local address, ignoring port and host configured in the {@link NetServerOptions} used when creating the server.
   *
   * @param localAddress the local address to listen on
   * @return a future of the result
   */
  default Future<TcpEventBusBridge> listen(SocketAddress localAddress) {
    return Future.failedFuture("Not supported");
  }

  /**
   * Close the current socket.
   *
   * @return a future of the result
   */
  Future<Void> close();
}
