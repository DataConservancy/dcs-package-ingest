<!--
Copyright 2016 Johns Hopkins University

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
# Simple Data Conservancy Package Ingest Service #

This is a description of the package ingest service

## Usage ##
### Prerequisites ###
 - Oracle JDK 8
 - Apache Karaf
 
### Installation in Karaf ###
See [Karaf install instructions](package-ingest-karaf/README.md).  


## Development ##

### Integration testing ###
Integration tests launch an instance of Fedora 4 for the entire (failsafe) integration test lifecycle duration.  To run all integration tests, simply do 
`mvn verify`

 The base URI for this instance is available to integration tests via the system property `fedora.baseURI`.  

The default port is 8080, but this can be changed via specifying a system property
`mvn -Dfedora.port=8090 ...`



### Running Fedora standalone ###
The embedded Fedora instance used for testing can be run manually via
`mvn cargo:run`

It may be run on a different port via
`mvn -Dfedora.port=8090 cargo:run`

The Fedora data directory is placed in `target`, so repository contents from the last run of tests (or manual changes) will be persisted until `mvn clean` is run.
