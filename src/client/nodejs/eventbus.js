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

var net = require('net');
var uuid = require('node-uuid');

// Parse lines

var stream = require('stream');
var liner = new stream.Transform( { objectMode: true } );

liner._transform = function (chunk, encoding, done) {
  var data = chunk.toString();
  if (this._lastLineData) data = this._lastLineData + data;

  var lines = data.split('\n');
  this._lastLineData = lines.splice(lines.length-1,1)[0];

  lines.forEach(this.push.bind(this));
  done();
};

liner._flush = function (done) {
  if (this._lastLineData) this.push(this._lastLineData);
  this._lastLineData = null;
  done();
};

// end of parsing of lines

var client;

var CALLBACK_REGISTRY = {};
var REPLY_REGISTRY = {};

/**
 * This is the internal function that sends messages over TCP
 *
 * @param {String} action
 * @param {String} address
 * @param {Object} message
 * @param {Object} [headers]
 * @param {Function} [callback]
 */
function sendOrPub(action, address, message, headers, callback) {
  var envelope = {
    type: action,
    address: address,
    body: message
  };

  if (headers) {
    envelope.headers = headers;
  }

  if (callback) {
    var replyAddress = uuid.v4();
    envelope.replyAddress = replyAddress;
    REPLY_REGISTRY[replyAddress] = callback;
  }

  client.write(JSON.stringify(envelope));
  client.write('\n');
}

module.exports = {
  /**
   * Connect to vert.x3 eventbus.
   *
   * @param {Number} port
   * @param {String} [address]
   * @param {Function} [callback]
   */
  connect: function (port, address, callback) {
    var self = this;

    CALLBACK_REGISTRY = {};
    REPLY_REGISTRY = {};

    client = net.connect(port, address, callback);

    client.pipe(liner);
    liner.on('readable', function () {
      var line;
      while (line = liner.read()) {
        var json = JSON.parse(line);

        var type = json.type;

        if (type === 'err') {
          if (self.onerror) {
            self.onerror(json.body);
          } else {
            console.error('Error received on connection: ' + json.body);
          }
          return;
        }

        var body = json.body;
        var replyAddress = json.replyAddress;
        var address = json.address;

        if (replyAddress) {
          if (!body) {
            body = {};
          }

          Object.defineProperty(body, "reply", {
            enumerable: false,
            value: function (reply, callback) {
              self.send(replyAddress, reply, callback);
            }
          });
        }

        var callbacks = CALLBACK_REGISTRY[address];

        if (callbacks) {
          // We make a copy since the handler might get unregistered from within the
          // handler itself, which would screw up our iteration
          var copy = callbacks.slice(0);
          for (var i = 0; i < copy.length; i++) {
            copy[i](body);
          }
        }

        var callback = REPLY_REGISTRY[address];

        if (callback) {
          delete REPLY_REGISTRY[address];
          var err;
          if (json.failureCode !== undefined) {
            err = {
              failureCode: json.failureCode,
              failureType: json.failureType,
              message: json.message
            };
          }

          callback(err, body);
        }
      }
    });

    client.on('end', function() {
      console.log('end');
    });

    client.on('error', function (err) {
      console.log(err);
    });
  },

  /**
   * Sends a message to the given address.
   *
   * @param {String} address
   * @param {Object} message
   * @param {Object} [headers]
   * @param {Function} [callback]
   */
  send: function (address, message, headers, callback) {
    if (headers === undefined && callback === undefined) {
      return sendOrPub('send', address, message);
    }
    if (callback === undefined) {
      return sendOrPub('send', address, message, null, headers);
    }

    sendOrPub('send', address, message, headers, callback);
  },

  /**
   * Publishes a message to the given address.
   *
   * @param {String} address
   * @param {Object} message
   * @param {Object} [headers]
   */
  publish: function (address,  message, headers) {
    if (headers === undefined) {
      return sendOrPub('publish', address, message);
    }

    sendOrPub('publish', address, message, headers);
  },

  /**
   * Registers a local callback to a given address.
   *
   * @param {String} address
   * @param {Function} callback
   */
  register: function (address, callback) {
    var callbacks = CALLBACK_REGISTRY[address];

    if (!callbacks) {
      callbacks = [callback];
      CALLBACK_REGISTRY[address] = callbacks;

      // First handler for this address so we should register the connection
      var msg = {
        type: 'register',
        address: address
      };
      client.write(JSON.stringify(msg));
      client.write('\n');
    } else {
      callbacks.push(callback);
    }
  },

  /**
   * Unregisters a local callback from a given address.
   *
   * @param {String} address
   * @param {Function} callback
   */
  unregister: function (address, callback) {
    var callbacks = CALLBACK_REGISTRY[address];
    if (callbacks) {
      var idx = callbacks.indexOf(callback);
      if (idx != -1) callbacks.splice(idx, 1);
      if (callbacks.length == 0) {
        // No more local handlers so we should unregister the connection

        var msg = {
          type: 'unregister',
          address: address
        };

        client.write(JSON.stringify(msg));
        client.write('\n');
        delete CALLBACK_REGISTRY[address];
      }
    }
  },

  /**
   * Closes the connection.
   */
  close: function() {
    client.end();
  }
};