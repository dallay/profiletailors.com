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
└── apps/smp/
    ├── compose.yaml
    ├── production/
    └── swarm/
```

## Changes

- `apps/smp/compose.yaml` assembles PostgreSQL, Mailpit, WireMock, Prometheus, and Grafana for local
  development.
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
at `http://localhost:3000` with their default local settings.

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

Override `SMP_DB_PORT`, `MAILPIT_SMTP_PORT`, `MAILPIT_UI_PORT`, `WIREMOCK_HOST_PORT`,
`PROMETHEUS_HOST_PORT`, or `GRAFANA_HOST_PORT` before starting the development stack.

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
