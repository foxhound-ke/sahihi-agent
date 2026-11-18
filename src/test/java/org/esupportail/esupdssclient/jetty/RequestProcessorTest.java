package org.esupportail.esupdssclient.jetty;

import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.internal.ResponseHttpFields;
import org.eclipse.jetty.util.Callback;
import org.esupportail.esupdssclient.api.AppConfig;
import org.esupportail.esupdssclient.api.EsupDSSClientAPI;
import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class RequestProcessorTest {

	private static final String ALLOWED = "https://sahihi.example";

	private Response response;
	private RequestProcessor rp;
	private EsupDSSClientAPI api;
	private AppConfig appConfig;
	private Request request;
	private Context context;
	private Callback callback;

	@SuppressWarnings("rawtypes")
	@Before
	public void init() {
		api = mock(EsupDSSClientAPI.class);
		request = mock(Request.class);
		callback = mock(Callback.class);
		response = mock(Response.class);
		when(response.getHeaders()).thenReturn(new ResponseHttpFields());
		when(request.getHeaders()).thenReturn(new ResponseHttpFields());
		rp = new RequestProcessor();
		appConfig = new AppConfig();
		when(api.getAppConfig()).thenReturn(appConfig);
		rp.setConfig(api);
		HttpURI mockHttpURI = mock(HttpURI.class);
		when(mockHttpURI.getCanonicalPath()).thenReturn("/");
		when(request.getHttpURI()).thenReturn(mockHttpURI);
		context = mock(Context.class);
		when(request.getContext()).thenReturn(context);
		path("/");
		when(request.getMethod()).thenReturn("GET");
		ConnectionMetaData mockConnectionMetaData = mock(ConnectionMetaData.class);
		when(mockConnectionMetaData.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 9795));
		when(request.getConnectionMetaData()).thenReturn(mockConnectionMetaData);
	}

	private void configure(final String corsAllowedOrigin) throws Exception {
		final Properties props = new Properties();
		if (corsAllowedOrigin != null) {
			props.load(new StringReader("cors_allowed_origin=" + corsAllowedOrigin));
		}
		appConfig.loadFromProperties(props);
	}

	private void path(final String path) {
		when(context.getPathInContext(anyString())).thenReturn(path);
	}

	private void header(final String name, final String value) {
		((ResponseHttpFields) request.getHeaders()).put(name, value);
	}

	private String allowOrigin() {
		return response.getHeaders().get("Access-Control-Allow-Origin");
	}

	@Test
	public void anAllowedOriginIsEchoed() throws Exception {
		configure(ALLOWED + ", http://localhost:8000");
		header("Origin", ALLOWED);
		rp.handle(request, response, callback);
		assertEquals(ALLOWED, allowOrigin());
		verify(response, never()).setStatus(403);
	}

	@Test
	public void anUnlistedOriginIsRefused() throws Exception {
		configure(ALLOWED);
		header("Origin", "https://elsewhere.example");
		rp.handle(request, response, callback);
		verify(response).setStatus(403);
		assertNull(allowOrigin());
	}

	@Test
	public void aRequestWithoutOriginIsRefused() throws Exception {
		configure(ALLOWED);
		path("/rest/certificates");
		rp.handle(request, response, callback);
		verify(response).setStatus(403);
	}

	@Test
	public void theLaunchersOwnCheckNeedsNoOrigin() throws Exception {
		configure(ALLOWED);
		path("/nexu-info");
		rp.handle(request, response, callback);
		verify(response, never()).setStatus(403);
	}

	@Test
	public void nothingIsAllowedByDefault() throws Exception {
		configure(null);
		header("Origin", ALLOWED);
		rp.handle(request, response, callback);
		verify(response).setStatus(403);
	}

	@Test
	public void aWildcardIsNotAccepted() throws Exception {
		configure("*");
		header("Origin", ALLOWED);
		rp.handle(request, response, callback);
		verify(response).setStatus(403);
		assertNull(allowOrigin());
	}

	@Test
	public void aPrivateNetworkPreflightIsAnsweredForAnAllowedOrigin() throws Exception {
		configure(ALLOWED);
		header("Origin", ALLOWED);
		header("Access-Control-Request-Private-Network", "true");
		when(request.getMethod()).thenReturn("OPTIONS");
		rp.handle(request, response, callback);
		assertEquals("true", response.getHeaders().get("Access-Control-Allow-Private-Network"));
		assertEquals(ALLOWED, allowOrigin());
	}
}
