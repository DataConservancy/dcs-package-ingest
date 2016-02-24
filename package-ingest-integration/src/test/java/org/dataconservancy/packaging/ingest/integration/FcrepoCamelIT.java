
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
package org.dataconservancy.packaging.ingest.integration;

import java.util.HashMap;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import org.fcrepo.camel.FcrepoOperationFailedException;
import org.fcrepo.camel.FcrepoTransactionManager;
import org.fcrepo.camel.HttpMethods;

import org.junit.Before;
import org.junit.Test;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.dataconservancy.packaging.ingest.integration.FedoraTestConstants.FEDORA_BASEURI;

/**
 * Tests for interacting with Fedora via Camel.
 * <p>
 * This is exploratory: investingating interacting with Fedora4 via Apache camel
 * via fcrepo-camel and pure HTTP.
 * </p>
 * 
 * @author apb@jhu.edu
 */
@SuppressWarnings("serial")
public class FcrepoCamelIT
        extends CamelTestSupport {

    private static final String HEADER_FCREPO_BASEURI = "test.fcrepo.baseURI";

    private static final String HEADER_FCREPO_CANONICAL_LOCATION =
            "test.fcrepo.canonical.location";

    private static final String HEADER_FCTRPO_TX_BASEURI =
            "test.fcrepo.txBaseURI";

    private TransactionTemplate txTemplate;

    private FcrepoTransactionManager txMgr;

    private final HttpClient client = HttpClientBuilder.create()
            .setMaxConnPerRoute(Integer.MAX_VALUE)
            .setMaxConnTotal(Integer.MAX_VALUE).build();

    /* Mock endpoint for inspecting messages sent to it */
    @EndpointInject(uri = "mock:response")
    private MockEndpoint responseEndpoint;

    /* Errors routed here for observation */
    @EndpointInject(uri = "mock:error")
    private MockEndpoint errorEndpoint;

    /*
     * This allows us to programatically send a message to a Camel endpoint in
     * java
     */
    @Produce
    private ProducerTemplate template;

    /*
     * Verifies that Fedora can ingest an object via fcrepo-camel, and retrieve
     * it
     */
    @Test
    public void putObjectFcrepoCamelTest() throws Exception {

        /*
         * Change this to direct:camelComponentTransactedRequest to see what
         * happens in a transaction
         */
        template.sendBodyAndHeaders("direct:camelComponentRequest",
                                    "<> a <http://www.w3.org/ns/ldp#BasicContainer> .",
                                    new HashMap<String, Object>() {

                                        {
                                            put(Exchange.HTTP_METHOD, "POST");
                                            put(Exchange.CONTENT_TYPE,
                                                "text/turtle");
                                        }
                                    });

        /* Verify that we got the response */
        responseEndpoint.setExpectedCount(1);
        responseEndpoint.assertIsSatisfied();

        /*
         * Now, take a look at the message sent to the mock endpoint. We should
         * find the URI of the newly created object in the body, as per the
         * behaviour of the fcrepo endpoint.
         */
        Exchange resultExchange = responseEndpoint.getExchanges().get(0);

        assertEquals(HttpStatus.SC_CREATED,
                     resultExchange.getIn()
                             .getHeader(Exchange.HTTP_RESPONSE_CODE));

        /*
         * When the route occurs in a transaction, the object URI is local to
         * the transaction, and returns a 4xx when retrieved after the
         * transaction is finished. Furthermore, the response does not contain
         * any information related to the transaction that would allow the
         * client to figure out the canonical URI. This is problematic.
         */
        String newObjectURI = resultExchange.getIn().getBody(String.class);

        /* Verify that we can get back the object */
        HttpGet get = new HttpGet(newObjectURI);
        HttpResponse response = client.execute(get);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

    }

    /*
     * Verifies that fedora can ingest an object via a transaction using lain
     * HTTP APIs, implemented in Camel.
     */
    @Test
    public void putObjectHttpTest() throws Exception {

        /* For convenience sake, we just POST to the root */
        final String uriOfContainer = FEDORA_BASEURI;

        template.sendBodyAndHeaders("direct:httpComponentRequest",
                                    "<> a <http://www.w3.org/ns/ldp#BasicContainer> .",
                                    new HashMap<String, Object>() {

                                        {
                                            put(HEADER_FCREPO_BASEURI,
                                                FEDORA_BASEURI);
                                            put(Exchange.HTTP_URI,
                                                uriOfContainer);
                                            put(Exchange.HTTP_METHOD, "POST");
                                            put(Exchange.CONTENT_TYPE,
                                                "text/turtle");
                                        }
                                    });

        Exchange resultExchange = responseEndpoint.getExchanges().get(0);

        /* Verify that we got the response */
        responseEndpoint.setExpectedCount(1);
        responseEndpoint.assertIsSatisfied();

        assertEquals(HttpStatus.SC_CREATED,
                     resultExchange.getIn()
                             .getHeader(Exchange.HTTP_RESPONSE_CODE));

        Message responseMessage = resultExchange.getIn();;

        String newObjectURI =
                responseMessage.getHeader(HEADER_FCREPO_CANONICAL_LOCATION,
                                          String.class);

        /* Verify that we can get back the object */
        HttpGet get = new HttpGet(newObjectURI);
        HttpResponse response = client.execute(get);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

    }

    /* Set up the transaction manager */
    @Before
    public void setUp() throws Exception {
        super.setUp();

        txTemplate = new TransactionTemplate(txMgr);
        txTemplate
                .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        txTemplate.afterPropertiesSet();
    }

    /*
     * Put our transaction manager in the camel registry. In practice, this
     * would be done via spring or OSGi, but we're doing it manually here. See:
     * http://camel.apache.org/transactional-client.html
     */
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        final JndiRegistry reg = super.createRegistry();

        txMgr = new FcrepoTransactionManager();
        txMgr.setBaseUrl(FEDORA_BASEURI);
        reg.bind("txManager", txMgr);

        final SpringTransactionPolicy txPolicy = new SpringTransactionPolicy();
        txPolicy.setTransactionManager(txMgr);
        txPolicy.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        reg.bind("required", txPolicy);

        return reg;
    }

    /* We define the camel routes available for testing here */
    @Override
    protected RouteBuilder createRouteBuilder() {
        String fcrepoEndpoint = FEDORA_BASEURI.replace("http://", "fcrepo:");

        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {

                onException(FcrepoOperationFailedException.class).handled(true)
                        .to("mock:errorEndpoint");

                /*
                 * Simple route, just sends a request to Fedora via the
                 * fcrepo-camel endpoint, and routes the response to a mock
                 * endpoint
                 */
                from("direct:camelComponentRequest").to(fcrepoEndpoint)
                        .to("mock:response");

                /*
                 * Uses fcrepo-camel in a transaction. Note that the location of
                 * returned responses point to the transaction URIs, and not
                 * canonical. There is no way to retrieve the canonical URI
                 * without a priori knowledge of fedora transaction URI
                 * construction.
                 */
                from("direct:camelComponentTransactedRequest")
                        .transacted("required").to(fcrepoEndpoint)
                        .to("mock:response");

                /*
                 * Implements a request within a transaction, using pure HTTP
                 * (i.e NOT fcrepo-camel)
                 */
                from("direct:httpComponentRequest")
                        .to("direct:startTransaction") /*  */
                        .doTry() /*  */
                        .to("http4:fcrepo-host").to("direct:canonicalize")
                        .to("direct:commitTransaction").to("mock:response") /* */
                        .doCatch(Exception.class)
                        .to("direct:rollbackTransaction").to("mock:error") /* */
                        .end();

                /*
                 * Starts a transaction in Fedora via HTTP API, and populates
                 * HEADER_FCTRPO_TX_BASEURI with the baseURI of the transaction.
                 */
                from("direct:startTransaction")
                        .enrich("direct:_doStartTransaction",
                                new AggregationStrategy() {

                                    @Override
                                    public Exchange aggregate(Exchange oldExchange,
                                                              Exchange newExchange) {

                                        String txBase =
                                                newExchange
                                                        .getIn()
                                                        .getHeader("Location",
                                                                   String.class);

                                        String fedoraBase =
                                                oldExchange
                                                        .getIn()
                                                        .getHeader(HEADER_FCREPO_BASEURI,
                                                                   String.class);
                                        String dest =
                                                oldExchange
                                                        .getIn()
                                                        .getHeader(Exchange.HTTP_URI,
                                                                   String.class);

                                        oldExchange
                                                .getIn()
                                                .setHeader(HEADER_FCTRPO_TX_BASEURI,
                                                           txBase);

                                        oldExchange
                                                .getIn()
                                                .setHeader(Exchange.HTTP_URI,
                                                           dest.replace(fedoraBase,
                                                                        txBase));

                                        return oldExchange;
                                    }
                                });

                /*
                 * Actually does the work of starting a transaction in Fedora
                 * via HTTP, sends a POST to the fcr:tx endpoint, which creates
                 * a transaction in Fedora.
                 */
                from("direct:_doStartTransaction")
                        .removeHeaders("*", HEADER_FCREPO_BASEURI)
                        .setHeader(Exchange.HTTP_URI,
                                   simple(String
                                           .format("${in.header.%s}/fcr:tx",
                                                   HEADER_FCREPO_BASEURI)))
                        .setHeader(Exchange.HTTP_METHOD,
                                   constant(HttpMethods.POST))
                        .to("http4:fcrepo-host");

                /*
                 * Commits transaction in Fedora via HTTP, does not modify the
                 * message sent through it.
                 */
                from("direct:commitTransaction")
                        .enrich("direct:_doCommitTransaction",
                                ((orig, resp) -> orig));

                /*
                 * Actually does the work of committing a trancation in Fedora
                 * via HTTP, sends a POST to the local transaction's
                 * fcr:tx/fcr:commit endpoint.
                 */
                from("direct:_doCommitTransaction")
                        .removeHeaders("*", HEADER_FCTRPO_TX_BASEURI)
                        .setHeader(Exchange.HTTP_URI,
                                   simple(String
                                           .format("${in.header.%s}/fcr:tx/fcr:commit",
                                                   HEADER_FCTRPO_TX_BASEURI)))
                        .setHeader(Exchange.HTTP_METHOD,
                                   constant(HttpMethods.POST))
                        .to("http4:fcrepo-host");

                /*
                 * Rollback transaction in Fedora via HTTP, does not modify the
                 * message sent through it.
                 */
                from("direct:rollbackTransaction")
                        .enrich("direct:_doRollbackTransaction",
                                ((orig, resp) -> orig));

                /*
                 * Actually does the work of transaction rollback in Fedora via
                 * HTTP, sends a POST to the local transaction's
                 * fcr:tx/fcr:rollback endpoint.
                 */
                from("direct:_doRollbackTransaction")
                        .removeHeaders("*", HEADER_FCTRPO_TX_BASEURI)
                        .setHeader(Exchange.HTTP_URI,
                                   simple(String
                                           .format("${in.header.%s}/fcr:tx/fcr:rollback",
                                                   HEADER_FCTRPO_TX_BASEURI)))
                        .setHeader(Exchange.HTTP_METHOD,
                                   constant(HttpMethods.POST))
                        .to("http4:fcrepo-host");

                /*
                 * For (HTTP) Fedora operations in a transaction, the Location
                 * header points to the URI of the object *in the transaction*.
                 * A really nice solution solution would be HEAD on this URI to
                 * get a Link rel=canonical header with the actual object URI
                 * (as shown in the route direct:canonicalizeViaLink).
                 * Unfortunately, the fedora API only provides canonical links
                 * for the root resource in a transaction. As a result, we need
                 * to rebase the URI to the Fedora baseURI.
                 */
                from("direct:canonicalize")
                        .process(exchange -> exchange
                                .getIn()
                                .setHeader(HEADER_FCREPO_CANONICAL_LOCATION,
                                           exchange.getIn()
                                                   .getHeader("Location",
                                                              String.class)
                                                   .replaceFirst(exchange.getIn()
                                                                         .getHeader(HEADER_FCTRPO_TX_BASEURI,
                                                                                    String.class),
                                                                 exchange.getIn()
                                                                         .getHeader(HEADER_FCREPO_BASEURI,
                                                                                    String.class))));

                /*
                 * This *would* get the canonical URI if fedora supported Link
                 * rel=canonical on objects within a transaction. Alas, this is
                 * only supported on the root resource, so we can't do this.
                 */
                from("direct:canonicalizeViaLink")
                        .enrich("direct:_doHead",
                                ((orig, resp) -> {
                                    orig.getIn()
                                            .setHeader(HEADER_FCREPO_CANONICAL_LOCATION,
                                                       getLinkRel(resp.getIn()
                                                                          .getHeader("Link",
                                                                                     String.class),
                                                                  "canonical"));
                                    return orig;
                                }));

                /* Perform an HTTP head */
                from("direct:_doHead").removeHeaders("*", "Location")
                        .setHeader(Exchange.HTTP_URI, header("Location"))
                        .setHeader(Exchange.HTTP_METHOD, constant("HEAD"));

            }

        };
    }

    /* Quick & dirty way to parse a Link header to retrieve a particular rel */
    private static String getLinkRel(String linkHeader, String rel) {

        for (String linkInstance : linkHeader.split(",")) {
            if (linkInstance.contains("rel=\"" + rel + "\"")) {
                return linkInstance.substring(linkInstance.indexOf('<') + 1,
                                              linkInstance.indexOf('>'));
            }
        }

        return null;
    }
}
