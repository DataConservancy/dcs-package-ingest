package org.dataconservancy.packaging.impl;

import org.dataconservancy.packaging.ingest.LdpResource;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PackageFileAnalyzerTest {

    private PackageFileAnalyzer underTest;
    private File testPackage;

    @Before
    public void setup() throws IOException {
        URL packageUrl = PackageFileAnalyzerTest.class.getResource("/test_pkg");
        testPackage = new File(packageUrl.getPath());

        OpenPackageService openPackageService = mock(OpenPackageService.class);
        when(openPackageService.openPackage(any(File.class), any(File.class))).thenReturn(testPackage);

        underTest = new PackageFileAnalyzer(openPackageService, testPackage.getParentFile());
    }

    @Test
    public void testPackageAnalyzer() throws URISyntaxException {
        //Doesn't matter what file we pass here since we're mocking the open package code.
        Collection<LdpResource> packageResources = underTest.getContainerRoots(testPackage);

        assertEquals(1, packageResources.size());

        LdpResource rootResource = packageResources.iterator().next();
        assertEquals(new URI("bag://test_pkg/data/obj/curl.ttl"), rootResource.getURI());

        assertEquals(LdpResource.Type.CONTAINER, rootResource.getType());
        assertNotNull(rootResource.getBody());
        assertNull(rootResource.getDescription());

        assertEquals(4, rootResource.getChildren().size());
    }

    @Test
    public void testEmptyContainer() throws URISyntaxException {
        //Doesn't matter what file we pass here since we're mocking the open package code.
        Collection<LdpResource> packageResources = underTest.getContainerRoots(testPackage);

        assertEquals(1, packageResources.size());

        LdpResource rootResource = packageResources.iterator().next();
        assertNotNull(rootResource);

        boolean emptyCollectionChecked = false;
        for (LdpResource child : rootResource.getChildren()) {
            if (child.getURI().equals(new URI("bag://test_pkg/data/obj/curl/out.ttl"))) {
                assertEquals(LdpResource.Type.CONTAINER, child.getType());

                assertTrue(child.getChildren().isEmpty());
                assertNotNull(child.getBody());
                assertNull(child.getDescription());
                emptyCollectionChecked = true;
            }
        }

        assertTrue(emptyCollectionChecked);
    }

    @Test
    public void testFileResource() throws URISyntaxException {
        //Doesn't matter what file we pass here since we're mocking the open package code.
        Collection<LdpResource> packageResources = underTest.getContainerRoots(testPackage);

        assertEquals(1, packageResources.size());

        LdpResource rootResource = packageResources.iterator().next();
        assertNotNull(rootResource);

        boolean emptyCollectionChecked = false;
        for (LdpResource child : rootResource.getChildren()) {
            if (child.getURI().equals(new URI("bag://test_pkg/data/bin/curl/log.txt"))) {
                assertEquals(LdpResource.Type.NONRDFSOURCE, child.getType());

                assertTrue(child.getChildren().isEmpty());
                assertNotNull(child.getBody());
                assertNotNull(child.getDescription());
                LdpResource childRdf = child.getDescription();

                assertEquals(LdpResource.Type.RDFSOURCE, childRdf.getType());
                assertEquals(new URI("bag://test_pkg/data/obj/curl/log.txt.ttl"), childRdf.getURI());
                assertNotNull(childRdf.getBody());

                assertTrue(child.getChildren().isEmpty());
                assertNull(childRdf.getDescription());
                emptyCollectionChecked = true;
            }
        }

        assertTrue(emptyCollectionChecked);
    }
}
