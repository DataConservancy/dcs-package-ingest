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
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.http.HttpHeaders;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
import org.dataconservancy.packaging.ingest.LdpPackageProvenanceGenerator;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.dataconservancy.packaging.ingest.camel.impl.config.FedoraConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = DepositDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = FedoraConfig.class)
public class FedoraDepositDriver
        extends LdpDepositDriver {

    static final String HEADER_FCTRPO_TX_BASEURI = "txBaseURI";

    static final String PROP_FEDORA_BASEURI = "fcrepo.baseuri";

    static final String ID_START_TRANSACTION = "fedora-start-transaction";

    static final String ID_COMMIT_TRANSACTION = "fedora-commit-transaction";

    static final String ID_ROLLBACK_TRANSACTION = "fedora-rollback-transaction";

    private FedoraConfig config;

    @Activate
    public void init(FedoraConfig config) {
        super.init();

        this.config = config;
    }

    @Reference
    public void setPackageAnalyzerFactory(LdpPackageAnalyzerFactory<File> analyzerFactory) {
        super.setPackageAnalyzerFactory(analyzerFactory);
    }

    @Reference
    public void setPackageProvenanceGenerator(LdpPackageProvenanceGenerator<File> gen) {
        super.setPackageProvenanceGenerator(gen);
    }

    @Override
    protected void configureTransactions() {
        /* TODO */
        from(ROUTE_TRANSACTION_BEGIN).id("fedora-tx-begin")
                .enrich("direct:_doStartTransaction", TX_DETAILS);

        from(ROUTE_TRANSACTION_COMMIT).id("fedora-tx-commit")
                .enrich("direct:_doCommitTransaction", ((orig, resp) -> orig));

        from(ROUTE_TRANSACTION_ROLLBACK).id("fedora-tx-rollback")
                .enrich("direct:_doRollbackTransaction",
                        ((orig, resp) -> orig));

        from(ROUTE_TRANSACTION_CANONICALIZE).id("fedora-tx-canonicalize")
                .process(e -> e.getIn()
                        .setHeader(Exchange.HTTP_URI,
                                   headerString(e, Exchange.HTTP_URI)
                                           .replace(strip(headerString(e,
                                                                       HEADER_FCTRPO_TX_BASEURI)),
                                                    strip(config
                                                            .fedora_baseuri()))));

        /*
         * Actually does the work of starting a transaction in Fedora
         * via HTTP, sends a POST to the fcr:tx endpoint, which creates
         * a transaction in Fedora.
         */
        from("direct:_doStartTransaction").id(ID_START_TRANSACTION)
                .removeHeaders("*")
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_URI,
                           constant(strip(config.fedora_baseuri()) + "/fcr:tx"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .to("http4:fcrepo-host");

        /*
         * Actually does the work of committing a transaction in Fedora
         * via HTTP, sends a POST to the local transaction's
         * fcr:tx/fcr:commit endpoint.
         */
        from("direct:_doCommitTransaction").id(ID_COMMIT_TRANSACTION)
                .removeHeaders("*", HEADER_FCTRPO_TX_BASEURI)
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_URI,
                           simple(String.format(
                                                "${in.header.%s}/fcr:tx/fcr:commit",
                                                HEADER_FCTRPO_TX_BASEURI)))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .to("http4:fcrepo-host");

        /*
         * Actually does the work of transaction rollback in Fedora via
         * HTTP, sends a POST to the local transaction's
         * fcr:tx/fcr:rollback endpoint.
         */
        from("direct:_doRollbackTransaction").id(ID_ROLLBACK_TRANSACTION)
                .removeHeaders("*", HEADER_FCTRPO_TX_BASEURI)
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_URI,
                           simple(String.format(
                                                "${in.header.%s}/fcr:tx/fcr:rollback",
                                                HEADER_FCTRPO_TX_BASEURI)))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .to("http4:fcrepo-host");

    }

    AggregationStrategy TX_DETAILS = (orig, tx) -> {
        String txBase = headerString(tx, HttpHeaders.LOCATION);

        String dest = headerString(orig, Exchange.HTTP_URI);

        orig.getIn().setHeader(HEADER_FCTRPO_TX_BASEURI, txBase);

        if (dest != null) {
            orig.getIn().setHeader(Exchange.HTTP_URI,
                                   dest.replace(strip(config.fedora_baseuri()),
                                                strip(txBase)));
        }
        return orig;
    };

    static String strip(String uri) {
        if (uri.endsWith("/")) {
            return uri.substring(0, uri.length() - 1);
        } else {
            return uri;
        }
    }

    static String headerString(Exchange e, String name) {
        return e.getIn().getHeader(name, String.class);
    }

}
