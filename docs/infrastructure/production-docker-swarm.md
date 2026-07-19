# Production Docker Swarm

## Overview

The Swarm stack under `infra/apps/smp/swarm` is the supported clustered deployment target for
Profile Tailors. It uses published dashboard and backend images, native Swarm secrets, overlay
networks, rolling updates, resource reservations, and automatic rollback policies.

The initial topology deliberately keeps the stateful services on one labeled node:

- Dashboard: replicated across the Swarm.
- Backend: one replica on the storage node.
- PostgreSQL: one replica on the storage node.
- Media: bind-mounted persistent directory on the storage node.

This topology provides orchestrated deployment and dashboard redundancy, but PostgreSQL and local
media remain single-node components. It must not be described as full application high
availability.

## Changes

Compared with the single-server Compose deployment, the Swarm target adds:

- Registry-distributed immutable application images.
- Native secrets encrypted in the Swarm Raft log.
- Read-only root filesystems with explicit writable mounts for application and database data.
- Overlay networks with an internal-only data network.
- Replicated dashboard tasks behind the Swarm routing mesh.
- Update and rollback policies with health monitoring.
- Resource limits and reservations used by the scheduler.
- Placement constraints for PostgreSQL, media, and the publishing worker.
- Versioned secret names for safe immutable-secret rotation.

Swarm does not build images during `docker stack deploy`. Both application images must be available
to every target node through a registry, preferably by immutable digest.

## Usage

### Initialize the Swarm

Run on the first manager when a Swarm does not already exist:

```bash
docker swarm init --advertise-addr <manager-address>
```

Join additional managers and workers using the commands printed by Docker.

### Prepare the storage node

Choose exactly one ready node for PostgreSQL and media, then label it from a manager:

```bash
just swarm-label-storage <node-name>
```

On that labeled node, create the media directory with the backend image ownership:

```bash
sudo install -d -o 1002 -g 1001 -m 0750 /var/lib/profiletailors/media
```

Only one node should carry `profiletailors.storage=true` while the stack uses local volumes.

### Prepare configuration and secrets

```bash
just swarm-prepare
```

Edit `infra/apps/smp/swarm/.env` and configure:

```dotenv
SMP_IMAGE=registry.example.com/profiletailors/smp@sha256:<digest>
DASHBOARD_IMAGE=registry.example.com/profiletailors/dashboard@sha256:<digest>
PUBLIC_ORIGIN=https://app.example.com
```

`swarm-prepare` initializes optional LinkedIn and Resend secret sources with `unconfigured`, because
Swarm rejects zero-byte secrets. Replace those placeholders before enabling either integration. The
deployment script validates every source, creates missing native Swarm secrets, and never replaces
an existing secret with the same name.

### Validate and deploy

```bash
just swarm-config
just swarm-deploy
just swarm-status
```

`swarm-deploy` runs from a manager, validates that exactly one ready node has the storage label,
creates missing secrets, deploys with registry authentication, and waits for `/healthz` to report
backend readiness. It succeeds only after every desired service replica has converged and the
dashboard and protected API proxy return their expected HTTP responses. If readiness fails, it
requests rollback only for application services changed by that deployment.

After deployment, verify the dashboard and API proxy from a manager or load-balancer host:

```bash
curl --fail http://127.0.0.1:8080/
curl --fail http://127.0.0.1:8080/healthz
curl --output /dev/null --silent --write-out '%{http_code}\n' \
  --header 'Accept: application/vnd.api.v1+json' \
  http://127.0.0.1:8080/api/auth/me
```

The last command must return `401`, proving that the dashboard proxy reaches the protected backend
API. This infrastructure check does not contact LinkedIn or Resend; run those provider flows with
real production credentials before inviting users.

Swarm does not implement the Compose `security_opt` contract. The Swarm stack therefore relies on
the non-root application images, read-only root filesystems, capability dropping on the dashboard,
native secrets, isolated networks, and resource policies supported by `docker stack deploy`.

The dashboard is published through the Swarm routing mesh on port `8080` by default. Terminate TLS
in an external load balancer or reverse proxy and forward the configured public hostname to the
Swarm nodes.

### Upgrade and rollback

Update the image digests in `swarm/.env` and rerun:

```bash
just swarm-deploy
```

Swarm updates dashboard tasks one at a time and rolls back automatically when the update fails.
The single backend replica uses stop-first updates to avoid two publishing workers sharing local
state during a rollout.

Manual rollback remains available:

```bash
just swarm-rollback backend
just swarm-rollback dashboard
```

### Rotate a secret

Swarm secrets are immutable. To rotate one:

1. Change its versioned name in `swarm/.env`, for example from `_v1` to `_v2`.
2. Replace the corresponding source file under `swarm/secrets`.
3. Run `just swarm-deploy`.
4. Remove the unused old secret only after all tasks use the new version.

### Backups

Database and media backups must run on the labeled storage node. Back up PostgreSQL before every
schema upgrade and copy `/var/lib/profiletailors/media` with a filesystem-aware backup tool.

Removing the stack does not intentionally remove native secrets or persistent volumes:

```bash
just swarm-remove
```

## Troubleshooting

### A service remains at `0/1`

Inspect placement and task errors:

```bash
docker stack ps profile-tailors-swarm --no-trunc
```

Confirm one ready node has `profiletailors.storage=true`, the media directory exists there, and the
image architecture matches that node.

### An image resolves on the manager but not workers

Publish a multi-platform image and authenticate on the manager before deployment. The deploy script
passes registry credentials with `--with-registry-auth`, but every node must be able to reach the
registry.

### Secret changes are ignored

Changing a source file does not mutate an existing Swarm secret. Assign a new versioned secret name
and redeploy.

### Stateful services are unavailable after losing the storage node

This is an explicit limitation of the initial topology. Recover the labeled node and its volumes,
or restore backups onto a replacement node. Full HA requires external PostgreSQL and shared object
storage before increasing backend replicas.

## References

- [Production Docker Compose](production-docker-compose.md)
- [Self-hosting guide](self-hosting.md)
- [Production secrets](../production-secrets.md)
- [Release verification](../release-verification.md)
