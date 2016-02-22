
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;
import java.io.FileOutputStream;

import java.net.URI;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.runner.RunWith;

import org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver;
import org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.dataconservancy.packaging.ingest.integration.KarafIT.configFile;

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
                .asList(configFile(FedoraDepositDriver.class),
                        configFile(PackageFileAnalyzerFactory.class),
                        configFile(PackageFileDepositWorkflow.class, "test"));
    }

    @Before
    public void addNotification() {

        cxt.registerService(NotificationDriver.class,
                            new NotificationProbe(),
                            null);
    }

    protected List<DepositLocation> getDepositLocations() {

        try {
            return cxt.getServiceReferences(DepositWorkflow.class, null)
                    .stream().map(TO_CONFIG).map(Configuration::getProperties)
                    .map(props -> new DepositLocation()
                            .withDepositDir(props.get("package.deposit.dir"))
                            .withFailDir(props.get("package.fail.dir"))
                            .withRepositoryURI(props.get("deposit.location")))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    protected String getRepositoryBaseURI() {
        try {
            return (String) cm
                    .getConfiguration((String) cxt
                            .getServiceReference(DepositDriver.class)
                            .getProperty(Constants.SERVICE_PID))
                    .getProperties().get("fedora.baseuri");
            //cxt.getServiceReferences(DepositWorkflow.class, "(test.role=root)").iterator().next().getProperty("deposit.location");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected DepositLocation newDepositLocationFor(String uri) {

        //Dictionary<String, Object> props = new Hashtable<>();
        Properties props = new Properties();
        props.put("create.directories", "true");
        props.put("deposit.location", uri);
        props.put("package.poll.interval.ms", "1000");

        try {

            String id = UUID.randomUUID().toString();

            File depositDir = new File(
                                       (String) cxt
                                               .getServiceReferences(DepositWorkflow.class,
                                                                     "(test.role=root)")
                                               .iterator().next()
                                               .getProperty("package.deposit.dir"),
                                       id);

            props.put("package.deposit.dir", depositDir.toString());
            props.put("package.fail.dir",
                      new File(depositDir, "fail").toString());

            /* Put a configuration file in /etc to create new workflow */
            File installFile = new File(
                                        new File(URI.create((String) cxt
                                                .getServiceReferences(DepositWorkflow.class,
                                                                      "(felix.fileinstall.filename=*)")
                                                .iterator().next()
                                                .getProperty("felix.fileinstall.filename")))
                                                        .getParentFile(),
                                        "org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow-"
                                                + id + ".cfg");

            props.store(new FileOutputStream(installFile), "");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new DepositLocation().withRepositoryURI(uri)
                .withDepositDir(props.get("package.deposit.dir"))
                .withFailDir(props.get("package.fail.dir"));
    }

    protected File getExtractLocation() {

        try {
            return new File((String) cm
                    .getConfiguration((String) cxt
                            .getServiceReference(LdpPackageAnalyzerFactory.class)
                            .getProperty(Constants.SERVICE_PID))
                    .getProperties().get("package.extract.dir"));
        } catch (Exception e) {
            throw new RuntimeException();
        }

    }

    Function<ServiceReference<DepositWorkflow>, Configuration> TO_CONFIG =
            (sr -> {
                try {
                    return cm.getConfiguration((String) sr
                            .getProperty(Constants.SERVICE_PID));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

}
