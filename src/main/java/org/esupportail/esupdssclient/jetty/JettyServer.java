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
package org.esupportail.esupdssclient.jetty;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;

import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class JettyServer extends AbstractJettyServer {

	@Override
	protected Connector[] getConnectors() {
		final HttpConfiguration http = new HttpConfiguration();
		final JettyListAwareServerConnector connector = new JettyListAwareServerConnector(getServer());
		connector.addConnectionFactory(new HttpConnectionFactory(http));
		connector.setPorts(getApi().getAppConfig().getBindingPorts());
		// Sahihi: bind to binding_ip itself (127.0.0.1), not to whatever "localhost"
		// resolves to, so the page's calls to http://127.0.0.1:9795 always arrive;
		// and never to anything but a loopback address.
		connector.setHost(loopback(getApi().getAppConfig().getBindingIP()));
		return new Connector[]{connector};
	}

	static String loopback(final String bindingIp) {
		try {
			if (InetAddress.getByName(bindingIp).isLoopbackAddress()) {
				return bindingIp;
			}
		} catch (final UnknownHostException e) {
			// fall through
		}
		LoggerFactory.getLogger(JettyServer.class).warn(
				"binding_ip " + bindingIp + " isn't a loopback address; using 127.0.0.1.");
		return "127.0.0.1";
	}
}
