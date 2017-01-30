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

import static org.dataconservancy.packaging.ingest.integration.KarafIT.configFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory;
import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/** Runs the DepositIT tests in Karaf, with OSGi providing the wiring */
@RunWith(PaxExam.class)
public class KarafDepositIT
        extends DepositIT
        implements KarafIT {

    @Inject
    BundleContext cxt;

    @Inject
    ConfigurationAdmin cm;

    @Override
    public List<Option> additionalKarafConfig() {
        return Arrays
                .asList(configFile(PackageFileAnalyzerFactory.class),
                        configFile(PackageFileDepositWorkflow.class, "test"));
    }

    @Before
    public void addNotification() {

        cxt.registerService(NotificationDriver.class,
                new NotificationProbe(),
                null);
    }

    @Override
    protected List<DepositLocation> getDepositLocations() {

        try {
            return cxt.getServiceReferences(DepositWorkflow.class, null)
                    .stream().map(TO_CONFIG).map(Configuration::getProperties)
                    .map(props -> new DepositLocation()
                            .withDepositDir(props.get("package.deposit.dir"))
                            .withFailDir(props.get("package.fail.dir"))
                            .withRepositoryURI(props.get("deposit.location")))
                    .collect(Collectors.toList());

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected String getRepositoryBaseURI() {
        try {
            return (String) cm
                    .getConfiguration((String) cxt
                            .getServiceReference(DepositFactory.class)
                            .getProperty(Constants.SERVICE_PID))
                    .getProperties().get("baseuri");
            // cxt.getServiceReferences(DepositWorkflow.class,
            // "(test.role=root)").iterator().next().getProperty("deposit.location");

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected DepositLocation newDepositLocationFor(String uri) {

        // Dictionary<String, Object> props = new Hashtable<>();
        final Properties props = new Properties();
        props.put("create.directories", "true");
        props.put("deposit.location", uri);
        props.put("package.poll.interval.ms", "1000");

        try {

            final String id = UUID.randomUUID().toString();

            final File depositRoot = new File(
                    (String) cxt
                            .getServiceReferences(DepositWorkflow.class,
                                    "(test.role=root)")
                            .iterator().next()
                            .getProperty("package.deposit.dir"),
                    id);

            props.put("package.deposit.dir",
                    new File(depositRoot, "deposit").toString());
            props.put("package.fail.dir",
                    new File(depositRoot, "fail").toString());

            /* Put a configuration file in /etc to create new workflow */
            final File installFile = new File(
                    new File(URI.create((String) cxt
                            .getServiceReferences(DepositWorkflow.class,
                                    "(felix.fileinstall.filename=*)")
                            .iterator().next()
                            .getProperty("felix.fileinstall.filename")))
                                    .getParentFile(),
                    "org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow-" + id + ".cfg");

            props.store(new FileOutputStream(installFile), "");

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        return new DepositLocation().withRepositoryURI(uri)
                .withDepositDir(props.get("package.deposit.dir"))
                .withFailDir(props.get("package.fail.dir"));
    }

    @Override
    protected File getExtractLocation() {

        try {
            return new File((String) cm
                    .getConfiguration((String) cxt
                            .getServiceReference(LdpPackageAnalyzerFactory.class)
                            .getProperty(Constants.SERVICE_PID))
                    .getProperties().get("package.extract.dir"))
                            .getAbsoluteFile();
        } catch (final Exception e) {
            throw new RuntimeException();
        }

    }

    @Override
    protected List<String> listResources(String path, FilenameFilter filter) {
        final List<String> names = new ArrayList<>();

        final Enumeration<URL> entries = cxt.getBundle()
                .findEntries(new File(path).getParent(), null, false);
        while (entries.hasMoreElements()) {
            final URL url = entries.nextElement();

            final String name = new File(url.getPath()).getName();

            if (filter.accept(new File(path), name)) {
                names.add(name);
            }
        }

        return names;

    }

    Function<ServiceReference<DepositWorkflow>, Configuration> TO_CONFIG =
            (sr -> {
                try {
                    return cm.getConfiguration((String) sr
                            .getProperty(Constants.SERVICE_PID));
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });

}
