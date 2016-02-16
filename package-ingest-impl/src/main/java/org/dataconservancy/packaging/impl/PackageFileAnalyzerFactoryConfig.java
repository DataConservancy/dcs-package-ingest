
package org.dataconservancy.packaging.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory", description = "Unpacks and analyzes package files for ingest")
public @interface PackageFileAnalyzerFactoryConfig {

    @AttributeDefinition(description = "Directory for temporary unpacking package contents as necessary")
    String package_extract_dir() default "/tmp";
}