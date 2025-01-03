package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assume.assumeTrue;

@RunWith(VertxUnitRunner.class)
public class UnixDomainSocketTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private VertxInternal vertx;
  private SocketAddress domainSocketAddress;

  @Before
  public void before(TestContext context) throws Exception {
    vertx = (VertxInternal) Vertx.vertx();
    assumeTrue("Domain sockets not supported on this platform", vertx.transport().supportsDomainSockets());

    domainSocketAddress = SocketAddress.domainSocketAddress(new File(tmp.newFolder(), "bridge.sock").getAbsolutePath());

    Async async = context.async();

    TcpEventBusBridge bridge = TcpEventBusBridge.create(
      vertx,
      new BridgeOptions()
        .addInboundPermitted(new PermittedOptions())
        .addOutboundPermitted(new PermittedOptions()));

    bridge.listen(domainSocketAddress).onComplete(res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @After
  public void after(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testRegister(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient();
    final Async async = context.async();

    client.connect(domainSocketAddress).onComplete(conn -> {
      context.assertFalse(conn.failed());

      NetSocket socket = conn.result();

      // 1 reply will arrive
      // MESSAGE for echo
      final FrameParser parser = new FrameParser(parse -> {
        context.assertTrue(parse.succeeded());
        JsonObject frame = parse.result();

        context.assertNotEquals("err", frame.getString("type"));
        context.assertEquals("Vert.x", frame.getJsonObject("body").getString("value"));
        client.close();
        async.complete();
      });

      socket.handler(parser);

      FrameHelper.sendFrame("register", "echo", null, socket);

      // now try to publish a message so it gets delivered both to the consumer registred on the startup and to this
      // remote consumer

      FrameHelper.sendFrame("publish", "echo", new JsonObject().put("value", "Vert.x"), socket);
    });
  }
}
