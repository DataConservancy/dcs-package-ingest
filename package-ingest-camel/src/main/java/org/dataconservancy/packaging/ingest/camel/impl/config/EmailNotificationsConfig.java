package org.dataconservancy.packaging.ingest.camel.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.EmailNotificationsConfig", description = "E-mail notification config")
public @interface EmailNotificationsConfig {

    String DEFAULT_SUCCESS_TEMPLATE = "org/dataconservancy/packaging/ingest/camel/impl/default-ingest-notification-email.vm";
    String DEFAULT_SMTP_PORT = "25";
    String DEFAULT_SUCCESS_NOTIFICATION_SUBJECT = "Deposit Success";
    String DEFAULT_FAILURE_NOTIFICATION_SUBJECT = "Deposit Failure";
    String DEFAULT_SENDER = "noreply@localhost";
    String DEFAULT_DEBUG = "false";

    // TODO SSL configuration parameters

    @AttributeDefinition(description = "SMTP Server used to send email notifications (required)")
    String mail_smtpHost();

    @AttributeDefinition(description = "SMTP port number (optional, defaults to " + DEFAULT_SMTP_PORT + ")",
            defaultValue = DEFAULT_SMTP_PORT)
    String mail_smtpPort() default DEFAULT_SMTP_PORT;

    @AttributeDefinition(description = "Username used to authenticate when sending notifications (optional)")
    String mail_smtpUser();

    @AttributeDefinition(description = "Password used to authenticate when sending notifications (optional)")
    String mail_smtpPass();

    @AttributeDefinition(description = "E-mail address the notifications appear to be from (optional, defaults to " +
            DEFAULT_SENDER + ")", defaultValue = DEFAULT_SENDER)
    String mail_from() default DEFAULT_SENDER;

    @AttributeDefinition(description = "E-mail address(es) to send notifications to (required)")
    String mail_to();

    @AttributeDefinition(description = "Classpath resource or file:/// URI containing the Velocity success email " +
            "template (optional, defaults to " + DEFAULT_SUCCESS_TEMPLATE + ")",
            defaultValue = DEFAULT_SUCCESS_TEMPLATE)
    String mail_template() default DEFAULT_SUCCESS_TEMPLATE;

    @AttributeDefinition(description = "Subject line of a successful notification (optional, defaults to " + DEFAULT_SUCCESS_NOTIFICATION_SUBJECT + ")",
            defaultValue = DEFAULT_SUCCESS_NOTIFICATION_SUBJECT)
    String mail_subjectSuccess() default DEFAULT_SUCCESS_NOTIFICATION_SUBJECT;

    @AttributeDefinition(description = "Subject line of a failure notification (optional, defaults to " + DEFAULT_FAILURE_NOTIFICATION_SUBJECT + ")",
            defaultValue = DEFAULT_FAILURE_NOTIFICATION_SUBJECT)
    String mail_subjectFailure() default DEFAULT_FAILURE_NOTIFICATION_SUBJECT;

    @AttributeDefinition(description = "Debug the underlying mail provider (optional, defaults to " + DEFAULT_DEBUG + ")")
    String mail_debug() default DEFAULT_DEBUG;
    
}