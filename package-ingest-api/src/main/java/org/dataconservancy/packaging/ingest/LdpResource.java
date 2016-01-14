
package org.dataconservancy.packaging.ingest;

import java.io.InputStream;

import java.net.URI;

import java.util.Collection;

/** Describes a package resource in terms of LDP semantics */
public interface LdpResource {

    /** LDP Resource type */
    public Type getType();

    /** URI of this resource */
    public URI getURI();

    /** If a container, these are the children */
    public Collection<LdpResource> getChildren();

    /** Body of the LDP Resource, if RDF, presumed to be utf-8 encoded */
    public InputStream getBody();

    /** IANA media type stream */
    public String getMediaType();

    /**
     * If this is a NonRdfSource, there may be a descriptinve RDFSource
     * containing its metadata.
     */
    public LdpResource getDescription();

    /** LDP resource type */
    public enum Type {
        RDFSOURCE, NONRDFSOURCE, CONTAINER
    }
}
