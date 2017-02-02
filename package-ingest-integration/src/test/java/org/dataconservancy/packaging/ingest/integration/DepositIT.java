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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public abstract class DepositIT {

    @Rule
    public TestName name = new TestName();

    @Before
    public void setUp() throws Exception {

    }

    /*
     * Verifies that failures due to bad packages go into fail folder and cause appropriate notification
     */
    @Test
    public void badPackageTest() throws Exception {
        // "/packages/badPackage.zip", location.depositDir;

    }

    @Test
    public void projectDepositTest() throws Exception {

        // "/packages/project1.zip",
    }

    @Test
    public void depositFullPackageTest() throws Exception {
        // "/packages/test-package.zip"
    }

    /**
     * Tests the deposit of a package which has multiple objects in the package without a parent.
     *
     * @throws Exception
     */
    @Test
    public void depositFlatPackageTest() throws Exception {
        // "/packages/flat-package.tar"

    }

    /**
     * Insures that a package containing a binary object that has no parent is properly ingested.
     *
     * @throws Exception
     */
    @Test
    public void depositFlatPackageWithBinariesTest() throws Exception {
        // "/packages/flat-package-with-binary.tar"

        // There are 8 objects in the package without a parent
        // assertEquals(8, locations.size());

        // One of them should be the binary
        // assertTrue(locations.stream().anyMatch(l -> l.endsWith("osfstorage_porsche_2.jpg")));
    }

    /*
     * Catch-all test for depositing packages that have have caused problems in the past
     */
    @Test
    public void problematicPackageTest() throws Exception {

        // final List<String> packageNames =
        // listResources("/problem-packages/README.txt",
        // (dir, name) -> !name.endsWith(".txt"));

        // packageNames.forEach(n -> copyResource("/problem-packages/" + n,
        // location.depositDir));

    }

}
