# Profile Tailors Infrastructure

This directory contains the centralized Docker Compose configuration for the Profile Tailors monorepo services.

## Documentation

All infrastructure documentation has been moved to the centralized docs directory:

-   [Modular Infrastructure Overview](../docs/infrastructure/modular-docker-compose.md)
-   [Monitoring Setup (Prometheus & Grafana)](../docs/monitoring/prometheus-grafana-setup.md)
-   [Actuator Security Guide](../docs/monitoring/actuator-security.md)

## Quick Structure

```
infra/
├── common.yml                    # Shared network bridge
├── mailpit/                      # Local SMTP email capture (dev)
├── postgres/                     # DB Service
├── monitoring/                   # Prometheus & Grafana
└── apps/                         # App-specific composite stacks
```

## Quick Start (SMP)

```bash
docker compose -f infra/apps/smp/compose.yaml up -d
```

## Local Email (dev)

Mailpit captures all outgoing emails without delivering them. Open the web UI after starting the stack:

```bash
open http://localhost:8025
```

See [Local Email Capture with Mailpit](../docs/infrastructure/modular-docker-compose.md#local-email-capture-with-mailpit) for full details.
