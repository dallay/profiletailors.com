# Self-Hosting Profile Tailors

## Overview

Profile Tailors supports two production deployment targets:

| Target         | Use it when                                                    | Operational model                                                                                |
|----------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Docker Compose | One server is sufficient                                       | Dashboard, backend, PostgreSQL, and media run on one Docker Engine                               |
| Docker Swarm   | Several Docker nodes or rolling dashboard updates are required | Dashboard is replicated; backend, PostgreSQL, and local media remain on one labeled storage node |

Docker Compose is the recommended starting point. Choose Swarm only when its scheduling and rolling
deployment features solve a current operational need. Kubernetes is not a supported target yet.

Both targets expect an external HTTPS reverse proxy or load balancer. Neither deployment publishes
PostgreSQL directly.

## Changes

The production deployment packages provide:

- Separate configurations for single-server Compose and Docker Swarm.
- Non-root application images and read-only application filesystems.
- Production-only Liquibase migrations with development seed data excluded.
- Persistent PostgreSQL and media storage.
- File-backed Compose secrets or native encrypted Swarm secrets.
- Health checks, resource limits, and repeatable deployment commands.
- A local production simulation that validates routing, migrations, persistence, and hardening.

## Usage

### Choose a deployment target

Use [Production Docker Compose](production-docker-compose.md) for a first installation or a single
server. It is the shortest supported path and can build the dashboard and backend locally.

Use [Production Docker Swarm](production-docker-swarm.md) when a Swarm already exists or dashboard
replication is required. Swarm requires application images in a registry accessible from every
node and exactly one labeled node for PostgreSQL and local media.

### Complete the production checklist

For either target:

1. Clone the repository at the release tag that will be deployed.
2. Install Docker Engine, the Docker Compose plugin, Git, OpenSSL, and `just`.
3. Prepare configuration and generate secret files with the target-specific command.
4. Replace example hostnames, image names, email addresses, and integration credentials.
5. Validate the rendered configuration before creating containers or services.
6. Start the deployment and wait for readiness.
7. Run the documented smoke test and inspect service status.
8. Configure HTTPS and verify login cookies through the public hostname.
9. Configure database and media backups before inviting users.
10. Perform the real LinkedIn publishing test with production OAuth credentials.

The local smoke tests do not contact LinkedIn or Resend. Their credentials and end-to-end provider
flows must be verified separately on the target server.

## Troubleshooting

### The correct deployment target is unclear

Start with Docker Compose. Migrating to Swarm later changes orchestration and secret management but
does not require changing the application architecture.

### A local production simulation passes but the public hostname fails

Inspect the external reverse proxy. It must forward the original `Host` and `X-Forwarded-Proto`
headers and terminate TLS for the exact value configured as `PUBLIC_ORIGIN`.

### The stack is healthy but an integration fails

Infrastructure readiness does not prove third-party credentials. Confirm the LinkedIn client ID,
client secret, callback URL, Resend API key, and provider-side application settings.

## References

- [Production Docker Compose](production-docker-compose.md)
- [Production Docker Swarm](production-docker-swarm.md)
- [Production secrets](../production-secrets.md)
- [Release verification](../release-verification.md)
