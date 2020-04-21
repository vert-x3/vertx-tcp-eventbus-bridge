package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.eventbus.bridge.tcp.impl.StreamParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class StreamParserTest {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  @Test(timeout = 30_000)
  public void testParseSimple(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler((contentType, body) -> {
        System.out.println(body.toString());
        test.complete();
      });

    parser.handle(Buffer.buffer(
      "Content-Length: 38\r\n" +
      "Content-Type: application/vscode-jsonrpc;charset=utf-8\r\n" +
      "\r\n" +
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"hi\"}"));
  }

  @Test(timeout = 30_000)
  public void testParseSimpleHeaderless(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler((contentType, body) -> {
        System.out.println(body.toString());
        test.complete();
      });

    parser.handle(Buffer.buffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"hi\"}\r\n"));
  }
}
