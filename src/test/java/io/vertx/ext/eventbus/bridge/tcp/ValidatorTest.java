package io.vertx.ext.eventbus.bridge.tcp;


import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.json.schema.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
 public class ValidatorTest {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  @Test
  public void testValidateSingle() {
    String path = "io/vertx/ext/eventbus/bridge/tcp/impl/protocol/jsonrpc.scehma.json";

    Validator validator = Validator.create(
      JsonSchema.of(new JsonObject(rule.vertx().fileSystem().readFileBlocking(path))),
      new JsonSchemaOptions()
        .setDraft(Draft.DRAFT202012)
        .setBaseUri("https://vertx.io")
    );

    JsonObject rpc = new JsonObject("{\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [42, 23], \"id\": 1}");

    assertTrue(validator.validate(rpc).getValid());
  }

  @Test
  public void testValidateBatch() {
    String path = "io/vertx/ext/eventbus/bridge/tcp/impl/protocol/jsonrpc.scehma.json";

    Validator validator = Validator.create(
      JsonSchema.of(new JsonObject(rule.vertx().fileSystem().readFileBlocking(path))),
      new JsonSchemaOptions()
        .setOutputFormat(OutputFormat.Basic)
        .setDraft(Draft.DRAFT202012)
        .setBaseUri("https://vertx.io")
    );

    JsonArray rpc = new JsonArray("[\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"sum\", \"params\": [1,2,4], \"id\": \"1\"},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"notify_hello\", \"params\": [7]},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [42,23], \"id\": \"2\"},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"foo.get\", \"params\": {\"name\": \"myself\"}, \"id\": \"5\"},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"get_data\", \"id\": \"9\"} \n" +
      "    ]");

    assertTrue(validator.validate(rpc).getValid());
  }

 }
