package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.bridge.BridgeOptionsConverter;
import io.vertx.ext.bridge.PermittedOptions;

import java.util.List;

@DataObject(generateConverter = true)
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


  /**
   * Adds an inbound permitted option to the current {@link JsonRPCBridgeOptions}.
   *
   * @param permitted the inbound permitted
   * @return the current {@link JsonRPCBridgeOptions}.
   */
  public JsonRPCBridgeOptions addInboundPermitted(PermittedOptions permitted) {
    super.addInboundPermitted(permitted);
    return this;
  }

  /**
   * @return the list of inbound permitted options. Empty if none.
   */
  public List<PermittedOptions> getInboundPermitteds() {
    return super.getInboundPermitteds();
  }

  /**
   * Sets the list of inbound permitted options.
   *
   * @param inboundPermitted the list to use, must not be {@link null}. This method use the direct list reference
   *                         (and doesn't create a copy).
   * @return the current {@link JsonRPCBridgeOptions}.
   */
  public JsonRPCBridgeOptions setInboundPermitteds(List<PermittedOptions> inboundPermitted) {
    super.setInboundPermitteds(inboundPermitted);
    return this;
  }

  /**
   * Adds an outbound permitted option to the current {@link JsonRPCBridgeOptions}.
   *
   * @param permitted the outbound permitted
   * @return the current {@link JsonRPCBridgeOptions}.
   */
  public JsonRPCBridgeOptions addOutboundPermitted(PermittedOptions permitted) {
    super.addOutboundPermitted(permitted);
    return this;
  }

  /**
   * @return the list of outbound permitted options. Empty if none.
   */
  public List<PermittedOptions> getOutboundPermitteds() {
    return super.getOutboundPermitteds();
  }

  /**
   * Sets the list of outbound permitted options.
   *
   * @param outboundPermitted the list to use, must not be {@link null}. This method use the direct list reference
   *                         (and doesn't create a copy).
   * @return the current {@link JsonRPCBridgeOptions}.
   */
  public JsonRPCBridgeOptions setOutboundPermitteds(List<PermittedOptions> outboundPermitted) {
    super.setOutboundPermitteds(outboundPermitted);
    return this;
  }

}
