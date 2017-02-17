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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple parser for event streams.
 *
 * @author apb@jhu.edu
 */
public class EventSource {

    static final Logger LOG = LoggerFactory.getLogger(EventSource.class);

    private final InputStream in;

    private Consumer<Event> onEvent = e -> {
    };

    private Consumer<Throwable> onException = e -> {

    };

    /**
     * Create an EventSource following the given stream.
     *
     * @param in the stream to follow.
     * @return EventSource following the stream.
     */
    public static EventSource from(final InputStream in) {
        return new EventSource(in);
    }

    private EventSource(final InputStream in) {
        this.in = in;
    }

    /**
     * Fire on a matching event.
     *
     * @param listener Listener for fired events.
     * @return configured EventSource.
     */
    public EventSource onEvent(final Consumer<Event> listener) {
        onEvent = listener;
        return this;
    }

    /**
     * Fire on an error consuming the event stream.
     *
     * @param listener listener for errors listening to the event stream.
     * @return Configured EventSource.
     */
    public EventSource onException(final Consumer<Throwable> listener) {
        onException = listener;
        return this;
    }

    /**
     * Start parsing an event stream, and firing events.
     */
    public void start() {

        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in, UTF_8), 256);

        Event event = new Event();
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                if (line.startsWith("id: ")) {
                    event.id = Integer.parseInt(value(line));
                } else if (line.startsWith("event: ")) {
                    event.event = value(line);
                } else if (line.startsWith("data: ")) {
                    if (event.data != "") {
                        event.data += "\n";
                    }
                    event.data += value(line);
                } else if ("".equals(line.trim())) {
                    // Blank lines fire events
                    onEvent.accept(event);
                    event = new Event();
                }
            }
        } catch (final Throwable t) {
            onException.accept(t);
        } finally {
            try {
                bufferedReader.close();
            } catch (final IOException e) {
                LOG.warn("Could not close event stream", e);
            }
        }
    }

    private static String value(final String line) {
        return line.substring(line.indexOf(" ")).trim();
    }

}
