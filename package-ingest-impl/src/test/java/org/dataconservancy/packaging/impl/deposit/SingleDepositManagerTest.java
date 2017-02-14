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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.DepositNotifier;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.EventListener;
import org.dataconservancy.packaging.ingest.EventType;
import org.dataconservancy.packaging.ingest.PackageWalker;
import org.dataconservancy.packaging.ingest.PackageWalkerFactory;
import org.dataconservancy.packaging.ingest.PackagedResource;
import org.dataconservancy.packaging.ingest.PackagedResource.Type;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * @author apb@jhu.edu
 */
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
public class SingleDepositManagerTest {

    @Mock
    EventListener listener;

    @Mock
    InputStream stream;

    @Mock
    private PackageWalkerFactory walkerFactory;

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

        when(walkerFactory.newWalker(any(InputStream.class))).thenReturn(walker);
        when(depositFactory.newDepositor(any(URI.class), any(Map.class))).thenReturn(depositer);

    }

    @Test
    public void commitTest() {
        toTest.newDeposit().intoContainer(URI.create("test:nowhere"))
                .withPackage(stream)
                .withListener(listener)
                .perform();

        verify(depositer).commit();
        verify(depositer, times(0)).rollback();
        verify(listener).onEvent(eq(EventType.SUCCESS), any(), any(), any());
    }

    @Test
    public void rollBackTest() {
        doThrow(new RuntimeException()).when(walker).walk(any(Depositor.class), any(DepositNotifier.class));

        toTest.newDeposit().intoContainer(URI.create("test:nowhere"))
                .withPackage(stream)
                .withListener(listener)
                .perform();

        verify(depositer).rollback();
        verify(depositer, times(0)).commit();
        verify(listener).onEvent(eq(EventType.ERROR), any(), any(), any());
    }

    @Test
    public void successfulDepositTest() {

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

        when(container.getURI()).thenReturn(localContainerUri);
        when(container.getType()).thenReturn(Type.CONTAINER);

        doAnswer(i -> {
            final DepositNotifier notifier = i.getArgument(1);

            notifier.onDeposit(depositedBinaryUri, binary);
            notifier.onDeposit(depositedBinaryDescriptionUri, binaryDescription);
            notifier.onDeposit(depositedContainerUri, container);

            return null;
        }).when(walker).walk(any(Depositor.class), any(DepositNotifier.class));

        doAnswer(i -> {
            final URI uri = i.getArgument(0);
            final Map map = i.getArgument(1);

            // Make sure the URI is one of the deposited URIs
            assertTrue(Arrays.asList(depositedContainerUri, depositedBinaryUri, depositedBinaryDescriptionUri)
                    .contains(uri));

            // Make sure the map has everything in it, mapped as expected;
            assertEquals(depositedContainerUri, map.get(localContainerUri));
            assertEquals(depositedBinaryUri, map.get(localBinaryUri));
            assertEquals(depositedBinaryDescriptionUri, map.get(localbinaryDescriptionUri));

            return null;
        }).when(depositer).remap(any(URI.class), any(Map.class));

        toTest.newDeposit().intoContainer(URI.create("test:nowhere"))
                .withPackage(stream)
                .withListener(listener)
                .perform();

        // The container and binary description should be remapped
        verify(depositer).remap(eq(depositedContainerUri), any(Map.class));
        verify(depositer).remap(eq(depositedBinaryDescriptionUri), any(Map.class));

        // The binary shouldn't be remapped!
        verify(depositer, times(0)).remap(eq(depositedBinaryUri), any(Map.class));

        // Three deposit events should be fired
        verify(listener, times(3)).onEvent(eq(EventType.DEPOSIT), any(), any(), any());

        // Two remap events should be fired
        verify(listener, times(2)).onEvent(eq(EventType.REMAP), any(), any(), any());
        verify(listener).onEvent(eq(EventType.REMAP), eq(depositedContainerUri), any(), any());
        verify(listener).onEvent(eq(EventType.REMAP), eq(depositedBinaryDescriptionUri), any(), any());
    }
}
