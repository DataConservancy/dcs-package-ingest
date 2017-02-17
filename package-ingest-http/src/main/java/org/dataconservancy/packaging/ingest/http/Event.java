/*
 * Copyright 2017 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

/**
 * ServerSentEvent event.
 *
 * @author apb@jhu.edu
 */
public class Event {

    int id = 0;

    String event = "message";

    String data = "";

    /**
     * Event ID.
     *
     * @return id;
     */
    public int id() {
        return id;
    }

    /**
     * Event name.
     *
     * @return Event name.
     */
    public String event() {
        return event;
    }

    /**
     * Event data.
     *
     * @return data, or empty string if none.
     */
    public String data() {
        return data;
    }

}
