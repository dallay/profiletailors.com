# Modular Infrastructure with Docker Compose

**Date:** 2026-05-25  
**Status:** ✅ Completed

## Overview

The infrastructure for Profile Tailors uses a modular Docker Compose architecture. This allows
services to be reused across different applications in the monorepo while maintaining a single
source of truth for configuration.

## Directory Structure

All infrastructure configurations reside in the `infra/` directory:

```text
infra/
├── common.yml                    # Shared network 'profiletailors'
├── mailpit/
│   └── compose.yaml             # Mailpit SMTP server (local dev email capture)
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

Each base service (Postgres, Redis, Monitoring) is defined in its own directory with its own
`compose.yaml`. These files are independent and can be started standalone.

### 2. Composition via `include`

We use the Docker Compose `include` feature to build application-specific stacks. This avoids
duplication and ensures consistency.

Example of a composite stack (`infra/apps/smp/compose.yaml`):

```yaml
include:
  - ../../postgres/compose.yaml
  - ../../monitoring/compose.yaml
```

### 3. Shared Network

All services share a common network bridge named `profiletailors`, defined in `infra/common.yml`.
This allows containers to communicate using their service names (e.g., `prometheus` can reach
`postgres`).

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

Services use default values but can be overridden using a `.env` file in the application directory
or by passing them directly.

| Variable            | Default Value        | Description                       |
|:--------------------|:---------------------|:----------------------------------|
| `POSTGRES_PORT`     | `5432`               | External port for PostgreSQL      |
| `POSTGRES_DB`       | `profiletailors_smp` | Database name                     |
| `POSTGRES_USER`     | `pt_user`            | Database user                     |
| `MANAGEMENT_PORT`   | `9091`               | Port for internal metrics         |
| `MAILPIT_SMTP_PORT` | `1025`               | SMTP port for local email capture |
| `MAILPIT_UI_PORT`   | `8025`               | Mailpit web UI port               |

## Local Email Capture with Mailpit

In the `dev` profile the backend sends emails through
[Mailpit](https://mailpit.axllent.org/) — a lightweight SMTP server that captures all outgoing
messages without delivering them to real inboxes.

### How it works

```text
SmtpEmailSender → localhost:1025 (Mailpit) → captured, never delivered
```

Mailpit is activated automatically when the stack starts. The `dev` profile already sets
`spring.mail.host=localhost` and `spring.mail.port=1025`, which causes `SmtpEmailSender` to be
preferred over `MockEmailSender`.

### Accessing the web UI

```bash
open http://localhost:8025
```

Every email sent by the backend (e.g. registration verification links) appears there in real time.

### Running the full dev stack

```bash
# 1. Start all services (PostgreSQL + Mailpit + WireMock)
docker compose up -d

# 2. Start the backend in dev mode
./gradlew :server:smp:bootRun --args='--spring.profiles.active=dev'

# 3. Register a user, then check emails at:
open http://localhost:8025
```

### Environment overrides for Mailpit

| Variable            | Default | Description               |
|:--------------------|:--------|:--------------------------|
| `MAILPIT_SMTP_PORT` | `1025`  | Host port bound to SMTP   |
| `MAILPIT_UI_PORT`   | `8025`  | Host port bound to web UI |

### Disabling Mailpit (use MockEmailSender instead)

Unset or leave `SMP_SMTP_HOST` empty and the backend falls back to `MockEmailSender`, which logs
emails to the console with no SMTP server needed:

```bash
# application.yaml default for non-dev profiles:
# spring.mail.host: ${SMP_SMTP_HOST:}  ← empty → MockEmailSender active
```

## SMTP for Staging and Production

Set these environment variables on the target environment. Any SMTP provider works (Resend,
SendGrid, AWS SES, etc.):

| Variable            | Required | Default                      | Description                  |
|:--------------------|:---------|:-----------------------------|:-----------------------------|
| `SMP_SMTP_HOST`     | yes      | —                            | SMTP server hostname         |
| `SMP_SMTP_PORT`     | no       | `587`                        | SMTP port                    |
| `SMP_SMTP_USERNAME` | yes      | —                            | SMTP auth username           |
| `SMP_SMTP_PASSWORD` | yes      | —                            | SMTP auth password / API key |
| `SMP_EMAIL_SENDER`  | no       | `noreply@profiletailors.com` | From address                 |

Example for Resend SMTP:

```bash
SMP_SMTP_HOST=smtp.resend.com
SMP_SMTP_PORT=587
SMP_SMTP_USERNAME=resend
SMP_SMTP_PASSWORD=re_your_api_key_here
SMP_EMAIL_SENDER=noreply@profiletailors.com
```

> When `SMP_SMTP_HOST` is set, `SmtpEmailSender` activates automatically (via
> `@ConditionalOnProperty("spring.mail.host")`). No code change needed.

## Adding New Services

1. Create a new directory in `infra/` (e.g., `infra/redis/`).
2. Add a `compose.yaml` including `../common.yml`.
3. Add any necessary config files in `infra/redis/configs/`.
4. Include the new service in the relevant `infra/apps/*/compose.yaml` files.

## References

- [Docker Compose Include Documentation](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)
- [Docker Compose Network Specification](https://docs.docker.com/compose/compose-file/06-networks/)
