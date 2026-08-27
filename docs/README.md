# Profile Tailors Documentation

**Last Updated:** 2026-07-28

## 📖 Table of Contents

### Architecture & Design

- [API Versioning](./api-versioning.md) - Spring Boot 4 media-type versioning implementation
- [API Versioning Frontend Migration](./api-versioning-frontend-migration.md) - Frontend migration
  notes and media-type requirements
- [API Versioning Implementation Summary](./api-versioning-implementation-summary.md) - Backend
  implementation and test evidence
- [Architecture Overview](./architecture/) - System architecture and design patterns
- [Media Library CAS Dedup](./architecture/media-library-cas-dedup.md) - Content-Addressed Storage
  for workspace-scoped asset deduplication
- [Scheduler URL State Standard](./architecture/scheduler-url-state-standard.md) - Route-owned
  scheduler state, filters, and deep-linkable post details

### Product Contracts & Release Evidence

- [OpenSpec](../openspec/README.md) - Product specifications, change artifacts, and verification
  evidence
- [Consent Management](./consent-management.md) - Shared consent model and frontend/backend flow
- [Compliance Baseline](./compliance/README.md) - Current legal controls and future-state compliance boundary
- [Marketing Legal Baseline Mapping](./compliance/marketing-legal-baseline.md) - Awesome Legal mapping to Profile Tailors legal artifacts
- [Marketing Site Audit (Ahrefs)](./marketing-site-audit-ahrefs.md) - Ahrefs Site Audit findings, repository fixes, and pending Cloudflare dashboard steps
- [Publishing Failure Modes](./publishing-failure-modes.md) - User-facing publishing error taxonomy
- [Release Verification](./release-verification.md) - Evidence required before release readiness

### Infrastructure
- [Modular Docker Compose](./infrastructure/modular-docker-compose.md) - Reusable infrastructure
  services
- [Private Beta Launch Readiness Runbook](./infrastructure/private-beta-launch-readiness-runbook.md) -
  Operator procedures for publishing safe-off, stale visibility, and rollback (DALLAY-555/557)
- [Private Beta Operator Checklist](./infrastructure/private-beta-operator-checklist.md) -
  Per-surface read-only checks for readiness, database, media, auth, waitlist, invitation, and publishing worker (DALLAY-557)
- [Private Beta Incident Response](./infrastructure/private-beta-incident-response.md) -
  Incident owner, severity ladder, communication templates, and threshold review cadence (DALLAY-557)
- [Private Beta Correlation Matrix](./infrastructure/private-beta-correlation-matrix.md) -
  Pivot recipes across `jobId`, `invitationId`, `waitlistEntryId`, and the redaction contract (DALLAY-557)
- [Private Beta Backup and Restore Status](./infrastructure/private-beta-backup-restore-status.md) -
  Rehearsed / documented-not-exercised / explicitly-not-rehearsed status of backup and restore (DALLAY-557)
- [PostgreSQL Setup](../infra/postgres/) - Database configuration

### Monitoring & Observability

- [Prometheus & Grafana Setup](./monitoring/prometheus-grafana-setup.md) - Metrics collection and
  visualization
- [Actuator Security](./monitoring/actuator-security.md) - Securing Spring Boot Actuator endpoints

### Development & Testing

- [Getting Started](./getting-started.md) - Local developer onboarding, `just` installation, and
  `just setup`
- [Portless — Local Development URLs](./portless-setup.md) - Named `.localhost` HTTPS URLs
- [Gradle Build System & Conventions](./gradle-build-system.md) - Centralized composite
  build-logic & convention plugins
- [Code Coverage Setup](./codecov-setup.md) - JaCoCo and Codecov integration
- [SonarQube Coverage](./sonarqube-coverage.md) - Technical guide for SonarQube coverage
- [SonarQube Setup](./sonarqube-setup.md) - Step-by-step SonarQube configuration guide
- [Coverage Summary](./coverage-setup-summary.md) - Summary of the test coverage implementation
- [Production Secrets](./production-secrets.md) - Secret inventory and production handling rules
- [Root README](../README.md) - High-level project overview and quick-start

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

OpenSpec artifacts remain under `openspec/` because they are product-contract and change records,
not general operational documentation. See the [OpenSpec guide](../openspec/README.md) for how to
navigate active and archived changes.

## 🔄 Contributing

When adding new documentation:

1. Place it in the appropriate subdirectory under `docs/`
2. Follow the established **Naming Convention** and **Structure**.
3. Update this index with a link to the new document.
4. Use clear, concise English.
