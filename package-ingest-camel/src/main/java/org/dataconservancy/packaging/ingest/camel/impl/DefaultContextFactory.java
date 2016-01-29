
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExplicitCamelContextNameStrategy;

import org.dataconservancy.packaging.ingest.camel.ContextFactory;

public class DefaultContextFactory
        implements ContextFactory {

    @Override
    public CamelContext newContext(String id) {
        CamelContext cxt = new DefaultCamelContext();

        if (id != null) {
            cxt.setNameStrategy(new ExplicitCamelContextNameStrategy(id));
        }

        cxt.setUseMDCLogging(true);
        cxt.setUseBreadcrumb(true);

        return cxt;
    }

}
