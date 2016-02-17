package org.dataconservancy.packaging.ingest.camel.impl;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.collections.map.HashedMap;
import org.dataconservancy.packaging.ingest.camel.impl.config.EmailNotificationsConfig;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_FAIL;
import static org.dataconservancy.packaging.ingest.camel.NotificationDriver.ROUTE_NOTIFICATION_SUCCESS;

/**
 * Created by esm on 2/12/16.
 */
public class EmailNotificationsTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationsTest.class);

    private EmailNotifications notifications;

    @Produce
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:out")
    private MockEndpoint mockOut = new MockEndpoint();

    @Test
    public void testNotificationSuccess() throws Exception {
        final String packageSourceUri = "file:///path/to/package.tar.gz";
        final List<String> resourceLocations = Arrays.asList("http://deposit/1", "http://deposit/2");

        template.sendBodyAndHeaders("direct:testNotificationSuccess",
                null,
                new HashMap<String, Object>() {
                    {
                        put(PackageFileDepositWorkflow.HEADER_RESOURCE_LOCATIONS, resourceLocations);
                        put(PackageFileDepositWorkflow.PACKAGE_SOURCE_URI, packageSourceUri);
                    }
                });

        mockOut.setExpectedCount(1);
        assertMockEndpointsSatisfied();

        final Message message = mockOut.getExchanges().get(0).getIn();
        assertNotNull(message.getHeader("subject"));
        assertEquals("Deposit Success", message.getHeader("subject"));
        final String messageBody = message.getBody().toString();

        assertFalse(messageBody.contains(PackageFileDepositWorkflow.HEADER_RESOURCE_LOCATIONS));
        assertFalse(messageBody.contains(PackageFileDepositWorkflow.PACKAGE_SOURCE_URI));
        assertTrue(messageBody.contains(packageSourceUri));
        assertTrue(messageBody.contains(resourceLocations.get(0)));
        assertTrue(messageBody.contains(resourceLocations.get(1)));
    }


    @Test
    public void testNotificationFail() throws Exception {
        DefaultExchange ex = new DefaultExchange(context());
        DefaultMessage in = new DefaultMessage();
        ex.setPattern(ExchangePattern.InOnly);
        ex.setIn(in);
        String failureMessage = "Reason for deposit failure:";
        Exception failureException = new Exception(failureMessage);

        ex.setProperty(Exchange.EXCEPTION_CAUGHT, failureException);
        in.setHeader(PackageFileDepositWorkflow.PACKAGE_SOURCE_URI, "file:///path/to/package.tar.gz");


        template.send("direct:testNotificationFail", ex);

        mockOut.setExpectedCount(1);
        assertMockEndpointsSatisfied();

        final Message message = mockOut.getExchanges().get(0).getIn();
        assertNotNull(message.getHeader("subject"));
        assertEquals("Deposit Failure", message.getHeader("subject"));

        final String messageBody = message.getBody().toString();
        assertTrue(messageBody.contains(failureMessage));
        assertFalse(messageBody.contains(PackageFileDepositWorkflow.HEADER_RESOURCE_LOCATIONS));
        assertFalse(messageBody.contains(PackageFileDepositWorkflow.PACKAGE_SOURCE_URI));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        notifications = new EmailNotifications();

        setUpProperties();
        setUpRegistry();

        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {

                notifications.addRoutesToCamelContext(context());

                from("direct:testNotificationSuccess")
                        .to(ROUTE_NOTIFICATION_SUCCESS)
                        .to("mock:out");

                from("direct:testNotificationFail")
                        .to(ROUTE_NOTIFICATION_FAIL)
                        .to("mock:out");
            }
        };
    }

    private void setUpProperties() {
        PropertiesComponent pc = context().getComponent("properties", PropertiesComponent.class);
        pc.setLocation("classpath:org/dataconservancy/packaging/ingest/camel/impl/EmailNotificationsConfig.properties");
    }

    private void setUpRegistry() {
        SSLContextParameters scp = new SSLContextParameters();
        Registry registry = unwrap(context().getRegistry());
        if (registry instanceof JndiRegistry) {
            ((JndiRegistry)registry).bind("sslContextParameters", scp);
        } else if (registry instanceof SimpleRegistry) {
            ((SimpleRegistry)registry).put("sslContextParameters", scp);
        }
    }

    private Registry unwrap(Registry registry) {
        if (registry instanceof PropertyPlaceholderDelegateRegistry) {
            return unwrap((
                    (PropertyPlaceholderDelegateRegistry) context().getRegistry()).getRegistry());
        }

        return registry;
    }
}