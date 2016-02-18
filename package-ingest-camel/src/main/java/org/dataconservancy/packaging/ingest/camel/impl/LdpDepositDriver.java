
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.File;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.http.HttpHeaders;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.util.ResourceUtils;

import org.dataconservancy.packaging.ingest.LdpPackageAnalyzer;
import org.dataconservancy.packaging.ingest.LdpPackageAnalyzerFactory;
import org.dataconservancy.packaging.ingest.LdpPackageProvenanceGenerator;
import org.dataconservancy.packaging.ingest.LdpResource;
import org.dataconservancy.packaging.ingest.camel.DepositDriver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

import static org.apache.http.HttpHeaders.LOCATION;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_URI;

import static org.dataconservancy.packaging.ingest.LdpResource.Type.NONRDFSOURCE;
import static org.dataconservancy.packaging.ingest.camel.Helpers.headerString;
import static org.dataconservancy.packaging.ingest.camel.impl.RdfUtil.parseRDFBody;
import static org.dataconservancy.packaging.ingest.camel.impl.RdfUtil.writeRDFBody;

import static org.dataconservancy.packaging.ingest.camel.Helpers.expression;

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

    /**
     * Collection of {@link LdpResource} that are root(s) of the tree of objects
     * being deposited
     */
    public static final String HEADER_LDP_RESOURCES = "deposit.ldp.resources";

    public static final String HEADER_URI_MAP = "deposit.ldp.uri_map";

    public static final String HEADER_BINARY_URIS = "deposit.ldp.binary.uris";

    public static final String HEADER_ORIG_URI = "deposit.ldp.uri.orig";

    public static final String HEADER_RESOURCE = "deposit.ldp.resource";

    public static final String HEADER_ANALYZER = "deposit.ldp.analyer";

    public static final String HEADER_RESOURCE_DESCRIPTION =
            "deposit.ldp.resource_description";

    static final String ID_DEPOSIT_ITERATE = "deposit-ldp-iterate";

    static final String ID_DEPOSIT_REMAP = "ldp-remap-uris";

    static final String ID_HTTP_OPERATION = "ldp-http-operation";

    private LdpPackageAnalyzerFactory<File> analyzerFactory;

    LdpPackageProvenanceGenerator<File> provGen;

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
    public void init() {
    }

    @Reference
    public void setPackageAnalyzerFactory(LdpPackageAnalyzerFactory<File> analyzerFactory) {
        this.analyzerFactory = analyzerFactory;
    }

    @Reference
    public void setPackageProvenanceGenerator(LdpPackageProvenanceGenerator<File> gen) {
        this.provGen = gen;
    }

    @Override
    public void configure() throws Exception {

        configureTransactions();

        /*
         * Entry point to deposit the resources in a package.
         * Body: File
         * Headers: -
         */
        from(ROUTE_DEPOSIT_RESOURCES).id("ldp-deposit-all-resources")
                .to("direct:_setup_for_deposit").doTry().process(m -> {
                    LdpPackageAnalyzer<File> analyzer =
                            m.getIn().getHeader(HEADER_ANALYZER,
                                                LdpPackageAnalyzer.class);
                    m.getIn().setHeader(HEADER_LDP_RESOURCES,
                                        analyzer.getContainerRoots(m.getIn()
                                                .getBody(File.class)));
                }).split(header(HEADER_LDP_RESOURCES), MERGE_URI_MAP)
                .stopOnException().to("direct:_deposit_hierarchical").end()
                .enrich("direct:_do_update_uris", ((orig, updated) -> orig))
                .endDoTry().doFinally()
                .process(e -> e.getIn()
                        .getHeader(HEADER_ANALYZER, LdpPackageAnalyzer.class)
                        .cleanUpExtractionDirectory());

        /* Initial setup for a series of subsequent LDP deposits */
        from("direct:_setup_for_deposit").id("ldp-deposit-setup")
                .setHeader(HEADER_ANALYZER,
                           expression(e -> analyzerFactory.newAnalyzer()))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(HEADER_BINARY_URIS, constant(new HashSet<>()))
                .setHeader(HEADER_URI_MAP, constant(new HashMap<>()));

        from(ROUTE_DEPOSIT_PROVENANCE)
                .setBody(expression(e -> provGen.generatePackageProvenance(
                                                                           new File(headerString(e,
                                                                                                 Exchange.FILE_PATH)),
                                                                           e.getIn()
                                                                                   .getHeader(HEADER_URI_MAP,
                                                                                              Map.class))))
                .to("direct:_deposit_ldpResource")
                .process(e -> e.getIn().setHeader(LOCATION,
                                                  getLinkRel(headerString(e,
                                                                          "Link"),
                                                             "describedby")));

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
        from("direct:_deposit_iterate").id(ID_DEPOSIT_ITERATE)
                .split(header(HEADER_LDP_RESOURCES), MERGE_URI_MAP)
                .stopOnException().enrich("direct:_deposit_ldpResource",
                                          (existing, deposited) -> {
                                              existing.getIn()
                                                      .setHeader(LOCATION,
                                                                 deposited
                                                                         .getIn()
                                                                         .getHeader(LOCATION));
                                              return MERGE_URI_MAP
                                                      .aggregate(existing,
                                                                 deposited);
                                          })
                .process(e -> e.getIn().setHeader(HEADER_LDP_RESOURCES, e.getIn().getBody(LdpResource.class).getChildren()))
                .setHeader(HTTP_URI, header(LOCATION))
                .to("direct:_deposit_iterate").end();

        /*
         * Deposit an LDP resource, and map the location. TODO: Digest/MD5
         * verification?
         */
        from("direct:_deposit_ldpResource").id("ldp-deposit-resource")
                .process(e -> {
                    LdpResource resource = e.getIn().getBody(LdpResource.class);
                    e.getIn().setBody(resource.getBody());

                    e.getIn().setHeader(HEADER_RESOURCE, resource);
                    e.getIn().setHeader(HEADER_RESOURCE_DESCRIPTION,
                                        resource.getDescription());
                    e.getIn().setHeader(HEADER_ORIG_URI,
                                        resource.getURI().toString());
                    e.getIn().setHeader(CONTENT_TYPE, resource.getMediaType());
                    e.getIn().setHeader("Slug", fileName(resource));

                    if (NONRDFSOURCE.equals(resource.getType()))
                        e.getIn().setHeader("Content-Disposition",
                                            "attachment; filename="
                                                    + fileName(resource));

                    /*
                     * If Location is set, it's from when we deposited the
                     * parent. POST to it to create a child. Otherwise, use the
                     * supplied HTTP_URI.
                     */
                    if (headerString(e, LOCATION) != null) {
                        e.getIn().setHeader(HTTP_URI,
                                            headerString(e, LOCATION));
                    }

                }).to("direct:_http_preserve_body").process(UPDATE_URI_MAP)
                .process(e -> {
                    LdpResource resource = e.getIn()
                            .getHeader(HEADER_RESOURCE, LdpResource.class);
                    if (NONRDFSOURCE.equals(resource.getType())) e.getIn()
                            .getHeader(HEADER_BINARY_URIS, Collection.class)
                            .add(headerString(e, HttpHeaders.LOCATION));
                }).choice()
                .when(header(HEADER_RESOURCE_DESCRIPTION).isNotNull())
                .enrich("direct:_deposit_resource_description", MERGE_URI_MAP)
                .end();

        /*
         * Perform an http operation, and merge the result headers back into the
         * original message, preserving its original body
         */
        from("direct:_http_preserve_body").id("ldp-http-preserve-body")
                .enrich("direct:_do_http_op", ((orig, http) -> {
                    http.getIn().getHeaders().entrySet()
                            .forEach(e -> orig.getIn().getHeaders()
                                    .put(e.getKey(), e.getValue()));
                    return orig;
                }));

        /* Sanitize headers and perform an HTTP operation */
        from("direct:_do_http_op").id(ID_HTTP_OPERATION)
                .to("direct:_sanitize_headers").to("http4:ldp-host");

        /*
         * Deposit a resources that describes another resource (indicated in LDP
         * by Link rel=described by). This assumes that the LDP implementation
         * automatically creates such a resource; we just follow the link
         * header, and add triples to it.
         */
        from("direct:_deposit_resource_description")
                .id("ldp-deposit-resource-description").process(e -> {
                    LdpResource description =
                            e.getIn().getHeader(HEADER_RESOURCE_DESCRIPTION,
                                                LdpResource.class);
                    String descriptionURI = getLinkRel(e.getIn()
                            .getHeader("Link", String.class), "describedby");
                    uriMap(e).put(description.getURI().toString(),
                                  descriptionURI);
                    e.getIn().setHeader(CONTENT_TYPE,
                                        description.getMediaType());
                    e.getIn().setHeader(HTTP_URI, descriptionURI);
                    e.getIn().setBody(IOUtils
                            .toByteArray(description.getBody()));
                }).process(REMAP_URIS)
                .setHeader(HttpHeaders.IF_MATCH, header(HttpHeaders.ETAG))
                .process(SparqlPatch.ADD).to("direct:_http_preserve_body");

        /* Retrieve the current turtle representation of an object */
        from("direct:_retrieveForUpdate").id("ldp-retrieve-for-update")
                .setHeader(HttpHeaders.ACCEPT, constant("text/turtle"))
                .setHeader(Exchange.HTTP_METHOD, constant("GET")).doTry()
                .to("direct:_do_http_op")
                .doCatch(HttpOperationFailedException.class)
                .onWhen(e -> e.getException(HttpOperationFailedException.class)
                        .getStatusCode() == 406)
                .setBody(constant("")).end();

        /* Strip all headers except for certain HTTP headers used in requests */
        from("direct:_sanitize_headers").id("lsp-sanitize-headers")
                .removeHeaders("*",
                               HTTP_URI,
                               CONTENT_TYPE,
                               Exchange.HTTP_METHOD,
                               HttpHeaders.ACCEPT,
                               HttpHeaders.AUTHORIZATION,
                               HttpHeaders.CONTENT_ENCODING,
                               HttpHeaders.IF_MATCH,
                               "Content-Disposition",
                               "Slug");

        /* Remap original URIs to ldp URIs */
        from("direct:_do_update_uris").id("ldp-do-update-uris")
                .id(ID_DEPOSIT_REMAP)
                .split(header(HEADER_URI_MAP).method("values"), ((o, n) -> o))
                .stopOnException().setHeader(HTTP_URI, body()).choice()
                .when(e -> !e.getIn()
                        .getHeader(HEADER_BINARY_URIS, Collection.class)
                        .contains(headerString(e, HTTP_URI)))
                .enrich("direct:_retrieveForUpdate", (orig, retrieved) -> {
                    retrieved.getIn()
                            .setHeader(HEADER_URI_MAP,
                                       orig.getIn().getHeader(HEADER_URI_MAP));
                    return retrieved;
                }).process(REMAP_URIS).choice().when(header("updated"))
                .enrich("direct:_retrieveForUpdate", SparqlPatch.MERGE)
                .setHeader(HttpHeaders.IF_MATCH, header(HttpHeaders.ETAG))
                .to("direct:_do_http_op").end().end().end();

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

    static final Processor REMAP_URIS = (e -> {
        Map<String, String> uriMap = uriMap(e);
        Model model = ModelFactory.createDefaultModel();
        StreamRDF sink = StreamRDFLib.graph(model.getGraph());

        parseRDFBody(sink, e);

        model.listSubjects()
                .andThen(model.listObjects().filterKeep(RDFNode::isURIResource)
                        .mapWith(RDFNode::asResource))
                .filterKeep(r -> uriMap.containsKey(r.toString()))
                .forEachRemaining(r -> {
            e.getIn().setHeader("updated", true);
            ResourceUtils.renameResource(r, uriMap.get(r.toString()));
        });

        writeRDFBody(model, e);
    });

    static final Processor UPDATE_URI_MAP =
            (e -> uriMap(e).put(headerString(e, HEADER_ORIG_URI),
                                headerString(e, HttpHeaders.LOCATION)));

    /*
     * Merges the RDF of two bodies
     * Body: Serialized RDF
     * Headers: CONTENT_TYPE, Location
     */
    //static final AggregationStrategy MERGE_RDF = ((orig, newer) -> {
    //    Model model = ModelFactory.createDefaultModel();
    //    StreamRDF sink = StreamRDFLib.graph(model.getGraph());

    //  parseRDFBody(sink, orig);
    //  parseRDFBody(sink, newer);
    //  writeRDFBody(model, orig);

    //  return orig;
    //});

    /* Retrieve the uri map of a message */
    static Map<String, String> uriMap(Exchange e) {
        return e.getIn().getHeader(HEADER_URI_MAP, Map.class);
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

    private static String fileName(LdpResource resource) {

        String name = new File(resource.getURI().getPath()).getName();

        if (NONRDFSOURCE.equals(resource.getType())) {
            return name;
        } else {
            return FilenameUtils.removeExtension(name);
        }
    }
}
