---
date: 2026-08-14
status: 🔄 In Progress
---

# Profile Tailors — C4 Architecture Summary

## Executive Summary

Profile Tailors is a social media management platform built with a modern, reactive architecture.
The system enables teams to schedule, publish, analyze, and collaborate on social media content
across multiple platforms (Twitter, LinkedIn, Instagram, Facebook, TikTok).

---

## Architecture Overview

### System Context

- **Users**: Content creators, team administrators, analysts
- **Core System**: Profile Tailors (marketing site + dashboard app + backend API)
- **External Integrations**: Social media platforms, email services, cloud storage

### Technology Stack

| Layer        | Technology                                 |
| ------------ | ------------------------------------------ |
| **Frontend** | Astro 7 (marketing), Vue 3 (dashboard app) |
| **Backend**  | Spring Boot 4, Kotlin, WebFlux (reactive)  |
| **Database** | PostgreSQL 18 with R2DBC (reactive driver) |
| **Cache**    | Caffeine (local) / Redis (optional)        |
| **Storage**  | S3-compatible (Cloudflare R2, AWS S3)      |

### Architecture Style

- **Hexagonal Architecture** (Ports & Adapters)
- **Domain-Driven Design** (Bounded Contexts)
- **CQRS** (Command Query Responsibility Segregation)
- **Reactive Programming** (Kotlin coroutines + R2DBC)
- **Modular Monolith** (Spring Modulith)

---

## Shared Kernel (Shared Libraries)

The API Application is composed of multiple Gradle modules. The **Shared Kernel** provides
framework-agnostic domain primitives and shared infrastructure:

| Module                      | Purpose                                                            | Spring Deps |
| --------------------------- | ------------------------------------------------------------------ | ----------- |
| `shared:common`             | Domain primitives, base entities, value objects, `@Service` marker | ❌ None      |
| `shared:bus`                | Event bus abstractions (CQRS mediator)                             | ❌ None      |
| `shared:security`           | Security primitives (Hasher interface + implementations)           | ❌ None      |
| `shared:presentation`       | Presentation layer utilities (PageResponse, pagination)            | ❌ None      |
| `shared:lead-capture:common`| Framework-free lead capture primitives                              | ❌ None      |
| `shared:lead-capture:waitlist`| Framework-free waitlist aggregates & ports                        | ❌ None      |
| `shared:spring-boot-common` | Spring Boot integration, exception handlers, filters, presenters   | ✅ Yes       |
| `shared:storage`            | Storage abstractions (S3/R2)                                       | ✅ Yes       |
| `shared:shield:ratelimit`   | Rate limiting with Bucket4j + Caffeine / Redis                     | ✅ Yes       |
| `shared:notifications`      | Notification abstractions and domain models                        | ❌ None      |

> **Full dependency graph:** See [Shared Module Dependencies](../shared/dependencies.md) for
> the complete module dependency diagram with all `api` vs `implementation` edges.

---

## Bounded Contexts (19 Bounded Contexts)

The backend `server:smp` comprises 19 modular bounded contexts:

1. **Analytics Context**: Engagement metrics collection, aggregation, and reporting.
2. **Audit Context**: Request outcomes, authorization decision auditing, and mutation event capture.
3. **Authorization Context**: RBAC, direct permission grants, workspace permissions, and entitlements.
4. **Config Context**: Dynamic platform and system configuration.
5. **Credentials Context**: API keys, OAuth tokens, and credential encryption (`PublishingCredentialsProperties`).
6. **Governance Context**: Mutation audit logging, policy enforcement, and compliance tracking.
7. **Hashtags Context**: Hashtag group management, aggregation, and performance tracking.
8. **Ideas Context**: Content brainstorming and draft idea management.
9. **Identity Context**: Native JWT and HttpOnly cookie authentication, principal management.
10. **Lead Capture Context**: Public waitlist joins, lead capture storage, and consent collection.
11. **MCP Context**: Model Context Protocol integration for platform AI tooling.
12. **Media Context**: Media asset storage, Content-Addressable Storage (CAS) deduplication.
13. **Notifications Context**: Transactional email delivery and notification channels.
14. **Observability Context**: Request monitoring, metrics collection, and rate limiting hooks.
15. **Platform Context**: Request context management (`PrincipalContext`), mediator pattern (`SpringMediator`).
16. **Platformadmin Context**: Global platform administration and feature flags.
17. **Privacy Context**: Data Subject Access Requests (DSAR), erasure, and privacy export operations.
18. **Publishing Context**: Post creation, scheduling, platform channel connections, and publishing execution.
19. **Tenancy Context**: Workspaces, memberships, ownership transfers, and tenant isolation (ADR-0008).

---

## Key Design Decisions

### 1. Reactive Stack

**Decision**: Use Spring WebFlux + Kotlin coroutines + R2DBC

**Rationale**:

- Non-blocking I/O for better resource utilization
- Handles high concurrency with fewer threads
- Natural fit for I/O-bound operations (API calls, database queries)

### 2. Hexagonal Architecture

**Decision**: Separate domain, application, and infrastructure layers

**Rationale**:

- Testability (domain logic isolated from frameworks)
- Flexibility (swap adapters without changing domain)
- Clear boundaries between layers

### 3. Bounded Contexts

**Decision**: Organize code by domain contexts, not technical layers

**Rationale**:

- Aligns with business domains
- Enables independent evolution
- Reduces coupling between contexts

### 4. CQRS

**Decision**: Separate commands (mutations) from queries (reads)

**Rationale**:

- Clear intent (command vs query)
- Enables different optimization strategies
- Supports event sourcing (future)

### 5. Modular Monolith

**Decision**: Start with a modular monolith, not microservices

**Rationale**:

- Simpler deployment and operations
- Easier to refactor and evolve
- Can extract microservices later if needed
- Spring Modulith enforces module boundaries

---

## Security Architecture

### Authentication

- **JWT tokens**: Short-lived (15 min), for user sessions
- **API keys**: Long-lived, for service-to-service communication
- **OAuth2**: For social media platform authorization

### Authorization

- **RBAC**: Role-based permissions (Owner, Admin, Editor, Viewer)
- **Direct grants**: Explicit permissions override roles
- **Entitlements**: Feature flags at workspace level

### Data Protection

- **TLS 1.3**: All external communication
- **Encrypted credentials**: AES-256 at rest
- **Secrets management**: Environment variables or secret manager
- **Audit logging**: All mutations tracked

---

## Scalability & Deployment Architecture

### Deployment Architecture

Profile Tailors utilizes **Docker Swarm** for backend orchestration (`infra/apps/smp/swarm/` and `docs/infrastructure/production-docker-swarm.md`):

```text
CDN (Cloudflare / Vercel)
├── Marketing Site (Astro static)
└── Web Application (Vue 3 SPA)
        ↓
Docker Swarm (`infra/apps/smp/swarm/stack.yaml`)
├── Dashboard Service (Vue 3 SPA, port 8080)
└── Backend Service (API Application, Spring Boot, port 7638)
        ↓
Managed & Local Storage
├── PostgreSQL 18 (R2DBC reactive driver)
└── Local Storage (bind-mounted /var/lib/profiletailors/media)
```

---

## Implementation Roadmap

### Phase 1: Foundation (✅ Complete)

- [x] Marketing site (Astro 7)
- [x] Backend foundation (Spring Boot 4, Kotlin, WebFlux)
- [x] Core bounded contexts (Identity, Authorization, Tenancy, Credentials, Governance, Platform)
- [x] JWT and API Key authentication
- [x] PostgreSQL with R2DBC

### Phase 2: Core Features (🔄 In Progress)

- [x] Web application (Vue 3)
- [ ] Content Context (post creation, scheduling)
- [ ] Scheduler Service (background jobs)
- [ ] Redis cache integration
- [ ] Message queue integration

### Phase 3: Integrations (🔲 Planned)

- [ ] Twitter/X integration
- [ ] LinkedIn integration
- [ ] Instagram integration
- [ ] Facebook integration
- [ ] TikTok integration

### Phase 4: Analytics (🔲 Planned)

- [ ] Analytics Context (metrics collection)
- [ ] Analytics Service (aggregation)
- [ ] Reporting dashboards
- [ ] Data export

### Phase 5: Collaboration (🔲 Planned)

- [ ] Team workflows
- [ ] Approval flows
- [ ] Comments and feedback
- [ ] Activity feeds

---

## Metrics & Monitoring

### Key Metrics (Planned)

- **API latency**: p50, p95, p99
- **Database query time**: per bounded context
- **Cache hit rate**: Caffeine (local) or Redis (when enabled)
- **Event throughput**: Reactor Channel publishers
- **Social media API rate limits**: per platform
- **Error rate**: per endpoint

### Observability Stack (Planned)

- **Logs**: Structured JSON logs (Logback)
- **Metrics**: Micrometer + Prometheus
- **Traces**: OpenTelemetry
- **Dashboards**: Grafana

---

## Documentation

### C4 Model Diagrams

1. **[System Context](01-system-context.md)** — Big picture, external dependencies
2. **[Container](02-container.md)** — Deployable units, technology stack
3. **[Component](03-component.md)** — Internal structure, bounded contexts
4. **[Code](04-code.md)** — Implementation patterns, class design

### Additional Resources

- **[DESIGN.md](../../../.agents/DESIGN.md)** — Design system and UI guidelines
- **[README.md](../../../README.md)** — Project overview and setup
- **[CONTRIBUTING.md](../../../CONTRIBUTING.md)** — Contribution guidelines
- **[openspec/](../../../openspec/)** — SDD artifacts (specs, designs, tasks)

---

## Contact

- **Issues**: <https://github.com/dallay/profiletailors.com/issues>
- **Email**: <dev@profiletailors.com>

---

Last updated: 2026-09-05
