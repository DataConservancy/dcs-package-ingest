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
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.spi.Registry;
import org.dataconservancy.packaging.ingest.camel.ContextFactory;

public class DefaultContextFactory
        implements ContextFactory {

    @Override
    public CamelContext newContext(String id, Registry registry) {
        CamelContext cxt = new DefaultCamelContext(registry);

        if (id != null && !id.equals("")) {
            cxt.setNameStrategy(new ExplicitCamelContextNameStrategy(id));
        }

        cxt.setUseMDCLogging(true);
        cxt.setUseBreadcrumb(true);

        return cxt;
    }

}
