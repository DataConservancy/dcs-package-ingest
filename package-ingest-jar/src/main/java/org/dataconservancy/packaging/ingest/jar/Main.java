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

package org.dataconservancy.packaging.ingest.jar;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;

import javax.servlet.Servlet;

import org.dataconservancy.packaging.impl.DcsPackageAnalyzerFactory;
import org.dataconservancy.packaging.impl.ExtensionRegistration;
import org.dataconservancy.packaging.impl.deposit.DefaultPackageWalkerFactory;
import org.dataconservancy.packaging.impl.deposit.FedoraDepositFactory;
import org.dataconservancy.packaging.impl.deposit.SingleDepositManager;
import org.dataconservancy.packaging.ingest.http.IngestServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
/**
 * Ingest servlet runner.
 *
 * @author apb@jhu.edu
 */
public abstract class Main {

    private static final URI REPOSITORY_URI = URI.create(getVal("REPOSITORY_BASEURI",
            "http://localhost/fcrepo/rest"));

    private static final int PORT = Integer.parseInt(getVal("PACKAGE_INGEST_PORT", "32080"));

    private static final String PATH = "/ingest";

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /**
     * Launch the package ingest servlet.
     *
     * @param args Commandline args.
     * @throws Exception
     */

    public static void main(final String[] args) throws Exception {

        LogUtil.adjustLogLevels();

        final Servlet ingest = initServlet();
        final Server server = startContainer(ingest, PORT, PATH);

        new ExtensionRegistration()
                .withPort(PORT)
                .withPath(PATH)
                .withRepositoryBaseUri(REPOSITORY_URI)
                .run();

        server.join();

        server.stop();
    }

    private static Server startContainer(final Servlet servlet, final int port, final String path) throws Exception {

        LOG.info("Listening on port " + port);

        final Server server = new Server(port);

        final ServletContextHandler servletContext = new ServletContextHandler();
        servletContext.setContextPath("/");
        servletContext.setBaseResource(Resource.newClassPathResource("/WEB_INF"));

        final ServletHolder ingestServlet = new ServletHolder(servlet);
        ingestServlet.setAsyncSupported(true);

        servletContext.addServlet(new ServletHolder(servlet), "/ingest");

        servletContext.addServlet(DefaultServlet.class, "/");

        server.setHandler(servletContext);
        server.start();

        return server;
    }

    private static Servlet initServlet() throws Exception {
        final FedoraDepositFactory fedora = new FedoraDepositFactory();
        fedora.setBaseUri(REPOSITORY_URI.toString());

        final DcsPackageAnalyzerFactory dcs = new DcsPackageAnalyzerFactory();
        final File tempDir = Files.createTempDirectory("extract").toFile();
        tempDir.deleteOnExit();
        dcs.setExtractDir(tempDir.toString());

        final DefaultPackageWalkerFactory ldpc = new DefaultPackageWalkerFactory();
        ldpc.setAnalyzerFactory(dcs);

        final SingleDepositManager mgr = new SingleDepositManager();
        mgr.setDepositFactory(fedora);
        mgr.setWalkerFactory(ldpc);

        return new IngestServlet(mgr);
    }

    private static String getVal(final String key, final String defaultValue) {
        return System.getProperty(key, System.getenv().getOrDefault(key, defaultValue));
    }
}
