# Infrastructure

## Overview

Operator entry point for local, production, and private-beta infrastructure. Start with the stack you run, then use the private-beta docs only when operating the DALLAY-555/557 cohort.

## Quick path

1. Local: `just infra-up`, then `just infra-info`
2. Production: `just production-config` (Compose) or `just swarm-config` (Swarm)
3. Trouble: `just infra-logs` or `just production-status`

## Usage

| Stack | Read first |
| --- | --- |
| Local services | [Modular Docker Compose](./modular-docker-compose.md) + `just infra-up` |
| Production Compose | [Production Docker Compose](./production-docker-compose.md) + `just production-config` |
| Production Swarm | [Production Docker Swarm](./production-docker-swarm.md) + `just swarm-config` |
| Pages frontend | [Cloudflare Deployment](./cloudflare-deployment.md) |
| Self-host | [Self-Hosting](./self-hosting.md) |

Private-beta operations:

- [Launch Readiness Runbook](./private-beta-launch-readiness-runbook.md)
- [Operator Checklist](./private-beta-operator-checklist.md)
- [Incident Response](./private-beta-incident-response.md)
- [Correlation Matrix](./private-beta-correlation-matrix.md)
- [Backup and Restore Status](./private-beta-backup-restore-status.md)

## Troubleshooting

- `just infra-up` fails: run `just infra-info` and `just infra-logs` before changing Compose files.
- Production config drift: run `just production-config` or `just swarm-config`; do not hand-edit rendered output.
- Database truth lives under `infra/postgres/`; this folder documents operations, not schema.

## References

- [Docs index](../README.md)
- [Production Secrets](../production-secrets.md)
- [Production Rollback](../runbooks/production-rollback.md)
