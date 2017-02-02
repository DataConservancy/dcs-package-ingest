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

import java.net.URI;

import java.util.Collection;

/** Describes a package resource in terms of LDP semantics */
public interface PackagedResource {

    /** LDP Resource type */
    public Type getType();

    /** URI of this resource */
    public URI getURI();

    /** If a container, these are the children. If there are no children and empty list will be returned, but never null. */
    public Collection<PackagedResource> getChildren();

    /** Body of the LDP Resource, if RDF, presumed to be utf-8 encoded */
    public InputStream getBody();

    /** IANA media type stream */
    public String getMediaType();

    /**
     * If this is a NonRdfSource, there may be a descriptinve RDFSource
     * containing its metadata.
     */
    public PackagedResource getDescription();

    /** LDP resource type */
    public enum Type {
        RDFSOURCE, NONRDFSOURCE, CONTAINER
    }
}
