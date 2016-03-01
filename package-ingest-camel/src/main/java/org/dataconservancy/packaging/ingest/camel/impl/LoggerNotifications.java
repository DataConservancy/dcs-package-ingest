/*
 * Copyright 2016 Johns Hopkins University
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true, property = {
        "service.ranking=-1"})
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
