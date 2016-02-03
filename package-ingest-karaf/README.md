
## Manual Install ##

* (This step is required only before the release, since we won't have artifacts in the maven repository yet.) Go to the toplevel package-ingest/ and do `mvn clean install` so that the latest build is put into your local
maven repository
* [Download](https://karaf.apache.org/index/community/download.html) and unzip apache karaf 4.0+ if you haven't done so
* Launch apache karaf, `${karaf.home}/bin/karaf`
* At the Karaf console:
    * `feature:repo-add mvn:org.dataconservancy.packaging/package-ingest-karaf/LATEST/xml/features`
    * `feature:install package-ingest-karaf`
    
## Configuration ##

### Manual (text file) configuration ###
* Create the following files in `${karaf.home}/etc`.  These define some global configuration settings.  The easiest way to do this is to just copy from `package-ingest-integration/src/test/resources/cfg` and edit as appropriate
    * `org.dataconservancy.packaging.impl.PackageFileAnalyzer.cfg`
    * `org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications.cfg`
    * `org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.cfg`
* Create as many deposit workflow configurations as desired.  Each configuration can be used to specify a directory to watch for packages, as well as a Fedora container to deposit into.  Give each one a unique [name]
    * `org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow-[name].cfg`
    
### Karaf Web Console Configuration ###
* Install the [karaf web console](http://karaf.apache.org/manual/latest/users-guide/webconsole.html) feature from the karaf command line
    * `feature:install webconsole`
    * Configure for security/port as necessary.  Default user/pass is karaf:karaf, port 8181: [http://localhost:8181/system/console](http://localhost:8181/system/console)
* Open the OSGI configuration manager page on your browser
    * Directly go to [http://localhost:8181/system/console/configMgr](http://localhost:8181/system/console/configMgr), or navigate `OSGi -> Configuration`
* Scroll down to find the DCS package ingest components (org.dataconservancy.packaging.ingest.*)
* For all that do not have a check mark next to them, click the edit button and fill in the fields.  Do this for:
    * `org.dataconservancy.packaging.impl.PackageFileAnalyzer`
    * `org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver`
    * `org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications`
* Create as many deposit workflow configurations as desired.  Each configuration can be used to specify a directory to watch for packages, as well as a Fedora container to deposit into.  To do this, click the `+` button next to `org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow` and fill in the fields to add a configuration.  You can add or remove as many as you wish.

## Verification ##
To verify that services are running, do `scr:list` on the command line, or go to the web console `OSGi -> Components`: [http://localhost:8181/system/console/components](http://localhost:8181/system/console/components).  Make sure everything you configured has an `active` state.
