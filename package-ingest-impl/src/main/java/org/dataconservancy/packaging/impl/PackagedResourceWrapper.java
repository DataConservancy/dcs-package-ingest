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

package org.dataconservancy.packaging.impl;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

import org.dataconservancy.packaging.ingest.PackagedResource;

/**
 * PackagedResource backed by a Jena model.
 *
 * @author apb@jhu.edu
 */
class PackagedResourceWrapper implements PackagedResource {

    private final PackagedResource delegate;

    public PackagedResourceWrapper(final PackagedResource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Type getType() {
        return delegate.getType();
    }

    @Override
    public URI getURI() {
        return delegate.getURI();
    }

    @Override
    public Collection<PackagedResource> getChildren() {
        return delegate.getChildren();
    }

    @Override
    public InputStream getBody() {
        return delegate.getBody();
    }

    @Override
    public String getMediaType() {
        return delegate.getMediaType();
    }

    @Override
    public PackagedResource getDescription() {
        return delegate.getDescription();
    }
}
