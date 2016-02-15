package org.dataconservancy.packaging.impl;

import org.apache.commons.io.IOUtils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.dataconservancy.packaging.ingest.LdpPackageProvenanceGenerator;
import org.dataconservancy.packaging.ingest.LdpResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

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

@Component(configurationPolicy = ConfigurationPolicy.IGNORE, immediate = true)
public class PackageFileProvenanceGenerator implements LdpPackageProvenanceGenerator<File> {
    @Override
    public LdpResource generatePackageProvenance(File pkg, Map<String, String> uriMap) {
        URI packageURI = pkg.toURI();
        PackageProvenanceLdpResource resource = new PackageProvenanceLdpResource(packageURI);
        resource.setType(LdpResource.Type.NONRDFSOURCE);

        try {
            resource.setBody(new FileInputStream(pkg));
            resource.setMediaType(Files.probeContentType(pkg.toPath()));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Couldn't open input stream for package file: " + pkg.toString() + " .");
        } catch (IOException e) {
            throw new RuntimeException("Unable to get mime type for package file: " + pkg.toString() + " .");
        }

        try {
            URI resourceURI = new URI(packageURI.getScheme(),
                         packageURI.getHost(),
                         packageURI.getPath(),
                         "provenance");
            resource.setDescription(generateRdfProvenanceResource(uriMap, resourceURI));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return resource;
    }

    //Creates an ldp resource that represents the URI map of archive URIs to package file URIs
    private LdpResource generateRdfProvenanceResource(Map<String, String> uriMap, URI uri) {
        PackageProvenanceLdpResource rdfResource = new PackageProvenanceLdpResource(uri);
        rdfResource.setType(LdpResource.Type.RDFSOURCE);
        rdfResource.setMediaType("text/turtle");
        Model remModel = ModelFactory.createDefaultModel();

        //Loop through the uri map and create triples for each
        Property derivedProperty = remModel.createProperty("http://www.w3.org/ns/prov#", "wasDerivedFrom");
        for (String repositoryURI : uriMap.keySet()) {
            Resource resource = remModel.createResource(repositoryURI);
            resource.addProperty(derivedProperty, uriMap.get(repositoryURI));
        }

        Map<String, String> prefixMap = new HashMap<>();
        prefixMap.put("http://www.w3.org/ns/prov#", "prov");
        //Now generate the input stream for the resource
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.createGraphWriter(RDFFormat.TTL).write(out, remModel.getGraph(),
                                                          PrefixMapFactory.create(prefixMap),
                                                          null,
                                                          null);

        rdfResource.setBody(IOUtils.toInputStream("<> a <http://www.w3.org/ns/prov#Entity> ."));
        return rdfResource;
    }
}
