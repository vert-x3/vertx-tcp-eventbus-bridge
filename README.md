# vertx-tcp-eventbus-bridge

This is a TCP eventbus bridge implementation. The protocol is quite simple:

* 4bytes int32 message length (big endian encoding)
* json string

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