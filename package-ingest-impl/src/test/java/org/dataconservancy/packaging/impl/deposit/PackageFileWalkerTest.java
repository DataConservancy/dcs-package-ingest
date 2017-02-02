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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.dataconservancy.packaging.impl.PackageFileAnalyzer;
import org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory;
import org.dataconservancy.packaging.ingest.DepositNotifier;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.Depositor.DepositedResource;
import org.dataconservancy.packaging.ingest.PackageWalker;
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
public class PackageFileWalkerTest {

    @Mock
    PackagedResource root1;

    @Mock
    PackagedResource root2;

    @Mock
    PackagedResource child1;

    @Mock
    PackagedResource binary;

    @Mock
    PackagedResource binaryDescription;

    @Mock
    Depositor deposit;

    @Mock
    DepositNotifier notifier;

    @Mock
    PackageFileAnalyzerFactory analyzerFactory;

    @Mock
    PackageFileAnalyzer analyzer;

    PackageFileWalker toTest = new PackageFileWalker();

    @Before
    public void setUp() {
        initContainer(root1, "root1");
        initContainer(root2, "root2");
        initContainer(child1, "child1");
        when(binaryDescription.getURI()).thenReturn(URI.create("test:binaryDescription"));
        when(binaryDescription.getType()).thenReturn(Type.RDFSOURCE);
        when(binary.getURI()).thenReturn(URI.create("test:binary"));
        when(binary.getDescription()).thenReturn(binaryDescription);
        when(binary.getType()).thenReturn(Type.NONRDFSOURCE);

        when(root1.getChildren()).thenReturn(Arrays.asList(child1));
        when(root2.getChildren()).thenReturn(Collections.emptyList());
        when(child1.getChildren()).thenReturn(Arrays.asList(binary));

        when(analyzer.getContainerRoots(any(File.class))).thenReturn(Arrays.asList(root1, root2));
        when(analyzerFactory.newAnalyzer()).thenReturn(analyzer);
        toTest.setAnalyzerFactory(analyzerFactory);
    }

    private void initContainer(PackagedResource container, String id) {
        when(container.getURI()).thenReturn(URI.create("test:" + id));
        when(container.getType()).thenReturn(Type.CONTAINER);
    }

    @Test
    public void allObjectsepositedTest() {
        final List<PackagedResource> depositedResources = new ArrayList<>();
        final PackageWalker walker = toTest.newWalker(new File("whatever"));

        when(deposit.deposit(any(PackagedResource.class), any(URI.class))).thenAnswer(i -> {
            depositedResources.add(i.getArgumentAt(0, PackagedResource.class));
            return new DepositedResource();
        });

        walker.walk(deposit, notifier);

        assertTrue(depositedResources.contains(root1));
        assertTrue(depositedResources.contains(root2));
        assertTrue(depositedResources.contains(child1));
        assertTrue(depositedResources.contains(binary));
        // The binaryDescription is *not* in this list, as it is implicit in `binary`
    }

    @Test
    public void depositedInRightContainersTest() {

        final PackageWalker walker = toTest.newWalker(new File("whatever"));

        final URI depositedRoot1 = URI.create("deposit:root1");
        final URI depositedRoot2 = URI.create("deposit:root2");
        final URI depositedChild1 = URI.create("deposited:child1");
        when(deposit.deposit(eq(root1), isNull(URI.class)))
                .thenReturn(new DepositedResource(depositedRoot1, null));
        when(deposit.deposit(eq(root2), isNull(URI.class)))
                .thenReturn(new DepositedResource(depositedRoot2, null));
        when(deposit.deposit(eq(child1), eq(depositedRoot1)))
                .thenReturn(new DepositedResource(depositedChild1, null));
        when(deposit.deposit(eq(binary), eq(depositedChild1)))
                .thenReturn(new DepositedResource());

        walker.walk(deposit, notifier);

        verify(deposit).deposit(eq(child1), eq(depositedRoot1));
        verify(deposit).deposit(eq(binary), eq(depositedChild1));
    }

    @Test
    public void notificationTest() {
        final PackageWalker walker = toTest.newWalker(new File("whatever"));

        final URI depositedRoot1 = URI.create("deposit:root1");
        final URI depositedRoot2 = URI.create("deposit:root2");
        final URI depositedChild1 = URI.create("deposited:child1");
        final URI depositedBinaryURI = URI.create("deposited:binary");
        final URI depositedBinaryDescriptionURI = URI.create("deposited:binaryDescription");

        when(deposit.deposit(eq(root1), isNull(URI.class)))
                .thenReturn(new DepositedResource(depositedRoot1, null));
        when(deposit.deposit(eq(root2), isNull(URI.class)))
                .thenReturn(new DepositedResource(depositedRoot2, null));
        when(deposit.deposit(eq(child1), eq(depositedRoot1)))
                .thenReturn(new DepositedResource(depositedChild1, null));
        when(deposit.deposit(eq(binary), eq(depositedChild1)))
                .thenReturn(new DepositedResource(depositedBinaryURI, depositedBinaryDescriptionURI));

        walker.walk(deposit, notifier);

        // Make sure all resources result in notifications
        verify(notifier).onDeposit(eq(depositedRoot1), eq(root1));
        verify(notifier).onDeposit(eq(depositedRoot2), eq(root2));
        verify(notifier).onDeposit(eq(depositedChild1), eq(child1));
        verify(notifier).onDeposit(eq(depositedBinaryURI), eq(binary));
        verify(notifier).onDeposit(eq(depositedBinaryDescriptionURI), eq(binaryDescription));

        // Make sure we have no other invocations!
        verify(notifier, times(5)).onDeposit(any(URI.class), any(PackagedResource.class));
    }
}
