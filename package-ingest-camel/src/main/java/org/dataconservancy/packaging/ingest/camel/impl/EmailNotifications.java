
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.EmailNotificationsConfig", description = "E-mail notification config")
@interface EmailNotificationsConfig {

    @AttributeDefinition(description = "E-mail address to send notifications to")
    String mail_to();
}

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
        });
        from(ROUTE_NOTIFICATION_SUCCESS).process(e -> {
        });

    }
}
