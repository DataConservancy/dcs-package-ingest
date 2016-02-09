package org.dataconservancy.packaging.ingest.camel.impl;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.SimpleRegistry;
import org.dataconservancy.packaging.ingest.camel.ContextFactory;
import org.dataconservancy.packaging.ingest.camel.ContextRoutes;
import org.dataconservancy.packaging.ingest.camel.ContextRunner;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class DemoContextRunner implements ContextRunner {
	SimpleRegistry registry = new SimpleRegistry();

	Properties properties;

	CamelContext context;
	ContextRoutes routes;

	ContextFactory faxtory;

	@Reference
	public void setContextFactory(ContextFactory factory) {
		this.faxtory = factory;
	}

	@Reference
	public void setRoutes(ContextRoutes routes, Map<String, Object> props) {
		System.out.println(routes + " has been set");
		addProps(props);
		this.routes = routes;
	}

	public void updatedRoutes(ContextRoutes routes, Map<String, Object> props) {
		System.out.println(routes + " has been updated");
		addProps(props);
	}

	@Activate
	public void init(Map<String, Object> props) throws Exception {
		System.out.println(this.getClass().getName() + " Activated!");
		
		registry.put("props", properties);
		addProps(props);

		context = faxtory.newContext("TEST", registry);

		PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
		pc.setLocation("ref:props");

		routes.addRoutesToCamelContext(context);
		context.start();

	}

	@Deactivate
	public void stop() throws Exception {
		System.out.println(this.getClass().getName() + " Deactivated!");
		context.stop();
	}

	@Override
	public CamelContext getContext() {
		return context;
	}

	void addProps(Map<String, Object> props) {
		props.forEach((k, v) -> properties.setProperty(k, v.toString()));
	}

}
