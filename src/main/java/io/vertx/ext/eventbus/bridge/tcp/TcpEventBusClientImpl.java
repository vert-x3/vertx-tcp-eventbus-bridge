package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * TCP EventBus Client implementation
 *
 * @author <a href="mailto:gracebisheko@gmail.com">Nakiguli Grace</a>
 */

public class TcpEventBusClientImpl implements TcpEventBusClient{

  @Override
  public TcpEventBusClient send(String String) {
    return null;
  }

  @Override
  public TcpEventBusClient send(String address,
    Handler<AsyncResult<TcpEventBusBridge>> handler) {
    return null;
  }

  @Override
  public TcpEventBusClient publish(String address, String body, DeliveryOptions deliveryOptions) {
    return null;
  }

  @Override
  public TcpEventBusClient register(
    Handler<AsyncResult<MessageConsumer<TcpEventBusBridge>>> handler) {
    return null;
  }

  @Override
  public TcpEventBusClient unRegister(Handler<AsyncResult<Void>> handler) {
    return null;
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {

  }
}
