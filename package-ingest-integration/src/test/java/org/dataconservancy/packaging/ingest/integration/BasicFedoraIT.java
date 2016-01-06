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
	public final String baseURI = System.getProperty("fedora.baseURI",
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
		HttpGet get = new HttpGet(
				response.getHeaders(HttpHeaders.LOCATION)[0].getValue());
		response = client.execute(get);
		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

	}
}
