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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.dataconservancy.packaging.ingest.LdpPackageProvenanceGenerator;
import org.dataconservancy.packaging.ingest.PackagedResource;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

/**
 * Generates package provenance.
 *
 * @author bbrosius@jhu.edu
 */
@Component(configurationPolicy = ConfigurationPolicy.IGNORE, immediate = true)
public class PackageFileProvenanceGenerator implements LdpPackageProvenanceGenerator<File> {

    @Override
    public PackagedResource generatePackageProvenance(final File pkg, final Map<String, String> uriMap) {
        final URI packageURI = pkg.toURI();
        final PackageProvenanceLdpResource resource = new PackageProvenanceLdpResource(packageURI);
        resource.setType(PackagedResource.Type.NONRDFSOURCE);

        try {
            resource.setBody(new FileInputStream(pkg));
            String mediaType = Files.probeContentType(pkg.toPath());
            if (mediaType == null || mediaType.trim().length() == 0) {
                mediaType = DcsPackageAnalyzer.APPLICATION_OCTETSTREAM;
            }
            resource.setMediaType(mediaType);
        } catch (final FileNotFoundException e) {
            throw new RuntimeException("Couldn't open input stream for package file: " + pkg.toString() + " .");
        } catch (final IOException e) {
            throw new RuntimeException("Unable to get mime type for package file: " + pkg.toString() + " .");
        }

        try {
            final URI resourceURI = new URI(packageURI.getScheme(),
                    packageURI.getHost(),
                    packageURI.getPath(),
                    "provenance");
            resource.setDescription(generateRdfProvenanceResource(uriMap, resourceURI));
        } catch (final URISyntaxException e) {
            e.printStackTrace();
        }

        return resource;
    }

    // Creates an ldp resource that represents the URI map of archive URIs to package file URIs
    private PackagedResource generateRdfProvenanceResource(final Map<String, String> uriMap, final URI uri) {
        final PackageProvenanceLdpResource rdfResource = new PackageProvenanceLdpResource(uri);
        rdfResource.setType(PackagedResource.Type.RDFSOURCE);
        rdfResource.setMediaType("text/turtle");
        final Model remModel = ModelFactory.createDefaultModel();

        // Loop through the uri map and create triples for each
        final Property derivedProperty = remModel.createProperty("http://www.w3.org/ns/prov#", "wasDerivedFrom");
        for (final String repositoryURI : uriMap.keySet()) {
            final Resource resource = remModel.createResource(repositoryURI);
            resource.addProperty(derivedProperty, uriMap.get(repositoryURI));
        }

        final Map<String, String> prefixMap = new HashMap<>();
        prefixMap.put("http://www.w3.org/ns/prov#", "prov");
        // Now generate the input stream for the resource
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.createGraphWriter(RDFFormat.TTL).write(out, remModel.getGraph(),
                PrefixMapFactory.create(prefixMap),
                null,
                null);

        rdfResource.setBody(new ByteArrayInputStream("<> a <http://www.w3.org/ns/prov#Entity> .".getBytes(UTF_8)));
        return rdfResource;
    }
}
