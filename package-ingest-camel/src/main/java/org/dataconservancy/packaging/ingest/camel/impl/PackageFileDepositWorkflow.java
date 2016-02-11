
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;
import java.io.FileInputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

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
        from(fileSourceURI).id("deposit-poll-file").to("direct:deposit");

        /* Main deposit workflow */
        from("direct:deposit").id("deposit-workflow")
                .setHeader(Exchange.HTTP_URI,
                           constant(config.deposit_location()))

                .doTry().to(ROUTE_TRANSACTION_BEGIN)
                .process(e -> System.out.println("Transaction has begun"))
                /* .to(ROUTE_DEPOSIT_PROVENANCE) */
                .to(ROUTE_DEPOSIT_RESOURCES)
                .process(e -> System.out
                        .println("Resources have been deposited"))
                .to(ROUTE_TRANSACTION_COMMIT)
                .process(e -> System.out
                        .println("Resources have been committed"))
                .doCatch(Exception.class)
                .process(e -> System.out.println("Exception caught!"))
                .to("direct:fail_copy_package")
                .process(e -> System.out.println("Wrote fail file")).doTry()
                .process(e -> System.out.println("Rolling back..."))
                .to(ROUTE_TRANSACTION_ROLLBACK).doCatch(Exception.class)
                .process(e -> {
                    System.err.println("Error during rollback!");

                    e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                            .printStackTrace(System.err);
                }).end()
                .process(e -> System.out.println("Going to notification fail"))
                .to(ROUTE_NOTIFICATION_FAIL).end().stop().end()
                .process(e -> System.out.println("Notifying of success"))
                .doTry()
                .enrich("direct:_canonicalize_resources", (orig, updated) -> {
                    orig.getIn()
                            .setHeader(HEADER_RESOURCE_LOCATIONS,
                                       updated.getIn()
                                               .getHeader(HEADER_RESOURCE_LOCATIONS));
                    return orig;
                }).to(ROUTE_NOTIFICATION_SUCCESS).doCatch(Exception.class)
                .process(e -> {
                    System.err.println("Error during success notify!");

                    e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                            .printStackTrace(System.err);
                }).end();

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
                .doCatch(Exception.class)
                .process(e -> e
                        .getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                        .printStackTrace(System.err))
                .throwException(new RuntimeException("Failed to copy fail file!"))
                .end();
    }

}
