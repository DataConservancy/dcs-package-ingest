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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.PackageDepositManager;
import org.dataconservancy.packaging.ingest.PackageWalker;
import org.dataconservancy.packaging.ingest.PackageWalkerFactory;
import org.dataconservancy.packaging.ingest.PackagedResource.Type;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Manages the deposit of a single package
 *
 * @author apb@jhu.edu
 */
@Component(immediate = true)
public class SingleDepositManager<T> implements PackageDepositManager<T> {

    PackageWalkerFactory<T> walkerFactory;

    DepositFactory depositFactory;

    @Reference
    public void setWalkerFactory(PackageWalkerFactory<T> wf) {
        this.walkerFactory = wf;
    }

    @Reference
    public void setDepositFactory(DepositFactory df) {
        this.depositFactory = df;
    }

    @Override
    public void depositPackageInto(URI resource, T pkg, Map<String, Object> context) {

        final Map<URI, URI> localUriToDeposited = new HashMap<>();
        final List<URI> toUpdate = new ArrayList<>();

        final Depositor depositor = depositFactory.newDepositor(resource, context);
        final PackageWalker walker = walkerFactory.newWalker(pkg);

        try {
            // First, initially deposit all objects
            walker.walk(depositor, (uri, ldpr) -> {
                localUriToDeposited.put(ldpr.getURI(), uri);

                if (!Type.NONRDFSOURCE.equals(ldpr.getType())) {
                    toUpdate.add(uri);
                }
            });

            // Next, re-map all URIs
            toUpdate.forEach(uri -> depositor.remap(uri, localUriToDeposited));

            // Finally, commit
            depositor.commit();
        } catch (final Throwable e) {

            // Rollback if error!
            depositor.rollback();
            throw e;
        }
    }
}
