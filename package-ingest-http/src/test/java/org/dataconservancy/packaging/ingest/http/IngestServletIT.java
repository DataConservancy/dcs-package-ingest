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
import java.io.InputStreamReader;
import java.net.URI;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author apb@jhu.edu
 */
public class IngestServletIT {

    static Server server;

    public static void main(final String[] args) throws Exception {
        setUp();
        server.join();

    }

    @BeforeClass
    public static void setUp() throws Exception {
        server = new Server(8080);
        final ServletContextHandler servletContext = new ServletContextHandler();
        servletContext.setContextPath("/");
        servletContext.setBaseResource(Resource.newClassPathResource("/index.html"));

        final ServletHolder servlet = servletContext.addServlet(IngestServlet.class, "/ingest");
        servlet.setAsyncSupported(true);

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
        try (FcrepoResponse response = FcrepoClient.client().throwExceptionOnFailure().build().post(URI.create(
                "http://localhost:8080/ingest"))
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
}
