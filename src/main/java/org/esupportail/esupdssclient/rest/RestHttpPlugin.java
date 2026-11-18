/**
 * © Nowina Solutions, 2015-2015
 *
 * Concédée sous licence EUPL, version 1.1 ou – dès leur approbation par la Commission européenne - versions ultérieures de l’EUPL (la «Licence»).
 * Vous ne pouvez utiliser la présente œuvre que conformément à la Licence.
 * Vous pouvez obtenir une copie de la Licence à l’adresse suivante:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Sauf obligation légale ou contractuelle écrite, le logiciel distribué sous la Licence est distribué «en l’état»,
 * SANS GARANTIES OU CONDITIONS QUELLES QU’ELLES SOIENT, expresses ou implicites.
 * Consultez la Licence pour les autorisations et les restrictions linguistiques spécifiques relevant de la Licence.
 */
package org.esupportail.esupdssclient.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.europa.esig.dss.model.x509.CertificateToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.esupportail.esupdssclient.api.*;
import org.esupportail.esupdssclient.api.plugin.*;
import org.esupportail.esupdssclient.json.ByteArrayTypeAdapter;
import org.esupportail.esupdssclient.windows.keystore.WindowsKeystore;
import org.esupportail.esupdssclient.json.CertificateTypeAdapter;
import org.esupportail.esupdssclient.json.LocalDateAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Default implementation of HttpPlugin for Esup-DSS-Client.
 *
 * @author David Naramski
 */
public class RestHttpPlugin implements HttpPlugin {

	private static final Logger logger = LoggerFactory.getLogger(RestHttpPlugin.class.getName());

	private static final Gson customGson = new GsonBuilder()
			.registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
			.registerTypeHierarchyAdapter(byte[].class, new ByteArrayTypeAdapter())
			.registerTypeAdapter(CertificateToken.class, new CertificateTypeAdapter()).create();

	@Override
	public List<InitializationMessage> init(String pluginId, EsupDSSClientAPI api) {
		return Collections.emptyList();
	}

	@Override
	public HttpResponse process(EsupDSSClientAPI api, HttpRequest req) throws Exception {

		final String target = req.getTarget();
		logger.info("PathInfo " + target);

		// Sahihi: never log a request body (it holds what is to be signed).
		final String payload = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);

		// Sahihi: only the routes Sahihi uses: /info, and the two that open
		// dialogs, each with a JSON body (the request processor has checked it's
		// a JSON POST). Upstream's GET forms (?dataToSign=...) and /identityInfo
		// and /authenticate are gone.
		switch(target) {
		case "/info":
			return info(api);
		case "/sign":
			return signRequest(api, payload);
		case "/certificates":
			return getCertificates(api, payload);
		default:
			return new HttpResponse("{\"success\":false,\"error\":\"not.found\"}",
					"application/json;charset=UTF-8", HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * Sahihi: GET /rest/info, which the Advanced page calls first to find the
	 * agent and check its version: {"name", "version", "keySources"}. It opens
	 * no dialog and says nothing about the certificates.
	 */
	private HttpResponse info(final EsupDSSClientAPI api) {
		final Map<String, Object> info = new LinkedHashMap<>();
		info.put("name", api.getAppConfig().getApplicationName());
		info.put("version", api.getAppConfig().getApplicationVersion());
		final List<String> sources = new ArrayList<>();
		for (final Product product : api.detectProducts()) {
			if (product instanceof WindowsKeystore) {
				sources.add("windows-certificate-store");
			}
		}
		info.put("keySources", sources);
		return new HttpResponse(customGson.toJson(info), "application/json;charset=UTF-8", HttpStatus.OK);
	}

	private static HttpResponse badRequest() {
		return new HttpResponse("{\"success\":false,\"error\":\"bad.request\"}",
				"application/json;charset=UTF-8", HttpStatus.BAD_REQUEST);
	}

	protected <T> Execution<T> returnNullIfValid(EsupDSSClientRequest request) {
		return null;
	}
	
	private HttpResponse signRequest(EsupDSSClientAPI api, String payload) {
		logger.info("Signature");
		if (StringUtils.isEmpty(payload)) {
			return badRequest();
		}
		final SignatureRequest r = customGson.fromJson(payload, SignatureRequest.class);
		if (r == null) {
			return badRequest();
		}

		final HttpResponse invalidRequestHttpResponse = checkRequestValidity(api, r);
		if(invalidRequestHttpResponse != null) {
			return invalidRequestHttpResponse;
		} else {
			logger.info("Call API");
			final Execution<?> respObj = api.sign(r);
			return toHttpResponse(respObj);
		}
	}

	private HttpResponse getCertificates(EsupDSSClientAPI api, String payload) {
		logger.info("API call certificates");
		final GetCertificateRequest r = StringUtils.isEmpty(payload)
				? new GetCertificateRequest()
				: customGson.fromJson(payload, GetCertificateRequest.class);
		if (r == null) {
			return badRequest();
		}

		final HttpResponse invalidRequestHttpResponse = checkRequestValidity(api, r);
		if(invalidRequestHttpResponse != null) {
			return invalidRequestHttpResponse;
		} else {
			logger.info("Call API");
			final Execution<?> respObj = api.getCertificate(r);
			return toHttpResponse(respObj);
		}
	}

	private HttpResponse checkRequestValidity(final EsupDSSClientAPI api, final EsupDSSClientRequest request) {
		final Execution<Object> verification = returnNullIfValid(request);
		if(verification != null) {
			final Feedback feedback;
			if(verification.getFeedback() == null) {
				feedback = new Feedback();
				feedback.setFeedbackStatus(FeedbackStatus.SIGNATURE_VERIFICATION_FAILED);
				verification.setFeedback(feedback);
			} else {
				feedback = verification.getFeedback();
			}
			feedback.setInfo(api.getEnvironmentInfo());
			feedback.setApiVersion(api.getAppConfig().getApplicationVersion());
			return toHttpResponse(verification);
		} else {
			return null;
		}
	}
	
	private HttpResponse toHttpResponse(final Execution<?> respObj) {
		// Sahihi: the outcome only, never the response (certificates, signature value).
		logger.info("Outcome: " + (respObj.isSuccess() ? "success" : "error " + respObj.getError()));
		if (respObj.isSuccess()) {
			return new HttpResponse(customGson.toJson(respObj), "application/json;charset=UTF-8", HttpStatus.OK);
		} else {
			return new HttpResponse(customGson.toJson(respObj), "application/json;charset=UTF-8", HttpStatus.ERROR);
		}
	}
}
