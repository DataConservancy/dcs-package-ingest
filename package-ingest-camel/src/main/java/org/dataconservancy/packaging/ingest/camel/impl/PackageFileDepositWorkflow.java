
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_FAIL;
import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_SUCCESS;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_PROVENANCE;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_DEPOSIT_RESOURCES;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_BEGIN;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_COMMIT;
import static org.dataconservancy.packaging.ingest.camel.DepositDriver.ROUTE_TRANSACTION_ROLLBACK;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow", description = "Package file deposit workflow.  Monitors a ilesystem location for package files, deposits them to a single location.")
@interface PackageFileDepositWorkflowConfig {

    @AttributeDefinition(description = "Attempt to create directories if not present")
    boolean create_directories() default true;

    @AttributeDefinition(description = "Filesystem path to a directory that will be monitored for package files")
    String package_deposit_dir();

    @AttributeDefinition(description = "Deposit files to this URI")
    String deposit_location() default "http://localhost:8080/fedora/rest";

    @AttributeDefinition(description = "Filesystem path to a directory where failed package files will be placed")
    String package_fail_dir();

    @AttributeDefinition(description = "Files that haven't changed size in this interval of time are considered 'complete' and will be processed")
    int package_poll_interval_ms() default 30000;

}

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

    @Activate
    private void init(PackageFileDepositWorkflowConfig config) {
        this.config = config;

        if (config.create_directories()) {
            new File(config.package_deposit_dir()).mkdirs();
            new File(config.package_fail_dir()).mkdirs();
        }
    }

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
        from("direct:deposit").id("deposit-workflow") /* */
                .doTry().to(ROUTE_TRANSACTION_BEGIN)
                .to(ROUTE_DEPOSIT_PROVENANCE).to(ROUTE_DEPOSIT_RESOURCES)
                .to(ROUTE_TRANSACTION_COMMIT).to(ROUTE_NOTIFICATION_SUCCESS)
                .doCatch(Exception.class).to("direct:fail_copy_package")
                .to(ROUTE_TRANSACTION_ROLLBACK).to(ROUTE_NOTIFICATION_FAIL)
                .end();

        /* Copy package to failure directory */
        from("direct:fail_copy_package").id("deposit-fail")
                .to(String.format(
                                  "file:%s?autoCreate=true&keepLastModified=true",
                                  config.package_fail_dir()));
    }
}
