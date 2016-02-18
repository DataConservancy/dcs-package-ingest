
package org.dataconservancy.packaging.ingest.camel.impl;

import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.util.jsse.SSLContextParameters;

import org.dataconservancy.packaging.ingest.camel.ContextFactory;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.dataconservancy.packaging.ingest.camel.DepositManager;
import org.dataconservancy.packaging.ingest.camel.DepositWorkflow;
import org.dataconservancy.packaging.ingest.camel.NotificationDriver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DepositManager.class, immediate = true)
public class CamelDepositManager implements DepositManager {

    private static final Logger LOG = LoggerFactory.getLogger(CamelDepositManager.class);

	Properties globalProperties = new Properties();

	private DepositDriver deposit;

	private NotificationDriver notification;

	private ContextFactory cxtFactory;

	private Map<DepositWorkflow, CamelContext> contexts = new ConcurrentHashMap<>();

	private Queue<WorkflowConfiguration> pendingWorkflows = new ConcurrentLinkedQueue<>();

	private AtomicBoolean active = new AtomicBoolean(false);

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	public void setContextFactory(ContextFactory factory) {
		this.cxtFactory = factory;
	}

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	public void setDepositDriver(DepositDriver driver, Map<String, Object> props) {
		updateProperties(globalProperties, props);
		this.deposit = driver;
	}

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	public void setNotificationDriver(NotificationDriver driver, Map<String, Object> props) {
        updateProperties(globalProperties, props);
        this.notification = driver;
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption=ReferencePolicyOption.GREEDY)
	public void addDepositWorkflow(DepositWorkflow workflow, Map<String, Object> props) {
	    LOG.debug("Request to add new deposit workflow");
		initDepositWorkflow(new WorkflowConfiguration(workflow, props));
	}

	public void updatedContextInfo(DepositWorkflow wf, Map<String, Object> props) {
		removeDepositWorkflow(wf);
		initDepositWorkflow(new WorkflowConfiguration(wf, props));
	}

	public void removeContextWorkflow(DepositWorkflow wf) {
		removeDepositWorkflow(wf);
	}

	@Activate
	public void init() {
        LOG.debug("Initializing Data Conservancy Package Ingest Framework...");
        int count = 0;
		active.set(true);
		while (pendingWorkflows.peek() != null) {
			initDepositWorkflow(pendingWorkflows.remove());
            count++;
		}
        LOG.info("Data Conservancy Package Ingest Framework initialized: created {} deposit workflow(s)", count);
	}

	public void initDepositWorkflow(WorkflowConfiguration wf) {
		if (active.get()) {
            LOG.debug("--> Initializing deposit workflow: {}", wf);
			try {
				SimpleRegistry registry = new SimpleRegistry();
                configureSslContext(registry);

				CamelContext context = cxtFactory.newContext("", registry);

				Properties p = copy(globalProperties);

                updateProperties(p, wf.props);

				registry.put("props", p);

				PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
				pc.setLocation("ref:props");

                contexts.put(wf.routes, context);

                notification.addRoutesToCamelContext(context);
				deposit.addRoutesToCamelContext(context);
				wf.routes.addRoutesToCamelContext(context);

                LOG.debug("--> Starting Camel Context {} for workflow.", context.getName());
                context.start();
                LOG.debug("--> Initialized deposit workflow: {}", wf);
			} catch (Exception e) {
                LOG.warn("--> Error configuring deposit workflow {}: {}", wf, e);
                throw new RuntimeException(e);
			}
		} else {
            LOG.debug("--> Adding pending package deposit workflow: {}", wf);
            pendingWorkflows.add(wf);
		}
	}

	public void removeDepositWorkflow(DepositWorkflow workflow) {
	    LOG.debug("Request to removve deposit workflow");
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
		contexts.values().forEach(cxt -> {
			try {
				cxt.stop();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		contexts.clear();
	}

	class WorkflowConfiguration {
		DepositWorkflow routes;
		Map<String, Object> props;

		public WorkflowConfiguration(DepositWorkflow routes, Map<String, Object> props) {
			this.routes = routes;
			this.props = props;
		}
	}

	static void updateProperties(Properties props, Map<String, Object> map) {
		props.putAll(map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString())));
	}

	void configureSslContext(SimpleRegistry registry) {
        // TODO make this set up configurable via OSGi
//        KeyStoreParameters ksp = new KeyStoreParameters();
//        ksp.setResource("/users/home/server/truststore.jks");
//        ksp.setPassword("keystorePassword");
//        TrustManagersParameters tmp = new TrustManagersParameters();
//        tmp.setKeyStore(ksp);
        SSLContextParameters scp = new SSLContextParameters();
//        scp.setTrustManagers(tmp);
        registry.put("sslContextParameters", scp);
    }

    Properties copy(Properties props) {
        Properties copy = new Properties();
        copy.putAll(props);
        return copy;
    }
}
