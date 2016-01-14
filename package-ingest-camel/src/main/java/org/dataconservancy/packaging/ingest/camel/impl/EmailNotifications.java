
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.CamelContext;

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

/**
 * Sends e-mail notification messages to specified recipients
 */
@Component(service = NotificationDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class EmailNotifications
        implements NotificationDriver {

    @Override
    public void addRoutesToCamelContext(CamelContext context) throws Exception {
        // TODO Auto-generated method stub

    }
}
