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

/**
 * Deposit workflow related events.
 *
 * @author apb@jhu.edu
 */
public enum EventType {
    /** A package entry has been deposited */
    DEPOSIT,

    /** URIs have been remapped in a resource. */
    REMAP,

    /** noop heartbeat */
    HEARTBEAT,

    /** An error has occurred */
    ERROR,

    /** Successful completion */
    SUCCESS;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
