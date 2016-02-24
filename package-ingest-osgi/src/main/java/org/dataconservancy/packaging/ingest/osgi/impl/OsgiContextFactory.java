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
package org.dataconservancy.packaging.ingest.osgi.impl;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.core.osgi.OsgiCamelContextPublisher;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.core.osgi.utils.BundleDelegatingClassLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;
import org.apache.camel.spi.Registry;
import org.dataconservancy.packaging.ingest.camel.ContextFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
@interface OsgiContextFactoryConfig {

    @AttributeDefinition(description = "Camel Context graceful shutdown time (seconds)")
    int context_shutdown_timeout() default 60;
}

@Component(enabled = true, configurationPolicy = ConfigurationPolicy.OPTIONAL)
public class OsgiContextFactory
        implements ContextFactory {

    OsgiCamelContextPublisher publisher;

    private BundleContext bundleContext;

    private int timeout = 60;

    @Activate
    public void activate(BundleContext bundleContext,
                         Map<String, Object> properties) {
        this.bundleContext = bundleContext;
        publisher = new OsgiCamelContextPublisher(bundleContext);
        try {
            publisher.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (properties.containsKey("context.shutdown.timeout")) {
            timeout = Integer.valueOf((String) properties
                    .get("context.shutdown.timeout"));
        }
    }

    @Modified
    public void update(OsgiContextFactoryConfig config) {
        timeout = config.context_shutdown_timeout();
    }

    @Override
    public CamelContext newContext(String id, Registry registry) {
        CamelContext context;

        if (bundleContext != null) {

            context = new OsgiDefaultCamelContext(bundleContext, registry);
            context.setApplicationContextClassLoader(new BundleDelegatingClassLoader(bundleContext
                    .getBundle()));
            Thread.currentThread().setContextClassLoader(context
                    .getApplicationContextClassLoader());

        } else {
            context = new DefaultCamelContext();
        }

        if (publisher != null) {
            context.getManagementStrategy().addEventNotifier(publisher);
        }

        if (id != null && id != "") {
            context.setNameStrategy(new ExplicitCamelContextNameStrategy(id));
        }

        context.getShutdownStrategy().setTimeout(timeout);

        context.setUseMDCLogging(true);
        context.setUseBreadcrumb(true);

        return context;
    }

}
