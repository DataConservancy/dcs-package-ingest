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

package org.dataconservancy.packaging.ingest;

import java.io.InputStream;
import java.net.URI;

/**
 * @author apb@jhu.edu
 */
public interface DepositBuilder {

    /**
     * Specifiy an LDP container to deposit into.
     *
     * @param container Container URI.
     * @return configured deposit builder.
     */
    public DepositBuilder intoContainer(URI container);

    /**
     * Specify the stream of the package to deposit
     *
     * @param pkgStream package stream
     * @return configured deposit builder.
     */
    public DepositBuilder withPackage(InputStream pkgStream);

    /**
     * Specify a listener for deposit events.
     *
     * @param listener
     * @return
     */
    public DepositBuilder withListener(EventListener listener);

    /** Perform a deposit */
    public void perform();
}
