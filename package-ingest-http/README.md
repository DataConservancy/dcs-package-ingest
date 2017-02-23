# Package Ingest HTTP API

This describes a simple HTTP API for the Data Conservancy package ingest service.  It adopts a lightweight, minimalist, stream-oriented approach that is usable with basic http and text processing tools (e.g. `curl`, `grep`, etc) even when depositing very large files.

The package ingest API may be used synchronously, or asynchronously.  The difference lies largely in separating the act of depositing a package and examining the status of the deposit process.   

## Synchronous
Synchronous usage involves a single request/response pair.  The request is a POST containing the package in the entity body.  The response is immediate (in most cases, the response begins sending before the entire contents of the package has been uploaded), and contains an [event stream](#event-stream) that terminates when the deposit is complete.  It is full duplex in the sense that the request and response can both be transmitted simultaneously.

The synchronous paradigm is most suited to usage with command line tools.  For example, suppose that you want to deposit the contents a directory and all its descendents from your local machine, and you wish to use a simple zipped tar file as the packaging. 

    tar czf - /path/to/directory | curl -X POST -H “Content-Type application/tgz” --data-binary @- http://path/to/endpoint | do_something_with_result_stream.sh

## Asynchronous
Asynchronous usage involves initiating a deposit via an empty POST.  The server responds with links to a resource used for upload (POSTing the content of the package), and a resource for monitoring the deposit status.  

The asynchronous paradigm is best suited for use in browsers.  A file upload may be initialized, while separately the browser monitors the event stream URI for the deposit.  As we’ll see in the API specification, events are available as `text/event-stream`.  This media type is defined by the [server sent events](http://www.w3.org/TR/eventsource/) specification, which also defines a javascript API for reacting to events.

# API Specification
Interaction through package ingest starts through a _deposit endpoint_.  In general, deposit endpoints are associated with a particular container/collection in a repository.  API-X provides a means of discovery in order to determine whether a given repository container has a deposit endpoint associated with it, and discover its URI.  Discovery is not otherwise addressed by the API specification.

The deposit endpoint MUST be support HTTP/1.1 or above

A _package_ is a file archive of some sort (e.g. a tar file, zip file).  

## Common interaction model
### _deposit endpoint_
#### GET
* The server provides a description of the deposit endpoint, enumerating the kinds of packages (i.e profiles) that are accepted. 
* The server provides a text/html representation which contains an appropriate UI for depositing packages through the browser

## Synchronous Interaction Model
### _deposit endpoint_
#### POST
* A POST request MUST contain an entity body containing the contents of the package.
* If using HTTP/1.1, the client SHOULD use chunked transfer encoding for uploads
  * This will allow the server to start sending the response event stream and start processing the package before the entire package has been transferred
* The server will start sending a response as soon as the first archive entry is successfully processed
  * In cases of a fail-fast error (i.e the file archive is malformed, or the depositor is not authorized), the server will return an appropriate 4xx error
  * If decoding the archive initially succeeds, the server will immediately return a `202 Accepted` response
* The response is of media type `text/event-stream` unless content negotiation mandates otherwise. 
  * Events will be sent on the response connection in realtime.
* If using HTTP/1.1 the response will use a chunked transfer encoding
* The deposit is complete after sending a `success` event in the event stream.

## Asynchronous interaction model
### _deposit endpoint_
#### POST
* A client initializes an asynchronous deposit with empty POST.  The request MUST NOT contain an entity body, nor a `Content-Type` header
* The server returns a `201 Created` response with Location header containing an _opened deposit resource_ uri.

### _opened deposit resource_
#### GET
* GET to an _opened deposit resource_ URI will return an `text/event-stream` unless content negotiation mandates otherwise.  
* If the deposit has completed successfully, the response will be a `303 see other`, pointing to the deposit container
* If the deposit has completed unsuccessfully, the response will be a `410 gone` The server will attempt to return a 410 as long as it can retain information related to the deposit.

#### POST
* A POST request MUST contain an entity body containing the contents of the package.
* If the deposit is successful, the response will be a `201 created`, with a `Location` header pointing to the location of the container
* If the deposit is not successful, a 4xx or 5xx result will be returned.  

## Event Stream
The deposit event stream has a media type`text/event-stream`, as defined in the [Server-Sent Events](http://www.w3.org/TR/eventsource/) specification.  The event stream contains the following events:
* `deposit` - Indicates that single resource from the package has been deposited.  The data associated with this event includes the original URI of the resource in the package, and the URI of the resource as deposited in the repository 
* `remap` - Indicates that local URIs present in a resource have been re-mapped to repository URIs.  For example, if resource A links to resource B, and A is deposited before B; the link will reference the URI of B as present in the package.  A remap of A will replace B’s URI with its corresponding repository resource URI; which is only known after B has been deposited.
* `error` - The deposit process has failed, and no resources have been persisted in the repository.  The data associated with this event contains an explanation of the failure in human readable text. 
* `success` - The deposit has succeeded, and all resources are durably persisted in the repository.  The data associated with the event contains the URI of the repository container created on deposit, which serves as a parent to all resources subsequently deposited from the package.
