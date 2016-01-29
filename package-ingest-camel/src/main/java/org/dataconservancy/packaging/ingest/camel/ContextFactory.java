
package org.dataconservancy.packaging.ingest.camel;

import org.apache.camel.CamelContext;

/**
 * Produces new camel contexts
 * <p>
 * The underlying implementation is responsible for assuring that the identities
 * of all created contexts are available from each other's registry.
 * </p>
 */
public interface ContextFactory {

    public CamelContext newContext(String id);

    /**
     * Creates a new, empty CamelContext.
     * 
     * @return A new CamelContext.
     */
    public default CamelContext newContext() {
        return newContext(null);
    }
}