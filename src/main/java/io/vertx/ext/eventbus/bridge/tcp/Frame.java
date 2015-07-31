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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.eventbus.bridge.tcp.impl.protocol.FrameImpl;

/**
 * TCP EventBus bridge Protocol Data Unit (Frame).
 *
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@VertxGen
public interface Frame {

  /**
   * Create a new Frame for a given action.
   *
   * @param action one of the enum possibilities.
   *
   * @return A bare minimal frame.
   */
  static Frame create(Action action) {
    return new FrameImpl(action);
  }

  /**
   * Get the action associated with this frame.
   *
   * @return the action
   */
  Action action();

  /**
   * Get the headers for this frame. The headers are a multimap so they behave just like with HTTP.
   *
   * @return the headers
   */
  MultiMap headers();

  /**
   * Set the body for this message, internally it will know how to handle several types:
   * * JsonObject
   * * String
   * * Buffer
   *
   * Any other type will raise an exception.
   *
   * @param body body value.
   * @return self
   */
  @Fluent
  Frame setBody(Object body);

  /**
   * Add a header to the frame. It uses the same semantic as with HTTP.
   *
   * @param key header name
   * @param value header value
   * @return self
   */
  @Fluent
  Frame addHeader(String key, String value);

  /**
   * Return the associated body, it will perform some conversion based on the `Content-Type` header, specifically for:
   * * application/json
   * * test/plain
   *
   * @param <R> converted body type
   * @return the body
   */
  <R> R getBody();

  /**
   * Tries to return the body as JSON ignoring if the `Content-Type` header is set or not.
   * @return json
   */
  JsonObject toJSON();

  /**
   * Write this frame into a WriteStream.
   *
   * @param stream stream such as a socket.
   */
  void write(WriteStream stream);

}
