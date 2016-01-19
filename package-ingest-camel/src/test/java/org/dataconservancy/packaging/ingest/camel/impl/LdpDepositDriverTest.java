
package org.dataconservancy.packaging.ingest.camel.impl;

import java.net.URI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpHeaders;

import org.junit.Test;

import org.dataconservancy.packaging.ingest.LdpResource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_LDP_RESOURCES;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_URI_MAP;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.PROP_LDP_CONTAINER;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.ID_DEPOSIT_ITERATE;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.ID_DEPOSIT_REMAP;

/* Verifies that a hierarchies of LDP nodes will be traversed for deposit */
@SuppressWarnings("serial")
public class LdpDepositDriverTest
        extends CamelTestSupport {

    @EndpointInject(uri = "mock:out")
    private MockEndpoint mockOut = new MockEndpoint();

    @EndpointInject(uri = "mock:deposit")
    private MockEndpoint mockDeposit = new MockEndpoint();

    @Produce
    private ProducerTemplate template;

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
                                            put(HEADER_URI_MAP, new HashMap<>());
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
        Arrays.asList(parent, child1, child2, child3)
                .stream()
                .forEach(r -> assertTrue(uriMap.containsKey(r.getURI()
                        .toString())));

        /* Assure we do not erase/mutate anything we don't know about */
        assertEquals(ORIG_BODY, filterMessage.getBody());
        assertEquals(filterMessage.getHeader(TEST_HEADER), TEST_HEADER_VALUE);

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
                                    e.getIn().setBody(objectContentMap.get(e
                                            .getIn()
                                            .getHeader(HttpHeaders.LOCATION)));
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
            String body = e.getIn().getBody(String.class);

            /* Assert that the body contains at least one of the real URIs */
            assertTrue(uriMap.values().stream()
                    .filter(uri -> body.contains(uri)).count() > 0);

            /* Assert that the body does NOT contain any original URIs */
            assertTrue(uriMap.keySet().stream()
                    .filter(uri -> body.contains(uri)).count() == 0);

            /* Assert that this is a PUT with TURTLE, and that IF-Match is set */
            assertEquals(e.getIn()
                    .getHeader(Exchange.HTTP_METHOD, String.class), "PUT");
            assertEquals(e.getIn().getHeader(Exchange.CONTENT_TYPE,
                                             String.class),
                         "text/turtle");
            assertEquals(ETAG, e.getIn().getHeader(HttpHeaders.IF_MATCH));

            /* Assert that the URI is real */
            assertTrue(uriMap.values().contains(e.getIn()
                    .getHeader(Exchange.HTTP_URI)));

        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected RouteBuilder createRouteBuilder() {
        LdpDepositDriver driver = new LdpDepositDriver();
        driver.init(new HashMap<String, String>() {

            {
                put(PROP_LDP_CONTAINER, "test");
            }
        });

        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                driver.addRoutesToCamelContext(context());

                from("direct:testHierarchical")
                        .to("direct:_deposit_hierarchical").to("mock:out");

                from("direct:testRemapUris").to("direct:_remap_uris")
                        .to("mock:out");

                from("direct:fakeDeposit").process(e -> e
                        .getIn()
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

}
