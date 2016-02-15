
package org.dataconservancy.packaging.ingest;

import java.util.Map;

public interface LdpPackageProvenanceGenerator<T> {

    LdpResource generatePackageProvenance(T pkg,
                                          Map<String, String> uriMapping);

}
