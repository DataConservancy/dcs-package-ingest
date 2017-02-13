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

package org.dataconservancy.packaging.ingest.http;

import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author apb@jhu.edu
 */
@SuppressWarnings("serial")
@WebServlet(asyncSupported = true, name = "PackageIngest", urlPatterns = { "/ingest" })
public class IngestServlet extends HttpServlet {

    static final Logger LOG = LoggerFactory.getLogger(IngestServlet.class);

    ExecutorService exe = Executors.newCachedThreadPool();

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        LOG.info("Async supported? " + req.isAsyncSupported());
        LOG.info("Async started? " + req.isAsyncStarted());
        final AsyncContext cxt = req.startAsync();
        cxt.setTimeout(0);

        exe.execute(() -> {
            final HttpServletResponse response = response(cxt);
            response.setStatus(SC_OK);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setContentType("text/event-stream");

            try {
                final PrintWriter out = response.getWriter();

                for (int i = 0; i < 10; i++) {
                    out.println("event: count");
                    out.println("data: " + i);
                    out.println();
                    out.flush();
                    response.flushBuffer();
                    Thread.sleep(1000);
                }

                out.println("event: error");
                out.println("data: done");
                out.println("\n");
                out.flush();
                response.flushBuffer();
                cxt.complete();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        LOG.info("Async supported? " + req.isAsyncSupported());
        LOG.info("Async started? " + req.isAsyncStarted());
        final AsyncContext cxt = req.startAsync();
        cxt.setTimeout(0);

        exe.execute(() -> analyzeArchive(cxt));

    }

    private static InputStream decompressIfNecessary(final InputStream in) {
        try {
            return new CompressorStreamFactory().createCompressorInputStream(buffered(in));
        } catch (final CompressorException e) {
            return in;
        }
    }

    private static ArchiveInputStream archivesOf(final InputStream in) throws ServletException {
        try {
            return new ArchiveStreamFactory().createArchiveInputStream(buffered(in));
        } catch (final ArchiveException e) {
            return new TarArchiveInputStream(in);
        }
    }

    private static InputStream buffered(final InputStream i) {
        if (i.markSupported()) {
            return i;
        }

        return new BufferedInputStream(i, 1024);
    }

    private static void analyzeArchive(final AsyncContext cxt) {

        final HttpServletResponse response = response(cxt);
        response.setStatus(SC_ACCEPTED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream");

        try {
            LOG.info("preparing input stream..");
            final ArchiveInputStream in =
                    archivesOf(
                            decompressIfNecessary(cxt.getRequest().getInputStream()));
            LOG.info("got input stream");
            final PrintWriter out = response.getWriter();

            for (ArchiveEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                out.println("event: entry");
                out.println("data: " + entry.getName());
                out.println();
                out.flush();
                response.flushBuffer();
            }

            out.println("event: done");
            out.write("");

        } catch (final Exception e) {

            try {
                final PrintWriter out = response.getWriter();
                out.println("event: error");
                out.println("data: " + e.getMessage());
            } catch (final IOException x) {
                System.out.println("OH NO");
            }
        } finally {
            cxt.complete();
        }
    }

    private static HttpServletResponse response(final AsyncContext cxt) {
        return (HttpServletResponse) cxt.getResponse();
    }

}
