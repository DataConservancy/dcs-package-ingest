
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;

import java.nio.file.Paths;

import java.util.Collection;

import javax.inject.Inject;

import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.dataconservancy.packaging.impl.PackageFileAnalyzer;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.dataconservancy.packaging.ingest.camel.DepositManager;
import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver;
import org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

@RunWith(PaxExam.class)
public class KarafIT
        extends CamelTestSupport {

    @Inject
    public BundleContext cxt;

    /* Will fail if any are unsatisfied */
    @Inject
    public DepositManager depositManager;

    @Inject
    public DepositWorkflow depositWorkflow;

    @Inject
    public NotificationDriver notificationDriver;

    @Inject
    public DepositDriver driver;

    @Inject
    public LdpPackageAnalyzer<File> analyzer;

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven().groupId("org.apache.karaf")
                .artifactId("apache-karaf").version(karafVersion()).type("zip");

        MavenUrlReference pkgKaraf =
                maven().groupId("org.dataconservancy.packaging")
                        .artifactId("package-ingest-karaf").versionAsInProject()
                        .classifier("features").type("xml");

        MavenUrlReference camelRepo = maven().groupId("org.apache.camel.karaf")
                .artifactId("apache-camel").type("xml").classifier("features")
                .versionAsInProject();

        return new Option[] {
                karafDistributionConfiguration().frameworkUrl(karafUrl)
                        .unpackDirectory(new File("target", "exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(), configureConsole().ignoreLocalConsole(),

                configFile(EmailNotifications.class),
                configFile(FedoraDepositDriver.class),
                configFile(PackageFileAnalyzer.class),
                configFile(PackageFileDepositWorkflow.class, "test"),
                configFile(PackageFileDepositWorkflow.class, "test2"),

                features(pkgKaraf, "package-ingest-karaf"),
                features(camelRepo, "camel-test")};
    }

    public static String karafVersion() {
        ConfigurationManager cm = new ConfigurationManager();
        String karafVersion = cm.getProperty("pax.exam.karaf.version", "4.0.4");
        return karafVersion;
    }

    private Option configFile(Class<?> impl, String... modifier) {
        try {
            String name = impl.getName()
                    + (modifier.length > 0 ? "-" + modifier[0] : "") + ".cfg";
            return replaceConfigurationFile("etc/" + name,
                                            Paths.get(getClass()
                                                    .getResource("/cfg/" + name)
                                                    .toURI()).toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void serviceInitializationTest() throws Exception {
        /*
         * If everything is good, *two* instances of deposit workflows will be
         * created. This replicates two separate workflow configs for monitoring
         * different directories to deposit in different collections.
         */
        Collection<ServiceReference<DepositWorkflow>> refs =
                cxt.getServiceReferences(DepositWorkflow.class, null);
        assertEquals(2, refs.size());
    }

}
