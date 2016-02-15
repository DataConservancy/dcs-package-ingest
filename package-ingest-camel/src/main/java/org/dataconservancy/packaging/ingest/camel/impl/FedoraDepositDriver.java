
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.http.HttpHeaders;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpPackageProvenanceGenerator;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
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
    public void setPackageAnalyzer(LdpPackageAnalyzer<File> analyzer) {
        super.setPackageAnalyzer(analyzer);
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
                .process(e -> {
                    e.getIn()
                            .setHeader(Exchange.HTTP_URI,
                                       headerString(e, Exchange.HTTP_URI)
                                               .replace(headerString(e,
                                                                     HEADER_FCTRPO_TX_BASEURI),
                                                        config.fedora_baseuri()));
                });

        /*
         * Actually does the work of starting a transaction in Fedora
         * via HTTP, sends a POST to the fcr:tx endpoint, which creates
         * a transaction in Fedora.
         */
        from("direct:_doStartTransaction").id(ID_START_TRANSACTION)
                .removeHeaders("*", config.fedora_baseuri())
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_URI,
                           constant(config.fedora_baseuri() + "/fcr:tx"))
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
            orig.getIn()
                    .setHeader(Exchange.HTTP_URI,
                               dest.replace(config.fedora_baseuri(), txBase));
        }
        return orig;
    };

    static String headerString(Exchange e, String name) {
        return e.getIn().getHeader(name, String.class);
    }

}
