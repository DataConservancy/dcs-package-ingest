/*
 * Copyright 2016 Johns Hopkins University
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

package org.dataconservancy.packaging.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import org.dataconservancy.packaging.ingest.PackagedResource;

import org.junit.Before;
import org.junit.Test;

/**
 * @author bbrosius@jhu.edu
 */
public class PackageFileAnalyzerTest {

    private static final URI EXPECTED_ROOT_URI = URI.create("bag://test_pkg/data/obj/curl.ttl");

    private static final URI EXPECTED_ORPHAN_BINARY_URI = URI.create("bag://test_pkg/data/bin/curl/NoParent.txt");

    private DcsPackageAnalyzer underTest;

    private final InputStream packageStream = mock(InputStream.class);

    private static final String TURTLE_MEDIA_TYPE = "text/turtle";

    @Before
    public void setup() throws IOException {
        final URL packageUrl = PackageFileAnalyzerTest.class.getResource("/test_pkg");
        final File testPackage;
        testPackage = new File(packageUrl.getPath());

        final OpenPackageService openPackageService = mock(OpenPackageService.class);
        when(openPackageService.openPackage(any(File.class), eq(packageStream))).thenReturn(testPackage);

        underTest = new DcsPackageAnalyzer(openPackageService, testPackage.getParentFile());
    }

    @Test
    public void testPackageAnalyzer() throws URISyntaxException {
        // Doesn't matter what file we pass here since we're mocking the open package code.
        final Collection<PackagedResource> packageResources = underTest.getContainerRoots(packageStream);

        // The root container, and a binary that has no container
        assertEquals(2, packageResources.size());

        final PackagedResource rootResource = get(EXPECTED_ROOT_URI, packageResources);
        assertEquals(EXPECTED_ROOT_URI, rootResource.getURI());
        assertEquals(TURTLE_MEDIA_TYPE, rootResource.getMediaType());
        assertEquals(PackagedResource.Type.CONTAINER, rootResource.getType());
        assertNotNull(rootResource.getBody());
        assertNull(rootResource.getDescription());

        assertEquals(4, rootResource.getChildren().size());
    }

    @Test
    public void testEmptyContainer() throws URISyntaxException {
        // Doesn't matter what file we pass here since we're mocking the open package code.
        final Collection<PackagedResource> packageResources = underTest.getContainerRoots(packageStream);

        // The root container, and a binary that has no container
        assertEquals(2, packageResources.size());

        final PackagedResource rootResource = get(EXPECTED_ROOT_URI, packageResources);
        assertNotNull(rootResource);

        boolean emptyCollectionChecked = false;
        for (final PackagedResource child : rootResource.getChildren()) {
            if (child.getURI().equals(new URI("bag://test_pkg/data/obj/curl/out.ttl"))) {
                assertEquals(PackagedResource.Type.CONTAINER, child.getType());

                assertEquals(TURTLE_MEDIA_TYPE, child.getMediaType());
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
        // Doesn't matter what file we pass here since we're mocking the open package code.
        final Collection<PackagedResource> packageResources = underTest.getContainerRoots(packageStream);

        // The root container, and a binary that has no container
        assertEquals(2, packageResources.size());

        final PackagedResource rootResource = get(EXPECTED_ROOT_URI, packageResources);
        assertNotNull(rootResource);

        boolean fileChecked = false;
        for (final PackagedResource child : rootResource.getChildren()) {
            if (child.getURI().equals(new URI("bag://test_pkg/data/bin/curl/log.txt"))) {
                assertEquals(PackagedResource.Type.NONRDFSOURCE, child.getType());
                assertEquals("text/plain", child.getMediaType());
                assertTrue(child.getChildren().isEmpty());
                assertNotNull(child.getBody());
                assertNotNull(child.getDescription());
                final PackagedResource childRdf = child.getDescription();

                assertEquals(PackagedResource.Type.RDFSOURCE, childRdf.getType());
                assertEquals(new URI("bag://test_pkg/data/obj/curl/log.txt.ttl"), childRdf.getURI());
                assertEquals(TURTLE_MEDIA_TYPE, childRdf.getMediaType());
                assertNotNull(childRdf.getBody());

                assertTrue(child.getChildren().isEmpty());
                assertNull(childRdf.getDescription());
                fileChecked = true;
            }
        }

        assertTrue(fileChecked);
    }

    // Ensures that the binary resource that has no container is properly processed
    @Test
    public void testBinaryResourceNoContainer() throws Exception {
        // Doesn't matter what file we pass here since we're mocking the open package code.
        final Collection<PackagedResource> packageResources = underTest.getContainerRoots(packageStream);

        // The root container, and a binary that has no container
        assertEquals(2, packageResources.size());

        final PackagedResource rootResource = get(EXPECTED_ROOT_URI, packageResources);
        assertNotNull(rootResource);

        // Assert that the orphan is present in the packaged resources
        final PackagedResource orphan = get(EXPECTED_ORPHAN_BINARY_URI, packageResources);
        assertNotNull(orphan);

        // Assert that the type of the resource is non-RDF
        assertEquals(PackagedResource.Type.NONRDFSOURCE, orphan.getType());

        // Assert that this resource is not contained by any other resource
        // the orphan URI should only be present once, for the orphan itself.
        assertFalse(packageResources
                .stream()
                // Exclude the orphan resource itself
                .filter(resource -> !resource.getURI().equals(EXPECTED_ORPHAN_BINARY_URI))
                // Flatten all the children
                .flatMap(resource -> resource.getChildren().stream())
                // See if the orphan's URI is present on any of the resources
                .anyMatch(resource -> resource.getURI().equals(EXPECTED_ORPHAN_BINARY_URI)));
    }

    /**
     * Obtain the identified LDPResource from the collection of resources, or null.
     *
     * @param resourceUri
     * @param resources
     * @return
     */
    PackagedResource get(final URI resourceUri, final Collection<PackagedResource> resources) {
        return resources.stream().filter(r -> r.getURI().equals(resourceUri)).findFirst().get();
    }
}
