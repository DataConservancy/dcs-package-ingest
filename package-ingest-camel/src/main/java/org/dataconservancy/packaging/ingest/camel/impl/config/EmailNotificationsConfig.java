package org.dataconservancy.packaging.ingest.camel.impl.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.EmailNotificationsConfig", description = "E-mail notification config")
public @interface EmailNotificationsConfig {

    @AttributeDefinition(description = "E-mail address(es) to send notifications to")
    String mail_to();

    @AttributeDefinition(description = "Outgoing smtp server")
    String mail_server();

    @AttributeDefinition(description = "smtp port")
    String mail_port();

    @AttributeDefinition(description = "Username")
    String mail_user();

    @AttributeDefinition(description = "Password")
    String mail_password();
}