
package org.dataconservancy.packaging.ingest.camel.impl;

import java.lang.annotation.Annotation;

import java.util.HashMap;

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

import org.junit.Before;
import org.junit.Test;

import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_BEGIN;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_CANONICALIZE;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_COMMIT;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_ROLLBACK;
import static org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.HEADER_FCTRPO_TX_BASEURI;
import static org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.ID_COMMIT_TRANSACTION;
import static org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.ID_ROLLBACK_TRANSACTION;
import static org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.ID_START_TRANSACTION;

@SuppressWarnings("serial")
public class FedoraDepositDriverTest
        extends CamelTestSupport {

    static final String FEDORA_BASEURI = "http://example.org/fedora";

    static final String FEDORA_TX_BASEURI = "http://example.org/fedora/tx/123";

    static final String OPERATION_TX_COMMIT = "test.txcommit";

    static final String OPERATION_TX_ROLLBACK = "test.txrollback";

    String operation;

    FedoraDepositDriver driver;

    @EndpointInject(uri = "mock:out")
    private MockEndpoint mockOut = new MockEndpoint();

    @Produce
    private ProducerTemplate template;

    @Before
    public void clear() {
        this.operation = null;
    }

    @Test
    public void transactionBeginTest() throws Exception {
        context.getRouteDefinition(ID_START_TRANSACTION)
                .adviceWith(context, new FakeTX());

        String PATH = "/test/path";
        String ORIG_URI = FEDORA_BASEURI + PATH;
        String TEST_BODY = "test.body";
        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        template.sendBodyAndHeaders("direct:testBeginTransaction",
                                    TEST_BODY,
                                    new HashMap<String, Object>() {

                                        {
                                            put(Exchange.HTTP_URI, ORIG_URI);
                                            put(TEST_HEADER, TEST_HEADER_VALUE);

                                        }
                                    });

        mockOut.setExpectedCount(1);

        assertMockEndpointsSatisfied();

        Message msg = mockOut.getExchanges().get(0).getIn();
        assertEquals(FEDORA_TX_BASEURI + PATH,
                     msg.getHeader(Exchange.HTTP_URI));
        assertEquals(TEST_BODY, msg.getBody());
        assertEquals(TEST_HEADER_VALUE, msg.getHeader(TEST_HEADER));
        assertEquals(FEDORA_TX_BASEURI,
                     msg.getHeader(HEADER_FCTRPO_TX_BASEURI));
    }

    @Test
    public void transactionCommitTest() throws Exception {
        context.getRouteDefinition(ID_COMMIT_TRANSACTION)
                .adviceWith(context, new FakeTX());

        String PATH = "/test/path";
        String ORIG_URI = FEDORA_TX_BASEURI + PATH;
        String TEST_BODY = "test.body";
        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        template.sendBodyAndHeaders("direct:testCommitTransaction",
                                    TEST_BODY,
                                    new HashMap<String, Object>() {

                                        {
                                            put(Exchange.HTTP_URI, ORIG_URI);
                                            put(TEST_HEADER, TEST_HEADER_VALUE);
                                            put(HEADER_FCTRPO_TX_BASEURI,
                                                FEDORA_TX_BASEURI);
                                        }
                                    });

        mockOut.setExpectedCount(1);

        assertMockEndpointsSatisfied();

        Message msg = mockOut.getExchanges().get(0).getIn();
        assertEquals(TEST_BODY, msg.getBody());
        assertEquals(TEST_HEADER_VALUE, msg.getHeader(TEST_HEADER));
        assertEquals(OPERATION_TX_COMMIT, operation);
    }

    @Test
    public void transactionRollbackTest() throws Exception {
        context.getRouteDefinition(ID_ROLLBACK_TRANSACTION)
                .adviceWith(context, new FakeTX());

        String PATH = "/test/path";
        String ORIG_URI = FEDORA_TX_BASEURI + PATH;
        String TEST_BODY = "test.body";
        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        template.sendBodyAndHeaders("direct:testRollbackTransaction",
                                    TEST_BODY,
                                    new HashMap<String, Object>() {

                                        {
                                            put(Exchange.HTTP_URI, ORIG_URI);
                                            put(TEST_HEADER, TEST_HEADER_VALUE);
                                            put(HEADER_FCTRPO_TX_BASEURI,
                                                FEDORA_TX_BASEURI);
                                        }
                                    });

        mockOut.setExpectedCount(1);

        assertMockEndpointsSatisfied();

        Message msg = mockOut.getExchanges().get(0).getIn();
        assertEquals(TEST_BODY, msg.getBody());
        assertEquals(TEST_HEADER_VALUE, msg.getHeader(TEST_HEADER));
        assertEquals(OPERATION_TX_ROLLBACK, operation);
    }

    @Test
    public void canonicalizeTest() throws Exception {

        String PATH = "/test/path";
        String ORIG_URI = FEDORA_TX_BASEURI + PATH;
        String TEST_BODY = "test.body";
        String TEST_HEADER = "test.header";
        String TEST_HEADER_VALUE = "test.header.value";

        template.sendBodyAndHeaders("direct:testCanonicalize",
                                    TEST_BODY,
                                    new HashMap<String, Object>() {

                                        {
                                            put(Exchange.HTTP_URI, ORIG_URI);
                                            put(TEST_HEADER, TEST_HEADER_VALUE);
                                            put(HEADER_FCTRPO_TX_BASEURI,
                                                FEDORA_TX_BASEURI);
                                        }
                                    });

        mockOut.setExpectedCount(1);

        assertMockEndpointsSatisfied();

        Message msg = mockOut.getExchanges().get(0).getIn();
        assertEquals(TEST_BODY, msg.getBody());
        assertEquals(TEST_HEADER_VALUE, msg.getHeader(TEST_HEADER));
        assertEquals(FEDORA_BASEURI + PATH, msg.getHeader(Exchange.HTTP_URI));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        driver = new FedoraDepositDriver();
        driver.init(new FedoraConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return FedoraConfig.class;
            }

            @Override
            public String fedora_baseuri() {
                return FEDORA_BASEURI;
            }
        });

        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                driver.addRoutesToCamelContext(context());

                from("direct:testBeginTransaction").to(ROUTE_TRANSACTION_BEGIN)
                        .to("mock:out");

                from("direct:testCommitTransaction")
                        .to(ROUTE_TRANSACTION_COMMIT).to("mock:out");

                from("direct:testRollbackTransaction")
                        .to(ROUTE_TRANSACTION_ROLLBACK).to("mock:out");

                from("direct:testCanonicalize")
                        .to(ROUTE_TRANSACTION_CANONICALIZE).to("mock:out");
            }
        };
    }

    private class FakeTX
            extends AdviceWithRouteBuilder {

        @Override
        public void configure() throws Exception {
            interceptSendToEndpoint("http4:fcrepo-host")
                    .skipSendToOriginalEndpoint().choice()
                    .when(header(Exchange.HTTP_URI)
                            .isEqualTo(FEDORA_BASEURI + "/fcr:tx"))
                    .process(e -> assertEquals("POST",
                                               e.getIn()
                                                       .getHeader(Exchange.HTTP_METHOD)))
                    .removeHeaders("*")
                    .setHeader(HttpHeaders.LOCATION,
                               constant(FEDORA_TX_BASEURI))
                    .when(header(Exchange.HTTP_URI).isEqualTo(FEDORA_TX_BASEURI
                            + "/fcr:tx/fcr:commit"))
                    .process(e -> {
                        assertEquals("POST",
                                     e.getIn().getHeader(Exchange.HTTP_METHOD));
                        operation = OPERATION_TX_COMMIT;
                    })
                    .when(header(Exchange.HTTP_URI).isEqualTo(FEDORA_TX_BASEURI
                            + "/fcr:tx/fcr:rollback"))
                    .process(e -> {
                        assertEquals("POST",
                                     e.getIn().getHeader(Exchange.HTTP_METHOD));
                        operation = OPERATION_TX_ROLLBACK;
                    });

        }
    };
}
