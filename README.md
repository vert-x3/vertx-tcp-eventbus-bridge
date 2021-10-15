# vertx-tcp-eventbus-bridge

[![Build Status](https://github.com/vert-x3/vertx-tcp-eventbus-bridge/workflows/CI/badge.svg?branch=master)](https://github.com/vert-x3/vertx-tcp-eventbus-bridge/actions?query=workflow%3ACI)

This is a TCP eventbus bridge implementation.


## The protocol

The protocol is quite simple:

* 4bytes int32 message length (big endian encoding)
* JSON-RPC string (encoded in UTF-8)

### Messages from the bridge -> client

Every JSON-RPC Response Object as a server reply includes:

#### `jsonrpc: "2.0"`

A string specifying the version of the JSON-RPC protocol.

#### `result: "subtract"`

**REQUIRED** on success and  **MUST NOT** exist if there was an error invoking the method.

#### `error: "{"code": -32600, "message": "Invalid Request"}"`

**REQUIRED** on error existence and **MUST NOT** exist if there was no error triggered during invocation.

For an error, the object will also contain:
* `code: -32601`: A Number that indicates the error type that occurred and **MUST** be an integer.
message
* `message: "Method not found"`: A String providing a short description of the error and **SHOULD** be limited to a concise single sentence.
* `data` : **OPTIONAL** Primitive or Structured value that contains additional information about the error.

#### `id: "6"`

An identifier established that **MUST** contain a String, Number, or NULL value if included. If it is not included it is assumed to be a notification.

### Messages from the client -> bridge

Every JSON-RPC Request Object to a Server will include:

#### `jsonrpc: "2.0"`

A string specifying the version of the JSON-RPC protocol.

#### `method: "subtract"`

A String containing the name of the method to be invoked

#### `params: "{"subtrahend": 23, "minuend": 42}"` or `params: [45, 67, 42]`

(Optional) holds the parameter values to be used during the invocation of the method.

#### `id: "6"`

An identifier established and MUST contain a String, Number, or NULL value if included. If it is not included it is assumed to be a notification.

## Example

An example nodejs client is provided as an example using the same API as SockJS bridge e.g.:

```js
var EventBus = require('tcp-vertx-eventbus');

var eb = new EventBus('localhost', 7000);

eb.onopen = function () {
  // send a echo message
  eb.send('echo', {value: 'vert.x'}, function (err, res) {
    ...
  });
};
```

