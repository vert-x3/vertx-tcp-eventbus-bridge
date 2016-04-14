# vertx-tcp-eventbus-bridge

This is a TCP eventbus bridge implementation. The protocol is quite simple:

* 4bytes int32 message length (big endian encoding)
* json string
* built-in keys
	* type: (String, required) One of "send", "publish", "register", "unregister".
	* headers: (Object, optional) Headers with JSON format. Value of string type is supported.
	* body: (Object, optional) Message content in JSON format.
	* address: (String, required) Destination address
	* replyAddress: (String, optional) Address for replying to. If it's "#backtrack", then it will reply back to the request end. 

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