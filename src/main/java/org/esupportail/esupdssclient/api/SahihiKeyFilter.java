/**
 * Sahihi signing agent: a fork of Esup-DSS-Client (EUPL-1.2). This file was
 * added by Foxhound Security & Development Consultants Ltd; see SAHIHI.md.
 */
package org.esupportail.esupdssclient.api;

import eu.europa.esig.dss.enumerations.KeyUsageBit;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Which keys the agent offers, whatever the page asks for: only those whose
 * certificate has nonRepudiation (a signing certificate) and, when
 * {@code certificate_issuer} is set, was issued by exactly that issuer. Applied
 * when the signer chooses a certificate and again when signing.
 */
public final class SahihiKeyFilter {

	private static final Logger logger = LoggerFactory.getLogger(SahihiKeyFilter.class);

	private SahihiKeyFilter() {
	}

	public static List<DSSPrivateKeyEntry> filter(final List<DSSPrivateKeyEntry> keys, final String issuer) {
		final List<DSSPrivateKeyEntry> kept = new ArrayList<>();
		for (final DSSPrivateKeyEntry key : keys) {
			if (accepts(key.getCertificate(), issuer)) {
				kept.add(key);
			}
		}
		return kept;
	}

	public static boolean accepts(final CertificateToken certificate, final String issuer) {
		if (certificate == null || !certificate.checkKeyUsage(KeyUsageBit.NON_REPUDIATION)) {
			return false;
		}
		if (StringUtils.isBlank(issuer)) {
			return true;
		}
		final X500Principal wanted;
		try {
			wanted = new X500Principal(issuer.trim());
		} catch (final IllegalArgumentException e) {
			logger.error("certificate_issuer isn't a distinguished name; offering no certificates.");
			return false;
		}
		return wanted.equals(certificate.getCertificate().getIssuerX500Principal());
	}
}
