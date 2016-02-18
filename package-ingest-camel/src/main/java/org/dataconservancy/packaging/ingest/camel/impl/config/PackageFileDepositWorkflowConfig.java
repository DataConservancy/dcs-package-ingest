
package org.dataconservancy.packaging.ingest.camel.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow", description = "Package file deposit workflow.  Monitors a ilesystem location for package files, deposits them to a single location.")
public @interface PackageFileDepositWorkflowConfig {

    @AttributeDefinition(description = "Attempt to create directories if not present")
    boolean create_directories() default true;

    @AttributeDefinition(description = "Filesystem path to a directory that will be monitored for package files")
    String package_deposit_dir();

    @AttributeDefinition(description = "Deposit files to this URI")
    String deposit_location() default "http://localhost:8080/fcrepo/rest";

    @AttributeDefinition(description = "Filesystem path to a directory where failed package files will be placed")
    String package_fail_dir();

    @AttributeDefinition(description = "Files that haven't changed size in this interval of time are considered 'complete' and will be processed")
    int package_poll_interval_ms() default 30000;

}