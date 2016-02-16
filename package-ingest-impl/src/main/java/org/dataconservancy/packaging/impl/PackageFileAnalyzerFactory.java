
package org.dataconservancy.packaging.impl;

import java.io.File;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Designate(ocd = PackageFileAnalyzerFactoryConfig.class)
@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class PackageFileAnalyzerFactory
        implements LdpPackageAnalyzerFactory<File> {

    private File extractBaseDir;

    @Activate
    @Modified
    public void init(PackageFileAnalyzerFactoryConfig config) {
        extractBaseDir = new File(config.package_extract_dir());
        extractBaseDir.mkdirs();
    }

    @Override
    public LdpPackageAnalyzer<File> newAnalyzer() {
        return new PackageFileAnalyzer(new OpenPackageService(),
                                       extractBaseDir);
    }

}
