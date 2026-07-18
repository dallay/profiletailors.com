# Production Docker Compose

## Overview

The production stack under `infra/apps/smp/production` is the supported single-server deployment
for Profile Tailors. It runs the dashboard, SMP backend, PostgreSQL, and persistent local media
storage with Docker Compose.

The stack expects TLS to terminate in a host-level reverse proxy. Only the dashboard HTTP port is
published, on `127.0.0.1:8080` by default. PostgreSQL and the backend management port remain inside
Docker networks.

## Changes

The production stack differs from `infra/apps/smp/compose.yaml` in these ways:

- It runs the application instead of development support services.
- It excludes Mailpit, WireMock, Grafana, and Prometheus.
- PostgreSQL uses a digest-pinned image and is reachable only through an internal network.
- The dashboard is built into a non-root NGINX image and proxies `/api` to the backend.
- Database, JWT, OAuth, encryption, and signing credentials use Docker Compose secrets.
- Database and media files use named volumes.
- All containers use read-only root filesystems; application containers also use
  `no-new-privileges`.
- CPU and memory limits are configurable per service.
- Liquibase always runs with the `prod` context.
- OpenAPI and Swagger UI endpoints are disabled.

## Usage

### Requirements

- Docker Engine with the Compose plugin
- OpenSSL
- A public HTTPS hostname and a host-level reverse proxy
- A published SMP backend image, or a locally built image with the configured tag

### Prepare the deployment

From the repository root:

```bash
just production-prepare
```

The command creates `infra/apps/smp/production/.env`, generates cryptographic secrets, and creates
empty files for third-party credentials. It never overwrites an existing non-empty generated
secret.

Edit `.env` and set at least:

```dotenv
SMP_IMAGE=profiletailors/smp:0.1.0
DASHBOARD_IMAGE=profiletailors/dashboard:0.1.0
PUBLIC_ORIGIN=https://app.example.com
```

If the backend image is not available in a registry, build it on the server:

```bash
just backend-image profiletailors/smp:0.1.0 0.1.0
```

The dashboard can also be built from the checked-out source:

```bash
just production-build-dashboard
```

Add integration credentials when required:

```text
infra/apps/smp/production/secrets/linkedin-client-secret
infra/apps/smp/production/secrets/resend-api-key
```

Set `SMP_LINKEDIN_CLIENT_ID` in the production `.env` file when configuring LinkedIn. The expected
OAuth callback is `${PUBLIC_ORIGIN}/integrations/linkedin/callback`.

### Validate and start

```bash
just production-config
just production-up
just production-status
just production-smoke
```

The dashboard is available on `http://127.0.0.1:8080` unless the bind address or port was changed.
Configure the host reverse proxy to forward the public HTTPS origin to that listener.
Its `/healthz` endpoint reports backend readiness, so `production-up` does not complete successfully
until PostgreSQL, Liquibase, the backend, and the dashboard proxy are all ready.

Run the restart simulation before exposing the installation publicly:

```bash
just production-smoke --restart
```

The smoke test checks the dashboard, backend readiness, unauthenticated API routing, Liquibase
migrations, exclusion of development seed data, secret handling, read-only application
filesystems, and persistence after a complete stack stop and start. It does not call LinkedIn or
Resend; validate those integrations separately with real production credentials.

### Upgrade

Update `SMP_IMAGE`, `DASHBOARD_IMAGE`, or their version tags in the production `.env`, then run:

```bash
docker compose \
  --env-file infra/apps/smp/production/.env \
  -f infra/apps/smp/production/compose.yaml \
  pull
just production-up
```

Liquibase applies production migrations before the backend becomes available.

### Backup

Back up both the PostgreSQL database and the `media_data` volume before upgrades. A logical database
backup can be created with:

```bash
docker compose \
  --env-file infra/apps/smp/production/.env \
  -f infra/apps/smp/production/compose.yaml \
  exec -T postgresql pg_dump -U profiletailors profiletailors_smp > profiletailors.sql
```

Use the configured `POSTGRES_USER` and `POSTGRES_DB` values when they differ from the defaults.

## Troubleshooting

### Compose reports a missing secret

Run `just production-prepare`. Confirm every file under `production/secrets` exists and has mode
`0600`. Do not put secret values in `.env`.

### The backend image cannot be pulled

Authenticate with the image registry or build the image locally with `just backend-image`, using
the exact tag configured by `SMP_IMAGE`.

### The dashboard is healthy but API calls return 502

Run `just production-logs backend` and `just production-logs postgresql`. The backend waits for a
healthy database and will stop when production credentials or Liquibase configuration are invalid.

### Login cookies are not retained

Production cookies require HTTPS. Confirm that `PUBLIC_ORIGIN` uses `https://` and that the host
reverse proxy preserves the `Host` and `X-Forwarded-Proto` headers.

## References

- [Production secrets](../production-secrets.md)
- [Release verification](../release-verification.md)
- [Self-hosting guide](self-hosting.md)
- [Modular development infrastructure](modular-docker-compose.md)
