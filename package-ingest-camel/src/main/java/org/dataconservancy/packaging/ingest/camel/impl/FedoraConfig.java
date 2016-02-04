package org.dataconservancy.packaging.ingest.camel.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver", description = "Transactional Fedora LDP Deposit Driver")
public @interface FedoraConfig {

    @AttributeDefinition(description = "Fedora base URI.  Points to the 'root' container in Fedora")
    String fedora_baseuri() default "http://localhost:8080/fedora/rest";
    
}