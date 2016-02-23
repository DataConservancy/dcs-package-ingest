
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

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

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;

import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_PROVENANCE_LOCATION;
import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_RESOURCE_LOCATIONS;

import static org.dataconservancy.packaging.ingest.camel.Helpers.headerString;

public abstract class DepositIT {

    @Rule
    public TestName name = new TestName();

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

        File created =
                copyResource("/packages/badPackage.zip", location.depositDir);

        long start = new Date().getTime();

        /* Wait for the package file to disappear from the deposit dir */
        while (created.exists() && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty */
        assertEquals(0, location.depositDir.list(ignoreFailDir).length);

        /* Fail directory should now have one package in it */
        assertEquals(1, location.failDir.list().length);

        /* Extract directory should be empty */
        assertEquals(0, getExtractLocation().list().length);

        assertEquals(0, success.size());
        assertEquals(1, fail.size());

    }

    @SuppressWarnings("unchecked")
    @Test
    public void projectDepositTest() throws Exception {

        DepositLocation location = newDepositLocation();

        File created =
                copyResource("/packages/project1.zip", location.depositDir);

        long start = new Date().getTime();

        while (created.exists() && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty now */
        assertEquals(0, location.depositDir.list(ignoreFailDir).length);

        /* Nothing in failure dir */
        assertEquals(0, location.failDir.list().length);

        /* Extract directory should be empty */
        assertEquals(0, getExtractLocation().list().length);

        assertEquals(0, fail.size());
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

        long start = new Date().getTime();

        while (created.exists() && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        if (fail.size() > 0) {
            fail.get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                    .printStackTrace(System.out);
        }

        assertEquals(0, fail.size());
        assertEquals(1, success.size());

        @SuppressWarnings("unchecked")
        List<String> locations =
                new ArrayList<String>(((Collection<String>) success.get(0)
                        .getIn().getHeader(HEADER_RESOURCE_LOCATIONS,
                                           Collection.class)));
        assertEquals(1, locations.size());

        /* Extract directory should be empty */
        assertEquals(0, getExtractLocation().list().length);
    }

    /* Verifies graceful failure when the repository URI is unresolvable */
    @Test
    public void badRepositoryUriTest() throws Exception {
        DepositLocation location =
                newDepositLocationFor("http://bad.unresolvable.example.org");

        File created =
                copyResource("/packages/project1.zip", location.depositDir);

        long start = new Date().getTime();

        /* Wait for the package file to disappear from the deposit dir */
        while (created.exists() && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty */
        assertEquals(0, location.depositDir.list(ignoreFailDir).length);

        /* Fail directory should now have one package in it */
        assertEquals(1, location.failDir.list().length);

        /* Extract directory should be empty */
        assertEquals(0, getExtractLocation().list().length);

        assertEquals(0, success.size());
        assertEquals(1, fail.size());
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
        long start = new Date().getTime();

        while (location.depositDir.list(ignoreFailDir).length != 0
                && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        assertEquals(packageNames.size(), success.size());

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
            this.depositDir = new File((String) depositDir);
            return this;
        }

        public DepositLocation withFailDir(Object failDir) {
            this.failDir = new File((String) failDir);
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
}
