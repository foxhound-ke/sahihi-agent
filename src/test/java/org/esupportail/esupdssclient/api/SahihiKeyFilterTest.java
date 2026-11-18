/**
 * Sahihi signing agent: a fork of Esup-DSS-Client (EUPL-1.2). This file was
 * added by Foxhound Security & Development Consultants Ltd; see SAHIHI.md.
 */
package org.esupportail.esupdssclient.api;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SahihiKeyFilterTest {

	private static CertificateToken load(final String name) throws Exception {
		try (InputStream in = SahihiKeyFilterTest.class.getResourceAsStream("/sahihi/" + name)) {
			return DSSUtils.loadCertificate(in);
		}
	}

	@Test
	public void onlySigningCertificatesAreOffered() throws Exception {
		assertTrue(SahihiKeyFilter.accepts(load("signing.pem"), ""));
		assertFalse(SahihiKeyFilter.accepts(load("no-non-repudiation.pem"), ""));
		assertFalse(SahihiKeyFilter.accepts(null, ""));
	}

	@Test
	public void theIssuerMustMatchWhenItIsSet() throws Exception {
		final CertificateToken signing = load("signing.pem");
		assertTrue(SahihiKeyFilter.accepts(signing, "CN=Test Signer,O=Sahihi Agent Test,C=KE"));
		// Distinguished names compare as names, not as strings.
		assertTrue(SahihiKeyFilter.accepts(signing, "cn=Test Signer, o=Sahihi Agent Test, c=KE"));
		assertFalse(SahihiKeyFilter.accepts(signing, "CN=Someone Else,O=Sahihi Agent Test,C=KE"));
	}

	@Test
	public void aMalformedIssuerOffersNothing() throws Exception {
		assertFalse(SahihiKeyFilter.accepts(load("signing.pem"), "not a distinguished name ==="));
	}
}
