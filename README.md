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
[![Build Status](https://travis-ci.org/DataConservancy/dcs-package-ingest.svg?branch=master)](https://travis-ci.org/DataConservancy/dcs-package-ingest)

This is a description of the package ingest service

## Usage ##
### Prerequisites ###
 - Oracle JDK 8
 - Apache Karaf 4.0+
 
### Operation
The simple package ingest service will monitor specified directories for the presence of 
[Data conservancy packages](http://dataconservancy.github.io/dc-packaging-spec/dc-packaging-spec-1.0.html). 
When packages are found, their contents are [ingested into a Fedora repository](https://docs.google.com/document/d/1709hcmO_lxUqDvCYKQuJ3XUvaywQFSxFGkt98EWvGRs).  This package ingest service
places additional semantics on Data Conservancy packages which influence how artifacts within them are ingested into Fedora.  In particular, it looks for additional statements in the [resource manifest file](http://dataconservancy.github.io/dc-packaging-spec/dc-packaging-spec-1.0.html#a3.2.3):
* Any resource `R` that is the subject of a statement `<R> <rdf:type> <ldp:Container>` will be created as a Container in Fedora
* For resources `R` and `C`, a statement `<R> <ldp:Contains> <C>` will result in `C` being ingested as member of the 
corresponding `R` container in Fedora.
* For any resource `R` that logically describes a resource `B`, and `B` is a binary resource (i.e not a domain object), 
presence of the statement `<R> <iana:describes> <B>` will result in B deposited as a NonRDFSource in Fedora, with the contents of `R` placed into the associated LDP-RS automatically created by Fedora as described in [§5.2.3.12](https://www.w3.org/TR/ldp/#h-ldpc-post-createbinlinkmetahdr) of the LDP specification.

Addditionally, Fedora places its own restrictions on object content, such as a restriction that all triples in an object MUST have the same subject, inless that subject is a blank node.  All domain objects (indicated by `ore:aggregates` in the manifest file) will be ingested as Fedora objects, and therefore subject so such restrictions.
 
### Installation in Karaf ###
See [Karaf install instructions](package-ingest-karaf/README.md).  

### Configuration ###
See [Karaf configuration instructions](package-ingest-karaf/README.md#Configuration)


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
