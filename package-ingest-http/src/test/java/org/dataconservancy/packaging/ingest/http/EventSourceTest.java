/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.ingest.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * @author apb@jhu.edu
 */
public class EventSourceTest {

    @Test
    public void eventParsingTest() throws Exception {
        final List<Event> events = new ArrayList<>();
        final String input =
                "id: 1 \n" +
                        "event: a\n" +
                        "data: data a\n" +
                        "\n" +
                        "\n" +
                        ":\n" +
                        ":\n" +
                        "data: c\n" +
                        "\n";
        EventSource.from(IOUtils.toInputStream(input, UTF_8)).onEvent(e -> {
            events.add(e);
        }).onException(x -> {
            fail("Exception thrown");
        }).start();

        assertEquals(3, events.size());
        final Event event1 = events.get(0);
        final Event event2 = events.get(1);
        final Event event3 = events.get(2);

        assertEquals(1, event1.id());
        assertEquals("a", event1.event());
        assertEquals("data a", event1.data());

        assertEquals("message", event2.event());
        assertEquals("", event2.data);

        assertEquals("message", event3.event());
        assertEquals("c", event3.data());
    }

    @Test
    public void multiLineTest() throws Exception {
        final List<Event> events = new ArrayList<>();
        final String input =
                "id: 1 \n" +
                        "event: a\n" +
                        "data: data a\n" +
                        "data: b\n" +
                        "data: c\n" +
                        "\n";
        EventSource.from(IOUtils.toInputStream(input, UTF_8)).onEvent(e -> {
            events.add(e);
        }).onException(x -> {
            fail("Exception thrown");
        }).start();

        assertEquals(1, events.size());
        final Event event = events.get(0);

        assertEquals("a", event.event());
        assertEquals("data a\nb\nc", event.data());
    }

    @Test
    public void exceptionTest() throws Exception {
        final CountDownLatch expectedExceptionCount = new CountDownLatch(1);
        final RuntimeException e = new RuntimeException("exception!");
        final InputStream stream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw e;
            }
        };

        EventSource.from(stream).onEvent(v -> {
            fail("Exception thrown");
        }).onException(x -> {
            expectedExceptionCount.countDown();
        }).start();

        expectedExceptionCount.await(1, TimeUnit.SECONDS);
    }
}
