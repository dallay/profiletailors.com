# Profile Tailors Documentation

**Last Updated: 2026-10-01

## 📖 Table of Contents

### AI-Assisted Engineering

- [AI Engineering](./ai-engineering/) - How ProfileTailors uses Agent Harness for structured, spec-driven development with AI agents
- [OpenSpec](../openspec/README.md) - Spec-driven development system with phase DAG

### Architecture & Design

- [API Versioning](./api-versioning.md) - Spring Boot 4 media-type versioning implementation
- [API Versioning Frontend Migration](./api-versioning-frontend-migration.md) - Frontend migration
  notes and media-type requirements
- [API Versioning Implementation Summary](./api-versioning-implementation-summary.md) - Backend
  implementation and test evidence
- [Platform Admin User Administration](./platform-admin-user-administration.md) - Disable/enable
  accounts, session revocation, idempotency, and audit contract
- [Architecture Overview](./architecture/) - System architecture and design patterns
- [C4 Summary](./architecture/c4/SUMMARY.md) - Executive summary and roadmap across levels 1-4
- [Shared Dependencies](./architecture/shared/dependencies.md) - Shared module dependency graph
- [Data Model](./architecture/data-model/README.md) - Per-context table catalog and FKs
- [Transaction Policy](./architecture/transaction-policy.md) - Transaction boundaries
- [IAM Platform](./architecture/iam-platform.md) - Identity and access platform notes
- [Login Flow](./architecture/login-flow.md) - Login sequence and refresh decisions
- [ADR Discovery](./architecture/adr-discovery/candidate-decisions.md) - Candidate decisions under review
- [ADR Enforcement](./architecture/adr-enforcement/ADR-0001.md) - Executable ADR ownership map (see folder for ADR-0002, ADR-0007, ADR-0008)
- [Media Library ADR](./architecture/adr-media-library-storage.md) - Storage decision for media library
- [Media Library CAS Dedup](./architecture/media-library-cas-dedup.md) - Content-Addressed Storage
  for workspace-scoped asset deduplication
- [Scheduler URL State Standard](./architecture/scheduler-url-state-standard.md) - Route-owned
  scheduler state, filters, and deep-linkable post details

### Product Contracts & Release Evidence

- [OpenSpec](../openspec/README.md) - Product specifications, change artifacts, and verification
  evidence
- [Consent Management](./consent-management.md) - Shared consent model and frontend/backend flow
- [Compliance Baseline](./compliance/README.md) - Current legal controls and future-state compliance boundary
- [Compliance Status Taxonomy](./compliance/status-taxonomy.md) - Status naming for all compliance artifacts
- [Record of Processing Activities](./compliance/ropa.md) - Internal processing register
- [Data Inventory](./compliance/data-inventory.md) - Data inventory and YAML source
- [Marketing Legal Baseline Mapping](./compliance/marketing-legal-baseline.md) - Awesome Legal mapping to Profile Tailors legal artifacts
- [Publishing Failure Modes](./publishing-failure-modes.md) - User-facing publishing error taxonomy
- [Release Verification](./release-verification.md) - Evidence required before release readiness

### Infrastructure

- [Modular Docker Compose](./infrastructure/modular-docker-compose.md) - Reusable infrastructure
  services
- [Production Docker Compose](./infrastructure/production-docker-compose.md) - Production stack
- [Production Docker Swarm](./infrastructure/production-docker-swarm.md) - Swarm deployment
- [Cloudflare Deployment](./infrastructure/cloudflare-deployment.md) - Release-driven Pages deployment
- [Self-Hosting](./infrastructure/self-hosting.md) - Self-host operator guide
- [Private Beta Launch Readiness Runbook](./infrastructure/private-beta-launch-readiness-runbook.md) -
  Operator procedures for publishing safe-off, stale visibility, and rollback (DALLAY-555/557)
- [Private Beta Operator Checklist](./infrastructure/private-beta-operator-checklist.md) - On-call
  checklist for the private beta cohort
- [Private Beta Incident Response](./infrastructure/private-beta-incident-response.md) - Incident
  ownership and response
- [PostgreSQL Setup](../infra/postgres/) - Database configuration

### Monitoring & Observability

- [Prometheus & Grafana Setup](./monitoring/prometheus-grafana-setup.md) - Metrics collection and
  visualization
- [Actuator Security](./monitoring/actuator-security.md) - Securing Spring Boot Actuator endpoints
- [Observability Contracts](./observability-contracts.md) - Shared telemetry and logging contracts
- [Shared Observability Usage Standard](./observability-usage.md) - Canonical usage guide for
  `shared/observability` and the SMP `observability` bounded context

### Development & Testing

- [Back Office QA Closure](../.agents/rpi/plan/tasks/back-office-qa.md) - QA coverage for the waitlist-to-first-login journey, local evidence, and remaining CI/deployed/operator closure status
- [Technical Debt Remediation](./technical-debt-remediation.md) - Java 25 migration and staged audit follow-up
- [Test Tags and Env](./testing/test-tags-and-env.md) - Backend test tags and local environment
- [Mutation Testing](./testing/mutation-testing.md) - Mutation testing with mutflow
- [Accessibility Regression Strategy](./testing/accessibility-regression-strategy.md) - A11y regression approach
- [SonarQube Batch Remediation Plan](./plans/2026-09-11-sonarqube-batch-remediation.md) - Batch remediation implementation plan
- [Tenancy 4R Review](./reviews/4r-review-tenancy-remediation.md) - Quality and resilience review for tenancy remediation

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
- [Security Audit Report](./security/audit-report.md) - Point-in-time audit findings and follow-up

### Data Retention

- [Retention Operations](./retention-framework-operations.md) - Current-state retention operations guide
- [Retention Quick Reference](./retention-framework-quick-reference.md) - Current-state quick reference
- [Retention Acceptance Criteria](./retention-framework-acceptance-criteria.md) - Gap assessment and acceptance criteria

### Platform Surfaces

- [MCP Server](./mcp-server.md) - MCP server contract and clients
- [SEO Runbook](./marketing/seo.md) - Marketing SEO operations
- [Diagrams](./diagrams/profiletailors-overview.html) - Visual system overview (see folder for analytics, auth, governance, inbox, MCP, media, publishing, scheduler cores)

## 🚀 Quick Links

### For Developers

- [SMP Server README](../server/smp/README.md)
- [Infrastructure README](../infra/README.md)

### For Operations

- [Monitoring Setup](./monitoring/prometheus-grafana-setup.md)
- [Actuator Security](./monitoring/actuator-security.md)
- [Infrastructure Management](./infrastructure/modular-docker-compose.md)
- [Production Rollback Runbook](./runbooks/production-rollback.md)
- [Password Recovery Runbook](./runbooks/password-recovery.md)

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
