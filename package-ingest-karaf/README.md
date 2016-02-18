
## Install ##

* [Download](https://karaf.apache.org/index/community/download.html) and unzip apache karaf 4.0+ if you haven't done so
* Launch apache karaf, `${karaf.home}/bin/karaf`

This starts an empty Karaf.  Now the package ingest application needs to be installed.  Install via `.kar` file, or features file in maven:

### Via .kar file ###
A karaf `.kar` file contains embedded dependencies and features files, and is deployed by placing it into
Karaf's deploy directory.

* Grab the package ingest .kar file from the INSERT LINK git release page, or from a local maven build `package-ingest-karaf/target/package-ingest-karaf-${version}.kar`
* Place the kar file into `${karaf.home}/deploy`

### Via features file in Maven ###
A features file tells Karaf where to download the package ingest application and its dependencies.  Karaf can load any features file that has been released to Maven:

* (This first step is required only before the release, since we won't have artifacts in the maven repository yet.) Go to the toplevel package-ingest/ and do `mvn clean install` so that the latest build is put into your local
maven repository
* At the Karaf console:
    * `feature:repo-add mvn:org.dataconservancy.packaging/package-ingest-karaf/LATEST/xml/features`
    * `feature:install package-ingest-karaf`
    
## Configuration ##

### Manual (text file) configuration ###
* Create the following files in `${karaf.home}/etc`.  These define some global configuration settings.  The easiest way to do this is to just copy from `package-ingest-integration/src/test/resources/cfg` and edit as appropriate
    * `org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory.cfg`
    * `org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications.cfg`
    * `org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver.cfg`
* Create as many deposit workflow configurations as desired.  Each configuration can be used to specify a directory to watch for packages, as well as a Fedora container to deposit into.  Give each one a unique [name]. Sample config file for workflow can also be copied from `package-ingest-integration/src/test/resources/fgc`. 
    * `org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow-[name].cfg`

(When editing these sample config files, replace the values placeholders with real values. For `PackageFileDepositWorkflow-[name].cfg` file, `deposit.location` should be the URI of the Fedora container to which the package's content is to be deposited.)

### Karaf Web Console Configuration ###
* Install the [karaf web console](http://karaf.apache.org/manual/latest/users-guide/webconsole.html) feature from the karaf command line
    * `feature:install webconsole`
    * Configure for security/port as necessary.  Default user/pass is karaf:karaf, port 8181: [http://localhost:8181/system/console](http://localhost:8181/system/console)
* Open the OSGI configuration manager page on your browser
    * Directly go to [http://localhost:8181/system/console/configMgr](http://localhost:8181/system/console/configMgr), or navigate `OSGi -> Configuration`
* Scroll down to find the DCS package ingest components (org.dataconservancy.packaging.ingest.*)
* For all that do not have a check mark next to them, click the edit button and fill in the fields.  Do this for:
    * `org.dataconservancy.packaging.impl.PackageFileAnalyzerFactory`
    * `org.dataconservancy.packaging.ingest.camel.impl.FedoraDepositDriver`
    * `org.dataconservancy.packaging.ingest.camel.impl.EmailNotifications`
* Create as many deposit workflow configurations as desired.  Each configuration can be used to specify a directory to watch for packages, as well as a Fedora container to deposit into.  To do this, click the `+` button next to `org.dataconservancy.packaging.ingest.camel.impl.PackageFileDepositWorkflow` and fill in the fields to add a configuration.  You can add or remove as many as you wish.

### Interaction Between the Two Methods ###
Testing suggests the following behavior occurs: When configuring via the Web Console, changes to configurations are saved in the appropriate bundle configuration deep in the in the `{karaf.home}/data/cache` directory. These cached configuration values will be used upon subsequent starts, unless manually configured files are present in `${karaf.home}/etc` - in this case, the values defined in the `${karaf.home}/etc` files will be used upon startup, and these values will be written to the cached configurations. Because it may be possible to lose updates to configurations if the two methods are mixed, it is recommended that only one method be used. If you are using the file method of configuration by placing configuration files in `{karaf.home}/etc`, then configuration changes should be done by editing these configuration files, and not through the Web Console.

## Verification ##
To verify that services are running, do `scr:list` on the command line, or go to the web console `OSGi -> Components`: [http://localhost:8181/system/console/components](http://localhost:8181/system/console/components).  Make sure everything you configured has an `active` state.
