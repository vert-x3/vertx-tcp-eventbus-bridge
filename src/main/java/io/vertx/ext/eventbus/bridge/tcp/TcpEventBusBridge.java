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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertx.ext.eventbus.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.JsonObjectTcpEventBusBridgeImpl;

/**
 * TCP EventBus bridge for Vert.x
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public interface TcpEventBusBridge {

  static TcpEventBusBridge create(Vertx vertx) {
    return create(vertx, PayloadType.JSON, new NetServerOptions());
  }

  static TcpEventBusBridge create(Vertx vertx, PayloadType type) {
    return create(vertx, type, new NetServerOptions());
  }

  static TcpEventBusBridge create(Vertx vertx, PayloadType type, NetServerOptions options) {
    switch (type) {
      case JSON:
        return new JsonObjectTcpEventBusBridgeImpl(vertx, options);
      default:
        throw new RuntimeException("Payload type not supported");
    }
  }

  @Fluent
  TcpEventBusBridge addInboundPermitted(PermittedOptions permitted);

  @Fluent
  TcpEventBusBridge addOutboundPermitted(PermittedOptions permitted);

  @Fluent
  TcpEventBusBridge listen();

  @Fluent
  TcpEventBusBridge listen(Handler<AsyncResult<TcpEventBusBridge>> handler);

  @Fluent
  TcpEventBusBridge listen(int port, String address);

  @Fluent
  TcpEventBusBridge listen(int port, String address, Handler<AsyncResult<TcpEventBusBridge>> handler);

  @Fluent
  TcpEventBusBridge listen(int port);

  @Fluent
  TcpEventBusBridge listen(int port, Handler<AsyncResult<TcpEventBusBridge>> handler);

  void close(Handler<AsyncResult<Void>> handler);

  void close();
}
