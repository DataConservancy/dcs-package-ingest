/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.packaging.impl.deposit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.DepositNotifier;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.PackageWalker;
import org.dataconservancy.packaging.ingest.PackageWalkerFactory;
import org.dataconservancy.packaging.ingest.PackagedResource;
import org.dataconservancy.packaging.ingest.PackagedResource.Type;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author apb@jhu.edu
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
public class SingleDepositManagerTest {

    @Mock
    private PackageWalkerFactory<File> walkerFactory;

    @Mock
    private PackageWalker walker;

    @Mock
    private DepositFactory depositFactory;

    @Mock
    private Depositor depositer;

    SingleDepositManager toTest = new SingleDepositManager();

    @Before
    public void setUp() {

        toTest.setDepositFactory(depositFactory);
        toTest.setWalkerFactory(walkerFactory);

        when(walkerFactory.newWalker(any(File.class))).thenReturn(walker);
        when(depositFactory.newDepositor(any(URI.class), any(Map.class))).thenReturn(depositer);

    }

    @Test
    public void commitTest() {
        toTest.depositPackageInto(URI.create("test:nowhere"), new File(""), Collections.emptyMap());

        verify(depositer).commit();
        verify(depositer, times(0)).rollback();
    }

    @Test
    public void rollBackTest() {
        doThrow(new RuntimeException()).when(walker).walk(any(Depositor.class), any(DepositNotifier.class));

        try {
            toTest.depositPackageInto(URI.create("test:nowhere"), new File(""), Collections.emptyMap());
            fail("Should have thrown exception");
        } catch (final Exception e) {
            // expected
        }

        verify(depositer).rollback();
        verify(depositer, times(0)).commit();
    }

    @Test
    public void remapTest() {

        final PackagedResource binaryDescription = mock(PackagedResource.class);
        final PackagedResource binary = mock(PackagedResource.class);
        final PackagedResource container = mock(PackagedResource.class);

        final URI localContainerUri = URI.create("test:container");
        final URI localbinaryDescriptionUri = URI.create("test:binaryDescription");
        final URI localBinaryUri = URI.create("test:binary");

        final URI depositedBinaryDescriptionUri = URI.create("test:deposited/binaryDescription");
        final URI depositedBinaryUri = URI.create("test:deposited/binary");
        final URI depositedContainerUri = URI.create("test:deposited/container");

        when(binaryDescription.getURI()).thenReturn(localbinaryDescriptionUri);
        when(binaryDescription.getType()).thenReturn(Type.RDFSOURCE);

        when(binary.getURI()).thenReturn(localBinaryUri);
        when(binary.getType()).thenReturn(Type.NONRDFSOURCE);
        when(binary.getDescription()).thenReturn(binaryDescription);

        when(container.getURI()).thenReturn(localContainerUri);
        when(container.getType()).thenReturn(Type.CONTAINER);

        doAnswer(i -> {
            final DepositNotifier notifier = i.getArgumentAt(1, DepositNotifier.class);

            notifier.onDeposit(depositedBinaryUri, binary);
            notifier.onDeposit(depositedBinaryDescriptionUri, binaryDescription);
            notifier.onDeposit(depositedContainerUri, container);

            return null;
        }).when(walker).walk(any(Depositor.class), any(DepositNotifier.class));

        doAnswer(i -> {
            final URI uri = i.getArgumentAt(0, URI.class);
            final Map map = i.getArgumentAt(1, Map.class);

            // Make sure the URI is one of the deposited URIs
            assertTrue(Arrays.asList(depositedContainerUri, depositedBinaryUri, depositedBinaryDescriptionUri)
                    .contains(uri));

            // Make sure the map has everything in it, mapped as expected;
            assertEquals(depositedContainerUri, map.get(localContainerUri));
            assertEquals(depositedBinaryUri, map.get(localBinaryUri));
            assertEquals(depositedBinaryDescriptionUri, map.get(localbinaryDescriptionUri));

            return null;
        }).when(depositer).remap(any(URI.class), any(Map.class));

        toTest.depositPackageInto(URI.create("test:nowhere"), new File(""), Collections.emptyMap());

        // The container and binary description should be remapped
        verify(depositer).remap(eq(depositedContainerUri), any(Map.class));
        verify(depositer).remap(eq(depositedBinaryDescriptionUri), any(Map.class));

        // The binary shouldn't be remapped!
        verify(depositer, times(0)).remap(eq(depositedBinaryUri), any(Map.class));
    }
}
