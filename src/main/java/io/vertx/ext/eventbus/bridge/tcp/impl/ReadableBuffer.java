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

import io.vertx.core.buffer.Buffer;

final class ReadableBuffer {

  private static final int MARK_WATERMARK = 4 * 1024;

  private Buffer buffer;
  private int offset;
  private int mark;

  void append(Buffer chunk) {
    // either the buffer is null or all read
    if (buffer == null || Math.min(mark, offset) == buffer.length()) {
      buffer = chunk;
      offset = 0;
      return;
    }

    // slice the buffer discarding the read bytes
    if (
      // the offset (read operations) must be further than the last checkpoint
      offset >= mark &&
        // there must be already read more than water mark
        mark > MARK_WATERMARK &&
        // and there are more bytes to read already
        buffer.length() > mark) {

      // clean up when there's too much data
      buffer = buffer.getBuffer(mark, buffer.length());
      offset -= mark;
      mark = 0;
    }

    buffer.appendBuffer(chunk);
  }

  int findSTX() {
    for (int i = offset; i < buffer.length(); i++) {
      byte b = buffer.getByte(i);
      switch (b) {
        case '\r':
        case '\n':
          // skip new lines
          continue;
        case '{':
        case '[':
          return i;
        default:
          throw new IllegalStateException("Unexpected value in buffer: (int)" + ((int) b));
      }
    }

    return -1;
  }

  int findETX(int offset) {
    // brace start / end
    final byte bs, be;
    // brace count
    int bc = 0;

    switch (buffer.getByte(offset)) {
      case '{':
        bs = '{';
        be = '}';
        break;
      case '[':
        bs = '[';
        be = ']';
        break;
      default:
        throw new IllegalStateException("Message 1st byte isn't valid: " + buffer.getByte(offset));
    }

    for (int i = offset; i < buffer.length(); i++) {
      byte b = buffer.getByte(i);
      if (b == bs) {
        bc++;
      } else
      if (b == be) {
        bc--;
      } else {
        continue;
      }
      // validation
      if (bc < 0) {
        // unbalanced braces
        throw new IllegalStateException("Message format is not valid: " + buffer.getString(offset, i) + "...");
      }
      if (bc == 0) {
        // complete
        return i + 1;
      }
    }

    return -1;
  }

  Buffer readBytes(int offset, int count) {
    Buffer bytes = null;
    if (buffer.length() - offset >= count) {
      bytes = buffer.getBuffer(offset, offset + count);
      this.offset = offset + count;
    }
    return bytes;
  }

  int readableBytes() {
    return buffer.length() - offset;
  }

  void mark() {
    mark = offset;
  }

  void reset() {
    offset = mark;
  }


  @Override
  public String toString() {
    return buffer != null ? buffer.toString() : "null";
  }
}
