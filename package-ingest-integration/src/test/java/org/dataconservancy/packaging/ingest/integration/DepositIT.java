
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.dataconservancy.packaging.impl.PackageFileAnalyzer;
import org.dataconservancy.packaging.impl.PackageFileAnalyzerConfig;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.CamelDepositManager;
import org.dataconservancy.packaging.ingest.camel.impl.DefaultContextFactory;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraConfig;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver;
import org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.impl.config.PackageFileDepositWorkflowConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_PROVENANCE_LOCATION;
import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_RESOURCE_LOCATIONS;

import static org.dataconservancy.packaging.ingest.camel.Helpers.headerString;

public class DepositIT {

    List<Exchange> success = new ArrayList<>();

    List<Exchange> fail = new ArrayList<>();

    private CamelDepositManager mgr;

    static String PACKAGE_DEPOSIT_DIR = System
            .getProperty("package.deposit.dir",
                         new File("target/package/deposit").getAbsolutePath());

    static String PACKAGE_FAIL_DIR = System
            .getProperty("package.fail.dir",
                         new File("target/package/fail").getAbsolutePath());

    static String PACKAGE_EXTRACT_DIR = System
            .getProperty("package.extract.dir",
                         new File("target/package/extract").getAbsolutePath());

    static String FEDORA_BASEURI = System
            .getProperty("fedora.baseuri", "http://localhost:8080/fcrepo/rest");

    private final HttpClient client =
            HttpClientBuilder.create().setMaxConnPerRoute(Integer.MAX_VALUE)
                    .setMaxConnTotal(Integer.MAX_VALUE).build();

    @Before
    public void setUp() throws Exception {
        wire();
        fail.clear();
        success.clear();
        FileUtils.cleanDirectory(new File(PACKAGE_DEPOSIT_DIR));
        FileUtils.cleanDirectory(new File(PACKAGE_FAIL_DIR));
    }

    @After
    public void tearDown() throws Exception {
        mgr.shutDown();
    }

    /* Verifies that failed packages go into fail older */
    @Test
    public void failureTest() throws Exception {
        Path depositDir = Paths.get(PACKAGE_DEPOSIT_DIR);
        Path failDir = Paths.get(PACKAGE_FAIL_DIR);
        File created =
                copyResource("/packages/badPackage.zip", depositDir.toFile());

        long start = new Date().getTime();

        /* Wait for the package file to disappear from the deposit dir */
        while (created.exists() && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty */
        assertEquals(0, depositDir.toFile().list().length);

        /* Fail directory should now have one package in it */
        assertEquals(1, failDir.toFile().list().length);

        assertEquals(0, success.size());
        assertEquals(1, fail.size());

    }

    @SuppressWarnings("unchecked")
    @Test
    public void projectDepositTest() throws Exception {

        File dir = new File(PACKAGE_DEPOSIT_DIR);

        File created = copyResource("/packages/project1.zip", dir);

        long start = new Date().getTime();

        while (created.exists() && new Date().getTime() - start < 30000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty now */
        assertEquals(0, dir.list().length);

        /* Nothing in failure dir */
        assertEquals(0, new File(PACKAGE_FAIL_DIR).list().length);

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
    }

    @Test
    public void depositFullPackageTest() throws Exception {
        File dir = new File(PACKAGE_DEPOSIT_DIR);

        File created = copyResource("/packages/test-package.zip", dir);

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
    }

    private File copyResource(String path, File file) throws IOException {
        File outFile = new File(file, new File(path).getName());

        try (InputStream content = this.getClass().getResourceAsStream(path);
                OutputStream out = new FileOutputStream(outFile)) {
            IOUtils.copy(content, out);
        }

        return outFile;
    }

    protected void wire() {

        FedoraConfig fedoraConfig = new FedoraConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return FedoraConfig.class;
            }

            @Override
            public String fedora_baseuri() {
                return FEDORA_BASEURI;
            }
        };

        PackageFileAnalyzerConfig analyzerConfig =
                new PackageFileAnalyzerConfig() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return PackageFileAnalyzerConfig.class;
                    }

                    @Override
                    public String package_extract_dir() {
                        return PACKAGE_EXTRACT_DIR;
                    }
                };

        PackageFileDepositWorkflowConfig workflowConfig =
                new PackageFileDepositWorkflowConfig() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return PackageFileDepositWorkflowConfig.class;
                    }

                    @Override
                    public int package_poll_interval_ms() {
                        return 1000;
                    }

                    @Override
                    public String package_fail_dir() {
                        return PACKAGE_FAIL_DIR;
                    }

                    @Override
                    public String package_deposit_dir() {
                        return PACKAGE_DEPOSIT_DIR;
                    }

                    @Override
                    public String deposit_location() {
                        return FEDORA_BASEURI;
                    }

                    @Override
                    public boolean create_directories() {
                        return true;
                    }
                };

        FedoraDepositDriver driver = new FedoraDepositDriver();
        driver.init(fedoraConfig);

        PackageFileAnalyzer analyzer = new PackageFileAnalyzer();
        analyzer.init(analyzerConfig);

        driver.setPackageAnalyzer(analyzer);

        PackageFileDepositWorkflow rootDeposit =
                new PackageFileDepositWorkflow();

        rootDeposit.init(workflowConfig);

        mgr = new CamelDepositManager();
        mgr.setContextFactory(new DefaultContextFactory());
        mgr.setDepositDriver(driver, asMap(fedoraConfig));
        mgr.setNotificationDriver(new NotificationProbe(), new HashMap<>());
        mgr.addDepositWorkflow(rootDeposit, asMap(workflowConfig));

        mgr.init();
    }

    private class NotificationProbe
            extends RouteBuilder
            implements NotificationDriver {

        @Override
        public void configure() throws Exception {
            from(NotificationDriver.ROUTE_NOTIFICATION_SUCCESS)
                    .process(e -> success.add(e.copy()));

            from(NotificationDriver.ROUTE_NOTIFICATION_FAIL)
                    .process(e -> fail.add(e.copy()));
        }
    }

    private Map<String, Object> asMap(Annotation config) {
        Map<String, Object> props = new HashMap<>();
        for (Method m : config.annotationType().getMethods()) {
            try {
                if (m.getParameterCount() == 0)
                    props.put(m.getName().replace('_', '.'), m.invoke(config));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return props;
    }
}
