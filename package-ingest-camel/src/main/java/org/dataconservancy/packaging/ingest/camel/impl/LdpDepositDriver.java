
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

/**
 * Deposits package content into an LDP repository.
 * 
 * @author apb@jhu.edu
 */
@Component(service = DepositDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class LdpDepositDriver
        extends RouteBuilder
        implements DepositDriver {

    public static final String HEADER_LDP_ROOTS = "deposit.ldp.roots";

    private LdpPackageAnalyzer<File> analyzer;

    protected void configureTransactions() {

    }

    @Override
    public void configure() throws Exception {

        configureTransactions();

        /* Deposit the resources in a package */
        from("direct:deposit_resources")
                .process(m -> m.getIn()
                        .setHeader(HEADER_LDP_ROOTS,
                                   analyzer.getContainerRoots(m.getIn()
                                           .getBody(File.class))))
                .split(header(HEADER_LDP_ROOTS), (a, b) -> a)
                .to("direct:deposit_tree").end();

        /* TODO: unfinished */

    }

}
