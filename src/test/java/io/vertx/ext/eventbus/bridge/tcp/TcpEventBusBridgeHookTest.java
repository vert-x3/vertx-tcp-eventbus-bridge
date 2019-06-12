package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TcpEventBusBridgeHookTest {

  private final String address = "hello";

  private Vertx vertx;

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testRegister(TestContext context) {

    final JsonObject payload = new JsonObject().put("value", "Francesco");

    // 0. Start the bridge
    // 1. Check if REGISTER hook is called
    // 2. Check if REGISTERED hook is called
    // 3. Try to send a message while managing REGISTERED event
    // 4. Check if client receives the message

    Async bridgeStart = context.async();
    Async register = context.async();
    Async registered = context.async();
    Async request = context.async();

    TcpEventBusBridge bridge = TcpEventBusBridge.create(
      vertx,
      new BridgeOptions()
        .addInboundPermitted(new PermittedOptions())
        .addOutboundPermitted(new PermittedOptions()),
      new NetServerOptions(),
      be -> {
        if (be.type() == BridgeEventType.REGISTER) {
          context.assertNotNull(be.socket());
          context.assertEquals(address, be.getRawMessage().getString("address"));
          register.complete();
        } else if (be.type() == BridgeEventType.REGISTERED) {
          context.assertNotNull(be.socket());
          context.assertEquals(address, be.getRawMessage().getString("address"));

          // The client should be able to receive this message
          vertx.eventBus().send(address, payload);

          registered.complete();
        }
        be.complete(true);
      }
    );
    bridge.listen(7000, res -> {
      context.assertTrue(res.succeeded());
      bridgeStart.complete();
    });

    bridgeStart.await();

    NetClient client = vertx.createNetClient();

    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      // 1 reply will arrive
      // MESSAGE for address
      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();

        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals("Francesco", frame.getJsonObject("body").getString("value"));
        client.close();
        request.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", address, null, socket);
    });

    register.await();
    registered.await();
    request.await();

  }

}
