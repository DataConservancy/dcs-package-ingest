/*
 * Copyright 2016 Johns Hopkins University
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
package org.dataconservancy.packaging.ingest.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;

/**
 * Produces new camel contexts
 * <p>
 * The underlying implementation is responsible for assuring that the identities
 * of all created contexts are available from each other's registry.
 * </p>
 */
public interface ContextFactory {

    public default CamelContext newContext(String id) {
    	return newContext(id, null);
    }
    
    public CamelContext newContext(String id, Registry registry);

    /**
     * Creates a new, empty CamelContext.
     * 
     * @return A new CamelContext.
     */
    public default CamelContext newContext() {
        return newContext(null, null);
    }
}