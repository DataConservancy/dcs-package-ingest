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

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

import org.dataconservancy.packaging.ingest.DepositNotifier;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.Depositor.DepositedResource;
import org.dataconservancy.packaging.ingest.PackageAnalyzer;
import org.dataconservancy.packaging.ingest.PackageAnalyzerFactory;
import org.dataconservancy.packaging.ingest.PackageWalker;
import org.dataconservancy.packaging.ingest.PackageWalkerFactory;
import org.dataconservancy.packaging.ingest.PackagedResource;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author apb@jhu.edu
 */
@Component(immediate = true)
public class DefaultPackageWalkerFactory implements PackageWalkerFactory {

    PackageAnalyzerFactory analyzerFactory;

    /**
     * Set the analyzer factory.
     *
     * @param analyzerFactory the package analyzer factory.
     */
    @Reference
    public void setAnalyzerFactory(final PackageAnalyzerFactory analyzerFactory) {
        this.analyzerFactory = analyzerFactory;
    }

    @Override
    public PackageWalker newWalker(final InputStream pkg) {
        final PackageAnalyzer analyzer = analyzerFactory.newAnalyzer();

        return new PackageWalker() {

            @Override
            public void walk(final Depositor depositor, final DepositNotifier notifier) {
                try {
                    doWalk(depositor, notifier, analyzer.getContainerRoots(pkg), null);
                } finally {
                    analyzer.cleanUpExtractionDirectory();
                }
            }
        };
    }

    private static void doWalk(final Depositor depositer, final DepositNotifier notify,
            final Collection<PackagedResource> packedResources,
            final URI into) {

        for (final PackagedResource packagedResource : packedResources) {

            // Deposit the current node
            final DepositedResource result = depositer.deposit(packagedResource, into);

            // Notify as appropriate
            notify.onDeposit(result.uri, packagedResource);

            if (packagedResource.getDescription() != null) {
                notify.onDeposit(result.describedBy, packagedResource.getDescription());
            }

            // Now descend to the children and recurse
            doWalk(depositer, notify, packagedResource.getChildren(), result.uri);
        }
    }
}
