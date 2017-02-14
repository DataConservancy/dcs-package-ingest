/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoResponse;

import org.dataconservancy.packaging.impl.DcsPackageAnalyzerFactory;
import org.dataconservancy.packaging.impl.deposit.DefaultPackageWalkerFactory;
import org.dataconservancy.packaging.impl.deposit.FedoraDepositFactory;
import org.dataconservancy.packaging.impl.deposit.SingleDepositManager;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author apb@jhu.edu
 */
public class IngestServletIT {

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
    }

    @Test
    public void smokeTest() throws Exception {
        final FcrepoClient client = FcrepoClient.client().throwExceptionOnFailure().build();
        try (FcrepoResponse response = client.post(ingestUri)
                .body(this.getClass().getResourceAsStream("/test_pkg.tar.gz"), "application/x-tar")
                .perform()) {
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody(),
                    UTF_8));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                System.out.println(line);
            }
            response.getBody().close();
            bufferedReader.close();
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
