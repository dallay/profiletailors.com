# SMP Server — Social Media Platform Backend

Reactive Spring Boot 4 modular monolith written in Kotlin, powering core Profile Tailors services including authentication, post scheduling, multi-network social publishing, workspace administration, and lead capture.

## Role in the platform

Acts as the central backend service (`server/smp`) for Profile Tailors. It exposes WebFlux REST endpoints consumed by the Vue 3 dashboard (`apps/web/app`) and admin portal (`apps/web/admin`). It integrates shared domain libraries (`shared/*`), enforces security policies, handles reactive database persistence (R2DBC / PostgreSQL), and coordinates background publishing jobs.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines & Flow
- **Framework**: Spring Boot 4.0, Spring WebFlux, Spring Modulith
- **Database & Persistence**: PostgreSQL 16, R2DBC, Spring Data R2DBC, Flyway
- **Security & Cryptography**: Spring Security Reactive, JWT, AES-256-GCM
- **Testing**: JUnit 5, MockK, Kotest, ArchUnit, Konsist, Cucumber BDD
- **Build & Quality**: Gradle 8.8, Detekt 1.23, JaCoCo, Spotless

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Docker & Docker Compose (for local PostgreSQL infrastructure)
- `just` command runner (`>= 1.30`)

### Installation

Bootstrap local environment and database password:

```bash
just setup
```

### Running locally

1. Start local PostgreSQL database:

```bash
just infra-up
```

2. Run the Spring Boot application:

```bash
just backend-run
```

App exposes REST API on `http://localhost:7638` (or port configured in `.env`).

### Environment variables

| Variable | Required | Description | Default |
| --- | --- | --- | --- |
| `SERVER_PORT` | No | HTTP port for REST API | `7638` |
| `MANAGEMENT_SERVER_PORT` | No | Management / Actuator HTTP port | `9091` |
| `SPRING_R2DBC_URL` | Yes | R2DBC PostgreSQL connection URL | `r2dbc:postgresql://localhost:5432/smp` |
| `SPRING_R2DBC_USERNAME` | Yes | Database user | `smp_user` |
| `SPRING_R2DBC_PASSWORD` | Yes | Database password | `smp_password` |
| `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY` | Yes | 32-byte secret key for social token encryption | `01234567890123456789012345678901` |
| `SMP_PLATFORM_RATE_LIMIT_ENABLED` | No | Rate limiting toggle | `false` |

## Project structure

```text
server/smp/
├── src/
│   ├── main/
│   │   ├── kotlin/com/profiletailors/smp/
│   │   │   ├── config/          # Spring configuration and security
│   │   │   ├── iam/             # Identity & Access Management context
│   │   │   ├── publishing/      # Social media publishing context
│   │   │   ├── scheduling/      # Post scheduling context
│   │   │   └── workspace/       # Workspace & multi-tenancy context
│   │   └── resources/
│   │       ├── db/migration/    # Flyway SQL schema migrations
│   │       └── application.yaml # Spring Boot application properties
│   └── test/
│       ├── kotlin/              # Unit, ArchUnit, Konsist, and Modulith tests
│       └── resources/features/  # Cucumber Gherkin BDD feature files
├── backend.Dockerfile           # Container image configuration
└── compose.yaml                 # Docker Compose local stack
```

## Testing

### Unit and architecture tests

Fast tests without database requirement:

```bash
just backend-test-fast
```

Full unit suite and Detekt analysis:

```bash
just backend-check
```

### Cucumber BDD tests

Run fast in-memory BDD scenarios:

```bash
just backend-bdd-fast
```

Run PostgreSQL integration BDD scenarios (requires `just infra-up`):

```bash
just backend-bdd-postgres
```

### Static analysis & coverage

```bash
just backend-lint
just backend-coverage
```

## API / Public interface

Main REST API endpoints (`Accept: application/vnd.api.v1+json`):

- `POST /api/v1/auth/login`, `POST /api/v1/auth/register` — Identity endpoints
- `GET /api/v1/workspaces`, `POST /api/v1/workspaces` — Workspace management
- `GET /api/v1/posts`, `POST /api/v1/posts` — Post creation and scheduling
- `GET /api/v1/media`, `POST /api/v1/media` — Media uploads and asset management
- `POST /api/governance/consent` — Privacy and consent tracking
- `/actuator/health` — Service health check (port `9091`)

## Configuration

- `src/main/resources/application.yaml`: Production properties configuration.
- `src/main/resources/db/migration/`: Versioned Flyway SQL schema migrations.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
