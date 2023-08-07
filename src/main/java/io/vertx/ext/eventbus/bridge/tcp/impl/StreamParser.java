/*
 * Copyright 2019 Red Hat, Inc.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 * <p>
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 * <p>
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public final class StreamParser implements Handler<Buffer> {

  private static final Logger LOGGER = LoggerFactory.getLogger(StreamParser.class);

  // the callback when a full response message has been decoded
  private Handler<Buffer> handler;
  private Handler<Throwable> exceptionHandler;

  // a composite buffer to allow buffer concatenation as if it was
  // a long stream
  private final ReadableBuffer buffer = new ReadableBuffer();

  public StreamParser handler(Handler<Buffer> handler) {
    this.handler = handler;
    return this;
  }

  public StreamParser exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public void handle(Buffer chunk) {
    if (chunk.length() > 0) {
      // add the chunk to the buffer
      buffer.append(chunk);

      // the minimum messages are "{}" or "[]"
      while (buffer.readableBytes() >= 2) {
        // setup a rollback point
        buffer.mark();

        final Buffer payload;

        try {
          // locate the message boundaries
          final int start = buffer.findSTX();

          // no start found yet
          if (start == -1) {
            buffer.reset();
            break;
          }

          final int end = buffer.findETX(start);

          // no end found yet
          if (end == -1) {
            buffer.reset();
            break;
          }

          payload = buffer.readBytes(start, end - start);
        } catch (IllegalStateException ise) {
          exceptionHandler.handle(ise);
          break;
        }

        // payload is found, deliver it to the handler
        try {
          handler.handle(payload);
        } catch (RuntimeException e) {
          // these are user exceptions, not protocol exceptions
          // we can continue the parsing job
          LOGGER.error("Failed to handle payload", e);
        }
      }
    }
  }
}
