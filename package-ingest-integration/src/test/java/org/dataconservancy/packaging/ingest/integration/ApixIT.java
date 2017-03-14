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

import static org.dataconservancy.packaging.ingest.integration.KarafIT.attempt;
import static org.dataconservancy.packaging.test.JarRunner.jar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;

import org.dataconservancy.packaging.ingest.EventType;
import org.dataconservancy.packaging.ingest.http.EventSource;
import org.dataconservancy.packaging.test.Resources;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
@RunWith(PaxExam.class)
public class ApixIT implements KarafIT {

    static final String SERVICE = "http://dataconservancy.org/services/packageIngest";

    @Rule
    public TestName name = new TestName();

    static Process ingest;

    URI containerUri;

    URI ingestServiceUri;

    @BeforeClass
    public static void init() throws Exception {
        ingest = jar(new File(System.getProperty("package.ingest.jar")))
                .logOutput(LoggerFactory.getLogger("IngestExtension"))
                .withEnv("REPOSITORY_BASEURI", REPOSITORY_BASEURI)
                .start();
    }

    @Before
    public void setUp() throws Exception {
        assertTrue(ingest.isAlive());
        final FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

        containerUri = attempt(10, () -> {
            try (FcrepoResponse post = client.post(URI.create(REPOSITORY_BASEURI)).slug(name.getMethodName())
                    .perform()) {
                return post.getLocation();
            }
        });

        // TODO: Make discovery easier, maybe jsonld, or a library.
        ingestServiceUri = KarafIT.attempt(60, () -> {
            try (FcrepoResponse head = client.head(containerUri).perform()) {
                try (FcrepoResponse serviceDoc = client.get(head.getLinkHeaders("service").get(0)).accept(
                        "text/turtle").perform()) {
                    final Model model = ModelFactory.createDefaultModel();
                    model.read(serviceDoc.getBody(), serviceDoc.toString(), "TURTLE");

                    final ResultSet result = QueryExecutionFactory.create(QueryFactory.create(
                            "SELECT ?endpoint WHERE {" +
                                    "?instance <http://fedora.info/definitions/v4/service#isServiceInstanceOf> <" +
                                    SERVICE + "> .\n" +
                                    "?instance <http://fedora.info/definitions/v4/service#hasEndpoint> ?endpoint .}"),
                            model)
                            .execSelect();

                    if (result.hasNext()) {
                        return URI.create(result.next().getResource("endpoint").getURI());
                    } else {
                        throw new RuntimeException(String.format(
                                "Did not find package ingest endpoint on <%s> for service <%s>", containerUri,
                                SERVICE));
                    }
                }
            }
        });
    }

    @Test
    public void syncDepositTest() throws Exception {
        final FcrepoClient client = FcrepoClient.client().build();

        final AtomicInteger deposited = new AtomicInteger(0);
        final AtomicInteger success = new AtomicInteger(0);
        final AtomicInteger error = new AtomicInteger(0);

        attempt(10, () -> {
            try (FcrepoResponse response = client.post(ingestServiceUri)
                    .body(Resources.class.getResourceAsStream("/packages/test-package.zip"), "application/zip")
                    .perform()) {

                if (response.getStatusCode() > 299) {
                    LOG.warn("Request to {} failed, {}", ingestServiceUri, response.getStatusCode());
                    response.getHeaders().entrySet().forEach(e -> LOG.debug(e.toString()));
                    LOG.debug("Body: " + IOUtils.toString(response.getBody(), StandardCharsets.UTF_8));
                    throw new FcrepoOperationFailedException(ingestServiceUri, response.getStatusCode(), Integer
                            .toString(response.getStatusCode()));
                }

                EventSource.from(response.getBody())
                        .onEvent((e) -> {
                            switch (EventType.valueOf(e.event().toUpperCase())) {
                            case DEPOSIT:
                                deposited.incrementAndGet();
                                break;
                            case SUCCESS:
                                success.incrementAndGet();
                                break;
                            case ERROR:
                                error.incrementAndGet();
                            default:
                            }

                        }).start();
                return true;
            }
        });

        assertEquals(0, error.get());
        assertEquals(1, success.get());
        assertEquals(21, deposited.get());
    }

    @AfterClass
    public static void stopExtension() {
        ingest.destroy();
    }

    @Override
    public List<Option> additionalKarafConfig() {
        final MavenArtifactUrlReference packageIngestApi = maven().groupId("org.dataconservancy.packaging")
                .artifactId("package-ingest-api")
                .versionAsInProject();
        final MavenArtifactUrlReference packagIngestHttp = maven().groupId("org.dataconservancy.packaging")
                .artifactId("package-ingest-http")
                .versionAsInProject();

        final MavenArtifactUrlReference testResources = maven().groupId("org.dataconservancy.packaging")
                .artifactId("package-ingest-test")
                .versionAsInProject();
        return Arrays.asList(mavenBundle(packageIngestApi), mavenBundle(packagIngestHttp), mavenBundle(
                testResources));
    }
}