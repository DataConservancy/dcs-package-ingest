
package org.dataconservancy.packaging.ingest.camel.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;

import org.dataconservancy.packaging.ingest.camel.ContextFactory;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.dataconservancy.packaging.ingest.camel.DepositManager;
import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

@Component(service = DepositManager.class, immediate = true)
public class CamelDepositManager
        implements DepositManager {

    private DepositDriver deposit;

    private NotificationDriver notification;

    private ContextFactory cxtFactory;

    private Map<DepositWorkflow, CamelContext> contexts =
            new ConcurrentHashMap<>();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setContextFactory(ContextFactory factory) {
        this.cxtFactory = factory;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setDepositDriver(DepositDriver driver) {
        this.deposit = driver;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public void setNotificationDriver(NotificationDriver driver) {
        this.notification = driver;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE)
    public void addDepositWorkflow(DepositWorkflow workflow) {
        CamelContext context = cxtFactory.newContext();
        try {
            notification.addRoutesToCamelContext(context);
            deposit.addRoutesToCamelContext(context);
            workflow.addRoutesToCamelContext(context);

            context.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void removeDepositWorkflow(DepositWorkflow workflow) {
        CamelContext cxt = contexts.remove(workflow);

        if (cxt != null) {
            try {
                cxt.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Deactivate
    public void shutDown() {
        for (CamelContext cxt : contexts.values()) {
            try {
                cxt.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        contexts.clear();
    }
}
