/*
/*
 * Copyright 2017 Johns Hopkins University
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

package org.dataconservancy.packaging.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.dataconservancy.packaging.ingest.PackagedResource;

import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFWrapper;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.riot.writer.NTriplesWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of rdf-related functions.
 *
 * @author apb@jhu.edu
 */
public abstract class RdfUtil {

    static final Logger LOG = LoggerFactory.getLogger(RdfUtil.class);

    private static final ExecutorService executor = Executors
            .newCachedThreadPool();

    /**
     * Make all URIs relative to the given URI.
     *
     * @param base base URI.
     * @return function that makes subject or object URIs relative.
     */
    public static Function<Triple, Triple> relativeUris(final URI base) {
        return (in) -> {
            return Triple.create(
                    relativize(in.getSubject(), base.toString()),
                    in.getPredicate(),
                    relativize(in.getObject(), base.toString()));
        };
    }

    /**
     * Remap URIs from local to public.
     *
     * @param localUriMap map of local to public URIs.
     * @return
     */
    public static Function<Triple, Triple> remap(final Map<URI, URI> localUriMap) {
        return (in) -> {
            return Triple.create(remap(in.getSubject(), localUriMap),
                    in.getPredicate(),
                    remap(in.getObject(), localUriMap));
        };
    }

    /**
     * Skolemizes blank nodes to relative hash URIs.
     *
     * @return skolemized triple.
     */
    public static Function<Triple, Triple> skolemizeToHash() {
        return (in) -> {
            return Triple.create(skolemize(in.getSubject()), in.getPredicate(), skolemize(in.getObject()));
        };
    }

    /**
     * Filter the RDF in the given resource body.
     *
     * @param pkg The package
     * @param transforms transforms to apply.
     * @return filtered resource.
     */
    @SafeVarargs
    public static PackagedResource filterBody(final PackagedResource pkg,
            final Function<Triple, Triple>... transforms) {
        return new PackagedResourceWrapper(pkg) {

            @Override
            public InputStream getBody() {
                final PipedInputStream in = new PipedInputStream();
                final PipedOutputStream out = new PipedOutputStream();

                try {
                    in.connect(out);
                } catch (final IOException e) {
                    throw new RuntimeException("Could not filter triples", e);
                }

                final StreamRDF transformer = new StreamRDFWrapper(StreamRDFWriter.getWriterStream(out,
                        RDFFormat.TURTLE_FLAT)) {

                    @Override
                    public void triple(final Triple triple) {
                        Triple filtered = triple;
                        for (final Function<Triple, Triple> transform : transforms) {
                            if (filtered != null) {
                                filtered = transform.apply(filtered);
                            }
                        }

                        if (filtered != null) {
                            super.triple(filtered);
                        }
                    }
                };
                final InputStream orig = pkg.getBody();

                executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            RDFDataMgr.parse(transformer, orig, "", RDFLanguages.contentTypeToLang(pkg
                                    .getMediaType()));
                        } finally {
                            try {
                                out.close();
                            } catch (final IOException e) {
                                LOG.warn("Could not close piped output stream", e);
                            }
                        }
                    }
                });

                return in;

            }
        };
    }

    /**
     * Creates a SPARQL/Update patch which, when applied to the original model, result in the updated.
     *
     * @param orig original model
     * @param updated updated model;
     * @return SPARQL patch which, when applied to the original resource, patches it to become equivalent to the
     *         updated.
     */
    public static InputStream makeSparqlPatch(final Model orig, final Model updated) {
        try {
            final ByteArrayOutputStream body = new ByteArrayOutputStream();

            IOUtils.write("INSERT DATA {\n".getBytes(UTF_8), body);

            /* New triples in 'updated' but not in 'orig' */
            NTriplesWriter.write(body,
                    updated.listStatements()
                            .filterDrop(s -> orig.contains(s))
                            .mapWith(Statement::asTriple));
            IOUtils.write("};\n".getBytes(UTF_8), body);

            IOUtils.write("DELETE DATA {\n".getBytes(UTF_8), body);

            /* Triples in 'orig' but not in 'updated' */
            NTriplesWriter.write(body,
                    orig.listStatements()
                            .filterDrop(s -> updated.contains(s))
                            .mapWith(Statement::asTriple));

            IOUtils.write("}\n".getBytes(UTF_8), body);

            return new ByteArrayInputStream(body.toByteArray());
        } catch (final IOException e) {
            throw new RuntimeException("Error creating spaeql patch", e);
        }
    }

    private static Node relativize(final Node in, final String base) {
        if (in.isURI() && in.getURI().startsWith(base)) {
            final String relative = in.getURI().replace(base, "");
            if (!relative.startsWith("/")) {
                return NodeFactory.createURI(relative);
            }
        }
        return in;
    }

    private static Node skolemize(final Node in) {
        if (in.isBlank()) {
            return NodeFactory.createURI("#_" + Base64.getEncoder().encodeToString(in.getBlankNodeId()
                    .getLabelString()
                    .getBytes(UTF_8)));
        } else {
            return in;
        }
    }

    private static Node remap(final Node in, final Map<URI, URI> localUriMap) {
        if (in.isURI()) {
            final URI nodeBase = URI.create(withoutHash(in.getURI()));
            if (localUriMap.containsKey(nodeBase)) {
                return NodeFactory.createURI(
                        in.getURI().replace(nodeBase.toString(),
                                localUriMap.get(nodeBase).toString()));
            }
        }

        return in;
    }

    private static String withoutHash(final String uri) {
        if (uri.contains("#")) {
            return uri.substring(0, uri.indexOf('#'));
        } else {
            return uri;
        }
    }
}
