
package org.dataconservancy.packaging.ingest.camel;

import org.apache.camel.RoutesBuilder;

public interface NotificationDriver
        extends RoutesBuilder {

    public static final String ROUTE_NOTIFICATION_SUCCESS =
            "direct:notification_success";

    public static final String ROUTE_NOTIFICATION_FAIL =
            "direct:notification_failure";

}
