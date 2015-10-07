/*
 * Copyright 2014 Red Hat, Inc.
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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Options for configuring the event bus bridge.
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@DataObject
public class BridgeOptions {

  private List<PermittedOptions> inboundPermitted = new ArrayList<>();
  private List<PermittedOptions> outboundPermitted = new ArrayList<>();

  /**
   * Copy constructor
   *
   * @param other  the options to copy
   */
  public BridgeOptions(BridgeOptions other) {
    this.inboundPermitted = new ArrayList<>(other.inboundPermitted);
    this.outboundPermitted = new ArrayList<>(other.outboundPermitted);
  }

  /**
   * Default constructor
   */
  public BridgeOptions() {
  }

  /**
   * Constructor from JSON
   *
   * @param json  the JSON
   */
  public BridgeOptions(JsonObject json) {
    JsonArray arr = json.getJsonArray("inboundPermitteds");
    if (arr != null) {
      for (Object obj: arr) {
        if (obj instanceof JsonObject) {
          JsonObject jobj = (JsonObject)obj;
          inboundPermitted.add(new PermittedOptions(jobj));
        } else {
          throw new IllegalArgumentException("Invalid type " + obj.getClass() + " in inboundPermitteds array");
        }
      }
    }
    arr = json.getJsonArray("outboundPermitteds");
    if (arr != null) {
      for (Object obj: arr) {
        if (obj instanceof JsonObject) {
          JsonObject jobj = (JsonObject)obj;
          outboundPermitted.add(new PermittedOptions(jobj));
        } else {
          throw new IllegalArgumentException("Invalid type " + obj.getClass() + " in outboundPermitteds array");
        }
      }
    }
  }

  public BridgeOptions addInboundPermitted(PermittedOptions permitted) {
    inboundPermitted.add(permitted);
    return this;
  }

  public List<PermittedOptions> getInboundPermitteds() {
    return inboundPermitted;
  }

  public void setInboundPermitted(List<PermittedOptions> inboundPermitted) {
    this.inboundPermitted = inboundPermitted;
  }

  public BridgeOptions addOutboundPermitted(PermittedOptions permitted) {
    outboundPermitted.add(permitted);
    return this;
  }

  public List<PermittedOptions> getOutboundPermitteds() {
    return outboundPermitted;
  }

  public void setOutboundPermitted(List<PermittedOptions> outboundPermitted) {
    this.outboundPermitted = outboundPermitted;
  }
}
