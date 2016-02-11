
package org.dataconservancy.packaging.ingest.camel;

import org.apache.camel.RoutesBuilder;

public interface DepositWorkflow
        extends RoutesBuilder {

    public static final String HEADER_RESOURCE_LOCATIONS = "deposit.locations";

    public static final String HEADER_PROVENANCE_LOCATION =
            "deposit.provenance.location";

}
