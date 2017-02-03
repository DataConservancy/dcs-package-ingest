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
 * LDP resource impl
 *
 * @author bbrosius@jhu.edu
 */
public class BasicLdpResource implements PackagedResource {

    private final URI uri;

    private Type type;

    private Collection<PackagedResource> children;

    private InputStream content;

    private String mediaType;

    private PackagedResource domainObjectDescription;

    /**
     * Create a packaged resource with the given URI
     *
     * @param uri local (to package) URI.
     */
    public BasicLdpResource(final URI uri) {
        this.uri = uri;
        children = new ArrayList<>();
    }

    @Override
    public Type getType() {
        return type;
    }

    /**
     * Set the type.
     *
     * @param type LDPR type.
     */
    public void setType(final Type type) {
        this.type = type;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    /**
     * Add an LDP child resource.
     *
     * @param child child resource.
     */
    public void addChild(final PackagedResource child) {
        if (children == null) {
            children = new ArrayList<>();
        }

        children.add(child);
    }

    @Override
    public Collection<PackagedResource> getChildren() {
        return children;
    }

    /**
     * Set the LDP child resources.
     *
     * @param children collection of children, or empty.
     */
    public void setChildren(final Collection<PackagedResource> children) {
        this.children = children;
    }

    @Override
    public InputStream getBody() {
        return content;
    }

    /**
     * Set the body
     *
     * @param body the body.
     */
    public void setBody(final InputStream body) {
        this.content = body;
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Set the media type.
     *
     * @param mediaType MIME type.
     */
    public void setMediaType(final String mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    public PackagedResource getDescription() {
        return domainObjectDescription;
    }

    /**
     * Set the description of an LDP-NR.
     *
     * @param description description resource.
     */
    public void setDescription(final PackagedResource description) {
        this.domainObjectDescription = description;
    }
}
