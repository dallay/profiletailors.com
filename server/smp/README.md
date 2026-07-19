# SMP Server - Social Media Platform

Spring Boot 4 backend service for Profile Tailors.

## 📚 Documentation

All documentation is centralized in the `docs/` directory:

### Architecture & Design
- [API Versioning](../../docs/api-versioning.md)
- [Architecture Overview](../../docs/architecture/)

### Infrastructure & Operations
- [Modular Infrastructure](../../docs/infrastructure/modular-docker-compose.md)
- [Monitoring Setup](../../docs/monitoring/prometheus-grafana-setup.md)
- [Actuator Security](../../docs/monitoring/actuator-security.md)

### Development
- [Code Coverage Setup](../../docs/codecov-setup.md)

## 🚀 Quick Start

### 1. Start Infrastructure
```bash
docker compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Prometheus (port 9090)
- Grafana (port 3000)

### 2. Run Server
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 3. Verify
```bash
curl http://localhost:8080/actuator/health
```

## Package Structure

Every bounded context under `com.profiletailors.smp` follows hexagonal architecture:

```
{context}/
├── domain/          # Pure Kotlin — no Spring annotations
├── application/     # Use cases, CQRS handlers
└── infrastructure/  # R2DBC, HTTP, Spring configuration
```

Cross-cutting contexts (`platform`, `audit`, `observability`, `governance`) follow the same
three-layer rule as product contexts (`identity`, `authorization`, `tenancy`, etc.).

- [C4 Code-level patterns](../../docs/architecture/c4/04-code.md)
- [Hexagonal architecture skill](../../.agents/skills/backend-platform/hexagonal-architecture/SKILL.md)

## 🔧 Development Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Check code style
./gradlew detekt
```

## Database-backed tests

Database-backed backend tests run against Testcontainers PostgreSQL. H2 is not part of the test
runtime.

Docker must be available and `SMP_DB_TEST_PASSWORD` must contain a non-empty test-only
password before running PostgreSQL tests:

```bash
export SMP_DB_TEST_PASSWORD=$SMP_DB_PASSWORD
just backend-test-fast
just backend-bdd-fast
```

The PostgreSQL-tagged integration suite can also be run directly:

```bash
SMP_DB_TEST_PASSWORD=$SMP_DB_PASSWORD \
  ./gradlew :server:smp:postgresIntegrationTest
```

### Troubleshooting

- **`SMP_DB_TEST_PASSWORD must be set`**: export a non-empty test-only password in the shell
  that launches Gradle.
- **Docker unavailable or containers cannot start**: start Docker and verify it with `docker info`.
- **A PostgreSQL test is unexpectedly skipped**: Testcontainers tests use
  `disabledWithoutDocker = true`; confirm the Docker daemon is reachable from the current shell.

## 📊 Monitoring

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Metrics**: http://localhost:8080/actuator/prometheus (dev mode)

## 🔒 Security

See [Actuator Security Guide](../../docs/monitoring/actuator-security.md) for endpoint protection details.
