
## Manual Install

* Go to toplevel package-ingest/ and `mvn clean install` so that the latest build is in your local
maven repo
* [Download](https://karaf.apache.org/index/community/download.html) and unzip apache karaf if you haven't done so
* Launch apache karaf, `${karaf.home}/bin/karaf`
* At the Karaf console:
    * `feature:repo-add mvn:org.dataconservancy.packaging/package-ingest-karaf/LATEST/xml/features`
    * `feature:install package-ingest`
    
## Configuring
* Create the following files in `${karaf.home}/etc`.  These define some global configuration settings.  The easiest way to do this is to just copy from `package-ingest-integration/src/test/resources/cfg` and edit as appropriate
    * `org.dataconservancy.packaging.impl.PackageFileAnalyzer.cfg`
    * `org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications.cfg`
    * `org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.cfg`
* Create as many deposit workflow configurations as desired.  Each configuration can be used to specify a directory to watch for packages, as well as a Fedora container to deposit into.  Give each one a unique [name]
    * `org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow-[name].cfg`