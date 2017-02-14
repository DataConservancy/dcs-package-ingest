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

import org.dataconservancy.packaging.ingest.DepositFactory;
import org.dataconservancy.packaging.ingest.Depositor;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ObjectClassDefinition(name = "Something")
@interface Config {

    @AttributeDefinition(name = "Fedora Base URI", description = "Fedora repository root")
    String baseUri() default "http//localhost:8080/fcrepo/rest";

    @AttributeDefinition(name = "Use Transactions", description = "Perform deposits in a transactioj")
    boolean useTransactions() default true;

    @AttributeDefinition(name = "Use sparql patch",
            description = "Use sparql patch instead of PUT for modifying resources")
    boolean useSparqlPatch() default true;
}

/**
 * Deposit factory into Fedora implementations.
 *
 * @author apb@jhu.edu
 */
@Designate(ocd = Config.class)
@Component(configurationPolicy = ConfigurationPolicy.OPTIONAL)
public class FedoraDepositFactory implements DepositFactory {

    private String baseUri = "http://localhost:8080/fcrepo/rest";

    private static final Logger LOG = LoggerFactory.getLogger(FedoraDepositFactory.class);

    /**
     * Set the Fedora baseURI.
     *
     * @param base the base URI.
     */
    public void setBaseUri(final String base) {
        this.baseUri = base;
    }

    /**
     * Whether to use Fedora transaction/batch operations.
     *
     * @param useTransactions true to use transactions
     */
    public void setUseTransactions(final boolean useTransactions) {
        this.doTx = useTransactions;
    }

    /**
     * Use SPARQL/Update PATCH requests.
     *
     * @param useSparql if True, updates to objects will use SPARQL/Update PATCH.
     */
    public void setUseSparql(final boolean useSparql) {
        this.setUseTransactions(true);
    }

    public boolean doTx = true;

    public boolean useSparql = true;

    /**
     * Configure via OSGi.
     *
     * @param config configuration params.
     */
    @Activate
    public void configure(final Config config) {
        setBaseUri(config.baseUri());
        setUseTransactions(this.doTx = config.useTransactions());
        setUseSparql(this.useSparql = config.useSparqlPatch());
    }

    @Override
    public Depositor newDepositor(final URI depositInto, final Map<String, Object> context) {

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

        private TxDepositor(final URI txBase, final URI canonicalDepositInto, final FcrepoClient client) {
            this.txBase = txBase;

            this.client = client;

            // This is a little hairy, but there's no real alternative!
            if (canonicalDepositInto != null) {
                txDepositInto = URI.create(canonicalDepositInto.toString().replace(baseUri, txBase.toString()));
            } else {
                LOG.warn("No deposit container specifying, using /");
                txDepositInto = txBase;
            }
        }

        @Override
        public DepositedResource deposit(final PackagedResource resource, final URI parent) {

            final URI depositInto = parent == null ? txDepositInto : parent;

            final DepositedResource deposited = new DepositedResource();

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

        private URI doDeposit(final PackagedResource resource, final URI parent) {
            LOG.debug("Depositing {} into {}", resource.getURI(), parent);
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

        private String fileNameIfBinary(final PackagedResource resource) {
            if (NONRDFSOURCE.equals(resource.getType())) {
                return fileName(resource);
            }
            return null;
        }

        private void doReplace(final PackagedResource resource, final URI nonrdfsource, final URI target) {

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

        private InputStream sparqlAdd(final PackagedResource resource, final URI into) throws IOException {

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
        public void remap(final URI toRemap, final Map<URI, URI> localToRepository) {
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

        private String withoutHash(final String uri) {
            if (uri.contains("#")) {
                return uri.substring(0, uri.indexOf('#'));
            } else {
                return uri;
            }
        }
    }

    // Creates a SPARQL/Update patch which, when applied to the original model, result in the updated.
    private static InputStream makeSparqlPatch(final Model orig, final Model updated) {
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

    private static String fileName(final PackagedResource resource) {

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

    private void checkError(final FcrepoResponse response) throws FcrepoOperationFailedException, IOException {
        if (response.getStatusCode() >= 400) {
            throw new FcrepoOperationFailedException(response.getUrl(), response.getStatusCode(),
                    IOUtils.toString(response.getBody(), "utf8"));
        }
    }
}
