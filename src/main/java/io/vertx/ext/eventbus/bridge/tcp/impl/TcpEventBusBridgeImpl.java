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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abstract TCP EventBus bridge. Handles all common socket operations but has no knowledge on the payload.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class TcpEventBusBridgeImpl implements TcpEventBusBridge {

  private final NetServer server;

  private final Map<String, Pattern> compiledREs = new HashMap<>();
  private final BridgeOptions options;

  public TcpEventBusBridgeImpl(Vertx vertx, BridgeOptions options, NetServerOptions netServerOptions, Handler<BridgeEvent> eventHandler) {
    this.options = options != null ? options : new BridgeOptions();
    server = vertx.createNetServer(netServerOptions == null ? new NetServerOptions() : netServerOptions);
    server.connectHandler(new TcpEventBusBridgeHandler(this, vertx.eventBus(), eventHandler));
  }

  public TcpEventBusBridgeImpl(Vertx vertx, BridgeOptions options, NetServerOptions netServerOptions) {
    this(vertx, options, netServerOptions, null);
  }

  @Override
  public Future<TcpEventBusBridge> listen() {
    return server.listen().map(this);
  }

  @Override
  public Future<TcpEventBusBridge> listen(int port) {
    return server.listen(port).map(this);
  }

  @Override
  public Future<TcpEventBusBridge> listen(int port, String address) {
    return server.listen(port, address).map(this);
  }

  @Override
  public TcpEventBusBridge listen(Handler<AsyncResult<TcpEventBusBridge>> handler) {
    server.listen(res -> {
      if (res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(this));
      }
    });
    return this;
  }

  @Override
  public TcpEventBusBridge listen(int port, String address, Handler<AsyncResult<TcpEventBusBridge>> handler) {
    server.listen(port, address, res -> {
      if (res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(this));
      }
    });
    return this;
  }

  @Override
  public TcpEventBusBridge listen(int port, Handler<AsyncResult<TcpEventBusBridge>> handler) {
    server.listen(port, res -> {
      if (res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(this));
      }
    });
    return this;
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    server.close(handler);
  }

  @Override
  public Future<Void> close() {
    return server.close();
  }

  boolean checkMatches(boolean inbound, String address) {
    return checkMatches(inbound, address, null);
  }

  boolean checkMatches(boolean inbound, String address, Map<String, Message<JsonObject>> replies) {
    // special case, when dealing with replies the addresses are not in the inbound/outbound list but on
    // the replies registry
    if (replies != null && inbound && replies.containsKey(address)) {
      return true;
    }

    List<PermittedOptions> matches = inbound ? options.getInboundPermitteds() : options.getOutboundPermitteds();

    for (PermittedOptions matchHolder : matches) {
      String matchAddress = matchHolder.getAddress();
      String matchRegex;
      if (matchAddress == null) {
        matchRegex = matchHolder.getAddressRegex();
      } else {
        matchRegex = null;
      }

      boolean addressOK;
      if (matchAddress == null) {
        addressOK = matchRegex == null || regexMatches(matchRegex, address);
      } else {
        addressOK = matchAddress.equals(address);
      }

      if (addressOK) {
        return true;
      }
    }

    return false;
  }

  private boolean regexMatches(String matchRegex, String address) {
    Pattern pattern = compiledREs.get(matchRegex);
    if (pattern == null) {
      pattern = Pattern.compile(matchRegex);
      compiledREs.put(matchRegex, pattern);
    }
    Matcher m = pattern.matcher(address);
    return m.matches();
  }

}
