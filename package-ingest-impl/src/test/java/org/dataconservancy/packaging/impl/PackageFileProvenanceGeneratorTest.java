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

import org.dataconservancy.packaging.ingest.LdpResource;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class PackageFileProvenanceGeneratorTest {
    private PackageFileProvenanceGenerator underTest;
    private File packageFile;
    private Map<String, String> testURIMap;

    @Before
    public void setup() {
        underTest = new PackageFileProvenanceGenerator();

        //Since we don't actually need the file to be a package file we'll just use a test resource.
        URL packageUrl = PackageFileAnalyzerTest.class.getResource("/test_pkg/bagit.txt");
        packageFile = new File(packageUrl.getPath());

        testURIMap = new HashMap<>();
        testURIMap.put("uri:foo", "bag://file1");
        testURIMap.put("uri:bar", "bag://file2");
    }

    @Test
    public void testProvenanceGeneration() {
        LdpResource packageResource = underTest.generatePackageProvenance(packageFile, testURIMap);

        assertNotNull(packageResource);

        assertEquals(packageFile.toURI(), packageResource.getURI());
        assertEquals(LdpResource.Type.NONRDFSOURCE, packageResource.getType());
        assertTrue(packageResource.getChildren().isEmpty());
        assertNotNull(packageResource.getBody());

        assertNotNull(packageResource.getDescription());

        LdpResource provenanceResource = packageResource.getDescription();
        assertNotNull(provenanceResource.getBody());
        assertNull(provenanceResource.getDescription());
        assertTrue(provenanceResource.getChildren().isEmpty());
        assertEquals(LdpResource.Type.RDFSOURCE, provenanceResource.getType());
    }
}
