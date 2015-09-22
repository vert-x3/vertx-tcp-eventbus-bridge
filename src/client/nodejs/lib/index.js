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
  headers = headers || {};
  var envelope = {
    type: action,
    headers: headers,
    body: message
  };

  envelope.headers.address = address;

  if (callback) {
    var replyAddress = uuid.v4();
    envelope.headers.replyAddress = replyAddress;
    REPLY_REGISTRY[replyAddress] = callback;
  }

  send(envelope);
}

/**
 * WireEncode the message to the bridge
 */
function send(message) {
  var json = JSON.stringify(message);

  var buffer = new Buffer(json.length + 4);
  buffer.writeInt32BE(json.length, 0);
  buffer.write(json, 4, json.length, 'utf-8');
  client.write(buffer);
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
    client.in = new Buffer(0);

    client.on('data', function (chunk) {
      client.in = Buffer.concat([client.in, chunk]);
      if (client.in.length >= 4) {
        var len = client.in.readInt32BE();
        if (client.in.length >= len + 4) {
          // we have a full message
          var json;

          try {
            json = JSON.parse(client.in.slice(4, len + 4).toString());
          } catch (e) {
            json = {type: 'err', headers: {message: e.message}};
          }

          var type = json.type;
          // force headers not to be undefined
          json.headers = json.headers || {};

          if (type === 'err') {
            if (self.onerror) {
              self.onerror(json.headers.message);
            } else {
              console.error('Error received on connection: ' + json.headers.message);
            }
            return;
          }

          var body = json.body;
          var replyAddress = json.headers.replyAddress;
          var address = json.headers.address;


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
                failureCode: json.headers.failureCode,
                failureType: json.headers.failureType,
                message: json.headers.message
              };
            }

            callback(err, body);
          }
        }
      }
    });

    client.on('error', function (err) {
      if (self.onerror) {
        self.onerror(err);
      } else {
        console.error(err);
      }
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
      return sendOrPub('message', address, message);
    }
    if (callback === undefined) {
      return sendOrPub('message', address, message, null, headers);
    }

    sendOrPub('message', address, message, headers, callback);
  },

  /**
   * Publishes a message to the given address.
   *
   * @param {String} address
   * @param {Object} message
   * @param {Object} [headers]
   */
  publish: function (address, message, headers) {
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
      send({
        type: 'register',
        headers: {
          address: address
        }
      });
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

        send({
          type: 'unregister',
          headers: {
            address: address
          }
        });

        delete CALLBACK_REGISTRY[address];
      }
    }
  },

  /**
   * Closes the connection.
   */
  close: function () {
    client.end();
  }
};