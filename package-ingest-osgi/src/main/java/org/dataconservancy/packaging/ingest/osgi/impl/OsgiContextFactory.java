
package org.dataconservancy.packaging.ingest.osgi.impl;

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

@Component(enabled = true)
public class OsgiContextFactory
        implements ContextFactory {

    OsgiCamelContextPublisher publisher;

    private BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        publisher = new OsgiCamelContextPublisher(bundleContext);
        try {
            publisher.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CamelContext newContext(String id, Registry registry) {
        CamelContext context;

        if (bundleContext != null) {

            context = new OsgiDefaultCamelContext(bundleContext, registry);
            context.setApplicationContextClassLoader(new BundleDelegatingClassLoader(bundleContext
                    .getBundle()));
            Thread.currentThread()
                    .setContextClassLoader(context.getApplicationContextClassLoader());

        } else {
            context = new DefaultCamelContext();
        }

        if (publisher != null) {
            context.getManagementStrategy().addEventNotifier(publisher);
        }

        if (id != null) {
            context.setNameStrategy(new ExplicitCamelContextNameStrategy(id));
        }

        context.setUseMDCLogging(true);
        context.setUseBreadcrumb(true);

        return context;
    }

}
