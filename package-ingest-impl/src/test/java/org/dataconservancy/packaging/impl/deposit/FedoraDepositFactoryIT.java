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

package org.dataconservancy.packaging.impl.deposit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoResponse;

import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.Depositor.DepositedResource;
import org.dataconservancy.packaging.ingest.PackagedResource;
import org.dataconservancy.packaging.ingest.PackagedResource.Type;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
public class FedoraDepositFactoryIT {

    static URI baseUri;

    static URI parentContaner;

    FedoraDepositFactory toTest = new FedoraDepositFactory();

    FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

    private static final Logger LOG = LoggerFactory.getLogger(FedoraDepositFactoryIT.class);

    private static final String LDP_CONTAINS = "http://www.w3.org/ns/ldp#contains";

    private AtomicInteger counter;

    URI myContainer;

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setUpFedora() {
        baseUri = URI.create("http://localhost:" + System.getProperty("fcrepo.dynamic.test.port", "8080") +
                "/fcrepo/rest");

        final FcrepoClient client = FcrepoClient.client().build();

        postLoop:
        while (true) {
            try (FcrepoResponse posted = client.post(baseUri).slug(FedoraDepositFactoryIT.class.getSimpleName())
                    .perform()) {
                parentContaner = posted.getLocation();
                break postLoop;
            } catch (final Exception e) {
                LOG.info("Test container creation failed, retrying: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException i) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        toTest.setBaseUri(baseUri.toString());
        counter = new AtomicInteger();

        try (FcrepoResponse response = client.post(parentContaner).slug(name.getMethodName()).perform()) {
            myContainer = response.getLocation();
        }
    }

    // Verify that containers (rdf resources) can be deposited and committed using both forms of deposit()
    @Test
    public void containerDepositTest() throws Exception {

        final Depositor test = toTest.newDepositer(myContainer);

        // Deposit a container into the default container (myContaier)
        final DepositedResource first = test.deposit(rdfResource("<> a <test:first>"));

        // Deposit into the container we just created;
        test.deposit(rdfResource("<> a <test:second>"), first.uri);

        test.commit();

        final List<URI> inMyContainer = getChildren(myContainer);
        assertEquals(1, inMyContainer.size());
        final URI firstDeposited = inMyContainer.get(0);

        try (FcrepoResponse get = client.get(firstDeposited).accept("application/n-triples").perform()) {
            assertTrue(IOUtils.toString(get.getBody(), "utf8").contains("test:first"));
        }

        final List<URI> inFirstDeposited = getChildren(firstDeposited);
        assertEquals(1, inFirstDeposited.size());
        final URI secondDeposited = inFirstDeposited.get(0);

        try (FcrepoResponse get = client.get(secondDeposited).accept("application/n-triples").perform()) {
            assertTrue(IOUtils.toString(get.getBody(), "utf8").contains("test:second"));
        }
    }

    // Verify that binaries and their descriptions can be deposited and committed.
    @Test
    public void binaryDepositTest() throws Exception {

        final Depositor test = toTest.newDepositer(myContainer);

        // Deposit a container into the default container (myContaier)
        test.deposit(binaryResource("CONTENT", rdfResource("<> a <test:binaryDescription>")));

        test.commit();

        final List<URI> inMyContainer = getChildren(myContainer);
        assertEquals(1, inMyContainer.size());
        final URI binary = inMyContainer.get(0);

        List<URI> descriptions;
        try (FcrepoResponse get = client.get(binary).perform()) {
            assertEquals("text/plain", get.getContentType());
            assertEquals("CONTENT", IOUtils.toString(get.getBody()));

            descriptions = get.getLinkHeaders("describedby");
        }

        assertEquals(1, descriptions.size());

        try (FcrepoResponse get = client.get(descriptions.get(0)).accept("application/n-triples").perform()) {
            final String body = IOUtils.toString(get.getBody(), "utf8");
            assertTrue(body.contains("test:binaryDescription"));
        }
    }

    // Verifies that rollback rolls back
    @Test
    public void rollBackTest() throws Exception {

        final Depositor test = toTest.newDepositer(myContainer);

        // Deposit a container into the default container (myContaier)
        test.deposit(rdfResource("<> a <test:first>"));

        // Once we roll back, we should see no children
        test.rollback();
        final List<URI> inMyContainer = getChildren(myContainer);
        assertEquals(0, inMyContainer.size());

    }

    // Verifies that containers and binary descriptions can be re-mapped
    @Test
    public void remapTest() throws Exception {

        final Map<URI, URI> toRemap = new HashMap<>();

        final Depositor test = toTest.newDepositer(myContainer);

        // Deposit a container into the default container (myContaier)
        final DepositedResource containerDeposit = test.deposit(rdfResource("<> <test:rel> <test:resource_2>"));
        toRemap.put(URI.create("test:resource_1"), containerDeposit.uri);

        final DepositedResource binaryDeposit = test.deposit(binaryResource("CONTENT", rdfResource(
                "<> <test:rel> <test:resource_1>")), containerDeposit.uri);
        toRemap.put(URI.create("test:resource_2"), binaryDeposit.uri);

        test.remap(containerDeposit.uri, toRemap);
        test.remap(binaryDeposit.describedBy, toRemap);

        test.commit();

        // Now, find out what their URIs are.
        final List<URI> inMyContainer = getChildren(myContainer);
        assertEquals(1, inMyContainer.size());
        final URI containerUri = inMyContainer.get(0);

        final List<URI> inFirstContainer = getChildren(containerUri);
        assertEquals(1, inFirstContainer.size());
        final URI binaryUri = inFirstContainer.get(0);

        URI secondX;
        try (FcrepoResponse head = client.head(binaryUri).perform()) {
            secondX = head.getLinkHeaders("describedby").get(0);
        }

        try (FcrepoResponse get = client.get(containerUri).accept("text/turtle").perform()) {

            final Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, get.getBody(), Lang.TURTLE);

            final List<RDFNode> objects = model.listObjectsOfProperty(model.getProperty("test:rel")).toList();
            assertEquals(1, objects.size());
            assertEquals(binaryUri.toString(), objects.get(0).asResource().getURI());

        }

        try (FcrepoResponse get = client.get(secondX).accept("text/turtle").perform()) {
            final Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, get.getBody(), Lang.TURTLE);

            final List<RDFNode> objects = model.listObjectsOfProperty(model.getProperty("test:rel")).toList();
            assertEquals(1, objects.size());
            assertEquals(containerUri.toString(), objects.get(0).asResource().getURI());
        }
    }

    @Test
    public void hashUriRemapTest() throws Exception {
        final Map<URI, URI> toRemap = new HashMap<>();

        final Depositor test = toTest.newDepositer(myContainer);

        // Deposit a container into the default container (myContaier)
        final DepositedResource first = test.deposit(rdfResource("<> <test:rel> <test:before#hash>"));
        toRemap.put(URI.create("test:before"), URI.create("test:after"));

        test.remap(first.uri, toRemap);

        test.commit();

        // Now, get its URI
        final List<URI> inMyContainer = getChildren(myContainer);
        assertEquals(1, inMyContainer.size());
        final URI resourceURI = inMyContainer.get(0);

        try (FcrepoResponse get = client.get(resourceURI).accept("text/turtle").perform()) {
            final Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, get.getBody(), Lang.TURTLE);

            final List<RDFNode> objects = model.listObjectsOfProperty(model.getProperty("test:rel")).toList();
            assertEquals(1, objects.size());
            assertEquals("test:after#hash", objects.get(0).asResource().getURI());
        }

    }

    private PackagedResource rdfResource(String rdf) {
        final PackagedResource resource = mock(PackagedResource.class);
        when(resource.getURI()).thenReturn(URI.create("file:/test/resource_" + counter.incrementAndGet()));
        when(resource.getType()).thenReturn(Type.CONTAINER);
        when(resource.getMediaType()).thenReturn("text/turtle");
        when(resource.getBody()).thenReturn(new ByteArrayInputStream(rdf.getBytes(UTF_8)));

        return resource;
    }

    private PackagedResource binaryResource(String content, PackagedResource description) {
        final PackagedResource resource = mock(PackagedResource.class);
        when(resource.getURI()).thenReturn(URI.create("file:/test/binary_" + counter.incrementAndGet() + ".txt"));
        when(resource.getType()).thenReturn(Type.NONRDFSOURCE);
        when(resource.getMediaType()).thenReturn("text/plain");
        when(resource.getDescription()).thenReturn(description);
        when(resource.getBody()).thenReturn(new ByteArrayInputStream(content.getBytes(UTF_8)));

        return resource;
    }

    private List<URI> getChildren(URI container) throws Exception {
        try (FcrepoResponse get = client.get(container).accept("application/rdf+xml").perform()) {
            final Model model = ModelFactory.createDefaultModel();

            return model.read(get.getBody(), Lang.RDFXML.getName())
                    .listObjectsOfProperty(model.getProperty(LDP_CONTAINS))
                    .mapWith(RDFNode::asResource)
                    .mapWith(Resource::getURI)
                    .mapWith(URI::create)
                    .toList();
        }
    }
}
