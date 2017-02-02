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
 * Manages the deposit of the contents of a package into a container in the repository.
 *
 * @author apb@jhu.edu
 */
public interface PackageDepositManager<T> {

    /**
     * deposit a package into a repository container.
     *
     * @param resource repository container URI.
     * @param pkg Some sort of representation of a package (e.g. a file, InputStream, etc)
     * @param context Opaque, implementation-specific context. May be null , or empty
     */
    public void depositPackageInto(URI resource, T pkg, Map<String, Object> context);
}
