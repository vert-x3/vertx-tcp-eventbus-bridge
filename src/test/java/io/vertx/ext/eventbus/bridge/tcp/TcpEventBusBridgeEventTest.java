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

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.json.JsonObject;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameHelper;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

@RunWith(VertxUnitRunner.class)
public class TcpEventBusBridgeEventTest {

  private static final Logger logger = LoggerFactory.getLogger(TcpEventBusBridgeEventTest.class);

  private Vertx vertx;

  private SSLKeyPairCerts sslKeyPairCerts;

  @Before
  public void before(TestContext context) {
    vertx = Vertx.vertx();
    final Async async = context.async();
    vertx.eventBus().consumer("hello", (Message<JsonObject> msg) -> msg.reply(new JsonObject().put("value", "Hello " + msg.body().getString("value"))));
    vertx.eventBus().consumer("echo", (Message<JsonObject> msg) -> msg.reply(msg.body()));
    vertx.setPeriodic(1000, __ -> vertx.eventBus().send("ping", new JsonObject().put("value", "hi")));
    sslKeyPairCerts = new SSLKeyPairCerts().createTwoWaySSL();
    TcpEventBusBridge bridge = TcpEventBusBridge.create(
      vertx,
      new BridgeOptions()
        .addInboundPermitted(new PermittedOptions().setAddress("hello"))
        .addInboundPermitted(new PermittedOptions().setAddress("echo"))
        .addInboundPermitted(new PermittedOptions().setAddress("test"))
        .addOutboundPermitted(new PermittedOptions().setAddress("echo"))
        .addOutboundPermitted(new PermittedOptions().setAddress("ping")),
      new NetServerOptions()
        .setClientAuth(ClientAuth.REQUEST)
        .setSsl(true)
        .setTrustStoreOptions(sslKeyPairCerts.getServerTrustStore())
        .setKeyStoreOptions(sslKeyPairCerts.getServerKeyStore()),
      be -> {
        logger.info("Handled a bridge event " + be.getRawMessage());
        if (be.<NetSocket>socket().isSsl()) {
          try {
            for (Certificate c : be.<NetSocket>socket().peerCertificates()) {
              logger.info(((X509Certificate)c).getSubjectDN().toString());
            }
          } catch (SSLPeerUnverifiedException e) {
            throw new RuntimeException("Failed to get peer certificates chain", e);
          }
        }
        be.complete(true);
      });
    bridge.listen(7000, res -> {
      context.assertTrue(res.succeeded());
      async.complete();
    });
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testSendVoidMessage(TestContext context) {
    // Send a request and get a response
    NetClient client = vertx.createNetClient(new NetClientOptions()
      .setSsl(true)
      .setTrustStoreOptions(sslKeyPairCerts.getClientTrustStore())
      .setKeyStoreOptions(sslKeyPairCerts.getClientKeyStore()));
    final Async async = context.async();
    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      client.close();
      async.complete();
    });
    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());
      NetSocket socket = conn.result();
      FrameHelper.sendFrame("send", "test", new JsonObject().put("value", "vert.x"), socket);
    });
  }

  @Test
  public void testSendVoidMessageTrustAll(TestContext context) {
    NetClient client = vertx.createNetClient(new NetClientOptions()
      .setSsl(true)
      .setTrustAll(true)
      .setKeyStoreOptions(sslKeyPairCerts.getClientKeyStore())
    );
    final Async async = context.async();
    vertx.eventBus().consumer("test", (Message<JsonObject> msg) -> {
      client.close();
      async.complete();
    });
    client.connect(7000, "localhost", conn -> {
      context.assertFalse(conn.failed());
      NetSocket socket = conn.result();
      FrameHelper.sendFrame("send", "test", new JsonObject().put("value", "vert.x"), socket);
    });
  }

}
