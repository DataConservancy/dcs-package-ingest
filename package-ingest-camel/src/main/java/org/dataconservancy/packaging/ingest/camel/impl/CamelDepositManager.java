
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

@Component(service = DepositManager.class, immediate = true)
public class CamelDepositManager implements DepositManager {

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

	@Reference(cardinality = ReferenceCardinality.MULTIPLE)
	public void addDepositWorkflow(DepositWorkflow workflow, Map<String, Object> props) {
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
		active.set(true);
		while (pendingWorkflows.peek() != null) {
			initDepositWorkflow(pendingWorkflows.remove());
		}
	}

	public void initDepositWorkflow(WorkflowConfiguration wf) {
		if (active.get()) {
			try {
				SimpleRegistry registry = new SimpleRegistry();
				CamelContext context = cxtFactory.newContext("", registry);

				Properties p = new Properties(globalProperties);
				updateProperties(p, wf.props);
				registry.put("props", p);

				PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
				pc.setLocation("ref:props");

				contexts.put(wf.routes, context);

				notification.addRoutesToCamelContext(context);
				deposit.addRoutesToCamelContext(context);
				wf.routes.addRoutesToCamelContext(context);

				context.start();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			pendingWorkflows.add(wf);
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
		props.putAll(map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.toString())));
	}
}
