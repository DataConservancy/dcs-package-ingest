
package org.dataconservancy.packaging.impl;

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

import org.apache.jena.atlas.RuntimeIOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpResource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import static org.dataconservancy.packaging.impl.UriUtility.resolveBagUri;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.impl.PackageFileAnalyzer", description = "Unpacks and analyzes package files for ingest")
@interface PackageFileAnalyzerConfig {
    
    @AttributeDefinition(description = "Directory for temporary unpacking package contents as necessary")
    String pkg_extract_dir() default "/tmp";
}

@Component(service = LdpPackageAnalyzer.class, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
@Designate(ocd = PackageFileAnalyzerConfig.class)
public class PackageFileAnalyzer
        implements LdpPackageAnalyzer<File> {
    
    public static final String PARAM_EXTRACT_DIR = "pkg.extract.dir";

    private final String BAG_INFO_NAME = "bag-info.txt";
    private final String REM_KEY = "Resource-Manifest";
    private final String NS_IANA = "http://www.iana.org/assignments/relation/";
    private final String NS_LDP = "http://www.w3.org/ns/ldp#";
    private final String NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private final Property TYPE = ResourceFactory.createProperty(NS_RDF, "type");
    private final String LDP_CONTAINER = NS_LDP + "Container";
    private final Property LDP_CONTAINS = ResourceFactory.createProperty(NS_LDP, "contains");
    private final Property DESCRIBES_PROPERTY = ResourceFactory.createProperty(NS_IANA, "describes");

    private OpenPackageService packageService;
    private File extractDir;

    public PackageFileAnalyzer(OpenPackageService openPackageService, File extractDir) {
        this.packageService = openPackageService;
        this.extractDir = extractDir;
    }
    
    public PackageFileAnalyzer() {
        packageService = new OpenPackageService();
    }
    
    @Activate
    public void init(PackageFileAnalyzerConfig config) {
        extractDir = new File(config.pkg_extract_dir());
        extractDir.mkdirs();
    }

    @Override
    public Collection<LdpResource> getContainerRoots(File pkg) {
        Map<URI, LdpResource> packageContainerResources = new HashMap<>();
        List<URI> visitedChildContainers = new ArrayList<>();
        try {
            File extractedPackageLocation = packageService.openPackage(extractDir, pkg);

            //Read bag info file to get ore-rem file
            File bagInfoFile = new File(extractedPackageLocation, BAG_INFO_NAME);
            String remURI = getTag(new FileInputStream(bagInfoFile), REM_KEY);

            try {
                if (UriUtility.isBagUri(new URI(remURI))) {
                    Path remPath = resolveBagUri(extractDir.toPath(), new URI(remURI));

                    Model remModel = ModelFactory.createDefaultModel();
                    remModel.read(remPath.toUri().toString(), getJenaFormatString(remPath));

                    ResIterator nodeIterator = remModel.listResourcesWithProperty(TYPE, LDP_CONTAINER);
                    if (!nodeIterator.hasNext()) {
                        throw new RuntimeException("Couldn't find any LDP Containers in the package.");
                    } else {

                        while (nodeIterator.hasNext()) {
                            Resource containerResource = nodeIterator.next();
                            if (!visitedChildContainers.contains(new URI(containerResource.getURI()))) {
                                LdpResource newContainer = populateLdpContainerResource(remModel, containerResource, visitedChildContainers, extractDir.toPath());
                                packageContainerResources.put(newContainer.getURI(), newContainer);
                            }
                        }
                    }
                }
            } catch (URISyntaxException | IOException e) {
                throw new RuntimeException("An error occurred reading the package Resource map. " + e.getMessage());
            }
            //Read through the REM File to get the ldp concepts to populate the ldpresources
        } catch (IOException e) {
            throw new RuntimeIOException("Failed to open the bag-info file for the package. " + e.getMessage());
        }

        //Should be only one container since we only support one root, if we have more than one it was added before it's parent so loop through the visited children and remove them here.
        if (packageContainerResources.size() > 1) {
            visitedChildContainers.forEach(packageContainerResources::remove);
        }
        return packageContainerResources.values();
    }

    //Parses out information from the ReM needed to populate LdpContainerResources.
    private LdpResource populateLdpContainerResource(Model model, Resource ldpContainerResource, List<URI> visitedContainerResources, Path extractDirectory)
        throws URISyntaxException, IOException {
        URI resourceBagUri = new URI(ldpContainerResource.getURI());
        BasicLdpResource resource = new BasicLdpResource(resourceBagUri);
        resource.setType(LdpResource.Type.CONTAINER);

        Path resourcePath = UriUtility.resolveBagUri(extractDirectory, resourceBagUri);
        resource.setMediaType(Files.probeContentType(resourcePath));
        resource.setBody(new FileInputStream(resourcePath.toFile()));

        if (ldpContainerResource.hasProperty(LDP_CONTAINS)) {
            List<RDFNode> childrenNodes = model.listObjectsOfProperty(ldpContainerResource, LDP_CONTAINS).toList();

            for (RDFNode child : childrenNodes) {
                try {
                    Resource childResource = child.asResource();
                    //Handle a file
                    if (!childResource.hasProperty(TYPE, LDP_CONTAINER)) {
                        resource.addChild(populateFileResource(childResource, extractDirectory));
                    } else {
                        LdpResource childContainer = populateLdpContainerResource(model, childResource, visitedContainerResources, extractDirectory);
                        resource.addChild(childContainer);
                        visitedContainerResources.add(childContainer.getURI());
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Resource map was invalid. " + e.getMessage());
                }
            }
        }

        return resource;
    }

    //Parses out file resource information to craft appropriate ldp resource objects.
    //This will return the non rdf resource which will have the rdf resource set as it's description
    private LdpResource populateFileResource(Resource fileResource, Path extractDirectory)
        throws URISyntaxException, IOException {

        //Handle the domain object first, then we'll get the binary content it describes.
        URI fileDomainObjectURI = new URI(fileResource.getURI());
        BasicLdpResource fileDomainObjectResource = new BasicLdpResource(fileDomainObjectURI);
        fileDomainObjectResource.setType(LdpResource.Type.RDFSOURCE);

        Path resourcePath = UriUtility.resolveBagUri(extractDirectory, fileDomainObjectURI);
        fileDomainObjectResource.setMediaType(Files.probeContentType(resourcePath));
        fileDomainObjectResource.setBody(new FileInputStream(resourcePath.toFile()));

        BasicLdpResource binaryFileResource = null;

        if (fileResource.hasProperty(DESCRIBES_PROPERTY)) {
            RDFNode value = fileResource.getProperty(DESCRIBES_PROPERTY).getObject();

            if (!value.isLiteral()) {
                throw new RuntimeException("Unable to read binary content in the package.");
            }

            URI binaryFileURI = new URI(value.asLiteral().getString());
            binaryFileResource = new BasicLdpResource(binaryFileURI);
            binaryFileResource.setType(LdpResource.Type.NONRDFSOURCE);
            binaryFileResource.setDescription(fileDomainObjectResource);

            Path binaryResourcePath = UriUtility.resolveBagUri(extractDirectory, binaryFileURI);
            binaryFileResource.setMediaType(Files.probeContentType(binaryResourcePath));
            binaryFileResource.setBody(new FileInputStream(binaryResourcePath.toFile()));
        } else {
            //TODO: Is it an exceptional case if there is no binary content?
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
    private String getTag(InputStream is, String tagFileKey)
            throws IOException {

        String result = null;
        BufferedReader r = new BufferedReader(
                new InputStreamReader(is, "UTF-8"));
        String line;

        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }

            //If the line starts with a white space it's a continuation of the value from the previous line
            if (!Character.isWhitespace(line.charAt(0))) {
                // New value

                int i = line.indexOf(':');

                if (i == -1) {
                    throw new IOException("BagIt tag file has invalid formatting.");
                } else {
                    String key = line.substring(0, i).trim();
                    if (key.equalsIgnoreCase(tagFileKey)) {
                        result = i == line.length() ? "" : line.substring(i + 1).trim();
                        break;
                    }

                }
            }
        }

        return result;
    }

    //The default jena read method that detects content types didn't seem to work, so this function sets the correct language.
    private String getJenaFormatString(Path remFile) {
        if (remFile.toString().toLowerCase().endsWith(".ttl")) {
            return "TTL";
        } else if (remFile.toString().toLowerCase().endsWith(".rdf")) {
            return "RDF/XML";
        } else if (remFile.toString().toLowerCase().endsWith(".jsonld")) {
            return "JSON-LD";
        }

        return "";
    }
}
