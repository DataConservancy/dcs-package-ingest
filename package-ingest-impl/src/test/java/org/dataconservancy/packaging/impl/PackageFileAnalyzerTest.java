package org.dataconservancy.packaging.impl;

import org.dataconservancy.packaging.ingest.LdpResource;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PackageFileAnalyzerTest {

    private OpenPackageService openPackageService;
    private PackageFileAnalyzer underTest;
    private File testPackage;

    @Before
    public void setup() throws IOException {
        URL packageUrl = PackageFileAnalyzerTest.class.getResource("/test_pkg");
        testPackage = new File(packageUrl.getPath());

        openPackageService = mock(OpenPackageService.class);
        when(openPackageService.openPackage(any(File.class), any(File.class))).thenReturn(testPackage);

        underTest = new PackageFileAnalyzer(openPackageService, testPackage.getParentFile());
    }

    @Test
    public void testPackageAnalyzer() {
        //Doesn't matter what file we pass here since we're mocking the open package code.
        Collection<LdpResource> packageResources = underTest.getContainerRoots(testPackage);

        assertEquals(1, packageResources.size());
    }
}
