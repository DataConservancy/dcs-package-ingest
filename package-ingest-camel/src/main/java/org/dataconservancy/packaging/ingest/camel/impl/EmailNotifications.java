/*
 * Copyright 2016 Johns Hopkins University
 *
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

import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.dataconservancy.packaging.ingest.camel.impl.config.EmailNotificationsConfig;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Sends e-mail notification messages to specified recipients. This implementation uses the SMTP server port number to
 * determine whether plain text, SSL, or TLS are to be used when connecting to an SMTP email relay.
 * <dl>
 * <dt>25</dt>
 * <dd>plain text</dd>
 * <dt>465</dt>
 * <dd>SSL</dd>
 * <dt>587</dt>
 * <dd>TLS (<strong>not supported</strong>)</dd>
 * </dl>
 * <strong>TLS</strong> is <em>not</em> supported, so using a port number of {@code 587} is not recommended. Port
 * numbers other than the ones defined above are not supported at this time.
 * <p>
 * The other configuration properties used by this class are defined in {@link EmailNotificationsConfig}, and have
 * sensible defaults.
 * </p>
 *
 * @see EmailNotificationsConfig
 */
@Component(service = NotificationDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true,
        property = {
            "service.ranking=1" })
@Designate(ocd = EmailNotificationsConfig.class)
public class EmailNotifications
        extends RouteBuilder
        implements NotificationDriver {

    /**
     * Header key whose presence in a message indicates a successful deposit occurred. The value of the header is a
     * boolean.
     */
    static final String DEPOSIT_SUCCESS = "deposit.success";

    /**
     * Header key whose presense in a message indicates a failed deposit occurred. The value of the header will be the
     * Exception object that caused the failure.
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
                .setHeader("CamelVelocityResourceUri").simple("${properties:mail.template}")
                .choice()
                .when(exchange -> exchange.getIn().getHeader(DEPOSIT_SUCCESS).equals(true))
                .setHeader("subject").simple("${properties:mail.subjectSuccess}").endChoice()
                .otherwise()
                .setHeader("subject").simple("${properties:mail.subjectFailure}").end()
                .to("velocity:dummy")
                .choice()
                .when(simple("'${properties:mail.smtpPort}' != '25'"))
                .to("smtps://{{mail.smtpHost}}:{{mail.smtpPort}}?from={{mail.from}}&to={{mail.to}}&" +
                        "sslContextParameters=#sslContextParameters&" +
                        "username={{mail.smtpUser}}&password={{mail.smtpPass}}&debugMode={{mail.debug}}")
                .otherwise()
                .to("smtp://{{mail.smtpHost}}:{{mail.smtpPort}}?from={{mail.from}}&to={{mail.to}}&" +
                        "debugMode={{mail.debug}}");

    }

}
