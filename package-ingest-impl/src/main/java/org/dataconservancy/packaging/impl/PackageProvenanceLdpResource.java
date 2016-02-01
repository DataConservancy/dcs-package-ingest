package org.dataconservancy.packaging.impl;

import org.dataconservancy.packaging.ingest.LdpResource;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

class PackageProvenanceLdpResource implements LdpResource {

    private URI uri;
    private Type type;
    private InputStream content;
    private String mediaType;
    private LdpResource domainObjectDescription;

    PackageProvenanceLdpResource(URI uri) {
        this.uri = uri;
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

    @Override
    public Collection<LdpResource> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public InputStream getBody() {
        return content;
    }

    void setBody(InputStream body) {
        this.content = body;
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    @Override
    public LdpResource getDescription() {
        return domainObjectDescription;
    }

    void setDescription(LdpResource description) {
        this.domainObjectDescription = description;
    }
}