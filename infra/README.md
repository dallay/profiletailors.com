# Profile Tailors Infrastructure

## Overview

This directory contains the Docker infrastructure for local development and supported self-hosted
production deployments.

```text
infra/
├── common.yml
├── mailpit/
├── postgres/
├── monitoring/
│   ├── compose.yaml
│   └── stack.yaml
└── apps/smp/
    ├── compose.yaml
    ├── production/
    └── swarm/
```

## Changes

- `apps/smp/compose.yaml` assembles PostgreSQL, Mailpit, WireMock, Prometheus, Grafana, Loki, and
  Alloy for local
  development.
- `monitoring/compose.yaml` is the reusable Compose example; Alloy discovers Docker containers and
  ships their logs to Loki.
- `monitoring/stack.yaml` is the equivalent Docker Swarm example; Alloy runs globally so each node
  ships its local container logs to the single Loki service.
- `apps/smp/production/compose.yaml` deploys the complete application on one server.
- `apps/smp/swarm/stack.yaml` deploys the complete application to Docker Swarm.
- Development services use project-scoped container names and configurable host ports, allowing
  isolated Compose projects to run without global name collisions.

## Usage

### Local development

```bash
just infra-up
```

Mailpit is available at `http://localhost:8025`, Prometheus at `http://localhost:9090`, and Grafana
at `http://localhost:3000` with their default local settings. Loki is available internally at
`http://loki:3100`; use Grafana's pre-provisioned **Loki** datasource to query logs.

### Loki and Alloy with Compose

The SMP development Compose file already includes the monitoring services:

```bash
docker compose -f infra/apps/smp/compose.yaml up -d loki alloy grafana
```

To run only the reusable monitoring example:

```bash
docker compose -f infra/monitoring/compose.yaml up -d
```

Alloy needs read-only access to the Docker socket and container metadata. This setup is intended for
trusted local hosts; do not expose the Docker socket outside the host.

### Loki and Alloy with Docker Stack

Initialize Swarm and deploy the example from the repository root:

```bash
docker swarm init
docker stack deploy --compose-file infra/monitoring/stack.yaml profile-tailors-monitoring
docker stack services profile-tailors-monitoring
```

The stack file uses bind-mounted configuration files, a local Loki volume, and a global Alloy
service. On a multi-node Swarm, `loki_data` must be backed by shared storage or Loki must be pinned
to the node that owns its volume.

### Single-server production

Docker Compose is the recommended self-hosting target for the first installation:

```bash
just production-prepare
just production-config
just production-up
just production-smoke --restart
```

Edit `infra/apps/smp/production/.env` and the integration secret files after preparation and before
starting the stack. Follow the production Compose guide for HTTPS, backup, and upgrade steps.

### Docker Swarm

Use Swarm only when its clustered scheduling or dashboard replication is needed:

```bash
just swarm-prepare
just swarm-label-storage <node-name>
just swarm-config
just swarm-deploy
```

The Swarm guide explains the registry, storage-node, secret, backup, and rollback requirements.

## Troubleshooting

### A local port is already in use

Override `SMP_POSTGRES_PORT`, `MAILPIT_SMTP_PORT`, `MAILPIT_UI_PORT`, `WIREMOCK_HOST_PORT`,
`PROMETHEUS_HOST_PORT`, or `GRAFANA_HOST_PORT` before starting the development stack.

If Alloy is running but no logs appear, verify that its node can read `/var/run/docker.sock`, that
the Loki service resolves as `loki` on the monitoring network, and inspect `docker logs` for Alloy
configuration errors.

### Production services do not become healthy

Run `just production-status` and `just production-logs <service>`. For Swarm, run
`just swarm-status` and inspect failed tasks with `docker stack ps profile-tailors-swarm --no-trunc`.

## References

- [Self-hosting guide](../docs/infrastructure/self-hosting.md)
- [Modular development infrastructure](../docs/infrastructure/modular-docker-compose.md)
- [Production Docker Compose](../docs/infrastructure/production-docker-compose.md)
- [Production Docker Swarm](../docs/infrastructure/production-docker-swarm.md)
- [Monitoring setup](../docs/monitoring/prometheus-grafana-setup.md)
- [Actuator security](../docs/monitoring/actuator-security.md)
