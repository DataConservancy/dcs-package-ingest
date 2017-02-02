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

package org.dataconservancy.packaging.ingest.integration;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.replaceConfigurationFile;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.ConfigurationManager;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;

/**
 * Implement this in a test class and run with Pax Exam. It will automatically set up a Karaf container.
 * <p>
 * This class contains the boilerplate to set up Karaf ITs.
 * </p>
 */
public interface KarafIT {

    @Configuration
    public default Option[] config() {
        final MavenArtifactUrlReference karafUrl = maven().groupId("org.apache.karaf")
                .artifactId("apache-karaf").version(karafVersion()).type("zip");

        final MavenUrlReference pkgKaraf =
                maven().groupId("org.dataconservancy.packaging")
                        .artifactId("package-ingest-karaf").versionAsInProject()
                        .classifier("features").type("xml");

        final ArrayList<Option> options = new ArrayList<>();

        final Option[] defaultOptions = new Option[] {
            karafDistributionConfiguration().frameworkUrl(karafUrl)
                    .unpackDirectory(new File("target", "exam"))
                    .useDeployFolder(false),
            // configureConsole().ignoreLocalConsole(),
            logLevel(LogLevel.WARN),

            features(pkgKaraf, "package-ingest-karaf") };

        options.addAll(Arrays.asList(defaultOptions));
        options.addAll(additionalKarafConfig());

        return options.toArray(defaultOptions);

    }

    /*
     * Use this to add additional karaf config options, or return an empty array for none
     */
    public List<Option> additionalKarafConfig();

    public static String karafVersion() {
        final ConfigurationManager cm = new ConfigurationManager();
        final String karafVersion = cm.getProperty("pax.exam.karaf.version", "4.0.8");
        return karafVersion;
    }

    public static Option configFile(Class<?> impl, String... modifier) {
        try {
            final String name = impl.getName() + (modifier.length > 0 ? "-" + modifier[0] : "") + ".cfg";
            return replaceConfigurationFile("etc/" + name,
                    Paths.get(KarafIT.class
                            .getResource("/cfg/" + name)
                            .toURI()).toFile());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
