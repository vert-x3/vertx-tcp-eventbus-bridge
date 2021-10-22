package io.vertx.ext.eventbus.bridge.tcp;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public class EventBusBridgeRestIT {
  @BeforeClass
  public static void configureRestAssured() {
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = Integer.getInteger("http.port", 8080);
  }

  @AfterClass
  public static void unConfigureRestAssured() {
    RestAssured.reset();
  }


}
