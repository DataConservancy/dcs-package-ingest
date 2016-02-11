
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.config.EmailNotificationsConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Sends e-mail notification messages to specified recipients
 */
@Component(service = NotificationDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = EmailNotificationsConfig.class)
public class EmailNotifications
        extends RouteBuilder
        implements NotificationDriver {

    @Activate
    public void init(EmailNotificationsConfig config) {
    }

    @Override
    public void configure() throws Exception {
        from(ROUTE_NOTIFICATION_FAIL).process(e -> {
            /* Just a place holder */
            e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                    .printStackTrace(System.err);
        }).to("smtp://{{mail.host}}:{{mail.port}}?password={{mail.password}}&username={{mail.username}}");

        from(ROUTE_NOTIFICATION_SUCCESS).process(e -> {
             //build message body here
        }).to("smtp://{{mail.host}}:{{mail.port}}?password={{mail.password}}&username={{mail.username}}");

    }
}
