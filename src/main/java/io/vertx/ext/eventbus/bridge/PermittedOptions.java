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
package io.vertx.ext.eventbus.bridge;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Specify a match to allow for inbound and outbound traffic.
 */
@DataObject
public class PermittedOptions {

  /**
   * The default permitted address : {@code null}.
   */
  public static String DEFAULT_ADDRESS = null;

  /**
   * The default permitted address regex : {@code null}.
   */
  public static String DEFAULT_ADDRESS_REGEX = null;

  private String address;
  private String addressRegex;

  public PermittedOptions() {
  }

  public PermittedOptions(PermittedOptions that) {
    address = that.address;
    addressRegex = that.addressRegex;
  }

  public PermittedOptions(JsonObject json) {
    address = json.getString("address", DEFAULT_ADDRESS);
    addressRegex = json.getString("addressRegex", DEFAULT_ADDRESS_REGEX);
  }

  public String getAddress() {
    return address;
  }

  /**
   * The exact address the message is being sent to. If you want to allow messages based on
   * an exact address you use this field.
   *
   * @param address the address
   * @return a reference to this, so the API can be used fluently
   */
  public PermittedOptions setAddress(String address) {
    this.address = address;
    return this;
  }

  public String getAddressRegex() {
    return addressRegex;
  }

  /**
   * A regular expression that will be matched against the address. If you want to allow messages
   * based on a regular expression you use this field. If the {@link #setAddress} value is specified
   * this will be ignored.
   *
   * @param addressRegex the address regex
   * @return a reference to this, so the API can be used fluently
   */
  public PermittedOptions setAddressRegex(String addressRegex) {
    this.addressRegex = addressRegex;
    return this;
  }
}
