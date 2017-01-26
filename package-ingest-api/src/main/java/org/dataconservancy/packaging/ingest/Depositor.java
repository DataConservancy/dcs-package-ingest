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

package org.dataconservancy.packaging.ingest;

import java.net.URI;
import java.util.Map;

/**
 * @author apb@jhu.edu
 */
public interface Depositor {

    public class Deposited {

        public URI uri;

        public URI describedBy;

        public Deposited() {

        }

        public Deposited(URI uri, URI describedBy) {
            this.uri = uri;
            this.describedBy = describedBy;
        }

        public boolean hasDescription() {
            return describedBy != null;
        }
    }

    /**
     * Deposit the given resource.
     *
     * @param packagedResource The resource to deposit
     * @param intoContainer URI of the parent container to deposit into
     * @return URI(s) of deposited resource;
     */
    public Deposited deposit(PackagedResource packagedResource, URI intoContainer);

    public default Deposited deposit(PackagedResource resource) {
        return deposit(resource, null);
    }

    /**
     * Replace any local URIs with repository URIs using the given map.
     *
     * @param toRemap URI of the repository resource
     * @param localToRepository Mapping of local URIs to repository URIs.
     */
    public void remap(URI toRemap, Map<URI, URI> localToRepository);

    public void commit();

    public void rollback();
}
