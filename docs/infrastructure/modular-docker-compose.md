# Modular Infrastructure with Docker Compose

**Date:** 2026-05-25  
**Status:** ✅ Completed

## Overview

The infrastructure for Profile Tailors uses a modular Docker Compose architecture. This allows services to be reused across different applications in the monorepo while maintaining a single source of truth for configuration.

## Directory Structure

All infrastructure configurations reside in the `@infra/` directory:

```
infra/
├── common.yml                    # Shared network 'profiletailors'
├── postgres/
│   └── compose.yaml             # PostgreSQL 18 service
├── monitoring/
│   ├── compose.yaml             # Prometheus + Grafana services
│   └── configs/                 # Service-specific configuration files
└── apps/
    └── smp/
        └── compose.yaml         # Composite stack for the SMP app
```

## Design Principles

### 1. Service Modularity
Each base service (Postgres, Redis, Monitoring) is defined in its own directory with its own `compose.yaml`. These files are independent and can be started standalone.

### 2. Composition via `include`
We use the Docker Compose `include` feature to build application-specific stacks. This avoids duplication and ensures consistency.

Example of a composite stack (`infra/apps/smp/compose.yaml`):
```yaml
include:
  - ../../postgres/compose.yaml
  - ../../monitoring/compose.yaml
```

### 3. Shared Network
All services share a common network bridge named `profiletailors`, defined in `infra/common.yml`. This allows containers to communicate using their service names (e.g., `prometheus` can reach `postgres`).

## How to Manage Infrastructure

### Starting a Full Application Stack
From the root of the project:
```bash
docker compose -f infra/apps/smp/compose.yaml up -d
```

### Starting Individual Services
```bash
docker compose -f infra/monitoring/compose.yaml up -d
```

### Stopping Services
```bash
docker compose -f infra/apps/smp/compose.yaml down
```

## Environment Variables

Services use default values but can be overridden using a `.env` file in the application directory or by passing them directly.

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `POSTGRES_PORT` | `5432` | External port for PostgreSQL |
| `POSTGRES_DB` | `profiletailors_smp` | Database name |
| `POSTGRES_USER` | `pt_user` | Database user |
| `MANAGEMENT_PORT`| `9091` | Port for internal metrics |

## Adding New Services

1.  Create a new directory in `infra/` (e.g., `infra/redis/`).
2.  Add a `compose.yaml` including `../common.yml`.
3.  Add any necessary config files in `infra/redis/configs/`.
4.  Include the new service in the relevant `infra/apps/*/compose.yaml` files.

## References
- [Docker Compose Include Documentation](https://docs.docker.com/compose/multiple-compose-files/include/)
- [Docker Compose Network Specification](https://docs.docker.com/compose/compose-file/06-networks/)
