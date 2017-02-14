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
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataconservancy.packaging.ingest.DepositBuilder;
import org.dataconservancy.packaging.ingest.EventType;
import org.dataconservancy.packaging.ingest.PackageDepositManager;

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

    PackageDepositManager depositManager;

    /** No arg constructor */
    public IngestServlet() {
    }

    /** Initialize with a specific package deposit manager */
    public IngestServlet(final PackageDepositManager manager) {
        this.depositManager = manager;
    }

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

        final AsyncContext cxt = req.startAsync();
        cxt.setTimeout(0);

        final DepositBuilder deposit = depositManager.newDeposit()
                .withPackage(cxt.getRequest().getInputStream())
                .intoContainer(uriFromRequest(req));

        exe.execute(() -> execDeposit(deposit, cxt));

    }

    private static void execDeposit(final DepositBuilder deposit, final AsyncContext cxt) {
        final HttpServletResponse response = response(cxt);
        final PrintWriter out;
        try {
            out = response.getWriter();
        } catch (final IOException e) {
            LOG.warn("Could not open response writer", e);
            response.setStatus(SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setStatus(SC_ACCEPTED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream");

        deposit.withListener((event, uri, resource, detail) -> {

            // If the first event we encounter is an error, just throw an http error
            if (EventType.ERROR.equals(event) && !response.isCommitted()) {
                response.setStatus(SC_BAD_REQUEST);
                out.println("event: error");
                out.println("data: " + event + "\n");
                return;
            }

            // Write the event to the stream
            synchronized (cxt) {
                switch (event) {
                case HEARTBEAT:
                    if (response.isCommitted()) {
                        out.println(":");
                    } else {
                        return;
                    }
                    break;
                default:
                    out.println("event: " + event.toString());
                    if (detail != null) {
                        out.println("data: " + detail);
                    }
                    out.println();
                }
                out.flush();
                flushResponse(response);
            }
        });

        cxt.complete();
    }

    private static void flushResponse(final HttpServletResponse response) {
        try {
            response.flushBuffer();
        } catch (final IOException e) {
            LOG.warn("Could not flush response", e);
        }
    }

    private static HttpServletResponse response(final AsyncContext cxt) {
        return (HttpServletResponse) cxt.getResponse();
    }

    private static URI uriFromRequest(final HttpServletRequest req) {
        if (req.getHeader("Apix-Ldp-Container") != null) {
            LOG.debug("Got container {} fom http header", req.getHeader("Apix-Ldp-Container"));
            return URI.create(req.getHeader("Apix-Ldp-Container"));
        } else if (req.getParameter("container") != null) {
            LOG.debug("Got container {} from parameter", req.getParameter("container"));
            return URI.create(req.getParameter("container"));
        } else {
            return URI.create("http://localhost:8080/fcrepo/rest");
        }
    }

}
