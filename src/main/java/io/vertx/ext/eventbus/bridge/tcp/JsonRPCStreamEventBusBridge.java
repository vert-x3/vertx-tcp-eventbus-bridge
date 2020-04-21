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
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.JsonRPCStreamEventBusBridgeImpl;

/**
 * JSONRPC stream EventBus bridge for Vert.x
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public interface JsonRPCStreamEventBusBridge extends Handler<NetSocket> {

  static JsonRPCStreamEventBusBridge create(Vertx vertx) {
    return create(vertx, null, null);
  }

  static JsonRPCStreamEventBusBridge create(Vertx vertx, BridgeOptions options) {
    return create(vertx, options, null);
  }

  static JsonRPCStreamEventBusBridge create(Vertx vertx, BridgeOptions options, Handler<BridgeEvent> eventHandler) {
    return new JsonRPCStreamEventBusBridgeImpl(vertx, options, eventHandler);
  }
}
