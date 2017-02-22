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


This package ingest service is intended transfer the contents of file archives (i.e. “packages”) into an LDP linked data repository such as Fedora.  It includes:
  * A core library in Java for ingesting packages of various formats
  * A Simple HTTP API 
  * An API-X extension for exposing deposit endpoints on repository containers.
  
# Premise
An archive contains custodial content (i.e. packaged files), and possibly additional packaging-specific metadata.  A _profile_ defines how these are distinguished.  For example, it can be presumed that all content of a simple zip or tar file is custodial content.  [BagIt](https://tools.ietf.org/html/draft-kunze-bagit-14) defines custodial content as all files underneath a `/data` directory, and specifies additional “tag files” which may describe the circumstances of creating a bag (its author, date, etc), checksums for files, etc.  

The package ingest service creates a repository resource (an [LDPR](http://www.w3.org/TR/ldp/#ldpr)) from each file in the custodial content of a package.  

Additional processing rules may apply for each supported profile which may enhance the contents of LDPRs (e.g. add metadata), or create additional LDPRs.  For example, If the package relates its resources into an LDP containment or membership hierarchy, the packaging profile may provide a way to encode this information, if this information is not otherwise present within the resources in the package

The original package may be discarded, or may be kept as part of an audit trail, used for authorization, etc. based upon policy.   At minimum, the package ingest service will provide a log of all events that occurred during ingest.

If ingesting a package succeeds, further interaction with the newly created resources may be performed as usual via Fedora’s LDP-based API. 

# Goals
  * Accommodate arbitrarily large packages with stream-oriented processing
  * Allow the use of using simple command-line tools to deposit and verify success/failure (e.g curl, grep, etc)
  * Accommodate backend workflows and policies
  * Support synchronous and asynchronous paradigms in exposed APIs

# Workflow

1. Produce a package.  For example
  * Zipping up a file system
  * Export from a repository
  * Generating resources by some local process (e.g. a desktop GUI, laboratory instrument, etc)

2. Choose a [container](http://www.w3.org/TR/ldp/#ldpc) in the repository to deposit into (an LDPC, identified by its URI)
  * No specific discovery mechanism is defined; it is presumed that a client can inspect repository resources and pick one to deposit into, or is given a URI for this purpose.

3. Submit the package to the container.
  * A new member resource will be created, and contents of package placed into it 

4. Follow the deposit results.
  * An event stream indicates processing as it happens, and indicates success or failure


