/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.impl.deposit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.entity.ContentType.parse;
import static org.apache.jena.riot.RDFLanguages.contentTypeToLang;
import static org.dataconservancy.packaging.ingest.PackagedResource.Type.NONRDFSOURCE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;

import org.dataconservancy.packaging.ingest.Depositor;
import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.PackagedResource;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.riot.writer.NTriplesWriter;
import org.apache.jena.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
public class FedoraDepositFactory implements DepositFactory {

    private String baseUri = "http://localhost:8080/fcrepo/rest";

    private static final Logger LOG = LoggerFactory.getLogger(FedoraDepositFactory.class);

    public void setBaseURi(String base) {
        this.baseUri = base;
    }

    public boolean doTx = true;

    public boolean useSparql = true;

    @Override
    public Depositor newDepositor(URI depositInto, Map<String, Object> context) {

        final FcrepoClient client = FcrepoClient.client().build();

        final String txStart = baseUri + "/fcr:tx";

        URI txBase = URI.create(baseUri);

        if (doTx) {
            try (FcrepoResponse response = client.post(URI.create(txStart)).perform()) {
                txBase = response.getLocation();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        return new TxDepositor(txBase, depositInto, client);
    }

    private class TxDepositor implements Depositor {

        private final URI txBase;

        private final URI txDepositInto;

        private final FcrepoClient client;

        private TxDepositor(URI txBase, URI canonicalDepositInto, FcrepoClient client) {
            this.txBase = txBase;

            this.client = client;

            // This is a little hairy, but there's no real alternative!
            txDepositInto = URI.create(canonicalDepositInto.toString().replace(baseUri, txBase.toString()));
        }

        @Override
        public Deposited deposit(PackagedResource resource, URI parent) {

            final URI depositInto = parent == null ? txDepositInto : parent;

            final Deposited deposited = new Deposited();

            deposited.uri = doDeposit(resource, depositInto);

            if (resource.getDescription() != null) {
                LOG.debug("Deposited binary {} has description, finding description resource in repository",
                        deposited.uri, deposited.describedBy);

                try (FcrepoResponse response = client.head(deposited.uri).perform()) {

                    deposited.describedBy = response.getLinkHeaders("describedby").get(0);

                    LOG.debug("Deposited binary has description {}", deposited.describedBy);

                    doReplace(resource.getDescription(), deposited.uri, deposited.describedBy);

                } catch (final Exception e) {
                    throw new RuntimeException("Replace failed", e);
                }
            }

            return deposited;
        }

        private URI doDeposit(PackagedResource resource, URI parent) {
            LOG.debug("Depositing into {} into {}", resource.getURI(), parent);
            try (InputStream content = resource.getBody();
                    FcrepoResponse r = client.post(parent)
                            .slug(fileName(resource))
                            .filename(fileNameIfBinary(resource))
                            .body(content, resource.getMediaType())
                            .perform()) {
                checkError(r);

                LOG.debug("Successfully deposited {} into {} as {}", resource.getURI(), parent, r.getLocation());
                return r.getLocation();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        private String fileNameIfBinary(PackagedResource resource) {
            if (NONRDFSOURCE.equals(resource.getType())) {
                return fileName(resource);
            }
            return null;
        }

        private void doReplace(PackagedResource resource, URI nonrdfsource, URI target) {

            LOG.debug("Updating contents of {} with {}", target, resource.getURI());

            try (
                    FcrepoResponse r = client.patch(target)
                            .body(sparqlAdd(resource, nonrdfsource))
                            .perform()) {
                checkError(r);
                LOG.debug("Done updating contents of {} with {}", target, resource.getURI());
            } catch (final Exception e) {
                throw new RuntimeException("Could not update contents of resource " + target + ": " + e.getMessage(),
                        e);
            }
        }

        private InputStream sparqlAdd(PackagedResource resource, URI into) throws IOException {

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write("INSERT DATA {\n".getBytes(UTF_8));

            try (InputStream content = resource.getBody()) {
                RDFDataMgr.parse(StreamRDFLib.writer(out), content, into.toString(), contentTypeToLang(parse(resource
                        .getMediaType())
                                .getMimeType()));

            } catch (final IOException e) {
                throw new RuntimeException("Could not read package contents of " + resource.getURI(), e);
            }

            out.write("}".getBytes(UTF_8));

            return new ByteArrayInputStream(out.toByteArray());

        }

        @Override
        public void commit() {

            if (doTx) {
                final URI commitUri = URI.create(txBase.toString() + "/fcr:tx/fcr:commit");
                try (FcrepoResponse r = client.post(commitUri).perform()) {
                    checkError(r);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void rollback() {

            if (doTx) {
                final URI rollbackUri = URI.create(txBase.toString() + "/fcr:tx/fcr:rollback");
                try (FcrepoResponse r = client.post(rollbackUri).perform()) {
                    checkError(r);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void remap(URI toRemap, Map<URI, URI> localToRepository) {
            final Model updatedModel = ModelFactory.createDefaultModel();
            final Model originalModel = ModelFactory.createDefaultModel();

            // GET an instance of the object without server-managed or ldp containment triples.
            // It's darn near impossible to do a PUT that contains *any* of these with Fedora, so
            // use a minimal approach.
            try (FcrepoResponse response = client.get(toRemap).preferMinimal()
                    .preferRepresentation(Collections.emptyList(), Arrays.asList(
                            URI.create("http://fedora.info/definitions/v4/repository#ServerManaged"),
                            URI.create("http://www.w3.org/ns/ldp#PreferContainment"))).accept("text/turtle")
                    .perform()) {
                RDFDataMgr.parse(StreamRDFLib.graph(updatedModel.getGraph()), response.getBody(),
                        contentTypeToLang(parse(response.getContentType()).getMimeType()));
            } catch (final Exception e) {
                throw new RuntimeException("Could not read object to remap: " + toRemap, e);
            }

            // If we're using sparql update, keep a copy of the original model around to take a diff.
            if (useSparql) {
                originalModel.add(updatedModel);
            }

            // Find all the package-local URIs to remap
            final Set<String> localUris = updatedModel.listObjects()
                    .filterKeep(RDFNode::isURIResource)
                    .mapWith(RDFNode::asResource)
                    .mapWith(Resource::getURI)
                    .filterKeep(uri -> localToRepository.containsKey(URI.create(withoutHash(uri))))
                    .toSet();

            // If we found any, re-map and update!
            if (!localUris.isEmpty()) {
                for (final String localURI : localUris) {
                    final String localBase = withoutHash(localURI);
                    final String resolved = localURI.replace(localBase, localToRepository.get(URI.create(localBase))
                            .toString());
                    ResourceUtils.renameResource(updatedModel.getResource(localURI), resolved);
                }

                if (!useSparql) {
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    updatedModel.write(out, "TTL");

                    try (FcrepoResponse response = client
                            .put(toRemap)
                            .body(new ByteArrayInputStream(out.toByteArray()), "text/turtle")
                            .preferLenient()
                            .perform()) {
                        checkError(response);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try (FcrepoResponse response = client
                            .patch(toRemap)
                            .body(makeSparqlPatch(originalModel, updatedModel))
                            .perform()) {
                        checkError(response);
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        private String withoutHash(String uri) {
            if (uri.contains("#")) {
                return uri.substring(0, uri.indexOf('#'));
            } else {
                return uri;
            }
        }
    }

    // Creates a SPARQL/Update patch which, when applied to the original model, result in the updated.
    private static InputStream makeSparqlPatch(Model orig, Model updated) {
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

    private static String fileName(PackagedResource resource) {

        String name = new File(resource.getURI().getPath()).getName();

        if (!NONRDFSOURCE.equals(resource.getType())) {
            name = FilenameUtils.removeExtension(name);
        }

        try {
            return URLEncoder.encode(name, "UTF-8");
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkError(FcrepoResponse response) throws FcrepoOperationFailedException, IOException {
        if (response.getStatusCode() >= 400) {
            throw new FcrepoOperationFailedException(response.getUrl(), response.getStatusCode(),
                    IOUtils.toString(response.getBody(), "utf8"));
        }
    }
}
