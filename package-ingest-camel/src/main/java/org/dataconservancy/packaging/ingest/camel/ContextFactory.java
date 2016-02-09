
package org.dataconservancy.packaging.ingest.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;

/**
 * Produces new camel contexts
 * <p>
 * The underlying implementation is responsible for assuring that the identities
 * of all created contexts are available from each other's registry.
 * </p>
 */
public interface ContextFactory {

    public default CamelContext newContext(String id) {
    	return newContext(id, null);
    }
    
    public CamelContext newContext(String id, Registry registry);

    /**
     * Creates a new, empty CamelContext.
     * 
     * @return A new CamelContext.
     */
    public default CamelContext newContext() {
        return newContext(null, null);
    }
}