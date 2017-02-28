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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoResponse;
import org.fcrepo.client.GetBuilder;
import org.fcrepo.client.HeadBuilder;
import org.fcrepo.client.PostBuilder;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author apb@jhu.edu
 */
@RunWith(MockitoJUnitRunner.class)
public class ExtensionRegistrationTest {

    @Mock
    FcrepoClient client;

    @Mock
    HeadBuilder head;

    @Mock
    FcrepoResponse headResponse;

    @Mock
    GetBuilder get;

    @Mock
    FcrepoResponse getResponse;

    @Mock
    PostBuilder post;

    @Mock
    FcrepoResponse postResponse;

    @Captor
    ArgumentCaptor<InputStream> captor;

    static final URI LOADER_SERVICE_URI = URI.create("http://localhost/services//apix:load");

    @Test
    public void successfulRegistrationTest() throws Exception {
        final int port = 1234;
        final String hostName = "example.org";
        final String path = "/this/path";

        final URI baseUri = URI.create("http://repository/base/");

        final URI serviceDocURI = URI.create("http://repository/serviceDocUri");

        when(client.head(any(URI.class))).thenReturn(head);
        when(head.perform()).thenReturn(headResponse);
        when(headResponse.getLinkHeaders(any(String.class))).thenReturn(Arrays.asList(serviceDocURI));

        when(client.get(any(URI.class))).thenReturn(get);
        when(get.accept(any(String.class))).thenReturn(get);
        when(get.perform()).thenReturn(getResponse);
        when(getResponse.getBody()).thenReturn(this.getClass().getResourceAsStream("/serviceDoc.ttl"));

        when(client.post(LOADER_SERVICE_URI)).thenReturn(post);
        when(post.body(any(InputStream.class), eq("text/plain"))).thenReturn(post);
        when(post.perform()).thenReturn(postResponse);
        when(postResponse.getStatusCode()).thenReturn(303);
        when(postResponse.getLocation()).thenReturn(URI.create("http://example.org/success"));

        new ExtensionRegistration().withClient(client).withPort(1234).withHostName(hostName).withPath(path)
                .withRepositoryBaseUri(baseUri).load();

        verify(client).head(eq(baseUri));
        verify(headResponse).getLinkHeaders("service");
        verify(client).get(serviceDocURI);
        verify(post).perform();
        verify(post).body(captor.capture(), eq("text/plain"));

        final String submittedURI = IOUtils.toString(captor.getValue(), UTF_8);
        assertEquals("http://" + hostName + ":" + port + path, submittedURI);
    }
}
