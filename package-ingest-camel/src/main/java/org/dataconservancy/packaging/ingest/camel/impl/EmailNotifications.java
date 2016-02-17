
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.config.EmailNotificationsConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sends e-mail notification messages to specified recipients
 */
@Component(service = NotificationDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = EmailNotificationsConfig.class)
public class EmailNotifications
        extends RouteBuilder
        implements NotificationDriver {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotifications.class);

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

        PropertiesComponent pc = getContext().getComponent("properties", PropertiesComponent.class);

        LOG.info("PC: {}", pc);
        if (pc != null && pc.getInitialProperties() != null) {
            LOG.info("Initial properties: {}", pc.getInitialProperties().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().toString()).collect(Collectors.joining(", ", "", "")));
        }

        if (pc != null && pc.getInitialProperties() != null) {
            LOG.info("Override properties: {}", pc.getOverrideProperties().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().toString()).collect(Collectors.joining(", ", "", "")));
        }

        LOG.info("Registry 'props': {}",
                getContext().getRegistry().lookupByNameAndType("props", Properties.class).entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().toString()).collect(Collectors.joining(", ", "", "")));

        LOG.info("mail.smtpHost from 'props': {}", getContext().getRegistry().lookupByNameAndType("props", Properties.class).getProperty("mail.smtpHost"));
//        LOG.info("mail.smtpHost from 'resolvePropertyPlaceholders': {}", getContext().resolvePropertyPlaceholders("mail.smtpHost"));
        LOG.info("mail.smtpHost from 'resolvePropertyPlaceholders' with {{}}: {}", getContext().resolvePropertyPlaceholders("{{mail.smtpHost}}"));

        if (pc != null && pc.getLocations() != null) {
            LOG.info("PropertiesComponent locations: {}", Stream.of(pc.getLocations()).collect(Collectors.joining(", ", "", "")));
        } else {
            LOG.info("PropertiesComponent reference or its locations were null (PropertiesComponent reference: {})", pc);
        }
//
//        if (pc != null && pc.getPropertiesResolver() != null) {
//            LOG.info("PropertiesComponent locations: {}", Stream.of(pc.getLocations()).collect(Collectors.joining(", ", "", "")));
//        } else {
//            LOG.info("PropertiesComponent reference or its locations were null (PropertiesComponent reference: {})", pc);
//        }



        from(ROUTE_NOTIFICATION_FAIL)
                .setHeader(DEPOSIT_SUCCESS).constant(false)
                .setHeader(DEPOSIT_FAILURE).exchangeProperty(Exchange.EXCEPTION_CAUGHT)
                .to("direct:_sendNotification");

        from(ROUTE_NOTIFICATION_SUCCESS)
                .setHeader(DEPOSIT_SUCCESS).constant(true)
                .to("direct:_sendNotification");

        from("direct:_sendNotification")
                .setHeader("CamelVelocityResourceUri").simple("${mail.template}")
                .choice()
                    .when(exchange -> exchange.getIn().getHeader(DEPOSIT_SUCCESS).equals(true))
                        .setHeader("subject").simple("${mail.subjectSuccess}").endChoice()
                    .otherwise()
                        .setHeader("subject").simple("${mail.subjectFailure}").end()
                .to("velocity:dummy")
                .choice()
                    .when(simple("'${mail.smtpPort}' != '25'"))
                        .to("smtps://{{mail.smtpHost}}:{{mail.smtpPort}}?from={{mail.from}}&to={{mail.to}}&" +
                            "sslContextParameters=#sslContextParameters&" +
                            "username={{mail.smtpUser}}&password={{mail.smtpPass}}&debugMode={{mail.debug}}")
                .otherwise()
                    .to("smtp://{{mail.smtpHost}}:{{mail.smtpPort}}?from={{mail.from}}&to={{mail.to}}&debugMode={{mail.debug}}");


    }

}
