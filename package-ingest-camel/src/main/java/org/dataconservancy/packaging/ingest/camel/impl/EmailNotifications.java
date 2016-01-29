
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;


/**
 * Sends e-mail notification messages to specified recipients
 */
@Component(service = NotificationDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class EmailNotifications
        extends RouteBuilder
        implements NotificationDriver {


    @Override
    public void configure() throws Exception {
        from(ROUTE_NOTIFICATION_FAIL).process(e -> {
        });
        from(ROUTE_NOTIFICATION_SUCCESS).process(e -> {
        });

    }
}
