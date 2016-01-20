
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.http.HttpHeaders;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.util.ResourceUtils;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpResource;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

/**
 * Deposits package content into an LDP repository.
 * 
 * @author apb@jhu.edu
 */
@Component(service = DepositDriver.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@SuppressWarnings("unchecked")
public class LdpDepositDriver
        extends RouteBuilder
        implements DepositDriver {

    Map<String, String> config;

    /** URI of the LDP container that will be deposited into */
    public static final String PROP_LDP_CONTAINER = "deposit.ldp.container";

    /**
     * Collection of {@link LdpResource} that are root(s) of the tree of objects
     * being deposited
     */
    public static final String HEADER_LDP_RESOURCES = "deposit.ldp.resources";

    public static final String HEADER_URI_MAP = "deposit.ldp.uri_map";

    public static final String HEADER_ORIG_URI = "deposit.ldp.uri.orig";

    public static final String HEADER_RESOURCE_DESCRIPTION =
            "deposit.ldp.resource_description";

    static final String ID_DEPOSIT_ITERATE = "deposit-ldp-iterate";

    static final String ID_DEPOSIT_REMAP = "ldp-remap-uris";

    static final String ID_HTTP_OPERATION = "ldp-http-operation";

    private LdpPackageAnalyzer<File> analyzer;

    protected void configureTransactions() {
        /*
         * Do nothing. It would be nice to implement manual transactions in the
         * future.
         */
        from(ROUTE_TRANSACTION_BEGIN).process(m -> {
        });
        from(ROUTE_TRANSACTION_COMMIT).process(m -> {
        });
        from(ROUTE_TRANSACTION_ROLLBACK).process(m -> {
        });
        from(ROUTE_TRANSACTION_CANONICALIZE).process(m -> {
        });
    }

    @Activate
    public void init(Map<String, String> config) {
        this.config = config;
    }

    @Reference
    public void setPackageAnalyzer(LdpPackageAnalyzer<File> analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public void configure() throws Exception {

        configureTransactions();

        /*
         * Entry point to deposit the resources in a package.
         * Body: File
         * Headers: -
         */
        from(ROUTE_DEPOSIT_RESOURCES)
                .id("ldp-deposit-all-resources")
                .to("direct:_setup_for_deposit")
                .process(m -> m.getIn()
                        .setHeader(HEADER_LDP_RESOURCES,
                                   analyzer.getContainerRoots(m.getIn()
                                           .getBody(File.class))))
                .split(header(HEADER_LDP_RESOURCES), MERGE_URI_MAP)
                .stopOnException().to("direct:_deposit_hierarchical").end()
                .enrich("direct:_remap_uris", ((orig, updated) -> orig));

        /* Initial setup for a series of subsequent LDP deposits */
        from("direct:_setup_for_deposit")
                .id("ldp-deposit-setup")
                .setHeader(Exchange.HTTP_URI,
                           constant(config.get(PROP_LDP_CONTAINER)))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(HEADER_URI_MAP, constant(new HashMap<>()));

        /*
         * Recurse through the nodes of the tree.
         */
        from("direct:_deposit_hierarchical").id("ldp-deposit-hierarchical")
                .enrich("direct:_deposit_iterate", MERGE_URI_MAP);

        /*
         * Iterate through levels of the tree.
         * Body: -
         * Headers: HEADER_LDP_RESOURCES: Collection<LdpResource>
         */
        from("direct:_deposit_iterate")
                .id(ID_DEPOSIT_ITERATE)
                .split(header(HEADER_LDP_RESOURCES), MERGE_URI_MAP)
                .stopOnException()
                .enrich("direct:_deposit_ldpResource",
                        (existing, deposited) -> {
                            existing.getIn()
                                    .setHeader(HttpHeaders.LOCATION,
                                               deposited
                                                       .getIn()
                                                       .getHeader(HttpHeaders.LOCATION));
                            return MERGE_URI_MAP.aggregate(existing, deposited);
                        })
                .setHeader(HEADER_LDP_RESOURCES,
                           bodyAs(LdpResource.class).method("getChildren"))
                .setHeader(Exchange.HTTP_URI, header(HttpHeaders.LOCATION))
                .to("direct:_deposit_iterate").end();

        /*
         * Deposit an LDP resource, and map the location
         */
        from("direct:_deposit_ldpResource")
                .id("ldp-deposit-resource")
                .process(e -> {
                    LdpResource resource = e.getIn().getBody(LdpResource.class);

                    e.getIn().setHeader(HEADER_RESOURCE_DESCRIPTION,
                                        resource.getDescription());
                    e.getIn().setHeader(HEADER_ORIG_URI,
                                        resource.getURI().toString());
                    e.getIn().setBody(resource.getBody());
                    e.getIn().setHeader(Exchange.CONTENT_TYPE,
                                        resource.getMediaType());

                    String name =
                            new File(resource.getURI().getPath()).getName();

                    e.getIn().setHeader("Slug", name);
                    e.getIn().setHeader("Content-Disposition",
                                        "attachment; filename=" + name);

                    /* TODO: digest/md5? */

                    /*
                     * If Location is set, it's from when we deposited the
                     * parent. POST to it to create a child. Otherwise, use the
                     * supplied Exchange.HTTP_URI.
                     */
                    e.getIn()
                            .setHeader(Exchange.HTTP_URI,
                                       e.getIn()
                                               .getHeader("Location",
                                                          e.getIn()
                                                                  .getHeader(Exchange.HTTP_URI,
                                                                             String.class),
                                                          String.class));

                }).to("direct:_http_preserve_body")
                .to("direct:_update_uri_map").choice()
                .when(header(HEADER_RESOURCE_DESCRIPTION).isNotNull())
                .enrich("direct:_deposit_resource_description", MERGE_URI_MAP)
                .end();

        /*
         * Perform an http operation, and merge the result headers back into the
         * original message, preserving its original body
         */
        from("direct:_http_preserve_body")
                .enrich("direct:_do_http_op",
                        ((orig, http) -> {
                            http.getIn()
                                    .getHeaders()
                                    .entrySet()
                                    .forEach(e -> orig.getIn().getHeaders()
                                            .put(e.getKey(), e.getValue()));
                            return orig;
                        }));

        /* Sanitize headers and perform an HTTP operation */
        from("direct:_do_http_op").id(ID_HTTP_OPERATION)
                .to("direct:_sanitize_headers").to("http4:ldp-host");

        /* Updates the URI map based on Location header */
        from("direct:_update_uri_map").id("ldp-update-uri-map")
                .process(e -> uriMap(e).put(e.getIn()
                                                    .getHeader(HEADER_ORIG_URI,
                                                               String.class),
                                            e.getIn().getHeader("Location",
                                                                String.class)));

        /*
         * Deposit a resources that describes another resource (indicated in LDP
         * by Link rel=describedby). This assumes that the LDP implementation
         * automatically creates such a resource; we just follow the link
         * header, and add triples to it.
         */
        from("direct:_deposit_resource_description")
                .id("ldp-deposit-resource-description")
                .process(e -> {
                    LdpResource description =
                            e.getIn().getHeader(HEADER_RESOURCE_DESCRIPTION,
                                                LdpResource.class);
                    String descriptionURI =
                            getLinkRel(e.getIn()
                                               .getHeader("Link", String.class),
                                       "describedby");
                    uriMap(e).put(description.getURI().toString(),
                                  descriptionURI);
                    e.getIn().setHeader(Exchange.CONTENT_TYPE,
                                        description.getMediaType());
                    e.getIn().setHeader(Exchange.HTTP_URI, descriptionURI);
                    e.getIn()
                            .setBody(IOUtils.toByteArray(description.getBody()));
                }).to("direct:_merge_ldpResource");

        /*
         * Merge rdf in the body with the contents of the resource at
         * Exchange.HTTP_URI
         */
        from("direct:_merge_ldpResource")
                .enrich("direct:_retrieveForUpdate", MERGE_RDF)
                .setHeader(HttpHeaders.IF_MATCH, header(HttpHeaders.ETAG))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/turtle"))
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .to("direct:_http_preserve_body");

        /* Retrieve the current turtle representation of an object */
        from("direct:_retrieveForUpdate")
                .setHeader(HttpHeaders.ACCEPT, constant("text/turtle"))
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .doTry()
                .to("direct:_do_http_op")
                .doCatch(HttpOperationFailedException.class)
                .onWhen(e -> e.getException(HttpOperationFailedException.class)
                        .getStatusCode() == 406).setBody(constant("")).end();

        /* Strip all headers except for certain HTTP headers used in requests */
        from("direct:_sanitize_headers")
                .removeHeaders("*",
                               Exchange.HTTP_URI,
                               Exchange.CONTENT_TYPE,
                               Exchange.HTTP_METHOD,
                               HttpHeaders.ACCEPT,
                               HttpHeaders.AUTHORIZATION,
                               HttpHeaders.CONTENT_ENCODING,
                               HttpHeaders.IF_MATCH,
                               "Content-Disposition",
                               "Slug");

        /* Remap original URIs to ldp URIs */
        from("direct:_remap_uris")
                .id(ID_DEPOSIT_REMAP)
                .split(header(HEADER_URI_MAP).method("values"), ((o, n) -> o))
                .stopOnException()
                .setHeader(Exchange.HTTP_URI, body())
                .enrich("direct:_retrieveForUpdate",
                        (orig, retrieved) -> {
                            retrieved
                                    .getIn()
                                    .setHeader(HEADER_URI_MAP,
                                               orig.getIn()
                                                       .getHeader(HEADER_URI_MAP));
                            return retrieved;
                        })
                .process(e -> {
                    Map<String, String> uriMap = uriMap(e);
                    Model model = ModelFactory.createDefaultModel();
                    StreamRDF sink = StreamRDFLib.graph(model.getGraph());

                    parseRDFBody(sink, e);

                    model.listSubjects()
                            .andThen(model.listObjects()
                                    .filterKeep(RDFNode::isURIResource)
                                    .mapWith(RDFNode::asResource))
                            .filterKeep(r -> uriMap.containsKey(r.toString()))
                            .forEachRemaining(r -> {
                                e.getIn().setHeader("updated", true);
                                ResourceUtils.renameResource(r, uriMap.get(r
                                        .toString()));
                            });

                    writeRDFBody(model, e);

                }).choice().when(header("updated"))
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setHeader(HttpHeaders.IF_MATCH, header(HttpHeaders.ETAG))
                .to("direct:_do_http_op").end().end();

    }

    /*
     * Merges the URI map of an 'newer' message, with the 'original', and
     * returns the original message, with its URI map updated.
     */
    static final AggregationStrategy MERGE_URI_MAP = ((orig, newer) -> {

        if (orig == null) return newer;

        Map<String, String> master = uriMap(orig);

        if (master != null) {
            uriMap(newer)
                    .forEach((k, v) -> master.merge(k, v, ((v1, v2) -> v1)));
        } else {
            orig.getIn().setHeader(HEADER_URI_MAP, uriMap(newer));
        }

        return orig;
    });

    /*
     * Merges the RDF of two bodies
     * Body: Serialized RDF
     * Headers: Exchange.CONTENT_TYPE, Location
     */
    static final AggregationStrategy MERGE_RDF = ((orig, newer) -> {
        Model model = ModelFactory.createDefaultModel();
        StreamRDF sink = StreamRDFLib.graph(model.getGraph());

        parseRDFBody(sink, orig);
        parseRDFBody(sink, newer);
        writeRDFBody(model, orig);

        return orig;
    });

    /* Retrieve the uri map of a message */
    static Map<String, String> uriMap(Exchange e) {
        return e.getIn().getHeader(HEADER_URI_MAP, Map.class);
    }

    /* Serializes rdf from a model into a message body */
    static void writeRDFBody(Model model, Exchange e) {
        e.getIn().setHeader(Exchange.CONTENT_TYPE, "text/turtle");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        model.write(out, "TURTLE");
        e.getIn().setBody(out.toByteArray());
    }

    /* Parses the body of the message in an exchange into rdf */
    static void parseRDFBody(StreamRDF sink, Exchange e) {

        RDFDataMgr
                .parse(sink,
                       new TypedInputStream(e.getIn()
                               .getBody(InputStream.class), ContentType
                               .create(e.getIn()
                                       .getHeader(Exchange.CONTENT_TYPE,
                                                  String.class))),
                       e.getIn().getHeader(HttpHeaders.LOCATION, String.class));
    }

    /*
     * Quick & dirty way to parse a Link header to retrieve a particular rel,
     * Note, will retrieve first match
     */
    private static String getLinkRel(String linkHeader, String rel) {

        for (String linkInstance : linkHeader.split(",")) {
            if (linkInstance.matches(".+?rel=\"?" + rel + "\"?.*?")) {
                return linkInstance.substring(linkInstance.indexOf('<') + 1,
                                              linkInstance.indexOf('>'));
            }
        }

        return null;
    }
}
