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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dataconservancy.packaging.ingest.DepositBuilder;
import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.EventType;
import org.dataconservancy.packaging.ingest.EventListener;
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
public class SingleDepositManager implements PackageDepositManager {

    PackageWalkerFactory walkerFactory;

    DepositFactory depositFactory;

    /**
     * Set the walker factory.
     *
     * @param wf the walker factory.
     */
    @Reference
    public void setWalkerFactory(final PackageWalkerFactory wf) {
        this.walkerFactory = wf;
    }

    /**
     * Set the deposit factory.
     *
     * @param df deposit factory.
     */
    @Reference
    public void setDepositFactory(final DepositFactory df) {
        this.depositFactory = df;
    }

    private void depositPackageInto(final URI resource, final InputStream pkg, final EventListener listener,
            final Map<String, Object> context) {

        final Map<URI, URI> localUriToDeposited = new HashMap<>();
        final List<URI> toUpdate = new ArrayList<>();

        final Depositor depositor = depositFactory.newDepositor(resource, context);
        final PackageWalker walker = walkerFactory.newWalker(pkg);

        // First, initially deposit all objects
        try {
            walker.walk(depositor, (uri, ldpr) -> {

                // Notify
                listener.onEvent(EventType.DEPOSIT, uri, ldpr,
                        String.format("Deposited <> as <>", ldpr.getURI(), uri));

                localUriToDeposited.put(ldpr.getURI(), uri);

                if (!Type.NONRDFSOURCE.equals(ldpr.getType())) {
                    toUpdate.add(uri);
                }
            });

            // Next, re-map all URIs
            toUpdate.forEach(uri -> {
                depositor.remap(uri, localUriToDeposited);
                listener.onEvent(EventType.REMAP, uri, null, "Remapped " + uri);
            });

            // Finally, commit
            depositor.commit();
            listener.onEvent(EventType.SUCCESS, null, null, "Ingest successfully completed");
        } catch (final Throwable e) {

            // Rollback if error!
            try {
                depositor.rollback();
            } finally {
                listener.onEvent(EventType.ERROR, null, null, e);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DepositBuilder newDeposit() {
        return new DepositBuilder() {

            private InputStream pkgStream;

            private URI container;

            // Use a noop listener if no listeners are explicitly added;
            private EventListener listener = (a, b, c, d) -> {
            };

            @Override
            public DepositBuilder withPackage(final InputStream pkgStream) {
                this.pkgStream = pkgStream;
                return this;
            }

            @Override
            public void perform() {
                depositPackageInto(container, pkgStream, listener, Collections.emptyMap());
            }

            @Override
            public DepositBuilder intoContainer(final URI container) {
                this.container = container;
                return this;
            }

            @Override
            public DepositBuilder withListener(final EventListener listener) {
                this.listener = listener;
                return this;
            }
        };
    }
}
