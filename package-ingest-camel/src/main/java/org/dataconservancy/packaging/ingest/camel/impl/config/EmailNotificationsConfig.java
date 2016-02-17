package org.dataconservancy.packaging.ingest.camel.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration properties supported by the {@link org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications}
 * {@code RouteBuilder}.  Methods on this interface are translated to Camel properties per Camel conventions: underscore
 * characters are replaced with a period.  So the method {@link #mail_smtpHost()} would translate to the Camel property
 * {@code mail.smtpHost}.
 * <dl>
 * <dt>mail.smtpHost</dt><dd><strong>Required:</strong> the outbound SMTP server which will be responsible for delivering the notification email.</dd>
 * <dt>mail.to</dt><dd><strong>Required:</strong> the comma-separated list of email address to deliver notifications to.</dd>
 * <dt>mail.smtpPort</dt><dd>Optional: the outbound SMTP server port.</dd>
 * <dt>mail.smtpUser</dt><dd>Optional: the username used to authenticate to the outbound SMTP server (aka SMTP AUTH).  Use this if your SMTP server requires authentication in order to send email.  <em>Requires the use of SSL.</em></dd>
 * <dt>mail.smtpPass</dt><dd>Optional: the password used to authenticate to the outbound SMTP server.</dd>
 * <dt>mail.from</dt><dd>Optional: the email address notifications will appear to be from.</dd>
 * <dt>mail.subjectFailure</dt><dd>Optional: the subject line of failed deposit notifications</dd>
 * <dt>mail.subjectSuccess</dt><dd>Optional: the subject line of successful deposit notifications</dd>
 * <dt>mail.template</dt><dd>Optional: the Velocity template used to render the body of a deposit notification.</dd>
 * <dt>mail.debug</dt><dd>Optional: set to {@code true} to debug the interaction with the SMTP server.</dd>
 * </dl>
 */
@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.EmailNotificationsConfig", description = "E-mail notification config")
public @interface EmailNotificationsConfig {

    String DEFAULT_SUCCESS_TEMPLATE = "org/dataconservancy/packaging/ingest/camel/impl/default-ingest-notification-email.vm";
    String SSL_SMTP_PORT = "465";
    String TLS_SMTP_PORT = "587";
    String PLAIN_SMTP_PORT = "25";
    String DEFAULT_SMTP_PORT = SSL_SMTP_PORT;
    String DEFAULT_SUCCESS_NOTIFICATION_SUBJECT = "Deposit Success";
    String DEFAULT_FAILURE_NOTIFICATION_SUBJECT = "Deposit Failure";
    String DEFAULT_SENDER = "noreply@localhost";
    String DEFAULT_DEBUG = "false";

    // TODO SSL configuration parameters

    @AttributeDefinition(description = "SMTP Server used to send email notifications (required)") String mail_smtpHost();

    @AttributeDefinition(description = "SMTP port number (optional, defaults to " + DEFAULT_SMTP_PORT + ")",
            defaultValue = DEFAULT_SMTP_PORT) String mail_smtpPort() default DEFAULT_SMTP_PORT;

    @AttributeDefinition(description = "Username used to authenticate when sending notifications (optional)") String mail_smtpUser();

    @AttributeDefinition(description = "Password used to authenticate when sending notifications (optional)") String mail_smtpPass();

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