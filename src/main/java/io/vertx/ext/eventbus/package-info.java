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

/**
 * = Vert.x-tcp-eventbus-bridge
 * :toc: left
 *
 * Vert.x-tcp-eventbus-bridge is a TCP bridge to Vert.x EventBus. To use this project, add the following
 * dependency to the _dependencies_ section of your build descriptor:
 *
 * * Maven (in your `pom.xml`):
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *   <groupId>{maven-groupId}</groupId>
 *   <artifactId>{maven-artifactId}</artifactId>
 *   <version>{maven-version}</version>
 * </dependency>
 * ----
 *
 * * Gradle (in your `build.gradle` file):
 *
 * [source,groovy,subs="+attributes"]
 * ----
 * compile {maven-groupId}:{maven-artifactId}:{maven-version}
 * ----
 *
 * The TCP EventBus bridge as its name states is a bridge built on top of TCP, meaning that any application able to
 * create sockets can use the EventBus from a remote Vert.x instance.
 *
 * The protocol has been kept as simple as possible and communications use Frames both ways. A typical frame looks like
 * this:
 *
 * ----
 * ACTION
 * Header: Value
 * HeaderN: ValueN
 *
 * payload
 * ^@
 * ----
 *
 * Where `^@` is the null character (byte 0).
 *
 * In order to support any type of payload the Bridge does not make any assumption on the payload, because of this there
 * are 2 headers that although not mandatory are good to be present.
 *
 * * Content-Type - Specify the content type of the payload as per link:http://www.iana.org/assignments/media-types/media-types.xhtml[media-types]
 * * Content-Length - The length in bytes of the payload
 *
 * When the `Content-Type` `application/json` is specified the Bridge will attempt to parse the body to a
 * {@link io.vertx.core.json.JsonObject} and return it as that type, when defined as `text/plain` the body is handler as
 * {@link java.lang.String}, for the rest it is assumed to be {@link io.vertx.core.buffer.Buffer}.
 *
 * Every received frame will return a frame. This is also the case for `PUBLISH`, `REGISTER` and `UNREGISTER`, in this
 * case you will receive either an acknowledgement or and error.
 */
@GenModule(name = "vertx-tcp-eventbus-bridge")
@Document(fileName = "index.adoc")
package io.vertx.ext.eventbus;

import io.vertx.codegen.annotations.GenModule;
import io.vertx.docgen.Document;
