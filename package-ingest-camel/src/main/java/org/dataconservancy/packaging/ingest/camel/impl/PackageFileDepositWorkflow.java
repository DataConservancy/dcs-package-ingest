
package org.dataconservancy.packaging.ingest.camel.impl;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_FAIL;
import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_SUCCESS;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_PROVENANCE;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_RESOURCES;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_REMAP_RELATIONSHIPS;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_BEGIN;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_COMMIT;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_ROLLBACK;

/**
 * The main package deposit workflow.
 * 
 * @author apb@jhu.edu
 */
@Component(service = DepositWorkflow.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class PackageFileDepositWorkflow
        extends RouteBuilder {

    private Map<String, String> config;

    public static final String PROP_PACKAGE_DEPOSIT_DIR = "package.deposit.dir";

    public static final String PROP_PACKAGE_FAIL_DIR = "package.fail.dir";

    public static final String PROP_PACKAGE_POLL_INTERVAL_MS =
            "package.poll.interval.ms";

    private static final String DEFAULT_PACKAGE_POLL_INTERVAL_MS = "30000";

    @Activate
    private void init(Map<String, String> config) {
        this.config = config;
    }

    @Override
    public void configure() throws Exception {

        /* Construct a camel endpoint URI for polling a specific file */
        String fileSourceURI =
                String.format("file:%s?delete=true&readLock=changed&readLockCheckInterval=%d",
                              config.get(PROP_PACKAGE_DEPOSIT_DIR),
                              Integer.valueOf(config
                                      .getOrDefault(PROP_PACKAGE_POLL_INTERVAL_MS,
                                                    DEFAULT_PACKAGE_POLL_INTERVAL_MS)));

        /* Poll the file */
        from(fileSourceURI).id("poll_file").to("direct:deposit");

        /* Main deposit workflow */
        from("direct:deposit") /* */
        .doTry().to(ROUTE_TRANSACTION_BEGIN).to(ROUTE_DEPOSIT_PROVENANCE)
                .to(ROUTE_DEPOSIT_RESOURCES)
                .to(ROUTE_DEPOSIT_REMAP_RELATIONSHIPS)
                .to(ROUTE_TRANSACTION_COMMIT).to(ROUTE_NOTIFICATION_SUCCESS)
                .doCatch(Exception.class).to("direct:fail_copy_package")
                .to(ROUTE_TRANSACTION_ROLLBACK).to(ROUTE_NOTIFICATION_FAIL)
                .end();

        /* Copy package to failure directory */
        from("direct:fail_copy_package")
                .to(String.format("file:%s?autoCreate=true&keepLastModified=true",
                                  config.get(PROP_PACKAGE_FAIL_DIR)));
    }
}
