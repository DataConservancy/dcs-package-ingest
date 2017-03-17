# Docker image

The docker image for the package ingest extension contains Java, and the package ingest extension's executable jar. 
It is presumed that API-X is running.  It will attempt to self-register as an extension/service instance at the URI of the
API-X instance provided in the `REPOSITORY_BASEURI` environment variable.

## Building

The package ingest docker image automatically builds as part of the maven build process, if docker is installed locally

    mvn clean install
    
If docker is not installed locally, this step is simply skipped.  It creates an image named 
`dataconservancy/ext-package-ingest:${project.version}`.  That is to say, with repository `dataconservancy/ext-package-ingest`
and tagged with the current version of the project in Maven.  

## Configuration

The image is configured via environment variables.

### `REPOSITORY_BASEURI`

*Required.*  This is the URI of the root repository resource via API-X.  The extension will inspect the service
document for this resource, find the URI of the extension loader service, and self-register.

### `PACKAGE_INGEST_PORT`

Optional.  This controls the local port that the package ingest service runs on.  Used in the context of API-X, it
is typically not exposed to end users.  Default is `32080`

### `LOG.*`

Optional.  Any environment variable that begins with `LOG.` can be used to specify the logging level of 
the logger whose name appears after the `LOG.` characters.  For example, setting the environment variable:

    LOG.org.dataconservancy=DEBUG
    
 This will set the logger called `org.dataconservancy` to the `DEBUG` level.  
