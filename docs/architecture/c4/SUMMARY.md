# Profile Tailors — C4 Architecture Summary

## Executive Summary

Profile Tailors is a social media management platform built with a modern, reactive architecture.
The system enables teams to schedule, publish, analyze, and collaborate on social media content
across multiple platforms (Twitter, LinkedIn, Instagram, Facebook, TikTok).

**Current Status**: Early development (v0.0.1-SNAPSHOT)

---

## Architecture Overview

### System Context

- **Users**: Content creators, team administrators, analysts
- **Core System**: Profile Tailors (marketing site + backend API)
- **External Integrations**: Social media platforms, auth providers, email services, cloud storage

### Technology Stack

| Layer        | Technology                                       |
|--------------|--------------------------------------------------|
| **Frontend** | Astro 6 (marketing), React 18+ (web app planned) |
| **Backend**  | Spring Boot 4, Kotlin, WebFlux (reactive)        |
| **Database** | PostgreSQL 16 with R2DBC (reactive driver)       |
| **Cache**    | Redis                                            |
| **Queue**    | RabbitMQ / Kafka                                 |
| **Storage**  | S3-compatible (Cloudflare R2, AWS S3)            |

### Architecture Style

- **Hexagonal Architecture** (Ports & Adapters)
- **Domain-Driven Design** (Bounded Contexts)
- **CQRS** (Command Query Responsibility Segregation)
- **Reactive Programming** (Kotlin coroutines + R2DBC)
- **Modular Monolith** (Spring Modulith)

---

## Bounded Contexts (Implemented)

### 1. Identity Context

**Purpose**: User authentication and principal management

**Key Features**:

- JWT and API Key authentication
- Principal materialization
- OAuth2 token validation

### 2. Authorization Context

**Purpose**: Permission checks and access control

**Key Features**:

- Role-based access control (RBAC)
- Direct grants (explicit permissions)
- Workspace entitlements (feature flags)
- Resource preview (what user can do)

### 3. Tenancy Context

**Purpose**: Workspace and membership management

**Key Features**:

- Multi-tenant workspaces
- Membership lifecycle (invite, activate, suspend, remove)
- Ownership transfers
- Active workspace resolution

### 4. Credentials Context

**Purpose**: API keys, OAuth tokens, and secret management

**Key Features**:

- API key generation and validation
- OAuth token storage and refresh
- Service account credentials
- Credential lifecycle management

### 5. Governance Context

**Purpose**: Audit logging, compliance, and data retention

**Key Features**:

- Mutation audit trails
- Compliance reporting
- Data retention policies

### 6. Platform Context

**Purpose**: Cross-cutting concerns and infrastructure

**Key Features**:

- Request context management (principal, workspace, trace ID)
- Mediator pattern for command/query dispatch
- Common contracts and abstractions

---

## Bounded Contexts (Planned)

### 7. Content Context

**Purpose**: Post creation, scheduling, and draft management

**Planned Features**:

- Multi-platform post creation
- Scheduling engine
- Draft and revision management
- Media asset handling

### 8. Analytics Context

**Purpose**: Metrics aggregation and reporting

**Planned Features**:

- Engagement metrics collection
- Performance reports
- KPI tracking
- Data export

### 9. Integrations Context

**Purpose**: Social media platform adapters

**Planned Features**:

- Twitter/X integration
- LinkedIn integration
- Instagram integration
- Facebook integration
- TikTok integration
- Rate limiting and retry logic

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

## Scalability Strategy

### Horizontal Scaling

- **API Application**: Stateless, can scale horizontally
- **Scheduler Service**: Partitioned by workspace or time slot
- **Analytics Service**: Partitioned by platform or metric type

### Database Scaling

- **Read replicas**: For analytics queries
- **Connection pooling**: R2DBC with reactive backpressure
- **Partitioning**: By workspace or time range (future)

### Caching Strategy

- **Session cache**: Redis (TTL: 15 min)
- **API response cache**: Redis (TTL: 1-5 min)
- **OAuth token cache**: Redis (TTL: token expiry - 5 min)

---

## Deployment Architecture

### Current (Development)

```
Local Development
├── Marketing Site: localhost:4321 (Astro)
├── API Application: localhost:8080 (Spring Boot)
├── PostgreSQL: localhost:5432 (Docker Compose)
├── Redis: localhost:6379 (Docker Compose)
└── RabbitMQ: localhost:5672 (Docker Compose)
```

### Target (Production)

```
CDN (Cloudflare / Vercel)
├── Marketing Site (static)
└── Web Application (static)
        ↓
Kubernetes / Cloud Run
├── API Application (3+ replicas)
├── Scheduler Service (2+ replicas)
└── Analytics Service (2+ replicas)
        ↓
Managed Services
├── PostgreSQL (AWS RDS / Google Cloud SQL / Neon)
├── Redis (AWS ElastiCache / Upstash)
├── RabbitMQ (CloudAMQP) / Kafka (Confluent Cloud)
└── S3 (AWS S3 / Cloudflare R2)
```

---

## Implementation Roadmap

### Phase 1: Foundation (✅ Complete)

- [x] Marketing site (Astro 6)
- [x] Backend foundation (Spring Boot 4, Kotlin, WebFlux)
- [x] Core bounded contexts (Identity, Authorization, Tenancy, Credentials, Governance, Platform)
- [x] JWT and API Key authentication
- [x] PostgreSQL with R2DBC

### Phase 2: Core Features (🔄 In Progress)

- [ ] Web application (React)
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
- **Cache hit rate**: Redis
- **Queue depth**: RabbitMQ / Kafka
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

- **[DESIGN.md](../../../DESIGN.md)** — Design system and UI guidelines
- **[README.md](../../../README.md)** — Project overview and setup
- **[CONTRIBUTING.md](../../../CONTRIBUTING.md)** — Contribution guidelines
- **[openspec/](../../../openspec/)** — SDD artifacts (specs, designs, tasks)

---

## Contact

- **Discussions**: https://github.com/dallay/profiletailors.com/discussions
- **Issues**: https://github.com/dallay/profiletailors.com/issues
- **Email**: dev@profiletailors.com

---

Last updated: 2026-05-19
