/*
 * Copyright 2016 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.dataconservancy.packaging.ingest.PackageAnalyzer;
import org.dataconservancy.packaging.ingest.PackageAnalyzerFactory;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Creates package file analyzers
 *
 * @author apb@jhu.edu
 */

@ObjectClassDefinition(name = "org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory",
        description = "Unpacks and analyzes package files for ingest")
@interface PackageFileAnalyzerFactoryConfig {

    @AttributeDefinition(description = "Directory for temporary unpacking package contents as necessary")
    String package_extract_dir();
}

@Designate(ocd = PackageFileAnalyzerFactoryConfig.class)
@Component(configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true)
public class DcsPackageAnalyzerFactory
        implements PackageAnalyzerFactory {

    private File extractBaseDir;

    /**
     * Set the extraction dir.
     *
     * @param dir Directory path.
     */
    public void setExtractDir(final String dir) {
        this.extractBaseDir = new File(dir);
        extractBaseDir.mkdirs();
    }

    /**
     * Initialize.
     *
     * @param config OSGi DS-style configuration
     */
    @Activate
    @Modified
    public void init(final PackageFileAnalyzerFactoryConfig config) {
        setExtractDir(config.package_extract_dir());
    }

    @Override
    public PackageAnalyzer newAnalyzer() {

        // Reasonablbe default in case it's not set
        if (extractBaseDir == null) {
            synchronized (this) {
                try {
                    extractBaseDir = Files.createTempDirectory("extractorStaging").toFile();
                } catch (final IOException e) {
                    throw new RuntimeException("Could not create temporary extraction directory", e);
                }
            }
        }
        return new DcsPackageAnalyzer(new OpenPackageService(),
                extractBaseDir);
    }

}
