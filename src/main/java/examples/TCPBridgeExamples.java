package examples;

import io.vertx.core.Vertx;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.TcpEventBusBridge;

/**
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class TCPBridgeExamples {

  public void example1(Vertx vertx) {

    TcpEventBusBridge bridge = TcpEventBusBridge.create(
        vertx,
        new BridgeOptions()
            .addInboundPermitted(new PermittedOptions().setAddress("in"))
            .addOutboundPermitted(new PermittedOptions().setAddress("out")));

    bridge.listen(7000, res -> {
      if (res.succeeded()) {
        // succeed...
      } else {
        // fail...
      }
    });

  }
}
