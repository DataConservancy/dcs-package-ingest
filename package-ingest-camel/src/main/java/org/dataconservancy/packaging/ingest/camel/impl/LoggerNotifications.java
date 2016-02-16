
package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_PROVENANCE_LOCATION;
import static org.dataconservancy.packaging.ingest.camel.DepositWorkflow.HEADER_RESOURCE_LOCATIONS;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.LoggerNotifications")
@interface LoggerNotificationsConfig {

}

@Designate(ocd = LoggerNotificationsConfig.class)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class LoggerNotifications
        extends RouteBuilder
        implements NotificationDriver {

    Logger LOG = LoggerFactory.getLogger(LoggerNotifications.class);

    @Override
    public void configure() throws Exception {
        from(ROUTE_NOTIFICATION_FAIL).process(e -> {
            LOG.warn("Deposit fail!",
                     e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
        });
        from(ROUTE_NOTIFICATION_SUCCESS).process(e -> {
            LOG.info("Deposited provenanance resource {}"
                    + e.getIn().getHeader(HEADER_PROVENANCE_LOCATION));
            LOG.info("Deposited resources at root(s) {}",
                     e.getIn().getHeader(HEADER_RESOURCE_LOCATIONS));
        });

    }

}
