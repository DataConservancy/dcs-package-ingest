
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;
import java.io.FileInputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpHeaders;

import org.dataconservancy.packaging.ingest.LdpResource;
import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.impl.config.PackageFileDepositWorkflowConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_FAIL;
import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_SUCCESS;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_PROVENANCE;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_CANONICALIZE;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_RESOURCES;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_BEGIN;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_COMMIT;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_ROLLBACK;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_LDP_RESOURCES;
import static org.dataconservancy.packaging.ingest.camel.impl.LdpDepositDriver.HEADER_URI_MAP;

import static org.dataconservancy.packaging.ingest.camel.Helpers.expression;
import static org.dataconservancy.packaging.ingest.camel.Helpers.headerString;

/**
 * The main package deposit workflow.
 * 
 * @author apb@jhu.edu
 */
@Component(service = DepositWorkflow.class, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
@Designate(ocd = PackageFileDepositWorkflowConfig.class, factory = true)
public class PackageFileDepositWorkflow
        extends RouteBuilder
        implements DepositWorkflow {

    private PackageFileDepositWorkflowConfig config;

    /**
     * Header that contains the URI to the originally deposited package
     */
    public static final String PACKAGE_SOURCE_URI = "package.source.uri";

    /**
     * Header that contains the URI(s) to the successfully deposited resources
     */
    public static final String HEADER_RESOURCE_LOCATIONS = "deposit.locations";

    @Activate
    public void init(PackageFileDepositWorkflowConfig config) {
        this.config = config;

        if (config.create_directories()) {
            new File(config.package_deposit_dir()).mkdirs();
            new File(config.package_fail_dir()).mkdirs();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure() throws Exception {

        /* Construct a camel endpoint URI for polling a specific file */
        String fileSourceURI =
                String.format("file:%s?delete=true&readLock=changed&readLockCheckInterval=%d&readLockTimeout=600000",
                              config.package_deposit_dir(),
                              config.package_poll_interval_ms());

        /* Poll the file */
        from(fileSourceURI).id("deposit-poll-file")
                .setHeader(PACKAGE_SOURCE_URI, header(Exchange.FILE_PATH))
                .to("direct:deposit");

        /* Main deposit workflow */
        from("direct:deposit").id("deposit-workflow")
                .setHeader(Exchange.HTTP_URI,
                           constant(config.deposit_location()))
                .doTry().to(ROUTE_TRANSACTION_BEGIN).to(ROUTE_DEPOSIT_RESOURCES)
                .enrich("direct:_deposit_provenance", (orig, prov) -> {
                    orig.getIn()
                            .setHeader(HEADER_PROVENANCE_LOCATION,
                                       headerString(prov,
                                                    HEADER_PROVENANCE_LOCATION));
                    return orig;
                }).to(ROUTE_TRANSACTION_COMMIT).doCatch(Exception.class)
                .to("direct:fail_copy_package").doTry()
                .to(ROUTE_TRANSACTION_ROLLBACK).doCatch(Exception.class).end()
                .to(ROUTE_NOTIFICATION_FAIL).end().stop().end().doTry()
                .enrich("direct:_canonicalize_resources", (orig, updated) -> {
                    orig.getIn()
                            .setHeader(/* List<String> */HEADER_RESOURCE_LOCATIONS,
                                       updated.getIn()
                                               .getHeader(HEADER_RESOURCE_LOCATIONS));
                    return orig;
                }).to(ROUTE_NOTIFICATION_SUCCESS).doCatch(Exception.class)
                .end();

        from("direct:_deposit_provenance").to(ROUTE_DEPOSIT_PROVENANCE)
                .setHeader(Exchange.HTTP_URI, header(HttpHeaders.LOCATION))
                .to(ROUTE_TRANSACTION_CANONICALIZE)
                .setHeader(HEADER_PROVENANCE_LOCATION,
                           header(Exchange.HTTP_URI));

        /* Canonicalize */
        from("direct:_canonicalize_resources")
                .setHeader(HEADER_RESOURCE_LOCATIONS,
                           constant(new ArrayList<>()))
                .split(header(HEADER_LDP_RESOURCES), (orig, updated) -> {
                    if (orig == null) orig = updated;
                    orig.getIn()
                            .getHeader(HEADER_RESOURCE_LOCATIONS,
                                       Collection.class)
                            .add(headerString(updated, Exchange.HTTP_URI));
                    return orig;
                })
                .setHeader(Exchange.HTTP_URI,
                           expression(e -> e.getIn()
                                   .getHeader(HEADER_URI_MAP, Map.class)
                                   .get(e.getIn().getBody(LdpResource.class)
                                           .getURI().toString())))
                .to(ROUTE_TRANSACTION_CANONICALIZE).end();

        /* Copy package to failure directory */
        from("direct:fail_copy_package").id("deposit-fail").doTry()
                .process(e -> e.getIn()
                        .setBody(new FileInputStream(e.getIn()
                                .getHeader(Exchange.FILE_PATH, String.class))))
                .to(String.format(
                                  "file:%s?autoCreate=true&keepLastModified=true",
                                  config.package_fail_dir()))
                .doCatch(Exception.class).process(e -> {
                    throw new RuntimeException("Could not copy failure file!",
                                               e.getProperty(Exchange.EXCEPTION_CAUGHT,
                                                             Exception.class));
                }).end();
    }

}
