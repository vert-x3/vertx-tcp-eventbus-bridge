/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/** @module vertx-tcp-eventbus-bridge-js/frame */
var utils = require('vertx-js/util/utils');
var WriteStream = require('vertx-js/write_stream');
var MultiMap = require('vertx-js/multi_map');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JFrame = io.vertx.ext.eventbus.bridge.tcp.Frame;

/**
 TCP EventBus bridge Protocol Data Unit (Frame).

 @class
*/
var Frame = function(j_val) {

  var j_frame = j_val;
  var that = this;

  /**
   Get the action associated with this frame.

   @public

   @return {Object} the action
   */
  this.action = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return (j_frame["action()"]()).toString();
    } else utils.invalidArgs();
  };

  /**
   Get the headers for this frame. The headers are a multimap so they behave just like with HTTP.

   @public

   @return {MultiMap} the headers
   */
  this.headers = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return utils.convReturnVertxGen(j_frame["headers()"](), MultiMap);
    } else utils.invalidArgs();
  };

  /**
   Set the body for this message, internally it will know how to handle several types:
   * JsonObject
   * String
   * Buffer
  
   Any other type will raise an exception.

   @public
   @param body {Object} body value. 
   @return {Frame} self
   */
  this.setBody = function(body) {
    var __args = arguments;
    if (__args.length === 1 && true) {
      j_frame["setBody(java.lang.Object)"](utils.convParamTypeUnknown(body));
      return that;
    } else utils.invalidArgs();
  };

  /**
   Add a header to the frame. It uses the same semantic as with HTTP.

   @public
   @param key {string} header name 
   @param value {string} header value 
   @return {Frame} self
   */
  this.addHeader = function(key, value) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] === 'string' && typeof __args[1] === 'string') {
      j_frame["addHeader(java.lang.String,java.lang.String)"](key, value);
      return that;
    } else utils.invalidArgs();
  };

  /**
   Return the associated body, it will perform some conversion based on the `Content-Type` header, specifically for:
   * application/json
   * test/plain

   @public

   @return {Object} the body
   */
  this.getBody = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return utils.convReturnTypeUnknown(j_frame["getBody()"]());
    } else utils.invalidArgs();
  };

  /**
   Tries to return the body as JSON ignoring if the `Content-Type` header is set or not.

   @public

   @return {Object} json
   */
  this.toJSON = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return utils.convReturnJson(j_frame["toJSON()"]());
    } else utils.invalidArgs();
  };

  /**
   Write this frame into a WriteStream.

   @public
   @param stream {WriteStream} stream such as a socket. 
   */
  this.write = function(stream) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'object' && __args[0]._jdel) {
      j_frame["write(io.vertx.core.streams.WriteStream)"](stream._jdel);
    } else utils.invalidArgs();
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_frame;
};

/**
 Create a new Frame for a given action.

 @memberof module:vertx-tcp-eventbus-bridge-js/frame
 @param action {Object} one of the enum possibilities. 
 @return {Frame} A bare minimal frame.
 */
Frame.create = function(action) {
  var __args = arguments;
  if (__args.length === 1 && typeof __args[0] === 'string') {
    return utils.convReturnVertxGen(JFrame["create(io.vertx.ext.eventbus.bridge.tcp.Action)"](io.vertx.ext.eventbus.bridge.tcp.Action.valueOf(__args[0])), Frame);
  } else utils.invalidArgs();
};

// We export the Constructor function
module.exports = Frame;