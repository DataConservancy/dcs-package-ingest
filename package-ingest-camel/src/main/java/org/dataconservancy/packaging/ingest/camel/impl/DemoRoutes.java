package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.dataconservancy.packaging.ingest.camel.ContextRoutes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
@interface DemoRoutesConfig {
	@AttributeDefinition(name = "the number", description = "The number to display")
	int the_number() default 42;

	@AttributeDefinition(name = "Listen Directory", description = "Directory to listen")
	String listen_dir() default "/tmp/listen";

	@AttributeDefinition(name = "the list", description = "A list of crap")
	String[] multiple_strings();
}

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
@Designate(ocd = DemoRoutesConfig.class, factory = true)
public class DemoRoutes extends RouteBuilder implements ContextRoutes {

	DemoRoutesConfig config;

	@Activate
	public void init(DemoRoutesConfig config) {
		this.config = config;
		new File(config.listen_dir()).mkdirs();
		System.out.println(" Activated demo routes! " + config.the_number());
	}

	@Modified
	public void updateConfig(DemoRoutesConfig config) {
		new File(config.listen_dir()).mkdirs();
		this.config = config;
		System.out.println("Demo route has been updated");
	}

	@Deactivate
	public void stop() {
		System.out.println("Demo route has been deactivated");
	}

	@Override
	public void configure() throws Exception {
		from("file:{{listen.dir}}").setHeader("PropNumberSimple", simple("${properties:the.number}"))
				.setHeader("PropNumberConstant", constant(config.the_number()))
				.process(e -> e.getIn().setHeader("PropNumberProcessor", config.the_number()))
				.process(e -> e.getIn().getHeaders().forEach((k, v) -> System.out.println(k + ": " + v)));

	}

}
