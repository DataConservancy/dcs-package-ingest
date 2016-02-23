
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;
import java.io.StringWriter;

import java.net.URI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpHeaders;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.update.UpdateAction;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
import org.dataconservancy.packaging.ingest.LdpResource;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_BINARY_URIS;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_LDP_RESOURCES;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_URI_MAP;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.ID_DEPOSIT_ITERATE;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.ID_DEPOSIT_REMAP;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.ID_HTTP_OPERATION;

/* Verifies that a hierarchies of LDP nodes will be traversed for deposit */
@SuppressWarnings("serial")
public class LdpDepositDriverTest
        extends CamelTestSupport {

    @Rule
    public TemporaryFolder extractFolder = new TemporaryFolder();

    @EndpointInject(uri = "mock:out")
    private MockEndpoint mockOut = new MockEndpoint();

    @EndpointInject(uri = "mock:deposit")
    private MockEndpoint mockDeposit = new MockEndpoint();

    @Produce
    private ProducerTemplate template;

    LdpDepositDriver driver;

    /*
     * Assures that an entire LDP hierarchy is deposited, and URI mappings
     * recorded
     */
    @Test
    public void depositHierarchicalTest() throws Exception {

        context.getRouteDefinition(ID_DEPOSIT_ITERATE)
                .adviceWith(context, new AdviceWithRouteBuilder() {

                    @Override
                    public void configure() throws Exception {
                        interceptSendToEndpoint("direct:_deposit_ldpResource")
                                .skipSendToOriginalEndpoint()
                                .to("direct:fakeDeposit").to("mock:deposit");
                    }
                });

        final String PARENT_URI = "http://example.org/parent";
        final String CHILD_URI_1 = "http://example.org/child1";
        final String CHILD_URI_2 = "http://example.org/child2";
        final String CHILD_URI_3 = "http://example.org/child3";

        LdpResource parent = mock(LdpResource.class);
        when(parent.getURI()).thenReturn(URI.create(PARENT_URI));

        LdpResource child1 = mock(LdpResource.class);
        when(child1.getURI()).thenReturn(URI.create(CHILD_URI_1));
        when(child1.getChildren()).thenReturn(new ArrayList<>());

        LdpResource child2 = mock(LdpResource.class);
        when(child2.getURI()).thenReturn(URI.create(CHILD_URI_2));

        LdpResource child3 = mock(LdpResource.class);
        when(child3.getURI()).thenReturn(URI.create(CHILD_URI_3));
        when(child3.getChildren()).thenReturn(new ArrayList<>());

        when(parent.getChildren()).thenReturn(Arrays.asList(child1, child2));
        when(child2.getChildren()).thenReturn(Arrays.asList(child3));

        String ORIG_BODY = "body";
        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        template.sendBodyAndHeaders("direct:testHierarchical",
                                    ORIG_BODY,
                                    new HashMap<String, Object>() {

                                        {
                                            put(HEADER_LDP_RESOURCES,
                                                Arrays.asList(parent));
                                            put(HEADER_URI_MAP,
                                                new HashMap<>());
                                            put(TEST_HEADER, TEST_HEADER_VALUE);
                                        }
                                    });

        /* Make sure we have our deposits */
        mockDeposit.setExpectedCount(4);

        /*
         * Make sure we have a filtered message
         */
        mockOut.setExpectedCount(1);

        assertMockEndpointsSatisfied();

        Message filterMessage = mockOut.getExchanges().get(0).getIn();

        /* Make sure we populated our map */
        Map<?, ?> uriMap = filterMessage.getHeader(HEADER_URI_MAP, Map.class);
        assertEquals(4, uriMap.size());

        /* Make sure we've mapped our objects */
        Arrays.asList(parent, child1, child2, child3).stream()
                .forEach(r -> assertTrue(uriMap
                        .containsKey(r.getURI().toString())));

        /* Assure we do not erase/mutate anything we don't know about */
        assertEquals(ORIG_BODY, filterMessage.getBody());
        assertEquals(filterMessage.getHeader(TEST_HEADER), TEST_HEADER_VALUE);

    }

    /*
     * Verifies that binary-descriptive resources are deposited via a merge with
     * the LDP-created description resource
     */
    @Test
    @SuppressWarnings("unchecked")
    public void depositWithBinaryTest() throws Exception {
        String PARENT_RESOURCE_URI = "test:/parent";
        String BINARY_RESOURCE_URI = "test:/bin/test.txt";
        String DESCRIPTION_RESOURCE_URI = "test:/desc";

        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        File file = mock(File.class);

        LdpResource descriptiveResource = mock(LdpResource.class);
        when(descriptiveResource.getURI())
                .thenReturn(URI.create(DESCRIPTION_RESOURCE_URI));
        when(descriptiveResource.getChildren()).thenReturn(Arrays.asList());
        when(descriptiveResource.getMediaType()).thenReturn("text/turtle");
        when(descriptiveResource.getBody()).thenReturn(IOUtils
                .toInputStream(String.format(
                                             "<%s> a <test:Binary> .\n"
                                                     + "<%s> <test:inverseRel> <%s> .",
                                             BINARY_RESOURCE_URI,
                                             DESCRIPTION_RESOURCE_URI,
                                             PARENT_RESOURCE_URI)));

        LdpResource binaryResource = mock(LdpResource.class);
        when(binaryResource.getChildren()).thenReturn(Arrays.asList());
        when(binaryResource.getMediaType()).thenReturn("text/plain");
        when(binaryResource.getBody())
                .thenReturn(IOUtils.toInputStream("test!"));
        when(binaryResource.getURI())
                .thenReturn(URI.create(BINARY_RESOURCE_URI));
        when(binaryResource.getDescription()).thenReturn(descriptiveResource);

        LdpResource parent = mock(LdpResource.class);
        when(parent.getURI()).thenReturn(URI.create(PARENT_RESOURCE_URI));
        when(parent.getMediaType()).thenReturn("text/turtle");
        when(parent.getChildren()).thenReturn(Arrays.asList(binaryResource));
        when(parent.getBody()).thenReturn(IOUtils.toInputStream(String
                .format("<%s> <test:rel> <%s> .",
                        PARENT_RESOURCE_URI,
                        DESCRIPTION_RESOURCE_URI)));

        LdpPackageAnalyzerFactory<File> analyzerFactory =
                mock(LdpPackageAnalyzerFactory.class);

        LdpPackageAnalyzer<File> analyzer =
                (LdpPackageAnalyzer<File>) mock(LdpPackageAnalyzer.class);
        when(analyzer.getContainerRoots(file))
                .thenReturn(Arrays.asList(parent));

        when(analyzerFactory.newAnalyzer()).thenReturn(analyzer);

        driver.setPackageAnalyzerFactory(analyzerFactory);

        MockLDP ldp = new MockLDP();

        /* Mock repository interactions */
        context.getRouteDefinition(ID_HTTP_OPERATION).adviceWith(context, ldp);

        template.sendBodyAndHeader("direct:testEntireDeposit",
                                   file,
                                   TEST_HEADER,
                                   TEST_HEADER_VALUE);

        mockOut.setExpectedCount(1);

        assertMockEndpointsSatisfied();

        Message outMessage = mockOut.getExchanges().get(0).getIn();

        /* Make sure we have three resources */
        assertEquals(3, ldp.httpBodies.size());

        /* Make sure the original URIs are not in the bodies! */
        ldp.httpBodies.forEach((k, v) -> {
            assertFalse(v.contains(DESCRIPTION_RESOURCE_URI));
            assertFalse(v.contains(PARENT_RESOURCE_URI));
            assertFalse(v.contains(BINARY_RESOURCE_URI));
        });

        Map<String, String> uriMap =
                outMessage.getHeader(HEADER_URI_MAP, Map.class);

        /* read in the LDP parent object */
        Model model = ModelFactory.createDefaultModel();
        String ldpParentURI = uriMap.get(PARENT_RESOURCE_URI);
        model.read(IOUtils.toInputStream(ldp.httpBodies.get(ldpParentURI)),
                   null,
                   "TURTLE");

        /* We only specified one child, so make sure that remains true */
        assertEquals(1,
                     model.listObjectsOfProperty(model
                             .getProperty("ldp:contains")).toList().size());

        /* Make sure this child is the binary */
        assertEquals(uriMap.get(BINARY_RESOURCE_URI),
                     model.listObjectsOfProperty(model
                             .getProperty("ldp:contains")).toList().get(0)
                             .toString());

        /*
         * Make sure our test rel is still there, and that it points to the
         * description
         * resource
         */
        assertEquals(1,
                     model.listObjectsOfProperty(model.getProperty("test:rel"))
                             .toSet().size());
        String ldpDescriptionURI =
                model.listObjectsOfProperty(model.getProperty("test:rel"))
                        .toList().get(0).toString();

        assertEquals(ldpDescriptionURI, uriMap.get(DESCRIPTION_RESOURCE_URI));

        /* Now load the description resource */
        model = ModelFactory.createDefaultModel();
        model.read(IOUtils.toInputStream(ldp.httpBodies.get(ldpDescriptionURI)),
                   null,
                   "TURTLE");

        /* It should have the inverse rel */
        assertEquals(1,
                     model.listObjectsOfProperty(model
                             .getProperty("test:inverseRel")).toList().size());
        /* The inverse rel should point to the parent */
        assertEquals(ldpParentURI,
                     model.listObjectsOfProperty(model
                             .getProperty("test:inverseRel")).toList().get(0)
                             .toString());
    }

    /* Verifies that the URI re-mapping route actually re-maps resources */
    @Test
    public void remapURiTest() throws Exception {

        final String ETAG = "Etag";

        Map<String, String> uriMap = new HashMap<>();
        uriMap.put("test:/1", "http://example.org/1");
        uriMap.put("test:/2", "http://example.org/2");
        uriMap.put("test:/3", "http://example.org/3");

        Map<String, String> objectContentMap = new HashMap<>();

        objectContentMap.put("http://example.org/1",
                             "<http://example.org/1> <test:rel> <test:/3> .");
        objectContentMap
                .put("http://example.org/2",
                     "<http://example.org/2> <test:rel> <http://example.org/another> .");

        objectContentMap.put("http://example.org/3",
                             "<test:/3> a <http://example.org/whatever> .");
        context.getRouteDefinition(ID_DEPOSIT_REMAP)
                .adviceWith(context, new AdviceWithRouteBuilder() {

                    @Override
                    public void configure() throws Exception {

                        /* Fake an LDP GET */
                        interceptSendToEndpoint("direct:_retrieveForUpdate")
                                .skipSendToOriginalEndpoint()
                                .setHeader(Exchange.CONTENT_TYPE,
                                           constant("text/turtle"))
                                .process(e -> {
                            e.getIn().setBody(objectContentMap.get(e.getIn()
                                    .getHeader(Exchange.HTTP_URI)));
                            e.getIn().setHeader(HttpHeaders.ETAG, ETAG);
                        });

                        interceptSendToEndpoint("direct:_do_http_op")
                                .skipSendToOriginalEndpoint()
                                .to("mock:deposit");
                    }
                });

        String ORIG_BODY = "body";
        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        template.sendBodyAndHeaders("direct:testRemapUris",
                                    ORIG_BODY,
                                    new HashMap<String, Object>() {

                                        {
                                            put(HEADER_URI_MAP, uriMap);
                                            put(HEADER_BINARY_URIS,
                                                new HashSet<>());
                                            put(TEST_HEADER, TEST_HEADER_VALUE);
                                        }
                                    });

        mockOut.setExpectedCount(1);

        /* Assure we do not erase/mutate anything we don't know about */
        Message filterMessage = mockOut.getExchanges().get(0).getIn();
        assertEquals(ORIG_BODY, filterMessage.getBody());
        assertEquals(filterMessage.getHeader(TEST_HEADER), TEST_HEADER_VALUE);

        /*
         * We expect two of the objects to be updated. The third doesn't have
         * any URIs that need mapping
         */
        mockDeposit.setExpectedCount(2);

        assertMockEndpointsSatisfied();

        /* Make sure that they've actually been transformed */
        for (Exchange e : mockDeposit.getExchanges()) {

            String body = patch(objectContentMap
                    .get(e.getIn().getHeader(Exchange.HTTP_URI, String.class)),
                                e.getIn().getBody(String.class));

            /* Assert that the body contains at least one of the real URIs */
            assertTrue(uriMap.values().stream()
                    .filter(uri -> body.contains(uri)).count() > 0);

            /* Assert that the body does NOT contain any original URIs */
            assertTrue(uriMap.keySet().stream()
                    .filter(uri -> body.contains(uri)).count() == 0);

            /*
             * Assert that this is a PUT with TURTLE, and that IF-Match is set
             */
            assertEquals(e.getIn().getHeader(Exchange.HTTP_METHOD,
                                             String.class),
                         "PATCH");
            assertEquals(e.getIn().getHeader(Exchange.CONTENT_TYPE,
                                             String.class),
                         "application/sparql-update");
            assertEquals(ETAG, e.getIn().getHeader(HttpHeaders.IF_MATCH));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected RouteBuilder createRouteBuilder() {
        driver = new LdpDepositDriver();
        driver.setPackageAnalyzerFactory(mock(LdpPackageAnalyzerFactory.class));

        driver.init();

        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                driver.addRoutesToCamelContext(context());

                from("direct:testEntireDeposit")
                        .to(DepositDriver.ROUTE_DEPOSIT_RESOURCES)
                        .to("mock:out");

                from("direct:testHierarchical")
                        .to("direct:_deposit_hierarchical").to("mock:out");

                from("direct:testRemapUris").to("direct:_do_update_uris")
                        .to("mock:out");

                from("direct:fakeDeposit").process(e -> e.getIn()
                        .getHeader(HEADER_URI_MAP, Map.class)
                        .put(e.getIn().getBody(LdpResource.class).getURI()
                                .toString(),
                             deposited(e.getIn().getBody(LdpResource.class)
                                     .getURI())));
            }
        };
    }

    private String deposited(URI uri) {
        return URI.create(uri.toString() + "#deposited").toString();
    }

    private class MockLDP
            extends AdviceWithRouteBuilder {

        AtomicInteger depositCount = new AtomicInteger(0);

        Map<String, String> httpBodies = new HashMap<>();

        Map<String, String> mediaTypes = new HashMap<>();

        @Override
        public void configure() throws Exception {
            interceptSendToEndpoint("http4:ldp-host?throwExceptionOnFailure=false")
                    .skipSendToOriginalEndpoint().choice()
                    .when(header(Exchange.HTTP_METHOD).isEqualTo("GET"))
                    .setHeader(HttpHeaders.ETAG, constant("etag"))
                    .process(e -> {
                        String uri = e.getIn().getHeader(Exchange.HTTP_URI,
                                                         String.class);
                        String type = mediaTypes.get(uri);
                        String accept = e.getIn().getHeader(HttpHeaders.ACCEPT,
                                                            String.class);
                        e.getIn().setHeader(Exchange.CONTENT_TYPE, type);
                        if (accept == null || accept.equals(type)) {
                            e.getIn().setBody(httpBodies.get(uri));
                            e.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE,
                                                200);
                        } else {
                            throw new HttpOperationFailedException(uri,
                                                                   406,
                                                                   "not acceptable",
                                                                   uri,
                                                                   new HashMap<>(),
                                                                   "");
                        }
                    }).when(header(Exchange.HTTP_METHOD).isEqualTo("PATCH"))
                    .process(e -> {
                        String uri = e.getIn().getHeader(Exchange.HTTP_URI,
                                                         String.class);
                        assertNotNull(uri);
                        httpBodies.put(uri,
                                       patch(httpBodies.get(uri),
                                             e.getIn().getBody(String.class)));
                        e.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);

                    }).when(header(Exchange.HTTP_METHOD).isEqualTo("POST"))
                    .process(e -> {
                        String uri = "http://example.org/deposited/"
                                + depositCount.incrementAndGet();
                        httpBodies.put(uri, e.getIn().getBody(String.class));

                        mediaTypes.put(uri,
                                       e.getIn().getHeader(
                                                           Exchange.CONTENT_TYPE,
                                                           String.class));

                        e.getIn().setHeader(HttpHeaders.LOCATION, uri);
                        e.getIn().setHeader(HttpHeaders.ETAG, "etag");

                        String parentURI = e.getIn()
                                .getHeader(Exchange.HTTP_URI, String.class);

                        /*
                         * add ldp:contains. Yes, it's not the real LDP URI, but
                         * this is a fake test impl.
                         */
                        if (httpBodies.containsKey(parentURI)) {
                            String parentBody = httpBodies.get(parentURI);
                            parentBody = parentBody
                                    + String.format("\n<%s> <ldp:contains> <%s> .",
                                                    parentURI,
                                                    uri);
                            httpBodies.put(parentURI, parentBody);
                        }

                        /* If binary */
                        if (mediaTypes.get(uri) != "text/turtle") {
                            String descriptionURI =
                                    "http://example.org/descriptions/"
                                            + depositCount.incrementAndGet();
                            httpBodies.put(descriptionURI,
                                           String.format("<%s> <iana:describes> <%s> .",
                                                         descriptionURI,
                                                         uri));
                            mediaTypes.put(descriptionURI, "text/turtle");
                            e.getIn().setHeader("Link",
                                                String.format("<%s> rel=describedby",
                                                              descriptionURI));
                        }
                        e.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
                    }).otherwise().process(e -> {
                        throw new RuntimeException("Unknown http method "
                                + e.getIn().getHeader(Exchange.HTTP_METHOD));
                    });
        }
    }

    private static String patch(String body, String patch) {
        Model model = ModelFactory.createDefaultModel();
        model.read(IOUtils.toInputStream(body), "", "TURTLE");

        DatasetGraph graph = DatasetGraphFactory.create(model.getGraph());
        UpdateAction.parseExecute(patch, graph);

        StringWriter writer = new StringWriter();
        model.write(writer, "TURTLE");

        return writer.toString();
    }
}
