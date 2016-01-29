
package org.dataconservancy.packaging.ingest.camel;

import org.apache.camel.RoutesBuilder;

public interface DepositDriver
        extends RoutesBuilder {

    public static final String ROUTE_TRANSACTION_BEGIN =
            "direct:transaction_begin";

    public static final String ROUTE_TRANSACTION_CANONICALIZE =
            "direct:transaction_canonicalize";

    public static final String ROUTE_TRANSACTION_COMMIT =
            "direct:transaction_commit";

    public static final String ROUTE_TRANSACTION_ROLLBACK =
            "direct:transaction_rollback";

    public static final String ROUTE_DEPOSIT_PROVENANCE =
            "direct:deposit_provenance";

    public static final String ROUTE_DEPOSIT_RESOURCES =
            "direct:deposit_resources";
    
}
