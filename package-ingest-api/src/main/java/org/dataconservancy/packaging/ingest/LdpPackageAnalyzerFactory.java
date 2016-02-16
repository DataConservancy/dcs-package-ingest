
package org.dataconservancy.packaging.ingest;

public interface LdpPackageAnalyzerFactory<T> {

    LdpPackageAnalyzer<T> newAnalyzer();
}
