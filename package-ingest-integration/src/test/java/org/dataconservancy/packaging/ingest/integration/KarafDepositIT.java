
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
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
import static org.junit.Assert.assertNotNull;

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
    
    @Test
    public void cmTest() {

        assertNotNull(cxt);
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
