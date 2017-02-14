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

import static org.dataconservancy.packaging.impl.UriUtility.resolveBagUri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dataconservancy.packaging.ingest.PackageAnalyzer;
import org.dataconservancy.packaging.ingest.PackagedResource;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author bbrosius@jhu.edu
 */
public class DcsPackageAnalyzer
        implements PackageAnalyzer {

    static final Logger LOG = LoggerFactory.getLogger(DcsPackageAnalyzer.class);

    public static final String PARAM_EXTRACT_DIR = "pkg.extract.dir";

    static final String APPLICATION_OCTETSTREAM = "application/octet-stream";

    private final String BAG_INFO_NAME = "bag-info.txt";

    private final String REM_KEY = "Resource-Manifest";

    private final String NS_IANA = "http://www.iana.org/assignments/relation/";

    private final String NS_LDP = "http://www.w3.org/ns/ldp#";

    private final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private final Property TYPE = ResourceFactory.createProperty(NS_RDF, "type");

    private final String LDP_CONTAINER = NS_LDP + "Container";

    private final Property LDP_CONTAINS = ResourceFactory.createProperty(NS_LDP, "contains");

    private final Property DESCRIBES_PROPERTY = ResourceFactory.createProperty(NS_IANA, "describes");

    private final OpenPackageService packageService;

    private final File extractDir;

    private File extractedPackageLocation;

    /**
     * Create an analyzer.
     *
     * @param openPackageService Service to extract/open the package.
     * @param extractDir Extraction directory.
     */
    public DcsPackageAnalyzer(final OpenPackageService openPackageService, final File extractDir) {
        this.packageService = openPackageService;
        this.extractDir = extractDir;
    }

    @Override
    public Collection<PackagedResource> getContainerRoots(final InputStream pkg) {
        final Map<URI, PackagedResource> packageContainerResources = new HashMap<>();
        final List<URI> visitedChildContainers = new ArrayList<>();
        try {
            extractedPackageLocation = packageService.openPackage(extractDir, pkg);

            // Read bag info file to get ore-rem file
            final File bagInfoFile = new File(extractedPackageLocation, BAG_INFO_NAME);
            final String remURI = getTag(new FileInputStream(bagInfoFile), REM_KEY);

            try {
                if (UriUtility.isBagUri(new URI(remURI))) {
                    final Path remPath = resolveBagUri(extractDir.toPath(), new URI(remURI));

                    final Model remModel = ModelFactory.createDefaultModel();
                    remModel.read(remPath.toUri().toString(), getJenaFormatString(remPath));

                    final ResIterator nodeIterator = remModel.listResourcesWithProperty(TYPE, remModel.getResource(
                            LDP_CONTAINER));
                    if (!nodeIterator.hasNext()) {
                        throw new RuntimeException("Couldn't find any LDP Containers in the package.");
                    } else {

                        while (nodeIterator.hasNext()) {
                            final Resource containerResource = nodeIterator.next();
                            if (!visitedChildContainers.contains(new URI(containerResource.getURI()))) {
                                final PackagedResource newContainer = populateLdpContainerResource(remModel,
                                        containerResource, visitedChildContainers, extractDir.toPath());
                                packageContainerResources.put(newContainer.getURI(), newContainer);
                            }
                        }
                    }

                    // process any remaining binary resources:
                    // these are resources that are the object of iana:describes, and are not the object of an
                    // ldp:contains.
                    remModel.listStatements(null, DESCRIBES_PROPERTY, (String) null)
                            .filterDrop(statement -> remModel.contains(null, LDP_CONTAINS, statement.getObject()))
                            .forEachRemaining(statement -> {
                                try {
                                    final PackagedResource binaryResource = populateFileResource(statement.getObject()
                                            .asResource(), extractDir.toPath(), remModel);
                                    packageContainerResources.put(binaryResource.getURI(), binaryResource);
                                } catch (URISyntaxException | IOException e) {
                                    throw new RuntimeException("Error processing non-container binary resources: " + e
                                            .getMessage(), e);
                                }
                            });

                }
            } catch (URISyntaxException | IOException e) {
                throw new RuntimeException("An error occurred reading the package Resource map. " + e.getMessage());
            }
            // Read through the REM File to get the ldp concepts to populate the ldpresources
        } catch (final IOException e) {
            throw new RuntimeException("Failed to open that package to retrieve the bag-info file. " + e
                    .getMessage(), e);
        }

        // Should be only one container since we only support one root, if we have more than one it was added before
        // it's parent so loop through the visited children and remove them here.
        if (packageContainerResources.size() > 1) {
            visitedChildContainers.forEach(packageContainerResources::remove);
        }

        return packageContainerResources.values();
    }

    // Parses out information from the ReM needed to populate LdpContainerResources.
    private PackagedResource populateLdpContainerResource(final Model model, final Resource ldpContainerResource,
            final List<URI> visitedContainerResources, final Path extractDirectory)
            throws URISyntaxException, IOException {
        final URI resourceBagUri = new URI(ldpContainerResource.getURI());
        final BasicLdpResource resource = new BasicLdpResource(resourceBagUri);
        resource.setType(PackagedResource.Type.CONTAINER);

        final Path resourcePath = UriUtility.resolveBagUri(extractDirectory, resourceBagUri);
        resource.setMediaType(getDomainObjectMimeType(resourcePath));
        resource.setBody(new FileInputStream(resourcePath.toFile()));

        if (ldpContainerResource.hasProperty(LDP_CONTAINS)) {
            final List<RDFNode> childrenNodes = model.listObjectsOfProperty(ldpContainerResource, LDP_CONTAINS)
                    .toList();

            for (final RDFNode child : childrenNodes) {
                try {
                    final Resource childResource = child.asResource();
                    // Handle a file
                    if (!childResource.hasProperty(TYPE, model.getResource(LDP_CONTAINER))) {
                        resource.addChild(populateFileResource(childResource, extractDirectory, model));
                    } else {
                        final PackagedResource childContainer = populateLdpContainerResource(model, childResource,
                                visitedContainerResources, extractDirectory);
                        resource.addChild(childContainer);
                        visitedContainerResources.add(childContainer.getURI());
                    }
                } catch (final Exception e) {
                    throw new RuntimeException("Resource map was invalid. ", e);
                }
            }
        }

        return resource;
    }

    // Parses out file resource information to craft appropriate ldp resource objects.
    // This will return the non rdf resource which will have the rdf resource set as it's description
    private PackagedResource populateFileResource(final Resource fileResource, final Path extractDirectory,
            final Model model)
            throws URISyntaxException, IOException {

        // Handle the domain object first, then we'll get the binary content it describes.
        final URI binaryFileURI = new URI(fileResource.getURI());
        final BasicLdpResource binaryFileResource = new BasicLdpResource(binaryFileURI);
        binaryFileResource.setType(PackagedResource.Type.NONRDFSOURCE);

        final Path resourcePath = UriUtility.resolveBagUri(extractDirectory, binaryFileURI);
        String mimeType = Files.probeContentType(resourcePath);
        if (mimeType == null) {
            mimeType = APPLICATION_OCTETSTREAM;
        }
        binaryFileResource.setMediaType(mimeType);
        binaryFileResource.setBody(new FileInputStream(resourcePath.toFile()));

        final BasicLdpResource domainObjectResource;

        final ResIterator nodeIterator = model.listResourcesWithProperty(DESCRIBES_PROPERTY, fileResource);
        if (!nodeIterator.hasNext()) {
            throw new RuntimeException("Could not find RDFSource for: " + binaryFileURI);
        } else {
            // There should be only one resource
            final Resource domainObject = nodeIterator.next();
            final URI domainObjectURI = new URI(domainObject.getURI());
            domainObjectResource = new BasicLdpResource(domainObjectURI);
            domainObjectResource.setType(PackagedResource.Type.RDFSOURCE);
            binaryFileResource.setDescription(domainObjectResource);

            final Path domainObjectResourcePath = UriUtility.resolveBagUri(extractDirectory, domainObjectURI);
            domainObjectResource.setMediaType(getDomainObjectMimeType(domainObjectResourcePath));
            domainObjectResource.setBody(new FileInputStream(domainObjectResourcePath.toFile()));
        }

        return binaryFileResource;

    }

    /**
     * Parse a BagIt tags file into key,(value+) pairs.
     *
     * @param is the InputStream
     * @return the key, (value+) pairs
     * @throws IOException if there is an IO exception
     */
    private String getTag(final InputStream is, final String tagFileKey)
            throws IOException {

        String result = null;
        final BufferedReader r = new BufferedReader(
                new InputStreamReader(is, "UTF-8"));
        String line;

        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }

            // If the line starts with a white space it's a continuation of the value from the previous line
            if (!Character.isWhitespace(line.charAt(0))) {
                // New value

                final int i = line.indexOf(':');

                if (i == -1) {
                    throw new IOException("BagIt tag file has invalid formatting.");
                } else {
                    final String key = line.substring(0, i).trim();
                    if (key.equalsIgnoreCase(tagFileKey)) {
                        result = i == line.length() ? "" : line.substring(i + 1).trim();
                        break;
                    }

                }
            }
        }

        return result;
    }

    // The default jena read method that detects content types didn't seem to work, so this function sets the correct
    // language.
    private String getJenaFormatString(final Path remFile) {
        if (remFile.toString().toLowerCase().endsWith(".ttl")) {
            return "TTL";
        } else if (remFile.toString().toLowerCase().endsWith(".rdf")) {
            return "RDF/XML";
        } else if (remFile.toString().toLowerCase().endsWith(".jsonld")) {
            return "JSON-LD";
        }

        return "";
    }

    // Java's probe content type won't give correct mime types for our domain object files so we'll do it manually.
    private String getDomainObjectMimeType(final Path remFile) {
        if (remFile.toString().toLowerCase().endsWith(".ttl")) {
            return "text/turtle";
        } else if (remFile.toString().toLowerCase().endsWith(".rdf")) {
            return "application/rdf+xml";
        } else if (remFile.toString().toLowerCase().endsWith(".jsonld")) {
            return "application/ld+json";
        }

        return "";
    }

    @Override
    public void cleanUpExtractionDirectory() {
        if (extractedPackageLocation != null && extractedPackageLocation.exists()) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(extractedPackageLocation);
            } catch (final IOException e) {
                throw new RuntimeException("Unable to clean up extract directory.", e);
            }
        } else {
            LOG.info("No extraction directory to clean up");
        }
    }
}
