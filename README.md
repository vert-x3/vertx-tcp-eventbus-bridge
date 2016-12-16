# vertx-tcp-eventbus-bridge

This is a TCP eventbus bridge implementation.


## The protocol

The protocol is quite simple:

* 4bytes int32 message length (big endian encoding)
* json string (encoded in UTF-8)

### Messages from the bridge -> client

Every JSON message will include a `type` key, with a string
value. Each type is shown below, along with the companion keys for
that type:

####  `type: "err"`

* `message`: (string, required) The type of error, one of:
  `"access_denied"`, `"address_required"`, `"unknown_address"`,
  `"unknown_type"`

#### `type: "message"`

For a regular message, the object will also contain:

* `address`: (string, required) Destination address.
* `body`: (object, required) Message content as a JSON object.
* `headers`: (object, optional) Headers as a JSON object with String values.
* `replyAddress`: (string, optional) Address for replying to.

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

