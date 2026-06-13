# Shared Module Dependencies

> Quick-reference dependency graph for the `shared/` Gradle modules in the Profile Tailors monorepo.
> Last updated: 2026-06-13

## Dependency Graph

```mermaid
%%{init: {'theme':'neutral', 'themeVariables': {'primaryColor':'#1a1a2e','primaryTextColor':'#e0e0e0','primaryBorderColor':'#4a4a6a','lineColor':'#6a6a8a','tertiaryColor':'#16213e'}}}%%

graph TB
    %% MODULE NODES
    COMMON["<b>shared:common</b><br/>Domain primitives, entities, value objects<br/>Zero framework dependencies"]
    BUS["<b>shared:bus</b><br/>CQRS mediator, event bus, command/query infra"]
    PRESENTATION["<b>shared:presentation</b><br/>PageResponse, pagination DTOs<br/>Presentation utilities"]
    SECURITY["<b>shared:security</b><br/>Hasher interface + implementations<br/>(SHA-256, HMAC)"]
    SBC["<b>shared:spring-boot-common</b><br/>Spring Boot integration<br/>Exception handlers, filters, presenters, repos"]
    STORAGE["<b>shared:storage</b><br/>File storage abstraction (S3, R2)"]
    RATELIMIT["<b>shared:shield:ratelimit</b><br/>Rate limiting with Bucket4j + Caffeine"]

    %% CLIENT NODES
    SMP["<b>server:smp</b><br/>Spring Boot API Application<br/>All bounded contexts"]

    %% COMPILE DEPENDENCIES (api / implementation)
    BUS -->|api| COMMON
    PRESENTATION -->|api| COMMON
    SECURITY -->|api| COMMON
    SBC -->|api| COMMON
    SBC -->|api| BUS
    SBC -->|api| SECURITY
    SBC -->|api| PRESENTATION
    STORAGE -->|impl| COMMON
    STORAGE -->|impl| BUS
    STORAGE -->|impl| RATELIMIT
    RATELIMIT -->|impl| COMMON
    RATELIMIT -->|impl| BUS
    RATELIMIT -->|impl| SBC
    SMP -->|impl| COMMON
    SMP -->|impl| BUS
    SMP -->|impl| SECURITY
    SMP -->|impl| PRESENTATION
    SMP -->|impl| SBC
    SMP -->|impl| STORAGE

    %% STYLING
    classDef foundation fill:#1a1a2e,stroke:#4a4a6a,color:#e0e0e0,rx:4px
    classDef shared fill:#16213e,stroke:#4a6a8a,color:#d0d0e0,rx:4px
    classDef spring fill:#1e3a2e,stroke:#4a8a6a,color:#d0e0d0,rx:4px
    classDef client fill:#2e1a1a,stroke:#8a4a4a,color:#e0d0d0,rx:4px
    classDef infra fill:#1e1e2e,stroke:#6a5a8a,color:#d0d0e0,rx:4px

    class COMMON foundation
    class BUS,PRESENTATION,SECURITY shared
    class SBC spring
    class STORAGE,RATELIMIT infra
    class SMP client
```

## Module Reference

| Module | Path | Type | Depends On | Consumed By |
|--------|------|------|------------|-------------|
| `:shared:common` | `shared/common/` | Foundation (no deps) | — | All modules |
| `:shared:bus` | `shared/bus/` | Shared | `:shared:common` | SBC, storage, ratelimit, smp |
| `:shared:presentation` | `shared/presentation/` | Shared | `:shared:common` | SBC, smp |
| `:shared:security` | `shared/security/` | Shared | `:shared:common` | SBC, smp |
| `:shared:spring-boot-common` | `shared/spring-boot-common/` | Spring Boot integration | `:shared:common`, `:shared:bus`, `:shared:security`, `:shared:presentation` | ratelimit, smp |
| `:shared:storage` | `shared/storage/` | Infrastructure | `:shared:common`, `:shared:bus`, `:shared:shield:ratelimit` | smp |
| `:shared:shield:ratelimit` | `shared/shield/ratelimit/` | Infrastructure | `:shared:common`, `:shared:bus`, `:shared:spring-boot-common` | storage |
| `:server:smp` | `server/smp/` | Application | All `shared:*` modules | — |

## Layer Rules

```
┌─────────────────────────────────────────────┐
│         server:smp (Application)            │
│  Bounded contexts with Spring Boot + WebFlux │
├─────────────────────────────────────────────┤
│         shared:spring-boot-common           │
│  Spring-specific: controllers, filters,      │
│  presenters, exception handlers, repos       │
├──────────────────┬──────────────────────────┤
│ shared:bus       │  shared:presentation     │
│ shared:security  │  shared:storage          │
│                  │  shared:shield:ratelimit │
├──────────────────┴──────────────────────────┤
│             shared:common                   │
│  Pure domain primitives, zero Spring deps   │
│  DDD building blocks, value objects, errors │
└─────────────────────────────────────────────┘
```

## Design Rules

1. **Cycles forbidden** — The graph is strictly acyclic. No module depends on something that depends on it.
2. **Foundation** — `shared:common` has zero dependencies. All domain primitives live here.
3. **Spring isolation** — If a type needs Spring annotations, it belongs in `shared:spring-boot-common`, never in `shared:common`.
4. **api vs implementation** — Modules use `api(...)` when their consumers need the transitive dependency (e.g., SBC exposes bus types). Use `implementation(...)` when the dependency is internal.
5. **Version alignment** — All shared modules use the same Kotlin, Jackson, and Spring Boot versions via the Gradle version catalog (`gradle/libs.versions.toml`).
