/*
 * Copyright 2018 Red Hat, Inc.
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

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.docgen.Source;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;

/**
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@Source
public class TCPBridgeExamples {

  public void example1(Vertx vertx) {
    TcpEventBusBridge bridge = TcpEventBusBridge.create(
        vertx,
        new BridgeOptions()
            .addInboundPermitted(new PermittedOptions().setAddress("in"))
            .addOutboundPermitted(new PermittedOptions().setAddress("out")));

    bridge.listen(7000).onComplete(res -> {
      if (res.succeeded()) {
        // succeed...
      } else {
        // fail...
      }
    });
  }

  public void serverWithDomainSockets(TcpEventBusBridge bridge) {
    SocketAddress domainSocketAddress = SocketAddress.domainSocketAddress("/var/tmp/bridge.sock");

    bridge.listen(domainSocketAddress).onComplete(res -> {
      if (res.succeeded()) {
        // succeed...
      } else {
        // fail...
      }
    });
  }
}
