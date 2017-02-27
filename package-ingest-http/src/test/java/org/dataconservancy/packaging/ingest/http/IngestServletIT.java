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

package org.dataconservancy.packaging.ingest.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;

import org.dataconservancy.packaging.impl.DcsPackageAnalyzerFactory;
import org.dataconservancy.packaging.impl.deposit.DefaultPackageWalkerFactory;
import org.dataconservancy.packaging.impl.deposit.FedoraDepositFactory;
import org.dataconservancy.packaging.impl.deposit.SingleDepositManager;
import org.dataconservancy.packaging.ingest.EventType;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
public class IngestServletIT {

    static final Logger LOG = LoggerFactory.getLogger(IngestServletIT.class);

    static Server server;

    static String fcrepoPort = System.getProperty("fcrepo.dynamic.test.port", "8080");

    static int ingestPort = Integer.parseInt(System.getProperty("ingest.dynamic.test.port", "8081"));

    static final URI ingestUri = URI.create("http://localhost:" +
            ingestPort + "/ingest");

    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();

    public static void main(final String[] args) throws Exception {
        setUp();
        server.join();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        server = new Server(ingestPort);

        final FedoraDepositFactory fedora = new FedoraDepositFactory();
        fedora.setBaseUri("http://localhost:" + fcrepoPort + "/fcrepo/rest");

        final DcsPackageAnalyzerFactory dcs = new DcsPackageAnalyzerFactory();
        dcs.setExtractDir(tempDir().toString());

        final DefaultPackageWalkerFactory ldpc = new DefaultPackageWalkerFactory();
        ldpc.setAnalyzerFactory(dcs);

        final SingleDepositManager mgr = new SingleDepositManager();
        mgr.setDepositFactory(fedora);
        mgr.setWalkerFactory(ldpc);

        final IngestServlet ingest = new IngestServlet(mgr);

        final ServletContextHandler servletContext = new ServletContextHandler();
        servletContext.setContextPath("/");
        servletContext.setBaseResource(Resource.newClassPathResource("/index.html"));

        final ServletHolder ingestServlet = new ServletHolder(ingest);
        ingestServlet.setAsyncSupported(true);

        servletContext.addServlet(new ServletHolder(ingest), "/ingest");

        servletContext.addServlet(DefaultServlet.class, "/");

        server.setHandler(servletContext);
        server.start();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stop();
        System.out.println("Removing temp folder " + testFolder.getRoot().getAbsolutePath());
        testFolder.delete();
    }

    @Test
    public void syncDepositTest() throws Exception {
        final FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

        final AtomicInteger deposited = new AtomicInteger(0);
        final AtomicInteger success = new AtomicInteger(0);
        final AtomicInteger error = new AtomicInteger(0);

        try (FcrepoResponse response = client.post(ingestUri)
                .body(this.getClass().getResourceAsStream("/packages/test-package.zip"), "application/zip")
                .perform()) {

            EventSource.from(response.getBody())
                    .onEvent((e) -> {
                        switch (EventType.valueOf(e.event.toUpperCase())) {
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
        }

        assertEquals(0, error.get());
        assertEquals(1, success.get());
        assertEquals(21, deposited.get());
    }

    @Test
    public void syncDepositBadPackageTest() throws Exception {
        final FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

        final AtomicInteger deposited = new AtomicInteger(0);
        final AtomicInteger success = new AtomicInteger(0);
        final AtomicInteger error = new AtomicInteger(0);

        try (FcrepoResponse response = client.post(ingestUri)
                .body(this.getClass().getResourceAsStream("/packages/malformedObjectPackage.tar.gz"),
                        "application/x-tgz")
                .perform()) {

            EventSource.from(response.getBody())
                    .onEvent((e) -> {
                        switch (EventType.valueOf(e.event.toUpperCase())) {
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
                    }).start();;
        }

        assertEquals(1, error.get());
        assertEquals(0, success.get());
        assertTrue(deposited.get() > 0);
    }

    @Test
    public void syncDepositMalformedPackageTest() throws Exception {
        final FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

        try (FcrepoResponse response = client.post(ingestUri)
                .body(this.getClass().getResourceAsStream("/packages/notAZipFile.zip"), "application/zip")
                .perform()) {
            Assert.fail("The request should have failed");
        } catch (final FcrepoOperationFailedException e) {
            // expected
        }
    }

    @Test
    public void otionsTest() throws Exception {
        final FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();

        try (FcrepoResponse response = client.options(ingestUri).perform()) {
            assertEquals("text/turtle", response.getContentType());
            assertEquals(200, response.getStatusCode());
            final String body = IOUtils.toString(response.getBody(), UTF_8);
            assertTrue(body.length() > 100);
        }
    }

    public static File tempDir() throws IOException {
        try {
            return testFolder.newFolder();
        } catch (final IllegalStateException e) {
            return Files.createTempDirectory("staging").toFile();
        }
    }
}
