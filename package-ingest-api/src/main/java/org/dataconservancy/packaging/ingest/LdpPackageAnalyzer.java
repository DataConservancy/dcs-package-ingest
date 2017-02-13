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

package org.dataconservancy.packaging.ingest;

import java.io.InputStream;
import java.util.Collection;

/**
 * Analyzes a DCS package.
 *
 * @author bbrosius@jhu.edu
 */
public interface LdpPackageAnalyzer {

    /**
     * Get root resources of the given package.
     *
     * @param pkg physical package.
     * @return Collection of resources that are "roots" of the given pavkage (i.e. are not contained by any other
     *         resource in the package.
     */
    public Collection<PackagedResource> getContainerRoots(final InputStream pkg);

    /**
     * Clean up the package extraction directory.
     */
    void cleanUpExtractionDirectory();

}
