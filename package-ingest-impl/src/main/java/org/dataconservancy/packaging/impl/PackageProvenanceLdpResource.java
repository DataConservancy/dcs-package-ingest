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

package org.dataconservancy.packaging.impl;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import org.dataconservancy.packaging.ingest.PackagedResource;

/**
 * Impl for provenance-containing resource.
 *
 * @author bbrosius@jhu.edu
 */
class PackageProvenanceLdpResource implements PackagedResource {

    private final URI uri;

    private Type type;

    private InputStream content;

    private String mediaType;

    private PackagedResource domainObjectDescription;

    PackageProvenanceLdpResource(final URI uri) {
        this.uri = uri;
    }

    @Override
    public Type getType() {
        return type;
    }

    public void setType(final Type type) {
        this.type = type;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public Collection<PackagedResource> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public InputStream getBody() {
        return content;
    }

    void setBody(final InputStream body) {
        this.content = body;
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    void setMediaType(final String mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    public PackagedResource getDescription() {
        return domainObjectDescription;
    }

    void setDescription(final PackagedResource description) {
        this.domainObjectDescription = description;
    }
}