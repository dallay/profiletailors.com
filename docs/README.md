# Profile Tailors Documentation

**Last Updated:** 2026-05-25

## 📖 Table of Contents

### Architecture & Design
- [API Versioning](./api-versioning.md) - Spring Boot 4 media-type versioning implementation
- [Architecture Overview](./architecture/) - System architecture and design patterns

### Infrastructure
- [Modular Docker Compose](./infrastructure/modular-docker-compose.md) - Reusable infrastructure services
- [PostgreSQL Setup](../infra/postgres/) - Database configuration

### Monitoring & Observability
- [Prometheus & Grafana Setup](./monitoring/prometheus-grafana-setup.md) - Metrics collection and visualization
- [Actuator Security](./monitoring/actuator-security.md) - Securing Spring Boot Actuator endpoints

### Development & Testing
- [Code Coverage Setup](./codecov-setup.md) - JaCoCo and Codecov integration

### Security
- [Security Guidelines](./security/) - Security best practices and configurations

## 🚀 Quick Links

### For Developers
- [SMP Server README](../server/smp/README.md)
- [Infrastructure README](../infra/README.md)

### For Operations
- [Monitoring Setup](./monitoring/prometheus-grafana-setup.md)
- [Infrastructure Management](./infrastructure/modular-docker-compose.md)

## 📝 Documentation Standards

All documentation in this directory follows these standards:

1. **Language**: English (unless explicitly specified otherwise)
2. **Format**: Markdown with frontmatter (Date, Status)
3. **Structure**: Overview → Changes → Usage → Troubleshooting → References
4. **Location**: Centralized in `docs/` directory, not scattered in service directories

## 🔄 Contributing

When adding new documentation:

1. Place it in the appropriate subdirectory under `docs/`
2. Follow the existing format and structure
3. Update this index with a link to the new document
4. Use clear, concise English
5. Include practical examples and troubleshooting sections
