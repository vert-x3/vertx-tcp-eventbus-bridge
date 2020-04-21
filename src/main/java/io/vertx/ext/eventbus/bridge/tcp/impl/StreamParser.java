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

import java.nio.charset.StandardCharsets;

public final class StreamParser implements Handler<Buffer> {

  private static final Logger log = LoggerFactory.getLogger(StreamParser.class);

  private static final Buffer EMPTY = Buffer.buffer(0);
  private static final byte[] CONTENT_TYPE = "Content-Type:".toLowerCase().getBytes();
  private static final byte[] CONTENT_LENGTH = "Content-Length:".toLowerCase().getBytes();

  // the callback when a full response message has been decoded
  private ParserHandler handler;
  private Handler<Throwable> exceptionHandler;

  // a composite buffer to allow buffer concatenation as if it was
  // a long stream
  private final ReadableBuffer buffer = new ReadableBuffer();

  public StreamParser handler(ParserHandler handler) {
    this.handler = handler;
    return this;
  }

  public StreamParser exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  // parser state machine state
  private boolean eol = true;
  private int contentLength = 0;
  private String contentType = null;

  @Override
  public void handle(Buffer chunk) {
    // add the chunk to the buffer
    buffer.append(chunk);

    while (buffer.readableBytes() >= (eol ? 3 : contentLength)) {
      // setup a rollback point
      buffer.mark();

      // we need to locate the eol
      if (eol) {
        // locate the eol and handle as a C string
        final int start = buffer.offset();
        final int eol = buffer.findLineEnd(); // including \r\n
        final int end = eol - 2;

        // not found at all
        if (eol == -1) {
          buffer.reset();
          break;
        }

        final int length = eol - start - 2;

        if (length == 0) {
          // switch from headers to content
          this.eol = false;
        } else {
          // process line
          if (
            ('{' == buffer.getByte(start) && '}' == buffer.getByte(end - 1)) ||
            ('[' == buffer.getByte(start) && ']' == buffer.getByte(end - 1))
          ) {
            // special case, when no headers were used
            Buffer payload = buffer.readBytes(length);
            if (handler != null) {
              try {
                handler.handle(contentType, payload);
              } catch (RuntimeException e) {
                log.error("Cannot handle message", e);
              }
            } else {
              log.info("No handler expecting: " + payload);
            }
            // reset as this was a message without headers
            contentType = null;
            contentLength = 0;
          }
          else if (buffer.startsWith(CONTENT_TYPE, end)) {
            // guaranteed to be possible as the startswith passed
            buffer.skip(13);
            // skip white space
            while (buffer.getByte(buffer.offset()) == ' ') {
              buffer.skip(1);
            }
            contentType = buffer.readLine(end, StandardCharsets.UTF_8);
          }
          else if (buffer.startsWith(CONTENT_LENGTH, end)) {
            // guaranteed to be possible as the startswith passed
            buffer.skip(15);
            // skip white space
            while (buffer.getByte(buffer.offset()) == ' ') {
              buffer.skip(1);
            }

            try {
              // set the expected content length
              contentLength = buffer.readInt(end);
            } catch (RuntimeException e) {
              if (exceptionHandler != null) {
                exceptionHandler.handle(e);
              } else {
                log.error("Cannot parse Content-Length", e);
              }
              return;
            }
          } else {
            log.warn("Unhandled header: " + buffer.readLine(end, StandardCharsets.UTF_8));
          }
        }
        // skip \r\n
        buffer.skip(2);
      } else {
        // empty string
        if (contentLength == 0) {
          // special case as we don't need to allocate objects for this
          if (handler != null) {
            try {
              handler.handle(contentType, EMPTY);
            } catch (RuntimeException e) {
              log.error("Cannot handle message", e);
            }
          } else {
            log.info("No handler expecting: \"\"");
          }
        } else {
          // fixed length parsing && read the required bytes
          Buffer b = buffer.readBytes(contentLength);
          if (handler != null) {
            try {
              handler.handle(contentType, b);
            } catch (RuntimeException e) {
              log.error("Cannot handle message", e);
            }
          } else {
            log.info("No handler expecting: " + b);
          }
        }
        // switch back to eol parsing
        eol = true;
        contentType = null;
        contentLength = 0;
      }
    }
  }
}
