/*
 * Copyright 2016 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.packaging.ingest.camel.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

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
        
        String body = "";

        try {
            body =
                    IOUtils.toString(e.getIn().getBody(InputStream.class));

            RDFDataMgr.parse(sink,
                             new TypedInputStream(IOUtils.toInputStream(body),
                                                  ContentType.create(e.getIn()
                                                          .getHeader(CONTENT_TYPE,
                                                                     String.class))),
                             e.getIn().getHeader(LOCATION, String.class));
        } catch (Exception x) {
            System.err.println(body);
            throw new RuntimeException(x);
        }
    }
}
