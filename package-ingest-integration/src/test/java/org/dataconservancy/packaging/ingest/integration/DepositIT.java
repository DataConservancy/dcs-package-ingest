
package org.dataconservancy.packaging.ingest.integration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.lang.annotation.Annotation;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;

import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.dataconservancy.packaging.impl.PackageFileAnalyzer;
import org.dataconservancy.packaging.impl.PackageFileAnalyzerConfig;
import org.dataconservancy.packaging.ingest.camel.impl.CamelDepositManager;
import org.dataconservancy.packaging.ingest.camel.impl.DefaultContextFactory;
import org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraConfig;
import org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver;
import org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.impl.config.EmailNotificationsConfig;
import org.dataconservancy.packaging.ingest.camel.impl.config.PackageFileDepositWorkflowConfig;

import static org.junit.Assert.assertEquals;

public class DepositIT {

    static WatchService svc;

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

    public DepositIT() {
        wire();
    }

    @Before
    public void setUp() throws Exception {
        svc = FileSystems.getDefault().newWatchService();
    }

    @After
    public void cleanUp() throws Exception {
        svc.close();
        FileUtils.cleanDirectory(new File(PACKAGE_DEPOSIT_DIR));
        FileUtils.cleanDirectory(new File(PACKAGE_FAIL_DIR));
    }

    /* Verifies that failed packages go into fail older */
    @Test
    public void failureTest() throws Exception {
        Path depositDir = Paths.get(PACKAGE_DEPOSIT_DIR);
        Path failDir = Paths.get(PACKAGE_FAIL_DIR);
        File created =
                copyResource("/packages/badPackage.zip", depositDir.toFile());
        depositDir.register(svc, StandardWatchEventKinds.ENTRY_DELETE);

        long start = new Date().getTime();

        /* Wait for the package file to disappear from the deposit dir */
        while (created.exists() && new Date().getTime() - start < 3000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty */
        assertEquals(0, depositDir.toFile().list().length);

        /* Fail directory should now have one package in it */
        assertEquals(1, failDir.toFile().list().length);

    }

    @Test
    public void projectDepositTest() throws Exception {

        Path dir = Paths.get(PACKAGE_DEPOSIT_DIR);

        File created = copyResource("/packages/project1.zip", dir.toFile());
        copyResource("/packages/project1.zip", new File("/tmp"));
        dir.register(svc, StandardWatchEventKinds.ENTRY_DELETE);

        long start = new Date().getTime();

        while (created.exists() && new Date().getTime() - start < 10000) {
            Thread.sleep(1000);
        }

        /* Package directory should be empty now */
        assertEquals(0, dir.toFile().list().length);

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

        FedoraDepositDriver driver = new FedoraDepositDriver();
        driver.init(new FedoraConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return FedoraConfig.class;
            }

            @Override
            public String fedora_baseuri() {
                return FEDORA_BASEURI;
            }
        });

        PackageFileAnalyzer analyzer = new PackageFileAnalyzer();
        analyzer.init(new PackageFileAnalyzerConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return PackageFileAnalyzerConfig.class;
            }

            @Override
            public String package_extract_dir() {
                return PACKAGE_EXTRACT_DIR;
            }
        });

        driver.setPackageAnalyzer(analyzer);

        EmailNotifications notifications = new EmailNotifications();
        notifications.init(new EmailNotificationsConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return EmailNotificationsConfig.class;
            }

            @Override
            public String mail_to() {
                return "nobody@example.org";
            }
        });

        PackageFileDepositWorkflow rootDeposit =
                new PackageFileDepositWorkflow();

        rootDeposit.init(new PackageFileDepositWorkflowConfig() {

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
        });

        CamelDepositManager mgr = new CamelDepositManager();
        mgr.setContextFactory(new DefaultContextFactory());
        mgr.setDepositDriver(driver);
        mgr.setNotificationDriver(notifications);
        mgr.addDepositWorkflow(rootDeposit);

        mgr.init();

    }
}
