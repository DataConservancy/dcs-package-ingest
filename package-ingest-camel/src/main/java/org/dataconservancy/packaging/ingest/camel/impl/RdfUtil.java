package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDF;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.LOCATION;

public class RdfUtil {
    /* Serializes rdf from a model into a message body */
    static void writeRDFBody(Model model, Exchange e) {
        e.getIn().setHeader(CONTENT_TYPE, "text/turtle");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        model.write(out, "TURTLE");
        e.getIn().setBody(out.toByteArray());
    }

    /* Parses the body of the message in an exchange into rdf */
    static void parseRDFBody(StreamRDF sink, Exchange e) {
        RDFDataMgr.parse(sink,
                         new TypedInputStream(e.getIn()
                                 .getBody(InputStream.class),
                                              ContentType.create(e.getIn()
                                                      .getHeader(CONTENT_TYPE,
                                                                 String.class))),
                         e.getIn().getHeader(LOCATION, String.class));
    }
}
