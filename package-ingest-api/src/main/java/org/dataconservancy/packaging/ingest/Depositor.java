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
import java.util.Map;

/**
 * Deposits resources into a repository.
 *
 * @author apb@jhu.edu
 */
public interface Depositor {

    /**
     * Identifies a deposited object.
     */
    public class DepositedResource {

        public URI uri;

        public URI describedBy;

        /** Empty constructur */
        public DepositedResource() {

        }

        /**
         * Deposited resource with explicit URIs.
         *
         * @param uri URI of deposited resource
         * @param describedBy URI of binary discription resource, or null.
         */
        public DepositedResource(final URI uri, final URI describedBy) {
            this.uri = uri;
            this.describedBy = describedBy;
        }

        /**
         * Whether the deposited resource has a non-null description.
         *
         * @return true if there is a description.
         */
        public boolean hasDescription() {
            return describedBy != null;
        }
    }

    /**
     * Deposit the given resource into a spacific container.
     *
     * @param packagedResource The resource to deposit
     * @param intoContainer URI of the parent container to deposit into
     * @return URI(s) of deposited resource;
     */
    public DepositedResource deposit(PackagedResource packagedResource, URI intoContainer);

    /**
     * Deposit given packaged packaged resource into a default container.
     *
     * @param resource The resource to deposit
     * @return URI(s) of deposited resources.
     */
    public default DepositedResource deposit(final PackagedResource resource) {
        return deposit(resource, null);
    }

    /**
     * Replace any local URIs with repository URIs using the given map.
     *
     * @param toRemap URI of the repository resource
     * @param localToRepository Mapping of local URIs to repository URIs.
     */
    public void remap(URI toRemap, Map<URI, URI> localToRepository);

    /**
     * Commit all deposits to the repository.
     */
    public void commit();

    /**
     * Rollback all commits
     */
    public void rollback();
}
