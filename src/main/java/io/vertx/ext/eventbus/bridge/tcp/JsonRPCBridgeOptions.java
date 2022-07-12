package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.BridgeOptionsConverter;

public class JsonRPCBridgeOptions extends BridgeOptions {
  private boolean websocketsTextAsFrame;

  public JsonRPCBridgeOptions() {}

  /**
   * Creates a new instance of {@link JsonRPCBridgeOptions} by copying the content of another {@link JsonRPCBridgeOptions}
   *
   * @param that the {@link JsonRPCBridgeOptions} to copy.
   */
  public JsonRPCBridgeOptions(JsonRPCBridgeOptions that) {
    super(that);
    this.websocketsTextAsFrame = that.websocketsTextAsFrame;
  }

  /**
   * Creates a new instance of {@link JsonRPCBridgeOptions} from its JSON representation.
   * This method uses the generated converter.
   *
   * @param json the serialized {@link JsonRPCBridgeOptions}
   * @see BridgeOptionsConverter
   */
  public JsonRPCBridgeOptions(JsonObject json) {
    BridgeOptionsConverter.fromJson(json, this);
    this.websocketsTextAsFrame = json.getBoolean("websocketsTextAsFrame", false);
  }

  /**
   * Serializes the current {@link JsonRPCBridgeOptions} to JSON. This method uses the generated converter.
   *
   * @return the serialized object
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    BridgeOptionsConverter.toJson(this, json);
    json.put("websocketsTextAsFrame", websocketsTextAsFrame);
    return json;
  }

  /**
   * Sets whether to use text format for websockets frames for the current {@link JsonRPCBridgeOptions}.
   *
   * @param websocketsTextAsFrame the choice whether to use text format
   * @return the current {@link JsonRPCBridgeOptions}.
   */
  public JsonRPCBridgeOptions setWebsocketsTextAsFrame(boolean websocketsTextAsFrame) {
    this.websocketsTextAsFrame = websocketsTextAsFrame;
    return this;
  }

  /**
   * @return whether to use text format for websockets frames.
   */
  public boolean getWebsocketsTextAsFrame() {
    return websocketsTextAsFrame;
  }

}
