
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;

import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dataconservancy.packaging.ingest.osgi.impl.OsgiContextFactory;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

/**
 * Implement this in a test class and run with Pax Exam. It will automatically
 * set up a Karaf container.
 * <p>
 * This class contains the boilerplate to set up Karaf ITs.
 * </p>
 */
public interface KarafIT {

    @Configuration
    public default Option[] config() {
        MavenArtifactUrlReference karafUrl = maven().groupId("org.apache.karaf")
                .artifactId("apache-karaf").version(karafVersion()).type("zip");

        MavenUrlReference pkgKaraf =
                maven().groupId("org.dataconservancy.packaging")
                        .artifactId("package-ingest-karaf").versionAsInProject()
                        .classifier("features").type("xml");

        MavenUrlReference camelRepo = maven().groupId("org.apache.camel.karaf")
                .artifactId("apache-camel").type("xml").classifier("features")
                .versionAsInProject();

        ArrayList<Option> options = new ArrayList<>();

        Option[] defaultOptions = new Option[] {
                karafDistributionConfiguration().frameworkUrl(karafUrl)
                        .unpackDirectory(new File("target", "exam"))
                        .useDeployFolder(false),
                configureConsole().ignoreLocalConsole(),
                logLevel(LogLevel.WARN),

                configFile(OsgiContextFactory.class),

                features(pkgKaraf, "package-ingest-karaf"),
                features(camelRepo, "camel-test")};

        options.addAll(Arrays.asList(defaultOptions));
        options.addAll(additionalKarafConfig());

        return options.toArray(defaultOptions);

    }

    /*
     * Use this to add additional karaf config options, or return an empty array
     * for none
     */
    public List<Option> additionalKarafConfig();

    public static String karafVersion() {
        ConfigurationManager cm = new ConfigurationManager();
        String karafVersion = cm.getProperty("pax.exam.karaf.version", "4.0.4");
        return karafVersion;
    }

    public static Option configFile(Class<?> impl, String... modifier) {
        try {
            String name = impl.getName()
                    + (modifier.length > 0 ? "-" + modifier[0] : "") + ".cfg";
            return replaceConfigurationFile("etc/" + name,
                                            Paths.get(KarafIT.class
                                                    .getResource("/cfg/" + name)
                                                    .toURI()).toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
