
package org.dataconservancy.packaging.ingest;

import java.util.Collection;

public interface LdpPackageAnalyzer<T> {

    public Collection<LdpResource> getContainerRoots(T pkg);

    void cleanUpExtractionDirectory();

}
