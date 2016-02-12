
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.ByteArrayOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.riot.writer.NTriplesWriter;

import static org.apache.commons.io.Charsets.UTF_8;

import static org.dataconservancy.packaging.ingest.camel.impl.RdfUtil.parseRDFBody;

/** Utilities for creating SPARQL/Update for PATCH */
public class SparqlPatch {
    
    public static final String MIME_TYPE = "application/sparql-update";

    public static Processor ADD = ((e) -> {
        try (ByteArrayOutputStream body = new ByteArrayOutputStream()) {
            IOUtils.write("INSERT {\n".getBytes(UTF_8), body);

            NTriplesWriter.write(body,
                                 parseToModel(e).listStatements()
                                         .mapWith(Statement::asTriple));

            IOUtils.write("\n}\nWHERE{}".getBytes(UTF_8), body);

            write(e, body);
        }
    });

    private static void write(Exchange e, ByteArrayOutputStream body) {
        e.getIn().setHeader(Exchange.CONTENT_TYPE, MIME_TYPE);
        e.getIn().setHeader(Exchange.HTTP_METHOD, "PATCH");
        e.getIn().setBody(body.toByteArray());
    }

    public static AggregationStrategy MERGE = ((a, b) -> {

        Model aTriples = parseToModel(a);
        Model bTriples = parseToModel(b);

        try (ByteArrayOutputStream body = new ByteArrayOutputStream()) {

            IOUtils.write("INSERT {\n".getBytes(UTF_8), body);

            /* New triples in 'a' but not in 'b' */
            NTriplesWriter.write(body,
                                 aTriples.listStatements()
                                         .filterDrop(s -> bTriples.contains(s))
                                         .mapWith(Statement::asTriple));
            IOUtils.write("}\nWHERE{};\n".getBytes(UTF_8), body);

            IOUtils.write("DELETE {\n".getBytes(UTF_8), body);

            /* Triples in 'b' but not in 'a' */
            NTriplesWriter.write(body,
                                 bTriples.listStatements()
                                         .filterDrop(s -> aTriples.contains(s))
                                         .mapWith(Statement::asTriple));

            IOUtils.write("}\nWHERE{}".getBytes(UTF_8), body);

            write(a, body);
            return a;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private static Model parseToModel(Exchange e) {
        Model model = ModelFactory.createDefaultModel();
        parseRDFBody(StreamRDFLib.graph(model.getGraph()), e);
        return model;
    }

}
