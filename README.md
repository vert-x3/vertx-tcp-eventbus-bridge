# vertx-tcp-eventbus-bridge

[![Build Status](https://github.com/vert-x3/vertx-tcm-eventbus-bridge/workflows/CI/badge.svg?branch=master)](https://github.com/vert-x3/vertx-tcp-eventbus-bridge/actions?query=workflow%3ACI)

This is a TCP eventbus bridge implementation.


## The protocol

The protocol is quite simple:

* 4bytes int32 message length (big endian encoding)
* json string (encoded in UTF-8)

### Messages from the bridge -> client

Every JSON message will include a `type` key, with a string
value. Each type is shown below, along with the companion keys for
that type:

#### `type: "pong"`

`pong` requires no additional keys.

It is the response to the `ping` request from client to bridge.

####  `type: "err"`

* `message`: (string, required) The type of error, one of:
  `"access_denied"`, `"unknown_address"`, `"unknown_type"`

#### `type: "message"`

For a regular message, the object will also contain:

* `address`: (string, required) Destination address.
* `body`: (object, required) Message content as a JSON object.
* `headers`: (object, optional) Headers as a JSON object with String values.
* `replyAddress`: (string, optional) Address for replying to.
* `send`: (boolean, required) Will be `true` if the message is a send, `false` if a publish.

When a message from the client requests a reply, and that reply fails,
the object will instead contain:

* `failureCode`: (number, required) The failure code
* `failureType`: (string, required) The failure name
* `message`: (string, required) The message from the exception that signaled the failure

### Messages from the client -> bridge

The JSON object must contain a `type` key with a string value.  Each
type is shown below, along with the companion keys for that type:

#### `type: "send"`, `type: "publish"`

* `address`: (string, required) Destination address
* `body`: (object, required) Message content as a JSON object.
* `headers`: (object, optional) Headers as a JSON object with String values.
* `replyAddress`: (string, optional) Address for replying to.

When type is "send" if the message contains the key failureCode the original message
will be failed, content is then:
* `address`: (string, required) Destination address to fail
* `failureCode`: (integer, required) Failure code
* `message`: (string, required) Message that explains the failure

#### `type: "register"`, `type: "unregister"`

* `address`: (string, required) Destination address
* `headers`: (object, optional) Headers as a JSON object with String values.

#### `type: "ping"`

`ping` requires no additional keys.

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

