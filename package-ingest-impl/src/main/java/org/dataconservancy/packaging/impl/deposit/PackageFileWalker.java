/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
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

import java.io.File;
import java.net.URI;
import java.util.Collection;

import org.dataconservancy.packaging.ingest.DepositNotifier;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.Depositor.Deposited;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.PackagedResource;
import org.dataconservancy.packaging.ingest.PackageWalker;
import org.dataconservancy.packaging.ingest.PackageWalkerFactory;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author apb@jhu.edu
 */
@Component(immediate = true)
public class PackageFileWalker implements PackageWalkerFactory<File> {

    LdpPackageAnalyzer<File> analyzer;

    @Reference
    public void setAnalyzer(LdpPackageAnalyzer<File> analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public PackageWalker newWalker(File pkgfile) {
        return new PackageWalker() {

            @Override
            public void walk(Depositor depositer, DepositNotifier notifier) {
                try {
                    doWalk(depositer, notifier, analyzer.getContainerRoots(pkgfile), null);
                } finally {
                    analyzer.cleanUpExtractionDirectory();
                }
            }
        };
    }

    private static void doWalk(Depositor depositer, DepositNotifier notify,
            Collection<PackagedResource> packedResources,
            URI into) {

        for (final PackagedResource packagedResource : packedResources) {

            // Deposit the current node
            final Deposited result = depositer.deposit(packagedResource, into);

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
