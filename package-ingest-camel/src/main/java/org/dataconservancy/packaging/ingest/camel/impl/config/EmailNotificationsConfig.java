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
package org.dataconservancy.packaging.ingest.camel.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration properties supported by the {@link org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications}
 * {@code RouteBuilder}.  Methods on this interface are translated to Camel properties per Camel conventions: underscore
 * characters are replaced with a period.  So the method {@link #mail_smtpHost()} would translate to the Camel property
 * {@code mail.smtpHost}.
 * <dl>
 * <dt>mail.to</dt><dd><strong>Required:</strong> the comma-separated list of email address to deliver notifications to.</dd>
 * <dt>mail.smtpHost</dt><dd>Optional: the outbound SMTP server which will be responsible for delivering the notification email.
 *      <strong>N.B.</strong> the default mail server uses SMTP AUTH so you <em>must</em> supply a username and password if you use the
 *      default value for {@code mail.smtpHost}</dd>
 * <dt>mail.smtpPort</dt><dd>Optional: the outbound SMTP server port.  <em>Must be {@code 465} (the default) if using the default {@code mail.smtpHost}</em></dd>
 * <dt>mail.smtpUser</dt><dd><strong>Required if using the default {@code mail.smtpHost}:</strong> the username used to authenticate to the outbound SMTP server (aka SMTP AUTH).  Use this if your SMTP server requires authentication in order to send email.  <em>Requires the use of SSL.</em></dd>
 * <dt>mail.smtpPass</dt><dd><strong>Required if using the default {@code mail.smtpHost}:</strong> the password used to authenticate to the outbound SMTP server.</dd>
 * <dt>mail.from</dt><dd>Optional: the email address notifications will appear to be from.</dd>
 * <dt>mail.subjectFailure</dt><dd>Optional: the subject line of failed deposit notifications</dd>
 * <dt>mail.subjectSuccess</dt><dd>Optional: the subject line of successful deposit notifications</dd>
 * <dt>mail.template</dt><dd>Optional: the Velocity template used to render the body of a deposit notification.</dd>
 * <dt>mail.debug</dt><dd>Optional: set to {@code true} to debug the interaction with the SMTP server.</dd>
 * </dl>
 */
@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications", description = "E-mail notification config")
public @interface EmailNotificationsConfig {

    String DEFAULT_SUCCESS_TEMPLATE = "org/dataconservancy/packaging/ingest/camel/default-ingest-notification-email.vm";
    String SSL_SMTP_PORT = "465";
    String TLS_SMTP_PORT = "587";
    String PLAIN_SMTP_PORT = "25";
    String DEFAULT_SMTP_PORT = SSL_SMTP_PORT;
    String DEFAULT_SUCCESS_NOTIFICATION_SUBJECT = "Deposit Success";
    String DEFAULT_FAILURE_NOTIFICATION_SUBJECT = "Deposit Failure";
    String DEFAULT_SENDER = "noreply@localhost";
    String DEFAULT_DEBUG = "false";
    String DEFAULT_SMTP_HOST = "smtp.gmail.com";

    // TODO SSL configuration parameters

    @AttributeDefinition(description = "SMTP Server used to send email notifications (optional, defaults to " + DEFAULT_SMTP_HOST + ")")
    String mail_smtpHost();

    @AttributeDefinition(description = "SMTP port number (optional, must be " + DEFAULT_SMTP_PORT + " when using " + DEFAULT_SMTP_HOST + ")",
            defaultValue = DEFAULT_SMTP_PORT) String mail_smtpPort() default DEFAULT_SMTP_PORT;

    @AttributeDefinition(description = "Username used to authenticate when sending notifications (required if using " + DEFAULT_SMTP_HOST + ")") String mail_smtpUser();

    @AttributeDefinition(description = "Password used to authenticate when sending notifications (required if using " + DEFAULT_SMTP_HOST + ")", type = AttributeType.PASSWORD) String mail_smtpPass();

    @AttributeDefinition(description = "E-mail address the notifications appear to be from (optional, defaults to " +
            DEFAULT_SENDER + ")", defaultValue = DEFAULT_SENDER) String mail_from() default DEFAULT_SENDER;

    @AttributeDefinition(description = "E-mail address(es) to send notifications to (required)") String mail_to();

    @AttributeDefinition(description = "Classpath resource or file:/// URI containing the Velocity success email " +
            "template (optional, defaults to " + DEFAULT_SUCCESS_TEMPLATE + ")",
            defaultValue = DEFAULT_SUCCESS_TEMPLATE) String mail_template() default DEFAULT_SUCCESS_TEMPLATE;

    @AttributeDefinition(description = "Subject line of a successful notification (optional, defaults to " + DEFAULT_SUCCESS_NOTIFICATION_SUBJECT + ")",
            defaultValue = DEFAULT_SUCCESS_NOTIFICATION_SUBJECT) String mail_subjectSuccess() default DEFAULT_SUCCESS_NOTIFICATION_SUBJECT;

    @AttributeDefinition(description = "Subject line of a failure notification (optional, defaults to " + DEFAULT_FAILURE_NOTIFICATION_SUBJECT + ")",
            defaultValue = DEFAULT_FAILURE_NOTIFICATION_SUBJECT) String mail_subjectFailure() default DEFAULT_FAILURE_NOTIFICATION_SUBJECT;

    @AttributeDefinition(description = "Debug the underlying mail provider (optional, defaults to " + DEFAULT_DEBUG + ")") String mail_debug() default DEFAULT_DEBUG;

}