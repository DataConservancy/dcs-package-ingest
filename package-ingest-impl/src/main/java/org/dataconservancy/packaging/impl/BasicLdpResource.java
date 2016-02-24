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

import org.dataconservancy.packaging.ingest.LdpResource;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

public class BasicLdpResource implements LdpResource {

    private URI uri;
    private Type type;
    private Collection<LdpResource> children;
    private InputStream content;
    private String mediaType;
    private LdpResource domainObjectDescription;

    public BasicLdpResource(URI uri) {
        this.uri = uri;
        children = new ArrayList<>();
    }

    @Override
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    public void addChild(LdpResource child) {
        if (children == null) {
            children = new ArrayList<>();
        }

        children.add(child);
    }

    @Override
    public Collection<LdpResource> getChildren() {
        return children;
    }

    public void setChildren(Collection<LdpResource> children) {
        this.children = children;
    }

    @Override
    public InputStream getBody() {
        return content;
    }

    public void setBody(InputStream body) {
        this.content = body;
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    public LdpResource getDescription() {
        return domainObjectDescription;
    }

    public void setDescription(LdpResource description) {
        this.domainObjectDescription = description;
    }
}
