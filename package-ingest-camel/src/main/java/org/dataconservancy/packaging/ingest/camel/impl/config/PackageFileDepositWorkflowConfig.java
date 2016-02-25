
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
package org.dataconservancy.packaging.ingest.camel.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow", description = "Package file deposit workflow.  Monitors a ilesystem location for package files, deposits them to a single location.")
public @interface PackageFileDepositWorkflowConfig {

    static final int DEFAULT_POLL_INTERVAL_MS = 30000;

    @AttributeDefinition(description = "Attempt to create directories if not present")
    boolean create_directories() default true;

    @AttributeDefinition(description = "Filesystem path to a directory that will be monitored for package files")
    String package_deposit_dir();

    @AttributeDefinition(description = "Deposit files to this URI")
    String deposit_location() default "http://localhost:8080/fcrepo/rest";

    @AttributeDefinition(description = "Filesystem path to a directory where failed package files will be placed")
    String package_fail_dir();

    @AttributeDefinition(description = "Files that haven't changed size in this interval of time are considered 'complete' and will be processed")
    int package_poll_interval_ms() default DEFAULT_POLL_INTERVAL_MS;

    @AttributeDefinition(description = "Amount of time to wait before giving up on acquiring an exclusive lock on the package file.  Must be greater than the package poll interval.")
    int package_read_log_timeout_ms() default (DEFAULT_POLL_INTERVAL_MS * 3);

}