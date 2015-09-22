var assert = require('assert');
var EventBus = require('../lib');

describe('echo test', function () {
  it('should echo the same message that was sent', function (done) {
    EventBus.onerror = function (err) {
      console.error(err);
      assert.fail();
    };

    EventBus.connect(7000, 'localhost', function(err) {
      if (err) {
        assert.fail();
        return;
      }

      // send a echo message
      EventBus.send('echo', {value: 'vert.x'}, function (err, res) {
        if (err) {
          assert.fail();
          return;
        }

        assert.equal(res.value, 'vert.x');
        done();
      });
    });
  });
});
