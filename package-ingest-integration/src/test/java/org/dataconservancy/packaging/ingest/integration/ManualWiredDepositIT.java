/*
 * Copyright 2016 Johns Hopkins University
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.ingest.integration;

import java.io.File;
import java.io.FilenameFilter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import java.nio.file.Paths;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;

import org.junit.After;
import org.junit.Before;

import org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory;
import org.dataconservancy.packaging.impl.PackageFileAnalyzerFactoryConfig;
import org.dataconservancy.packaging.impl.PackageFileProvenanceGenerator;
import org.dataconservancy.packaging.ingest.camel.ContextFactory;
import org.dataconservancy.packaging.ingest.camel.impl.CamelDepositManager;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver;
import org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.impl.config.EmailNotificationsConfig;
import org.dataconservancy.packaging.ingest.camel.impl.config.FedoraConfig;
import org.dataconservancy.packaging.ingest.camel.impl.config.PackageFileDepositWorkflowConfig;

/** Runs the DepositIT locally, with manual wiring */
public class ManualWiredDepositIT
        extends DepositIT {

    CamelDepositManager mgr;

    static String PACKAGE_DEPOSIT_DIR = System
            .getProperty("package.deposit.dir",
                         new File("target/package/deposit").getAbsolutePath());

    static String PACKAGE_FAIL_DIR = System
            .getProperty("package.fail.dir",
                         new File("target/package/fail").getAbsolutePath());

    static String PACKAGE_EXTRACT_DIR = System
            .getProperty("package.extract.dir",
                         new File("target/package/extract").getAbsolutePath());

    static String FEDORA_BASEURI = System
            .getProperty("fedora.baseuri", "http://localhost:8080/fcrepo/rest");

    @Before
    public void wire() {

        FedoraConfig fedoraConfig = new FedoraConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return FedoraConfig.class;
            }

            @Override
            public String fedora_baseuri() {
                return FEDORA_BASEURI;
            }
        };

        EmailNotificationsConfig emailConfig = new EmailNotificationsConfig() {

            @Override
            public String mail_smtpHost() {
                return "localhost";
            }

            @Override
            public String mail_smtpUser() {
                return "fooSmtpUser";
            }

            @Override
            public String mail_smtpPass() {
                return "barSmtpPass";
            }

            @Override
            public String mail_to() {
                return "user@remoteHost";
            }

            @Override
            public String mail_smtpPort() {
                return EmailNotificationsConfig.DEFAULT_SMTP_PORT;
            }

            @Override
            public String mail_from() {
                return EmailNotificationsConfig.DEFAULT_SENDER;
            }

            @Override
            public String mail_template() {
                return EmailNotificationsConfig.DEFAULT_SUCCESS_TEMPLATE;
            }

            @Override
            public String mail_subjectSuccess() {
                return EmailNotificationsConfig.DEFAULT_SUCCESS_NOTIFICATION_SUBJECT;
            }

            @Override
            public String mail_subjectFailure() {
                return EmailNotificationsConfig.DEFAULT_FAILURE_NOTIFICATION_SUBJECT;
            }

            @Override
            public String mail_debug() {
                return EmailNotificationsConfig.DEFAULT_DEBUG;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return EmailNotificationsConfig.class;
            }
        };

        PackageFileAnalyzerFactoryConfig analyzerConfig =
                new PackageFileAnalyzerFactoryConfig() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return PackageFileAnalyzerFactoryConfig.class;
                    }

                    @Override
                    public String package_extract_dir() {
                        return PACKAGE_EXTRACT_DIR;
                    }
                };

        PackageFileDepositWorkflowConfig workflowConfig =
                new PackageFileDepositWorkflowConfig() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return PackageFileDepositWorkflowConfig.class;
                    }

                    @Override
                    public int package_poll_interval_ms() {
                        return 1000;
                    }

                    @Override
                    public String package_fail_dir() {
                        return PACKAGE_FAIL_DIR;
                    }

                    @Override
                    public String package_deposit_dir() {
                        return PACKAGE_DEPOSIT_DIR;
                    }

                    @Override
                    public String deposit_location() {
                        return FEDORA_BASEURI;
                    }

                    @Override
                    public int package_read_lock_timeout_ms() {
                        return 1000 * 3;
                    }

                    @Override
                    public boolean create_directories() {
                        return true;
                    }
                };

        FedoraDepositDriver driver = new FedoraDepositDriver();
        driver.init(fedoraConfig);

        PackageFileAnalyzerFactory analyzerFactory =
                new PackageFileAnalyzerFactory();
        analyzerFactory.init(analyzerConfig);

        driver.setPackageAnalyzerFactory(analyzerFactory);
        driver.setPackageProvenanceGenerator(new PackageFileProvenanceGenerator());

        PackageFileDepositWorkflow rootDeposit =
                new PackageFileDepositWorkflow();

        rootDeposit.init(workflowConfig);

        mgr = new CamelDepositManager();
        mgr.setContextFactory(new ContextFactory() {

            @Override
            public CamelContext newContext(String id, Registry registry) {
                DefaultCamelContext cxt = new DefaultCamelContext(registry);
                cxt.getShutdownStrategy().setTimeout(1);
                cxt.setUseMDCLogging(true);
                cxt.setUseBreadcrumb(true);
                return cxt;
            }
        });
        mgr.setDepositDriver(driver, asMap(fedoraConfig));
        mgr.setNotificationDriver(new NotificationProbe(), asMap(emailConfig));
        mgr.addDepositWorkflow(rootDeposit, asMap(workflowConfig));

        mgr.init();
    }

    @After
    public void stop() {
        mgr.shutDown();
    }

    @Override
    protected List<DepositLocation> getDepositLocations() {
        return Arrays.asList(new DepositLocation()
                .withDepositDir(PACKAGE_DEPOSIT_DIR)
                .withFailDir(PACKAGE_FAIL_DIR)
                .withRepositoryURI(FEDORA_BASEURI));
    }

    @Override
    protected File getExtractLocation() {
        return new File(PACKAGE_EXTRACT_DIR).getAbsoluteFile();
    }

    protected List<String> listResources(String path, FilenameFilter filter) {
        try {
            return Arrays.asList(Paths
                    .get(this.getClass()
                            .getResource("/problem-packages/README.txt")
                            .toURI())
                    .toFile().getParentFile()
                    .list((dir, name) -> !name.endsWith(".txt")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> asMap(Annotation config) {
        Map<String, Object> props = new HashMap<>();
        for (Method m : config.annotationType().getMethods()) {
            try {
                if (m.getParameterCount() == 0)
                    props.put(m.getName().replace('_', '.'), m.invoke(config));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return props;
    }

    @Override
    protected String getRepositoryBaseURI() {
        return FEDORA_BASEURI;
    }

    @Override
    protected DepositLocation newDepositLocationFor(String uri) {
        File depositDir = new File("target/package/deposit",
                                   UUID.randomUUID().toString());
        DepositLocation location = new DepositLocation().withRepositoryURI(uri)
                .withDepositDir(depositDir.toString())
                .withFailDir(new File(depositDir, "fail").toString());

        PackageFileDepositWorkflowConfig workflowConfig =
                new PackageFileDepositWorkflowConfig() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return PackageFileDepositWorkflowConfig.class;
                    }

                    @Override
                    public int package_poll_interval_ms() {
                        return 1000;
                    }

                    @Override
                    public String package_fail_dir() {
                        return location.failDir.toString();
                    }

                    @Override
                    public String package_deposit_dir() {
                        return location.depositDir.toString();
                    }

                    @Override
                    public String deposit_location() {
                        return location.repositoryURI;
                    }

                    @Override
                    public int package_read_lock_timeout_ms() {
                        return 1000 * 3;
                    }

                    @Override
                    public boolean create_directories() {
                        return true;
                    }
                };

        PackageFileDepositWorkflow wf = new PackageFileDepositWorkflow();
        wf.init(workflowConfig);

        mgr.shutDown();
        mgr.addDepositWorkflow(wf, asMap(workflowConfig));
        mgr.init();

        return location;
    }

}
