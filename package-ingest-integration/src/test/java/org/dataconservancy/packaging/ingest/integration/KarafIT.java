/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.packaging.ingest.integration;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
public interface KarafIT {

    public static final Logger LOG = LoggerFactory.getLogger(KarafIT.class);

    public final String REPOSITORY_BASEURI = String.format("http://localhost:%s/fcrepo/rest", System.getProperty(
            "apix.dynamic.test.port", "32080"));

    /**
     * Karaf configuration.
     *
     * @return Karaf config.
     */
    @Configuration
    public default Option[] config() {
        final MavenArtifactUrlReference karafUrl = maven().groupId("org.apache.karaf")
                .artifactId("apache-karaf").versionAsInProject().type("zip");

        final MavenUrlReference apixRepo =
                maven().groupId("org.fcrepo.apix")
                        .artifactId("fcrepo-api-x-karaf").versionAsInProject()
                        .classifier("features").type("xml");

        // This dependency is not in any features files, so we have to add it manually.
        final MavenArtifactUrlReference fcrepoClient = maven().groupId("org.fcrepo.client")
                .artifactId("fcrepo-java-client")
                .versionAsInProject();

        final ArrayList<Option> options = new ArrayList<>();

        final Option[] defaultOptions = new Option[] {

            // Fcrepo client is not a dependency of anything else, but tests need it.
            // As this test runs as a maven bundle in Karaf, the test's reactor dependencies are not
            // available a priori.
            mavenBundle(fcrepoClient),

            karafDistributionConfiguration().frameworkUrl(karafUrl)
                    .unpackDirectory(new File("target", "exam")).useDeployFolder(false),

            logLevel(LogLevel.INFO),

            keepRuntimeFolder(),

            features(apixRepo, "fcrepo-api-x"),

            // We need to tell Karaf to set any system properties we need.
            // This code is run prior to executing Karaf, the tests themselves are run in Karaf, in a separate
            // VM.
            editConfigurationFilePut("etc/system.properties", "apix.dynamic.test.port", System.getProperty(
                    "apix.dynamic.test.port")),
            editConfigurationFilePut("etc/system.properties", "fcrepo.dynamic.test.port", System.getProperty(
                    "fcrepo.dynamic.test.port")),
            editConfigurationFilePut("etc/system.properties", "loader.dynamic.test.port", System.getProperty(
                    "loader.dynamic.test.port")),
            editConfigurationFilePut("etc/system.properties", "fcrepo.dynamic.jms.port", System.getProperty(
                    "fcrepo.dynamic.jms.port")),
            editConfigurationFilePut("/etc/system.properties", "fcrepo.cxtPath", System.getProperty(
                    "fcrepo.cxtPath")),
            editConfigurationFilePut("/etc/system.properties", "package.ingest.jar", System.getProperty(
                    "package.ingest.jar")),

            deployFile("org.fcrepo.apix.jena.cfg"),
            deployFile("org.ops4j.pax.logging.cfg"),
            deployFile("org.fcrepo.apix.registry.http.cfg"),
            deployFile("org.fcrepo.apix.routing.cfg"),
            deployFile("org.fcrepo.apix.loader.cfg"),
            deployFile("org.fcrepo.camel.service.activemq.cfg"),

        };

        options.addAll(Arrays.asList(defaultOptions));
        options.addAll(additionalKarafConfig());

        return options.toArray(defaultOptions);

    }

    /**
     * Deploy a configuration with the given classpath.
     *
     * @param path the path in the classpath.
     * @return
     */
    public default Option deployFile(String path) {
        try {

            final File configFile = Files.createTempFile("karaf", "cfg").toFile();
            configFile.deleteOnExit();

            try (OutputStream out = new FileOutputStream(configFile);
                    InputStream in = this.getClass().getResourceAsStream("/cfg/" + path)) {
                IOUtils.copy(in, out);
            }

            return replaceConfigurationFile("etc/" + path, configFile);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Attempt a task a given number of times.
     *
     * @param times number of times
     * @param it the task
     * @return the result.
     */
    public static <T> T attempt(final int times, final Callable<T> it) {

        Throwable caught = null;

        for (int tries = 0; tries < times; tries++) {
            try {
                return it.call();
            } catch (final Throwable e) {
                caught = e;
                try {
                    Thread.sleep(1000);
                    System.out.println(".");
                } catch (final InterruptedException i) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        throw new RuntimeException("Failed executing task", caught);
    }

    /**
     * Use this to add additional karaf config options, or return an empty array for none
     *
     * @return list of Karaf options.
     */
    public default List<Option> additionalKarafConfig() {
        return new ArrayList<>();
    }
}
