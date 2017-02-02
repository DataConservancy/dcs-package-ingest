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
 * Create a new walker who can walk and deposit resources in the given package.
 *
 * @author apb@jhu.edu
 */
public interface PackageWalkerFactory<T> {

    /**
     * Create a walker to walk the contents of a package and deposit its resources.
     *
     * @param pkgSrc The package.
     * @return A package walker that walks the given package.
     */
    public PackageWalker newWalker(T pkgSrc);
}
