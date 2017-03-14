/*
 * Copyright 2017 Johns Hopkins University
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
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers an extension with API-X
 *
 * @author apb@jhu.edu
 */
public class ExtensionRegistration implements Runnable {

    static final Logger LOG = LoggerFactory.getLogger(ExtensionRegistration.class);

    FcrepoClient client = FcrepoClient.client().build();

    private int port = 0;

    private String host = ip(hostname());

    private String path = "";

    private URI baseUri = URI.create("http://localhost:8080/fcrepo/rest");

    private static final URI APIX_LOADER_SERVICE = URI.create(
            "http://fedora.info/definitions/v4/api-extension#LoaderService");

    /**
     * Set the service instance port to register.
     *
     * @param port the port
     */
    public void setPort(final int port) {
        this.port = port;
    }

    /**
     * Set the service instance host name.
     * <p>
     * If not set, it defaults to `hostname`.
     * </p>
     *
     * @param host host name
     */
    public void setHost(final String host) {
        this.host = host;
    }

    /**
     * Set the context path of the service instance.
     *
     * @param path the context path
     */
    public void setPath(final String path) {
        this.path = path;
    }

    /**
     * Set the repository base URI.
     *
     * @param uri The repository base URI;
     */
    public void setRepositoryBaseUri(final URI uri) {
        this.baseUri = uri;
    }

    /**
     * Set the fcrepo client to be used when loading an extension.
     *
     * @param client the fcrepo client.
     */
    public void setClient(final FcrepoClient client) {
        this.client = client;
    }

    /**
     * Set the service instance port using the fluent builder.
     *
     * @param port the port.
     * @return Extension Registration.
     */
    public ExtensionRegistration withPort(final int port) {
        setPort(port);
        return this;
    }

    /**
     * Set the service instance path using the fluent builder.
     *
     * @param path service instance endpoint path
     * @return extension registration.
     */
    public ExtensionRegistration withPath(final String path) {
        setPath(path);
        return this;
    }

    /**
     * Set the service instance host name using the fluent builder.
     * <p>
     * If not set, it defaults to `hostname`.
     * </p>
     *
     * @param name The host name
     * @return extension registration
     */
    public ExtensionRegistration withHostName(final String name) {
        setHost(name);
        return this;
    }

    /**
     * Set the fcrepo client using the fluent builder.
     *
     * @param client the client
     * @return extension registration.
     */
    public ExtensionRegistration withClient(final FcrepoClient client) {
        setClient(client);
        return this;
    }

    /**
     * Set the repository base URI using the fluent builder.
     *
     * @param uri repository base URI
     * @return extension registration.
     */
    public ExtensionRegistration withRepositoryBaseUri(final URI uri) {
        setRepositoryBaseUri(uri);
        return this;
    }

    /**
     * Attempt to load the extension.
     *
     * @throws Exception if something goes wrong.
     */
    public void load() throws Exception {
        final URI loader = getServiceEndpoint(APIX_LOADER_SERVICE, baseUri);

        final URI myServiceUri = URI.create("http://" + host + ":" + port + "/" + path.replaceFirst("/", ""));
        LOG.info("Registering service instance at <{}>", myServiceUri);

        try (FcrepoResponse response = client.post(loader).body(new ByteArrayInputStream(myServiceUri
                .toString().getBytes(UTF_8)), "text/plain").perform()) {
            checkError(response);

            LOG.info("Extension successfully registered");
        }
    }

    /** Attempt to load an extension infinitely. */
    @Override
    public void run() {
        while (true) {
            try {
                load();
                return;
            } catch (final Exception e) {
                LOG.info("Failed loading extension: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException i) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private URI getServiceEndpoint(final URI service, final URI resource) throws Exception {
        final URI serviceDoc = getServiceDoc(resource);

        LOG.info("Getting service doc " + serviceDoc);
        try (FcrepoResponse response = client.get(serviceDoc).accept("text/turtle").perform()) {
            final Model model = ModelFactory.createDefaultModel();
            model.read(response.getBody(), serviceDoc.toString(), "TURTLE");

            final ResultSet result = QueryExecutionFactory.create(QueryFactory.create(
                    "SELECT ?endpoint WHERE {" +
                            "?instance <http://fedora.info/definitions/v4/service#isServiceInstanceOf> <" +
                            service + "> .\n" +
                            "?instance <http://fedora.info/definitions/v4/service#hasEndpoint> ?endpoint .}"), model)
                    .execSelect();

            if (result.hasNext()) {
                return URI.create(result.next().getResource("endpoint").getURI());
            }
        }
        throw new RuntimeException("No endpoints of service <" + service + ">" +
                " found on resource <" + resource + ">");
    }

    private URI getServiceDoc(final URI resource) throws Exception {
        try (FcrepoResponse response = client.head(baseUri).perform()) {
            checkError(response);

            final List<URI> linkHeaders = response.getLinkHeaders("service");

            if (linkHeaders.isEmpty()) {
                throw new RuntimeException("Cannot discover extension loader service from <" + baseUri +
                        ">: No service document found!");
            }

            return linkHeaders.get(0);
        }
    }

    private static String hostname() {
        try {
            final Process proc = Runtime.getRuntime().exec("hostname");
            try (InputStream stream = proc.getInputStream(); Scanner s = new Scanner(stream)) {
                final Scanner delimited = s.useDelimiter("\\A");
                return delimited.hasNext() ? delimited.next().trim() : "localhost";
            }
        } catch (final IOException e) {
            LOG.info("Could not determine own hostname: " + e.getMessage());
            return "localhost";
        }
    }

    private static String ip(final String hostname) {
        try {
            final InetAddress address = InetAddress.getByName(hostname);
            return address.getHostAddress();
        } catch (final UnknownHostException e) {
            return hostname;
        }
    }

    private void checkError(final FcrepoResponse response) throws FcrepoOperationFailedException, IOException {
        if (response.getStatusCode() >= 400) {
            throw new FcrepoOperationFailedException(response.getUrl(), response.getStatusCode(),
                    IOUtils.toString(response.getBody(), "utf8"));
        }
    }
}
