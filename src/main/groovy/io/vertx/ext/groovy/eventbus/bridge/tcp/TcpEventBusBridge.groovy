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
import io.vertx.ext.bridge.BridgeOptions
import io.vertx.core.AsyncResult
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
    def ret= InternalHelper.safeCreate(io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge.create((io.vertx.core.Vertx)vertx.getDelegate(), options != null ? new io.vertx.ext.bridge.BridgeOptions(new io.vertx.core.json.JsonObject(options)) : null), io.vertx.ext.groovy.eventbus.bridge.tcp.TcpEventBusBridge.class);
    return ret;
  }
  public static TcpEventBusBridge create(Vertx vertx, Map<String, Object> options, Map<String, Object> netServerOptions) {
    def ret= InternalHelper.safeCreate(io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge.create((io.vertx.core.Vertx)vertx.getDelegate(), options != null ? new io.vertx.ext.bridge.BridgeOptions(new io.vertx.core.json.JsonObject(options)) : null, netServerOptions != null ? new io.vertx.core.net.NetServerOptions(new io.vertx.core.json.JsonObject(netServerOptions)) : null), io.vertx.ext.groovy.eventbus.bridge.tcp.TcpEventBusBridge.class);
    return ret;
  }
  /**
   * Listen on default port 7000
   * @return self
   */
  public TcpEventBusBridge listen() {
    this.delegate.listen();
    return this;
  }
  /**
   * Listen on default port 7000 with a handler to report the state of the socket listen operation.
   * @param handler the result handler
   * @return self
   */
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
  /**
   * Listen on specific port and bind to specific address
   * @param port tcp port
   * @param address tcp address to the bind
   * @return self
   */
  public TcpEventBusBridge listen(int port, String address) {
    this.delegate.listen(port, address);
    return this;
  }
  /**
   * Listen on specific port and bind to specific address
   * @param port tcp port
   * @param address tcp address to the bind
   * @param handler the result handler
   * @return self
   */
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
  /**
   * Listen on specific port
   * @param port tcp port
   * @return self
   */
  public TcpEventBusBridge listen(int port) {
    this.delegate.listen(port);
    return this;
  }
  /**
   * Listen on specific port
   * @param port tcp port
   * @param handler the result handler
   * @return self
   */
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
  /**
   * Close the current socket.
   * @param handler the result handler
   */
  public void close(Handler<AsyncResult<Void>> handler) {
    this.delegate.close(handler);
  }
  /**
   * Close the current socket.
   */
  public void close() {
    this.delegate.close();
  }
}
