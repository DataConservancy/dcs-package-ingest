
package org.dataconservancy.packaging.impl;

import java.io.File;

import java.util.Collection;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

@Component(service = LdpPackageAnalyzer.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class PackageFileAnalyzer
        implements LdpPackageAnalyzer<File> {

    @Override
    public Collection<LdpResource> getContainerRoots(File pkg) {
        // TODO Auto-generated method stub
        return null;
    }

}
