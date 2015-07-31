package io.vertx.ext.eventbus.bridge.tcp.impl.protocol;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.eventbus.bridge.tcp.Action;

public class FrameParser implements Handler<Buffer> {

  private Buffer _buffer;
  private int _offset;

  enum State {
    HEADERS,
    BODY,
    EOF
  }

  private final Handler<FrameImpl> client;

  public FrameParser(Handler<FrameImpl> client) {
    this.client = client;
  }

  private FrameImpl parseResult() throws IndexOutOfBoundsException {
    int start, end;

    State state = State.EOF;
    FrameImpl frame = null;

    while (bytesRemaining() > 0) {
      switch (state) {
        case HEADERS:
          end = packetEndOffset((byte) '\n');
          start = _offset;

          // include the delimiter
          _offset = end + 1;

          String line = _buffer.getString(start, end);

          if (line.length() > 0) {
            if (frame == null) {
              frame = new FrameImpl(Action.valueOf(line.toUpperCase()));
            } else {
              // add header
              int idx = line.indexOf(":");

              String key = line.substring(0, idx);
              String value = line.substring(idx + 1);
              // utility function to trim any whitespace before and after a string
              key = key.replaceAll("^\\s+|\\s+$", "");
              value = value.replaceAll("^\\s+|\\s+$", "");
              frame.addHeader(key, FrameImpl.unescape(value));
            }
          } else {
            if (frame != null) {
              state = State.BODY;
            }
          }

          break;
        case BODY:
          String contentLength = frame.headers().get("Content-Length");
          int read = -1;

          if (contentLength != null) {
            read = Integer.parseInt(contentLength);
          }

          Buffer body;

          if (read == -1) {
            end = packetEndOffset((byte) '\0');
            start = _offset;
          } else {
            start = _offset;
            end = start + read;

            if (end >= _buffer.length()) {
              throw new ArrayIndexOutOfBoundsException("needs more data");
            }
          }

          // include the delimiter
          _offset = end + 1;

          body = _buffer.getBuffer(start, end);
          frame.setBody(body);
          state = State.EOF;
          break;
        case EOF:
          if ((_buffer.length() - _offset) > 0) {
            if (_buffer.getByte(_offset + 1) == '\n') {
              _offset++;
              break;
            }
          }

          if (frame != null) {
            return frame;
          }

          state = State.HEADERS;
          break;
      }
    }

    // if the loop ended but the state was not EOF the we did not complete parsing the frame
    if (state != State.EOF) {
      throw new ArrayIndexOutOfBoundsException("need more data");
    }

    return frame;
  }

  public void handle(Buffer buffer) {
    append(buffer);

    FrameImpl ret;
    int offset;

    while (true) {
      // set a rewind point. if a failure occurs,
      // wait for the next handle()/append() and try again
      offset = _offset;
      try {
        // at least 1 byte
        if (bytesRemaining() == 0) {
          break;
        }

        ret = parseResult();

        if (ret == null) {
          break;
        }

        client.handle(ret);
      } catch (IndexOutOfBoundsException err) {
        // catch the error (not enough data), rewind, and wait
        // for the next packet to appear
        _offset = offset;
        break;
      }
    }
  }

  private void append(Buffer newBuffer) {
    if (newBuffer == null) {
      return;
    }

    // first run
    if (_buffer == null) {
      _buffer = newBuffer;

      return;
    }

    // out of data
    if (_offset >= _buffer.length()) {
      _buffer = newBuffer;
      _offset = 0;

      return;
    }

    // very large packet
    if (_offset > 0) {
      _buffer = _buffer.getBuffer(_offset, _buffer.length());
    }
    _buffer.appendBuffer(newBuffer);

    _offset = 0;
  }

  private int packetEndOffset(byte delim) throws ArrayIndexOutOfBoundsException {
    int offset = _offset;

    while (_buffer.getByte(offset) != delim) {
      offset++;

      if (offset >= _buffer.length()) {
        throw new ArrayIndexOutOfBoundsException("didn't see delimiter");
      }
    }

    return offset;
  }

  private int bytesRemaining() {
    return (_buffer.length() - _offset) < 0 ? 0 : (_buffer.length() - _offset);
  }
}