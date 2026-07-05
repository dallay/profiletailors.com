# Profile Tailors — Architecture Documentation

This directory contains the architecture documentation for Profile Tailors, a social media
management platform.

---

## 📐 C4 Architecture Models

The C4 model provides a hierarchical set of architecture diagrams for visualizing software
architecture at different levels of abstraction.

### Quick Navigation

| Level       | Document                                  | Description                           | Audience               |
|-------------|-------------------------------------------|---------------------------------------|------------------------|
| **Summary** | [summary.md](c4/summary.md)               | Executive summary and roadmap         | Everyone               |
| **Level 1** | [System Context](c4/01-system-context.md) | Big picture, external dependencies    | Everyone               |
| **Level 2** | [Container](c4/02-container.md)           | Deployable units, technology stack    | Technical leadership   |
| **Level 3** | [Component](c4/03-component.md)           | Internal structure, bounded contexts  | Developers, architects |
| **Level 4** | [Code](c4/04-code.md)                     | Implementation patterns, class design | Developers             |
| **Shared**  | [Dependencies](shared/dependencies.md)    | Shared module dependency graph        | Developers, architects |

### Visual Overview

```
┌─────────────────────────────────────────────────────────────┐
│ Level 1: System Context                                    │
│ ┌─────────────────────────────────────────────────────┐   │
│ │  Users → Profile Tailors → External Systems         │   │
│ └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Level 2: Container                                          │
│ ┌─────────────────────────────────────────────────────┐   │
│ │  Marketing Site | Web App | API | Scheduler |       │   │
│ │  Analytics | Database | Cache | Queue               │   │
│ └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Level 3: Component (API Application)                        │
│ ┌─────────────────────────────────────────────────────┐   │
│ │  Identity | Authorization | Tenancy | Credentials | │   │
│ │  Governance | Platform | Content | Analytics |      │   │
│ │  Integrations                                       │   │
│ └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ Level 4: Code                                               │
│ ┌─────────────────────────────────────────────────────┐   │
│ │  Domain Models | Application Services |             │   │
│ │  Infrastructure Adapters | Design Patterns          │   │
│ └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Architecture Principles

### 1. Hexagonal Architecture (Ports & Adapters)

- **Domain**: Pure business logic, no framework dependencies
- **Application**: Use cases, ports (interfaces)
- **Infrastructure**: Adapters (R2DBC, HTTP, external APIs)

### 2. Domain-Driven Design (DDD)

- **Bounded Contexts**: Identity, Authorization, Tenancy, Credentials, Governance, Platform, Audit, Media, Observability
- **Shared Kernel**: Multiple module layers — see [full dependency graph](shared/dependencies.md)
  for all `api` and `implementation` relationships
- **Foundation**: [`shared:common`](../../shared/common/) — framework-agnostic domain primitives,
  zero Spring dependencies
- **Aggregates**: Clear boundaries and consistency rules
- **Domain Events**: For cross-context communication

### 3. Reactive Programming

- **Kotlin Coroutines**: Structured concurrency
- **R2DBC**: Non-blocking database access
- **Spring WebFlux**: Reactive HTTP

### 4. CQRS

- **Commands**: Mutate state
- **Queries**: Read state
- Separate handlers for each

### 5. Modular Monolith

- **Spring Modulith**: Enforces module boundaries
- **Clear interfaces**: Between bounded contexts
- **Independent evolution**: Each context can evolve separately

---

## 🎯 Key Design Decisions

### Why Reactive?

- **Non-blocking I/O**: Better resource utilization
- **High concurrency**: Handles many concurrent requests with fewer threads
- **Natural fit**: For I/O-bound operations (API calls, database queries)

### Why Modular Monolith?

- **Simpler deployment**: Single deployable unit
- **Easier operations**: No distributed system complexity
- **Flexibility**: Can extract microservices later if needed

### Why Kotlin?

- **Type safety**: Null safety, immutability by default
- **Conciseness**: Less boilerplate than Java
- **Coroutines**: First-class support for structured concurrency

### Why PostgreSQL?

- **Robust**: ACID compliance, mature
- **JSON support**: For flexible schemas
- **R2DBC driver**: Reactive access

---

## 📊 Current Status

**Implemented** (✅):

- Marketing site (Astro 6)
- Backend foundation (Spring Boot 4, Kotlin, WebFlux)
- Core bounded contexts (Identity, Authorization, Tenancy, Credentials, Governance, Platform, Audit, Media, Observability, Publishing)
- LinkedIn publishing integration
- JWT and API Key authentication
- PostgreSQL with R2DBC

**In Progress** (🔄):

- Web application (Vue 3, dashboard implementation)
- Content Context (post creation, scheduling)

**Planned** (🔲):

- Additional social media integrations (Twitter, Instagram, etc.)
- Analytics Context
- Team collaboration features

---

## 📚 Additional Resources

### Project Documentation

- **[README.md](../../README.md)** — Project overview and setup
- **[DESIGN.md](../../DESIGN.md)** — Design system and UI guidelines
- **[CONTRIBUTING.md](../../CONTRIBUTING.md)** — Contribution guidelines
- **[AGENTS.md](../../AGENTS.md)** — AI agent instructions

### Specifications

- **[openspec/](../../openspec/)** — SDD artifacts (specs, designs, tasks)
- **[docs/plans/](../plans/)** — Design specs and implementation plans
- **[Media Library CAS Dedup](./media-library-cas-dedup.md)** — Content-Addressed Storage
  deduplication for workspace-scoped media assets

### Security

- **[SECURITY.md](../../SECURITY.md)** — Security policy
- **[docs/security/](../security/)** — Security documentation

---

## 🔗 External Links

- **Repository**: https://github.com/dallay/profiletailors.com
- **Discussions**: https://github.com/dallay/profiletailors.com/discussions
- **Issues**: https://github.com/dallay/profiletailors.com/issues

---

Last updated: 2026-06-13
