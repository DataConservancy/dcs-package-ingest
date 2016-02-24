/*
 * Copyright 2016 Johns Hopkins University
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.ingest.integration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.UnknownHostException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;

import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_PROVENANCE_LOCATION;
import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_RESOURCE_LOCATIONS;

import static org.apache.commons.lang.exception.ExceptionUtils.getRootCause;
import static org.dataconservancy.packaging.ingest.camel.Helpers.headerString;

public abstract class DepositIT {

    @Rule
    public TestName name = new TestName();

    private static long timeout_ms =
            Integer.valueOf(System.getProperty("deposit.timeout.seconds", "60"))
                    * 1000;

    List<Exchange> success = new ArrayList<>();

    List<Exchange> fail = new ArrayList<>();

    static final FilenameFilter ignoreFailDir =
            ((dir, name) -> !name.equals("fail"));

    private final HttpClient client =
            HttpClientBuilder.create().setMaxConnPerRoute(Integer.MAX_VALUE)
                    .setMaxConnTotal(Integer.MAX_VALUE).build();

    @Before
    public void setUp() throws Exception {

        fail.clear();
        success.clear();

        for (DepositLocation loc : getDepositLocations()) {
            if (loc.depositDir.exists())
                FileUtils.cleanDirectory(loc.depositDir);
            if (loc.failDir.exists()) {
                FileUtils.cleanDirectory(loc.failDir);
            };
        }

        File extractLocation = getExtractLocation();

        if (extractLocation.exists()) {
            FileUtils.cleanDirectory(extractLocation);
        }

    }

    /*
     * Verifies that failures due to bad packages go into fail folder and cause
     * appropriate notification
     */
    @Test
    public void badPackageTest() throws Exception {
        DepositLocation location = newDepositLocation();

        assertTrue(!location.failDir.exists()
                || location.failDir.list().length == 0);

        copyResource("/packages/badPackage.zip", location.depositDir);

        /* Wait for fail directory to get something in it */
        waitFor(() -> fileCount(location.failDir) > 0);

        /* Package directory should be empty */
        assertEquals(0, fileCount(location.depositDir));

        /* Fail directory should now have one package in it */
        assertEquals(1, fileCount(location.failDir));

        /* Extract directory should be empty */
        assertEquals(0, fileCount(getExtractLocation()));

        /* One failure notification */
        assertEquals(0, success.size());
        assertEquals(1, fail.size());

    }

    @SuppressWarnings("unchecked")
    @Test
    public void projectDepositTest() throws Exception {

        DepositLocation location = newDepositLocation();

        File created =
                copyResource("/packages/project1.zip", location.depositDir);

        waitFor(() -> !created.exists());

        assertNoFailureMessages();

        /* Package directory should be empty now */
        assertEquals(0, fileCount(location.depositDir));

        /* Nothing in failure dir */
        assertEquals(0, fileCount(location.failDir));

        /* Extract directory should be empty */
        assertEquals(0, fileCount(getExtractLocation()));

        assertEquals(1, success.size());

        List<String> locations =
                new ArrayList<String>(((Collection<String>) success.get(0)
                        .getIn().getHeader(HEADER_RESOURCE_LOCATIONS,
                                           Collection.class)));

        assertEquals(1, locations.size());
        assertNotEquals(headerString(success.get(0), Exchange.HTTP_URI),
                        locations.get(0));

        HttpGet get = new HttpGet(locations.get(0));
        get.setHeader(HttpHeaders.ACCEPT, "text/turtle");

        HttpResponse response = client.execute(get);

        assertEquals(HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        assertEquals("text/turtle",
                     response.getFirstHeader(HttpHeaders.CONTENT_TYPE)
                             .getValue());

        /* provenance */
        String provenanceUri = success.get(0).getIn()
                .getHeader(HEADER_PROVENANCE_LOCATION, String.class);
        assertNotEquals(headerString(success.get(0), Exchange.HTTP_URI),
                        provenanceUri);

        get = new HttpGet(provenanceUri);
        get.setHeader(HttpHeaders.ACCEPT, "text/turtle");
        response = client.execute(get);

        assertEquals(HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());
        assertEquals("text/turtle",
                     response.getFirstHeader(HttpHeaders.CONTENT_TYPE)
                             .getValue());
    }

    @Test
    public void depositFullPackageTest() throws Exception {
        DepositLocation location = newDepositLocation();
        File created =
                copyResource("/packages/test-package.zip", location.depositDir);

        waitFor(() -> !created.exists());

        assertNoFailureMessages();
        assertEquals(1, success.size());

        @SuppressWarnings("unchecked")
        List<String> locations =
                new ArrayList<String>(((Collection<String>) success.get(0)
                        .getIn().getHeader(HEADER_RESOURCE_LOCATIONS,
                                           Collection.class)));
        assertEquals(1, locations.size());

        /* Extract directory should be empty */
        assertEquals(0, fileCount(getExtractLocation()));
    }

    /* Verifies graceful failure when the repository URI is unresolvable */
    @Test
    public void badRepositoryUriTest() throws Exception {
        DepositLocation location =
                newDepositLocationFor("http://bad.unresolvable.example.org");

        assertTrue(!location.failDir.exists()
                || location.failDir.list().length == 0);

        copyResource("/packages/project1.zip", location.depositDir);

        waitFor(() -> fileCount(location.failDir) > 0);

        /* Package directory should be empty */
        assertEquals(0, fileCount(location.depositDir));

        /* Extract directory should be empty */
        assertEquals(0, fileCount(getExtractLocation()));

        assertEquals(0, success.size());
        assertEquals(1, fail.size());
        assertTrue(getRootCause(fail.get(0)
                .getProperty(Exchange.EXCEPTION_CAUGHT,
                             Exception.class)) instanceof UnknownHostException);
    }

    /*
     * Catch-all test for depositing packages that have have caused problems in
     * the past
     */
    @Test
    public void problematicPackageTest() throws Exception {

        DepositLocation location = newDepositLocation();
        List<String> packageNames =
                listResources("/problem-packages/README.txt",
                              (dir, name) -> !name.endsWith(".txt"));

        assertNotEquals(0, packageNames.size());

        packageNames.forEach(n -> copyResource("/problem-packages/" + n,
                                               location.depositDir));

        waitFor(() -> fileCount(location.depositDir) == 0);

        assertEquals(packageNames.size(), success.size());

        assertEquals(0, fileCount(location.failDir));

        assertEquals(0, fileCount(getExtractLocation()));
    }

    /*
     * Verify that original package is still in deposit dir if it cannot be
     * moved to fail dir
     */
    @Test
    public void exceptionDuringFailTest() throws Exception {
        DepositLocation location =
                newDepositLocationFor("http://bad.unresolvable.example.org");

        /* This will intentionally make the failure handling route fail */
        location.failDir.delete();
        FileUtils.touch(location.failDir);

        copyResource("/packages/project1.zip", location.depositDir);

        /* Wait for a notification */
        waitFor(() -> !fail.isEmpty());

        /*
         * Make sure we logged a failure, and have not removed the package from
         * the deposit dir
         */
        assertTrue(fileCount(location.depositDir) > 0);

    }

    /*
     * List classpath resources in the directory containing a file at the given
     * path
     */
    protected abstract List<String> listResources(String path,
                                                  FilenameFilter filter);

    private File copyResource(String path, File file) {
        try {
            File outFile = new File(file, new File(path).getName());
            outFile.getParentFile().mkdirs();

            try (InputStream content =
                    this.getClass().getResourceAsStream(path);
                    OutputStream out = new FileOutputStream(outFile)) {

                IOUtils.copy(content, out);
            }

            return outFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static class DepositLocation {

        public File depositDir;

        public File failDir;

        public String repositoryURI;

        public DepositLocation withDepositDir(Object depositDir) {
            this.depositDir = new File((String) depositDir).getAbsoluteFile();
            return this;
        }

        public DepositLocation withFailDir(Object failDir) {
            this.failDir = new File((String) failDir).getAbsoluteFile();
            return this;
        }

        public DepositLocation withRepositoryURI(Object uri) {
            this.repositoryURI = (String) uri;
            return this;
        }
    }

    /*
     * Creates a new deposit location and a new container in Fedora, starts
     * associated deposit workflow
     */
    protected DepositLocation newDepositLocation() {

        try {
            HttpPost post = new HttpPost(getRepositoryBaseURI());
            post.setHeader("Slug", this.getClass().getSimpleName() + "."
                    + name.getMethodName());
            HttpResponse response = client.execute(post);

            assertEquals(HttpStatus.SC_CREATED,
                         response.getStatusLine().getStatusCode());

            return newDepositLocationFor(response.getFirstHeader("Location")
                    .getValue());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* Get the LDP/Fedora base URI */
    protected abstract String getRepositoryBaseURI();

    /*
     * Creates a deposit directory which deposits to the given Fedora URI,
     * starts associated workflow.
     */
    protected abstract DepositLocation newDepositLocationFor(String uri);

    /* Get all known deposit locations */
    protected abstract List<DepositLocation> getDepositLocations();

    /* Get the package extract location */
    protected abstract File getExtractLocation();

    protected class NotificationProbe
            extends RouteBuilder
            implements NotificationDriver {

        @Override
        public void configure() throws Exception {
            from(ROUTE_NOTIFICATION_SUCCESS)
                    .process(e -> assertNotNull(headerString(e,
                                                             HEADER_PROVENANCE_LOCATION)))
                    .process(e -> assertNotNull(headerString(e,
                                                             HEADER_RESOURCE_LOCATIONS)))
                    .process(e -> success.add(e.copy()));

            from(ROUTE_NOTIFICATION_FAIL)
                    .process(e -> assertNotNull(e
                            .getProperty(Exchange.EXCEPTION_CAUGHT)))
                    .process(e -> assertNotNull(e.getIn()
                            .getHeader(Exchange.FILE_NAME)))
                    .process(e -> fail.add(e.copy()));
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long started = new Date().getTime();
        while (!condition.getAsBoolean()) {
            if (new Date().getTime() - started > timeout_ms) {
                throw new TimeoutException("Test timed out!");
            }
            Thread.sleep(500);
        }
    }

    public static int fileCount(File dir) {
        if (!dir.exists()) return 0;
        return dir.list(ignoreFailDir).length;
    }

    public void assertNoFailureMessages() throws Exception {
        if (fail.size() > 0) {
            throw fail.get(0).getProperty(Exchange.EXCEPTION_CAUGHT,
                                          Exception.class);
        }
    }
}
