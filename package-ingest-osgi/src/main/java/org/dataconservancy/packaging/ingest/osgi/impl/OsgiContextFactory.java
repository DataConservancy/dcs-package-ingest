
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
