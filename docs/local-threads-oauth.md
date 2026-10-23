# Local Threads OAuth with WireMock

## Overview

Use the repository's `social-api-wiremock` service to exercise the Threads connection flow without calling Meta. The browser authorizes against a local OAuth stub; SMP exchanges the mock authorization code, refreshes the token, fetches the mock profile, and stores the resulting connection.

The mock is development-only. Its fixed authorization code, user identity, and tokens are not credentials and must never be used outside local testing.

## Changes

This guide covers the local WireMock-backed Threads OAuth setup, including the mock endpoints, local API URL, and JVM trust required for Portless TLS.

## Usage

### Prerequisites

- Docker and the repository dependencies are available.
- Portless is installed and its HTTPS proxy is running; see [Portless setup](./portless-setup.md).
- A local `.env` has non-empty `SMP_THREADS_CLIENT_ID`, `SMP_THREADS_CLIENT_SECRET`, and `SMP_THREADS_REDIRECT_URI` values. Use throwaway local values; do not use production credentials.
- The Threads redirect URI is the app callback registered in local configuration, normally `https://pt-app.localhost/integrations/threads/callback`.

The `.env.example` describes the Threads settings. Keep local values in the ignored `.env`; do not commit them.

### Start the mock

The root `compose.yaml` owns the `social-api-wiremock` service and publishes it on host port `8089` by default. Start repository infrastructure and register the local HTTPS alias:

```bash
just infra-up
bash scripts/setup-threads-mock.sh
```

The alias is `https://threads-mock.localhost`. If `WIREMOCK_HOST_PORT` is customized, the setup script derives the published host port from Compose. Confirm the route with:

```bash
pnpm exec portless list
```

The Threads mappings live in `infra/wiremock/mappings/threads/`:

- `015-oauth-authorize.json` returns a redirect to the app callback with a mock authorization code and the incoming OAuth `state`. The callback uses the supplied `redirect_uri` or falls back to `https://pt-app.localhost/integrations/threads/callback`.
- `070-oauth-token.json` accepts the form-urlencoded authorization-code exchange at `/oauth/access_token`.
- `071-oauth-long-lived-token.json` accepts the GET request to `/access_token` that exchanges the short-lived token.
- `072-oauth-refresh-token.json` accepts the GET request to `/refresh_access_token` and returns a mock refreshed token.
- `073-user-profile.json` returns a stable mock profile.

WireMock loads these mappings through the root Compose service. Rebuild that service after changing a mapping:

```bash
node scripts/compose-run.mjs up -d --build social-api-wiremock
```

### Configure the local backend

Set the local Threads API base URL in `.env`:

```dotenv
SMP_THREADS_ENABLED=true
SMP_THREADS_CLIENT_ID=local-threads-client
SMP_THREADS_CLIENT_SECRET=local-threads-secret
SMP_THREADS_REDIRECT_URI=https://pt-app.localhost/integrations/threads/callback
SMP_THREADS_API_BASE_URL=https://threads-mock.localhost
SMP_THREADS_AUTHORIZATION_URL=https://threads-mock.localhost/oauth/authorize
SMP_THREADS_TOKEN_EXCHANGE_URL=https://threads-mock.localhost/oauth/access_token
SMP_THREADS_LONG_LIVED_TOKEN_URL=https://threads-mock.localhost/access_token
```

Use the exact redirect URI configured for this local app. Restart SMP after changing `.env`; the running backend does not pick up changes automatically.

### Let the local JVM trust Portless

The browser and the Java backend are separate TLS clients. Trusting Portless in macOS/browser does not automatically make the JDK trust its CA. SMP must trust Portless because it calls the mock over HTTPS. Do not disable certificate or hostname verification.

On macOS, Portless stores its local CA at `~/.portless/ca.pem`. Create a private truststore under the ignored `.worktree/` directory, seeded from the active JDK's regular CA set, and import only the Portless CA:

```bash
JAVA_HOME="$(/usr/libexec/java_home)"
TRUSTSTORE="$PWD/.worktree/portless-truststore.p12"
mkdir -p .worktree
cp "$JAVA_HOME/lib/security/cacerts" "$TRUSTSTORE"
keytool -importcert -noprompt -trustcacerts \
  -alias portless-local-ca \
  -file "$HOME/.portless/ca.pem" \
  -keystore "$TRUSTSTORE" \
  -storepass changeit
chmod 600 "$TRUSTSTORE"
```

This retains the JDK's existing public trust anchors and adds the local Portless root CA. The truststore is local, ignored, and must not be committed. Recreate it if the JDK changes or Portless regenerates its CA. The example uses the JDK's default local truststore password; it is not a production secret.

Start or restart the repository services with those JVM properties in the environment so the backend process inherits them:

```bash
JAVA_HOME="$(/usr/libexec/java_home)" \
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$PWD/.worktree/portless-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
just serve --force
```

For an already-running `just serve`, stop it with the repository's `just kill-servers` recipe and start it again with the environment above. The trust properties are for local JVM processes launched by this command; they do not change the JDK installation, system trust settings, browser trust, or production configuration. See [Portless setup: JVM clients](./portless-setup.md#trusting-portless-from-local-jvm-clients) for more detail.

Before retrying OAuth, check the Java TLS path independently:

```bash
jshell \
  -R-Djavax.net.ssl.trustStore="$PWD/.worktree/portless-truststore.p12" \
  -R-Djavax.net.ssl.trustStorePassword=changeit <<'EOF'
import java.net.URI;
import java.net.http.*;
var client = HttpClient.newHttpClient();
var response = client.send(HttpRequest.newBuilder(URI.create("https://threads-mock.localhost/__admin/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
System.out.println("status=" + response.statusCode());
EOF
```

A `status=200` confirms that this JDK process trusts the Portless certificate and can reach WireMock. It does not by itself prove that the SMP process inherited the same settings; restart SMP with the environment above.

### Exercise the OAuth flow

1. Open `https://pt-app.localhost` and sign in with the local account.
2. Start a **new** Threads connection from the dashboard. Do not reuse an old callback URL: OAuth `state` is short-lived and each attempt must begin with a fresh authorization request.
3. The browser should visit `threads-mock.localhost`, then return to the dashboard callback.
4. The dashboard submits the authorization code to `POST /api/publishing/threads/connections/complete`.
5. SMP should exchange the code at `/oauth/access_token`, exchange the short-lived token at `/access_token`, refresh it with GET `/refresh_access_token`, fetch `/v1.0/me`, and complete the connection.
6. Confirm Threads appears as connected in the dashboard and remains connected after reloading the page.

The mock profile is `wiremock_threads_user` with ID `local-wiremock-threads-user-001`. The access tokens are generated mock values and are not accepted by Meta.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `threads-mock.localhost` does not resolve or Portless returns an error | Start the Portless proxy, run `pnpm exec portless list`, and confirm the alias points to the published WireMock host port. Re-run `bash scripts/setup-threads-mock.sh` if needed. |
| WireMock endpoints return 404 | Confirm `social-api-wiremock` is running through root Compose (`just infra-up`) and that the Threads mapping files exist. Rebuild the service after mapping edits. |
| Browser callback reports an expired or invalid state | Start a fresh connection attempt from the dashboard; do not replay the previous callback URL. |
| SMP reports `SSLHandshakeException` or `PKIX path building failed` | Confirm the Portless CA is present in `.worktree/portless-truststore.p12`, then restart `just serve --force` with `JAVA_TOOL_OPTIONS` and the truststore path. Do not disable TLS validation. |
| OAuth endpoint returns 404 or does not match | Confirm `.env` sets `SMP_THREADS_AUTHORIZATION_URL=https://threads-mock.localhost/oauth/authorize`, `SMP_THREADS_TOKEN_EXCHANGE_URL=https://threads-mock.localhost/oauth/access_token`, `SMP_THREADS_LONG_LIVED_TOKEN_URL=https://threads-mock.localhost/access_token`, and `SMP_THREADS_API_BASE_URL=https://threads-mock.localhost` for refresh and Graph API calls. Restart SMP and rebuild the WireMock service after mapping edits. |
| OAuth succeeds but Threads is not visible | Check the SMP log for the completion request and subsequent profile/persistence errors; then reload the dashboard and verify the active workspace. |

Local logs are under `.worktree/logs/` when using the repository's managed `just serve` workflow. Never include credentials or tokens when sharing logs.

## References

- [Portless setup](./portless-setup.md)
- [Threads settings in `.env.example`](../.env.example)
- [Root Compose configuration](../compose.yaml)
- [Threads WireMock mappings](../infra/wiremock/mappings/threads/)
- [SMP configuration reference](../server/smp/README.md)
