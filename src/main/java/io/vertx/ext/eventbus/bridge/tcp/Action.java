/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.eventbus.bridge.tcp;

/**
 * This are the possible actions that are allowed in the event bus bridge.
 *
 * * OK - ok messages do not contain any body, they just acknowledge a received frame
 * * ERROR - error messages are internal errors, not exceptions from the consumers
 * * MESSAGE - this is a message that has been sent by a consumer
 * * PUBLISH - works same as message but it publishes to the event bus
 * * REGISTER - registers a remote listener to the bridge
 * * UNREGISTER - unregisters a remote listener from the bridge
 */
public enum Action {
  OK,
  ERROR,
  MESSAGE,
  PUBLISH,
  REGISTER,
  UNREGISTER
}