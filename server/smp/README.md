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

## 📊 Monitoring

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Metrics**: http://localhost:8080/actuator/prometheus (dev mode)

## 🔒 Security

See [Actuator Security Guide](../../docs/monitoring/actuator-security.md) for endpoint protection details.
