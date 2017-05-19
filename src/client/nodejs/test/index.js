var assert = require('assert');
var EventBus = require('../lib/tcp-vertx-eventbus');

describe('echo test', function () {
  it('should echo the same message that was sent', function (done) {
    var eb = new EventBus('localhost', 7000);


    eb.onerror = function (err) {
      console.error(err);
      assert.fail();
    };

    eb.onopen = function () {
      // send a echo message
      eb.send('echo', {value: 'vert.x'}, function (err, res) {
        if (err) {
          assert.fail();
          return;
        }

        assert.equal(res.body.value, 'vert.x');
        done();
      });
    };
  });
});


describe('send v. publish', function () {
  it('should deliver sends to one listener', function (done) {
    var eb = new EventBus('localhost', 7000);

    eb.onerror = function (err) {
      console.error(err);
      assert.fail();
    };

    var messages = [];
    
    var handler = function (err, msg) {
      if (err) {
        assert.fail();
        return;
      }
      
      messages.push(msg);
    };
    
    eb.onopen = function () {
      eb.registerHandler("echo2_response", handler);
      eb.registerHandler("echo2_response", handler);

      setTimeout(function () {
        assert.equal(messages.length, 1);
        done();
      }, 50);
      
      eb.send('echo2', {response_type: 'send'}, function (err, res) {
        if (err) {
          assert.fail();
        }
      });
    };
  });

  it('should deliver publishes to every listener', function (done) {
    var eb = new EventBus('localhost', 7000);

    eb.onerror = function (err) {
      console.error(err);
      assert.fail();
    };

    var messages = [];
    
    var handler = function (err, msg) {
      if (err) {
        assert.fail();
        return;
      }
      
      messages.push(msg);
    };
    
    eb.onopen = function () {
      eb.registerHandler("echo2_response", handler);
      eb.registerHandler("echo2_response", handler);

      setTimeout(function () {
        assert.equal(messages.length, 2);
        done();
      }, 50);
      
      eb.send('echo2', {response_type: 'publish'}, function (err, res) {
        if (err) {
          assert.fail();
        }
      });
    };
  });
});
