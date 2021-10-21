package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.bridge.BridgeOptions;

/**
 * TCP EventBus Client for to connect to the event bus bridge
 *
 * @author <a href="mailto:gracebisheko@gmail.com">Nakiguli Grace</a>
 */

public interface TcpEventBusClient {

  /**create an event bus client */
  static TcpEventBusClient create(TcpEventBusClient tcpEventBusClient) {
    return create(tcpEventBusClient);
  }

  /**send message to eventBus address*/
  TcpEventBusClient send(String message);

  /**send message to eventBus address and expect a reply*/
  TcpEventBusClient send(String address, Handler<AsyncResult<TcpEventBusBridge>> handler);

  /**publish message to eventBus address*/
  TcpEventBusClient publish(String address, String body, DeliveryOptions deliveryOptions);

  /**Subscribe a consumer*/
  TcpEventBusClient register(Handler<AsyncResult<MessageConsumer<TcpEventBusBridge>>> handler);

  /**un subscribe a consumer if it does not want to listen*/
  TcpEventBusClient unRegister(Handler<AsyncResult<Void>> handler);

  /**close client*/
  void close(Handler<AsyncResult<Void>> handler);
}
