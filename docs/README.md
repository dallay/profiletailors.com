# Profile Tailors Documentation

**Last Updated:** 2026-05-27

## 📖 Table of Contents

### Architecture & Design

- [API Versioning](./api-versioning.md) - Spring Boot 4 media-type versioning implementation
- [Architecture Overview](./architecture/) - System architecture and design patterns

### Infrastructure

- [Modular Docker Compose](./infrastructure/modular-docker-compose.md) - Reusable infrastructure
  services
- [PostgreSQL Setup](../infra/postgres/) - Database configuration

### Monitoring & Observability

- [Prometheus & Grafana Setup](./monitoring/prometheus-grafana-setup.md) - Metrics collection and
  visualization
- [Actuator Security](./monitoring/actuator-security.md) - Securing Spring Boot Actuator endpoints

### Development & Testing

- [Portless — Local Development URLs](./portless-setup.md) - Named `.localhost` HTTPS URLs
- [Gradle Build System & Conventions](./gradle-build-system.md) - Centralized composite
  build-logic & convention plugins
- [Code Coverage Setup](./codecov-setup.md) - JaCoCo and Codecov integration
- [SonarQube Coverage](./sonarqube-coverage.md) - Technical guide for SonarQube coverage
- [SonarQube Setup](./sonarqube-setup.md) - Step-by-step SonarQube configuration guide
- [Coverage Summary](./coverage-setup-summary.md) - Summary of the test coverage implementation
- [Root README](../README.md) - Developer onboarding, `just` installation, and `just setup`

### Security

- [Security Guidelines](./security/) - Security best practices and configurations
- [Scanning Stack](./security/scanning-stack.md) - Layered DevSecOps scanning model

## 🚀 Quick Links

### For Developers

- [SMP Server README](../server/smp/README.md)
- [Infrastructure README](../infra/README.md)

### For Operations

- [Monitoring Setup](./monitoring/prometheus-grafana-setup.md)
- [Infrastructure Management](./infrastructure/modular-docker-compose.md)

## 📝 Documentation Standards

All documentation in this repository MUST follow these standards:

1. **Language**: English (mandatory for all documentation)
2. **Naming Convention**: Lowercase `kebab-case.md` for all files (except `README.md`).
3. **Format**: Markdown with frontmatter (Date, Status) whenever possible.
4. **Structure**:
    - **Overview**: Purpose and context.
    - **Changes**: Recent modifications (if applicable).
    - **Usage**: Practical instructions and commands.
    - **Troubleshooting**: Common issues and fixes.
    - **References**: Links to related documentation or external resources.
5. **Location**: Centralized in the `docs/` directory. Avoid scattering documentation in
   service-specific directories unless it's a `README.md` for that specific module.

## 🔄 Contributing

When adding new documentation:

1. Place it in the appropriate subdirectory under `docs/`
2. Follow the established **Naming Convention** and **Structure**.
3. Update this index with a link to the new document.
4. Use clear, concise English.
