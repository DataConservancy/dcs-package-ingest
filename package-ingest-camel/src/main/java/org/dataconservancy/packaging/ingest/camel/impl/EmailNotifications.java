
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.jsse.SSLContextParameters;
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

    /**
     * Header key whose presence in a message indicates a successful deposit occurred.  The value of the
     * header is a boolean.
     */
    static final String DEPOSIT_SUCCESS = "deposit.success";

    /**
     * Header key whose presense in a message indicates a failed deposit occurred.  The value
     * of the header will be the Exception object that caused the failure.
     */
    static final String DEPOSIT_FAILURE = "deposit.failure";

    @Activate
    public void init(EmailNotificationsConfig config) {
    }

    @Override
    public void configure() throws Exception {

        from(ROUTE_NOTIFICATION_FAIL)
                .setHeader(DEPOSIT_SUCCESS).constant(false)
                .setHeader(DEPOSIT_FAILURE).exchangeProperty(Exchange.EXCEPTION_CAUGHT)
                .to("direct:_sendNotification");

        from(ROUTE_NOTIFICATION_SUCCESS)
                .setHeader(DEPOSIT_SUCCESS).constant(true)
                .to("direct:_sendNotification");

        from("direct:_sendNotification")
                .setHeader("CamelVelocityResourceUri").simple("{{template}}")
                .choice()
                    .when(exchange -> exchange.getIn().getHeader(DEPOSIT_SUCCESS).equals(true))
                        .setHeader("subject").simple("{{subjectSuccess}}").endChoice()
                    .otherwise()
                        .setHeader("subject").simple("{{subjectFailure}}").end()
                .to("velocity:dummy")
                .choice()
                    .when(simple("'{{smtpPort}}' != '25'"))
                        .to("smtps://{{smtpHost}}:{{smtpPort}}?from={{from}}&to={{to}}&" +
                            "sslContextParameters=#sslContextParameters&" +
                            "username={{smtpUser}}&password={{smtpPass}}&debugMode={{debug}}")
                .otherwise()
                    .to("smtp://{{smtpHost}}:{{smtpPort}}?from={{from}}&to={{to}}&debugMode={{debug}}");


    }

}
