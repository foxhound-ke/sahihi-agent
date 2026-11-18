# Sahihi signing agent

The Sahihi signing agent lets a signer make an Advanced signature in Sahihi
(Foxhound's document-signing service) with their own Foxhound certificate, whose
key stays in the Windows certificate store. The Sahihi page calls the agent on
`http://127.0.0.1:9795`, sends it the bytes to be signed (a few hundred bytes
of CMS signed attributes, never the document), and gets back a signature value
and the certificate chain. The agent shows its certificate picker, and Windows
asks for consent before the key signs.

This is a demo build for Sahihi's proof of concept (milestone 3). Documents
signed on the PoC have no legal effect.

## Licence and origin

The agent is a modified version of
[Esup-DSS-Client](https://github.com/EsupPortail/esup-dss-client) by the ESUP
consortium (Université Rouen Normandie), itself derived from Nowina Solutions'
NexU. Both are under the European Union Public Licence, version 1.2
(`LICENSE`; the English text is at
<https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12>). The fork is
under the same licence.

- Upstream: `https://github.com/EsupPortail/esup-dss-client`, tag `1.3.0`,
  commit `0066a88e93dbd3adaad4955c91ac4550000b5731` (17 Jul 2026).
- Modified by Foxhound Security & Development Consultants Ltd. As EUPL
  article 5 requires, every change is listed below with its date, and each is
  its own commit on top of the upstream tag.
- The source is published wherever an installer is offered: the agent's
  repository under Foxhound's GitHub organisation, linked next to every
  download (with its SHA-256) on `trust.foxhound.co.ke`.

## Changes from upstream 1.3.0

All made on 18 Nov 2026.

| Change | Why |
|---|---|
| The name "Sahihi signing agent" in window titles, dialogs, the tray, the Windows installer, its shortcuts and its uninstall entry. Java packages and file names (`esup-dss-client.jar`, the zip) stay as upstream's. | Signers see what they're running; rebasing on upstream stays easy. |
| Per-computer settings from `sahihi-agent.properties` in the install directory (or `-Dsahihi.agent.config=<file>`), overriding the defaults in the jar. The installer ships one. | Sahihi's domain isn't decided, so the allowed origins are configuration, never code. |
| Listens on `binding_ip` (127.0.0.1) itself, and only ever on a loopback address. | Upstream bound to whatever "localhost" resolves to, which can be `::1`. |
| Every request needs an `Origin` listed in `cors_allowed_origin`; anything else gets 403 before any dialog. `*` is refused and the default list is empty. Only exception: the launcher's own `GET /nexu-info`. | CORS only stops a page reading the answer; it doesn't stop a cross-site request reaching the agent and opening its dialogs. |
| `/rest/sign` and `/rest/certificates` take only `POST` with `Content-Type: application/json` (415 otherwise). The GET forms (`?dataToSign=...`), `/rest/identityInfo` and `/rest/authenticate` are gone. | A JSON body forces a CORS preflight; `/rest/authenticate` was another way to use the key. |
| No outbound connections: no `server_url`, no anonymous statistics, no incident report, no product database download, no keystore from a URL. | The agent has no business on the network. |
| Logs the route and the outcome only, never a request body, a key, a certificate's details or a signature value. | Upstream logged every body (what is to be signed) at INFO. |
| The Windows certificate store is the only key source: the keystore plugin (PKCS #12/JKS files) and the OpenSC plugin are off, and the agent goes straight to the store. The installer no longer installs OpenSC. | A key in a file isn't what Advanced promises; hardware keys come later. |
| Only certificates with nonRepudiation are offered, whatever the page asks, when choosing and when signing; optionally only those from the issuer in `certificate_issuer`. | Upstream filtered only when asked. |
| `GET /rest/info`: `{"name", "version", "keySources"}`. | The Advanced page's first call: is the agent there, and new enough? |
| CI builds the Windows zip on `windows-latest` and keeps it, with its SHA-256, as an artifact. | The agent ships for Windows. |
| `tools/spike.html` and `tools/verify_signature.py`. | The spike below. |

Not changed, and why:

- **HTTPS listener.** Upstream 1.3.0 has none (`binding_ports_https` is read
  but never used), so there was nothing to turn off. `127.0.0.1` is a secure
  context for browsers, so an HTTPS page may call it.
- **The installer runs elevated** (`<run-privileged/>`), as upstream's does. For
  production: a per-user MSI built with `jpackage`, Authenticode-signed (see
  Sahihi's `docs/milestone-3.md`, "After the demo").
- **Unsigned.** The demo zip isn't code-signed; Windows SmartScreen will warn.

## Settings (`sahihi-agent.properties`)

| Key | Meaning |
|---|---|
| `cors_allowed_origin` | Comma-separated origins (`scheme://host[:port]`, exactly as the browser sends them) allowed to call the agent. The shipped file allows the PoC's Sahihi and `http://localhost:8000`. |
| `certificate_issuer` | Empty, or the issuer distinguished name every offered certificate must have. |

Restart the agent (tray icon, then Exit) after changing it.

## What Sahihi calls

All answers are upstream's Gson-serialised `Execution` objects,
`{"success": true, "response": {...}}` or `{"success": false, "error": "<code>",
"errorMessage": "..."}`; binary values are base64. Sahihi's
`sahihi/signing/agent.py` reads them.

- `GET /rest/info` → `{"name", "version", "keySources"}` (not wrapped).
- `POST /rest/certificates` with `{}` → `tokenId`, `keyId`, `certificate`,
  `certificateChain`, `encryptionAlgorithm`, `supportedDigests`,
  `preferredDigest`.
- `POST /rest/sign` with `{"tokenId": {"id": ...}, "keyId": ...,
  "toBeSigned": {"bytes": "<base64>"}, "digestAlgorithm": "SHA256"}` →
  `signatureValue`, `signatureAlgorithm` (`RSA_SHA256`), `certificate`,
  `certificateChain`. The agent hashes `toBeSigned` with SHA-256 and signs it
  (PKCS #1 v1.5 for RSA).
- The signer cancelling: `{"success": false, "error": "user.cancel"}`.

## Building

On Linux, with Docker (no local JDK needed):

```sh
docker run --rm -v "$PWD":/src -v sahihi-agent-m2:/root/.m2 -w /src \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp package    # tests + jar
# mvn install also downloads Azul's JRE and builds target/esup-dss-client-win64.zip
```

CI (`.github/workflows/maven.yml`) runs `mvn clean install` on `windows-latest`
and keeps `esup-dss-client-win64.zip` with its SHA-256.

## The spike (milestone 3.1)

On the Windows test machine, with the demo certificate from 3.0 in the
Personal store and the agent installed from the CI zip:

1. Serve the spike page twice from `tools/`: `python -m http.server 8000` and,
   in another window, `python -m http.server 8001`. (The dev Sahihi on port
   8000 is the same origin.)
2. In Chrome, open `http://localhost:8000/spike.html`. Allow the local-network
   prompt if it appears. Run 1 (info), 2 (choose the demo certificate) and 3
   (sign: Windows asks for consent), then save the result.
3. `python tools/verify_signature.py spike-result.json` must print `OK`.
4. Run 4: the plain-text POST must be refused (415) with no dialog.
5. Repeat 2-4 in Edge and Firefox.
6. Open `http://localhost:8001/spike.html` (not an allowed origin). Every step
   must fail **before any dialog opens**, and the agent's log says `Refused`.
7. While the agent runs, `netstat -ano | findstr <agent PID>` (or Resource
   Monitor, Network) shows only the listener on `127.0.0.1:9795`: no outbound
   connections.
8. Note what the signer sees at each step (the agent's picker, Windows' strong
   key protection prompt, the browser's local-network prompt), with
   screenshots.

Done when the signature verifies in all three browsers, the foreign origin is
refused, and the agent makes no outbound connections.

## Remotes

`upstream` is EsupPortail's repository, for fetching only (pushing to it is
disabled in this clone). The repository is public from the start under
Foxhound's GitHub organisation once that exists; nothing has been pushed yet.
