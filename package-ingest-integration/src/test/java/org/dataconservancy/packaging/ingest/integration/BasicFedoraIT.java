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
package org.dataconservancy.packaging.ingest.integration;

import org.apache.commons.io.IOUtils;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BasicFedoraIT {

    public final String baseURI =
            System.getProperty("fedora.baseURI",
       "http://localhost:8080/fcrepo/rest/");

    private final HttpClient client = HttpClientBuilder.create()
            .setMaxConnPerRoute(Integer.MAX_VALUE)
            .setMaxConnTotal(Integer.MAX_VALUE).build();

    @Test
    public void smokeTest() throws Exception {

        /* POST a new container to Fedora */
        final HttpPost request = new HttpPost(baseURI);
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(IOUtils
                .toInputStream("<> a <http://www.w3.org/ns/ldp#BasicContainer> ."));
        request.setEntity(entity);
        request.setHeader("Content-Type", "text/turtle");
        HttpResponse response = client.execute(request);

        /* Verify that it was created */
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine()
                .getStatusCode());

        /* Verify that we can get it back */
        HttpGet get =
                new HttpGet(response.getHeaders(HttpHeaders.LOCATION)[0].getValue());
        response = client.execute(get);
        assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

    }
}
