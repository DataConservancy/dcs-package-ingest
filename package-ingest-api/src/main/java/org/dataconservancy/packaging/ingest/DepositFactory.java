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
import java.util.Collections;
import java.util.Map;

/**
 * @author apb@jhu.edu
 */
public interface DepositFactory {

    /**
     * Create a depositor that deposits into the given container.
     *
     * @param depositInto URI of an LDP container.
     * @return Initialized Depositor.
     */
    public default Depositor newDepositer(URI depositInto) {
        return newDepositor(depositInto, Collections.emptyMap());
    }

    /**
     * Create a depositor that deposits into the given container.
     *
     * @param depositInto URI of the container to deposit into.
     * @param context Additional context.
     * @return Initialized Depositor.
     */
    public Depositor newDepositor(URI depositInto, Map<String, Object> context);
}
