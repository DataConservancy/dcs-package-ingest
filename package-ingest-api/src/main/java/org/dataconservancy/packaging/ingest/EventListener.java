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

package org.dataconservancy.packaging.ingest;

import java.net.URI;

/**
 * Notification for events during a deposit.
 *
 * @author apb@jhu.edu
 */
public interface EventListener {

    /**
     * Respond to an event.
     *
     * @param type Type of event
     * @param repositoryResource Repository resource relevant to the event, null otherwise.
     * @param resource The packaged resource. May be null.
     * @param detail Event-specific additional informaiton. May be null.
     */
    public void onEvent(EventType type, URI repositoryResource, PackagedResource resource, Object detail);
}
