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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dataconservancy.packaging.ingest.PackagedResource;

import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author apb@jhu.edu
 */
@RunWith(MockitoJUnitRunner.class)
public class RdfUtilTest {

    final Node predicate = NodeFactory.createURI("http://example.org/predicate");

    final String originalBase = "http://example.org/original/";

    final String newBase = "http://example.org/remapped/";

    @Mock
    PackagedResource testResource;

    @Test
    public void remapTest() {

        final Triple toRemap = Triple.create(
                NodeFactory.createURI(originalBase + "s"),
                predicate,
                NodeFactory.createURI(originalBase + "o"));

        final Map<URI, URI> map = new HashMap<>();
        map.put(URI.create(originalBase + "s"), URI.create(newBase + "s'"));
        map.put(URI.create(originalBase + "o"), URI.create(newBase + "o'"));

        final Triple remapped = RdfUtil.remap(map).apply(toRemap);

        assertEquals(newBase + "s'", remapped.getSubject().getURI());
        assertEquals(predicate, remapped.getPredicate());
        assertEquals(newBase + "o'", remapped.getObject().getURI());
    }

    @Test
    public void remapHashTest() {

        final Triple toRemap = Triple.create(
                NodeFactory.createURI(originalBase + "s#subject"),
                predicate,
                NodeFactory.createURI(originalBase + "o#object"));

        final Map<URI, URI> map = new HashMap<>();
        map.put(URI.create(originalBase + "s"), URI.create(newBase + "s"));
        map.put(URI.create(originalBase + "o"), URI.create(newBase + "o"));

        final Triple remapped = RdfUtil.remap(map).apply(toRemap);

        assertEquals(newBase + "s#subject", remapped.getSubject().getURI());
        assertEquals(predicate, remapped.getPredicate());
        assertEquals(newBase + "o#object", remapped.getObject().getURI());
    }

    @Test
    public void remapNonUriTest() {

        final Triple toRemap = Triple.create(NodeFactory.createBlankNode(), predicate, NodeFactory.createLiteral(
                "literal"));

        final Map<URI, URI> map = new HashMap<>();
        map.put(URI.create(originalBase + "s"), URI.create(newBase + "s"));
        map.put(URI.create(originalBase + "o"), URI.create(newBase + "o"));

        assertEquals(toRemap, RdfUtil.remap(map).apply(toRemap));
    }

    @Test
    public void relativeSlashTest() {
        final String base = "http://example.org/base/";

        final Triple baseTriple = Triple.create(
                NodeFactory.createURI(base + "s"), predicate,
                NodeFactory.createURI(base + "o"));

        final Triple relative = RdfUtil.relativeUris(URI.create(base)).apply(baseTriple);

        assertEquals("s", relative.getSubject().getURI());
        assertEquals("o", relative.getObject().getURI());
    }

    @Test
    public void relativeNoSlashTest() {
        final String base = "http://example.org/base";

        final Triple baseTriple = Triple.create(
                NodeFactory.createURI(base + "/s"), predicate,
                NodeFactory.createURI(base + "/o"));

        final Triple relative = RdfUtil.relativeUris(URI.create(base)).apply(baseTriple);

        assertEquals(base + "/s", relative.getSubject().getURI());
        assertEquals(base + "/o", relative.getObject().getURI());
    }

    @Test
    public void relativeHashTest() {
        final String base = "http://example.org/base";

        final Triple baseTriple = Triple.create(
                NodeFactory.createURI(base + "#s"), predicate,
                NodeFactory.createURI(base + "#o"));

        final Triple relative = RdfUtil.relativeUris(URI.create(base)).apply(baseTriple);

        assertEquals("#s", relative.getSubject().getURI());
        assertEquals("#o", relative.getObject().getURI());
    }

    @Test
    public void nullRelativeUriTest() {
        final String base = "http://example.org/base/";

        final Triple baseTriple = Triple.create(
                NodeFactory.createURI(base), predicate,
                NodeFactory.createURI(base));

        final Triple relative = RdfUtil.relativeUris(URI.create(base)).apply(baseTriple);

        assertEquals("", relative.getSubject().getURI());
        assertEquals("", relative.getObject().getURI());
    }

    @Test
    public void relativeNonUriTest() {
        final String base = "http://example.org/base/";

        final Triple baseTriple = Triple.create(
                NodeFactory.createBlankNode(), predicate,
                NodeFactory.createLiteral("literal"));

        final Triple relative = RdfUtil.relativeUris(URI.create(base)).apply(baseTriple);

        assertEquals(baseTriple, relative);
    }

    @Test
    public void skolemizeTest() {
        final Triple baseTriple = Triple.create(NodeFactory.createBlankNode(), predicate, NodeFactory
                .createBlankNode());

        final Triple filtered = RdfUtil.skolemizeToHash().apply(baseTriple);

        assertTrue(filtered.getSubject().isURI());
        assertTrue(filtered.getObject().isURI());
        assertTrue(filtered.getSubject().getURI().startsWith("#"));
        assertTrue(filtered.getObject().getURI().startsWith("#"));
    }

    @Test
    public void filterBodyRelativeUriTest() throws Exception {
        final URI base = URI.create("http://example.org/base");
        final String rdf = String.format("<%s> a <test:whatever>.\n" + "<%s#hash> a <test:somethingElse>", base,
                base);

        final AtomicBoolean closed = new AtomicBoolean(false);

        final InputStream in = new ByteArrayInputStream(rdf.getBytes(UTF_8)) {

            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };

        when(testResource.getBody()).thenReturn(in);
        when(testResource.getMediaType()).thenReturn("text/turtle");

        final PackagedResource filtered = RdfUtil.filterBody(testResource, RdfUtil.relativeUris(base));

        final String body = IOUtils.toString(filtered.getBody(), UTF_8);
        assertTrue(body.contains("<>"));
        assertTrue(body.contains("<#hash>"));
        assertFalse(body.contains(base.toString()));
        assertTrue(closed.get());

    }

    @Test
    public void filterRemoveAllTriplesTest() throws Exception {
        final String rdf = "<test:something> a <test:whatever>.\n" +
                "<test:something#hash> a <test:somethingElse>";

        when(testResource.getBody()).thenReturn(new ByteArrayInputStream(rdf.getBytes(UTF_8)));
        when(testResource.getMediaType()).thenReturn("text/turtle");

        final PackagedResource filtered = RdfUtil.filterBody(testResource, (in) -> null, in -> {
            Assert.fail("Should not have executed this");
            return in;
        });

        assertEquals(-1, filtered.getBody().read());
    }

    @Test
    public void filterSkolemizeTest() throws Exception {
        final String rdf = String.format("[] <%s> [ a <test:somethingElse> ] .\n", predicate.getURI());

        when(testResource.getBody()).thenReturn(new ByteArrayInputStream(rdf.getBytes(UTF_8)));
        when(testResource.getMediaType()).thenReturn("text/turtle");

        final PackagedResource filtered = RdfUtil.filterBody(testResource, RdfUtil.skolemizeToHash());

        final Model model = ModelFactory.createDefaultModel();
        model.read(filtered.getBody(), "", "TTL");

        final List<Resource> subjects = model.listSubjectsWithProperty(model.getProperty(predicate.getURI()))
                .filterKeep(
                        Resource::isURIResource).toList();

        assertEquals(1, subjects.size());

        final List<Statement> statementsWwithSubjejct = model.listStatements(subjects.get(0), model.getProperty(
                predicate
                        .getURI()), (Resource) null).toList();

        assertEquals(1, statementsWwithSubjejct.size());

        assertEquals(1, model.listStatements(statementsWwithSubjejct.get(0).getObject().asResource(), null,
                (Resource) null).toList().size());
    }
}
