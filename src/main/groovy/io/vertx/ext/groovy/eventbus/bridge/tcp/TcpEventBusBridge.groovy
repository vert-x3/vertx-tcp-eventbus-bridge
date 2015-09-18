/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.ext.groovy.eventbus.bridge.tcp;
import groovy.transform.CompileStatic
import io.vertx.lang.groovy.InternalHelper
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServerOptions
import io.vertx.groovy.core.Vertx
import io.vertx.core.AsyncResult
import io.vertx.ext.eventbus.bridge.tcp.PermittedOptions
import io.vertx.core.Handler
/**
 * TCP EventBus bridge for Vert.x
*/
@CompileStatic
public class TcpEventBusBridge {
  private final def io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge delegate;
  public TcpEventBusBridge(Object delegate) {
    this.delegate = (io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge) delegate;
  }
  public Object getDelegate() {
    return delegate;
  }
  public static TcpEventBusBridge create(Vertx vertx) {
    def ret= InternalHelper.safeCreate(io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge.create((io.vertx.core.Vertx)vertx.getDelegate()), io.vertx.ext.groovy.eventbus.bridge.tcp.TcpEventBusBridge.class);
    return ret;
  }
  public static TcpEventBusBridge create(Vertx vertx, Map<String, Object> options) {
    def ret= InternalHelper.safeCreate(io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge.create((io.vertx.core.Vertx)vertx.getDelegate(), options != null ? new io.vertx.core.net.NetServerOptions(new io.vertx.core.json.JsonObject(options)) : null), io.vertx.ext.groovy.eventbus.bridge.tcp.TcpEventBusBridge.class);
    return ret;
  }
  public TcpEventBusBridge addInboundPermitted(Map<String, Object> permitted = [:]) {
    this.delegate.addInboundPermitted(permitted != null ? new io.vertx.ext.eventbus.bridge.tcp.PermittedOptions(new io.vertx.core.json.JsonObject(permitted)) : null);
    return this;
  }
  public TcpEventBusBridge addOutboundPermitted(Map<String, Object> permitted = [:]) {
    this.delegate.addOutboundPermitted(permitted != null ? new io.vertx.ext.eventbus.bridge.tcp.PermittedOptions(new io.vertx.core.json.JsonObject(permitted)) : null);
    return this;
  }
  public TcpEventBusBridge listen() {
    this.delegate.listen();
    return this;
  }
  public TcpEventBusBridge listen(Handler<AsyncResult<TcpEventBusBridge>> handler) {
    this.delegate.listen(new Handler<AsyncResult<io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge>>() {
      public void handle(AsyncResult<io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge> event) {
        AsyncResult<TcpEventBusBridge> f
        if (event.succeeded()) {
          f = InternalHelper.<TcpEventBusBridge>result(new TcpEventBusBridge(event.result()))
        } else {
          f = InternalHelper.<TcpEventBusBridge>failure(event.cause())
        }
        handler.handle(f)
      }
    });
    return this;
  }
  public TcpEventBusBridge listen(int port, String address) {
    this.delegate.listen(port, address);
    return this;
  }
  public TcpEventBusBridge listen(int port, String address, Handler<AsyncResult<TcpEventBusBridge>> handler) {
    this.delegate.listen(port, address, new Handler<AsyncResult<io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge>>() {
      public void handle(AsyncResult<io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge> event) {
        AsyncResult<TcpEventBusBridge> f
        if (event.succeeded()) {
          f = InternalHelper.<TcpEventBusBridge>result(new TcpEventBusBridge(event.result()))
        } else {
          f = InternalHelper.<TcpEventBusBridge>failure(event.cause())
        }
        handler.handle(f)
      }
    });
    return this;
  }
  public TcpEventBusBridge listen(int port) {
    this.delegate.listen(port);
    return this;
  }
  public TcpEventBusBridge listen(int port, Handler<AsyncResult<TcpEventBusBridge>> handler) {
    this.delegate.listen(port, new Handler<AsyncResult<io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge>>() {
      public void handle(AsyncResult<io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge> event) {
        AsyncResult<TcpEventBusBridge> f
        if (event.succeeded()) {
          f = InternalHelper.<TcpEventBusBridge>result(new TcpEventBusBridge(event.result()))
        } else {
          f = InternalHelper.<TcpEventBusBridge>failure(event.cause())
        }
        handler.handle(f)
      }
    });
    return this;
  }
  public void close(Handler<AsyncResult<Void>> handler) {
    this.delegate.close(handler);
  }
  public void close() {
    this.delegate.close();
  }
}
