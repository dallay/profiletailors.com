# Level 4: Code Diagram

## Overview

The Code diagram shows implementation details for critical components within the API Application.
This level focuses on class structure, key methods, and design patterns.

**Audience**: Developers

**Purpose**: Understand implementation patterns, class relationships, and coding conventions.

---

## Design Patterns Summary

### 1. Hexagonal Architecture (Ports & Adapters)

- **Domain**: Pure business logic, no framework dependencies
- **Application**: Use cases, ports (interfaces)
- **Infrastructure**: Adapters (R2DBC, HTTP, external APIs)

All bounded contexts — including cross-cutting ones (`platform`, `audit`, `observability`,
`governance`) — must expose `domain/`, `application/`, and `infrastructure/` packages.
Enforced by `HexagonalArchTest` in `server/smp/src/test/`.

### 2. CQRS (Command Query Responsibility Segregation)

- **Commands**: Mutate state (e.g., `UpdateWorkspaceMembershipStatusCommand`)
- **Queries**: Read state (e.g., `GetResourcePreviewQuery`)
- Separate handlers for each

### 3. Repository Pattern

- Interface in application layer (port)
- R2DBC implementation in infrastructure layer (adapter)
- Hides persistence details from domain

### 4. Mediator Pattern

- `SpringMediator` dispatches commands/queries to handlers
- Decouples sender from receiver
- Enables cross-cutting concerns (logging, validation)

### 5. Domain-Driven Design (DDD)

- **Entities**: `Workspace`, `WorkspaceMembership`
- **Value Objects**: `AuthenticatedPrincipal`, `PrincipalContext`
- **Aggregates**: `WorkspaceMembership` (aggregate root)
- **Domain Services**: `WorkspaceOwnershipPolicy`
- **Bounded Contexts**: Identity, Authorization, Tenancy, etc.

### 6. Reactive Programming

- Kotlin coroutines (`suspend` functions)
- R2DBC for non-blocking database access
- Spring WebFlux for reactive HTTP

---

## Package Structure

### Shared Kernel (framework-agnostic, zero Spring dependencies)

```
com.profiletailors.common
├── domain                       # Domain primitives
│   ├── model/                   # BaseEntity, AggregateRoot, AuditableEntity
│   ├── vo/                      # Email, Name, Credential, IpHash, etc.
│   ├── error/                   # Domain exception hierarchy
│   ├── bus/event/               # DomainEvent interface
│   ├── bus/query/               # Response marker
│   ├── authentication/          # AccessToken
│   ├── context/                 # PrincipalType
│   ├── observability/           # RequestOutcome
│   ├── workspace/               # WorkspaceMembershipSnapshot
│   ├── Service.kt               # @Service annotation
│   ├── Memoizers.kt             # Thread-safe memoization
│   ├── Generated.kt             # Generated code marker
│   └── GlobalSystemConstants.kt # SYSTEM_USER constants
└── util/                        # SystemEnvironment
```

**Dependency direction:** `com.profiletailors.common` → no dependencies (pure Kotlin library)

### Bounded Contexts (Spring Boot application)

```
com.profiletailors.smp
├── {context}                    # Bounded context (e.g., identity, authorization)
│   ├── domain                   # Domain models, policies
│   ├── application              # Use cases, ports
│   └── infrastructure           # Adapters (R2DBC, HTTP, external)
│       ├── http                 # REST controllers
│       ├── security             # Security filters, converters
│       └── {AdapterName}.kt     # Repository implementations
```

**Dependency direction:** `com.profiletailors.smp.{context}` → `com.profiletailors.common`

---

## Coding Conventions

### Kotlin Style

- **Immutability**: Prefer `val` over `var`, `data class` for value objects
- **Null Safety**: Use `?` for nullable types, avoid `!!`
- **Coroutines**: Use `suspend` for async operations, avoid blocking calls
- **Extension Functions**: Use for cross-cutting concerns

### Naming Conventions

- **Commands**: `{Verb}{Noun}Command` (e.g., `UpdateWorkspaceMembershipStatusCommand`)
- **Queries**: `Get{Noun}Query` (e.g., `GetResourcePreviewQuery`)
- **Handlers**: `{CommandOrQuery}Handler` (e.g., `UpdateWorkspaceMembershipStatusHandler`)
- **Repositories**: `{Aggregate}Repository` (e.g., `WorkspaceMembershipRepository`)
- **Adapters**: `R2dbc{Aggregate}Repository` (e.g., `R2dbcWorkspaceMembershipRepository`)
- **Identifiers**: Mixed strategy across contexts. Most aggregates use prefixed UUID v4 strings stored as `varchar(64)` (e.g., `user-`, `ws-`, `wm-`, `pub-`, `pa-`, `idea-`, `nevt-`, `pjob-`, `recur-`, `htset-`, `ra-`, `cs-`, `ev-`, `ctrl-`). Some tables use raw `uuid` type without prefixes (e.g., `secure_credentials`, `platform_invitations`, `waitlist_invitations`).

### Testing Conventions

- **Unit Tests**: `{ClassName}Test.kt`
- **Integration Tests**: `{ClassName}IntegrationTest.kt`
- **Test Containers**: Use for PostgreSQL integration tests
- **Mocking**: Use MockK for Kotlin

---

## Current Implementation Status

**Implemented Bounded Contexts** (19 total):

- ✅ Analytics Context (engagement metrics & reporting)
- ✅ Audit Context (request outcomes, authorization decisions, mutations)
- ✅ Authorization Context (permission checking, RBAC, direct grants)
- ✅ Config Context (application & system configuration)
- ✅ Credentials Context (API key validation, secret management)
- ✅ Governance Context (audit logging, compliance)
- ✅ Hashtags Context (hashtag group management & tracking)
- ✅ Ideas Context (content ideas & draft brainstorming)
- ✅ Identity Context (native JWT + API key authentication)
- ✅ Lead Capture Context (waitlist joins, consent capture)
- ✅ MCP Context (Model Context Protocol AI tool integrations)
- ✅ Media Context (media asset management, CAS deduplication)
- ✅ Notifications Context (transactional email & notifications)
- ✅ Observability Context (metrics hooks, rate limiting)
- ✅ Platform Context (request context, mediator pattern)
- ✅ Platformadmin Context (global platform administration)
- ✅ Privacy Context (data subject requests, DSAR, erasure)
- ✅ Publishing Context (posts, schedules, OAuth channel connections)
- ✅ Tenancy Context (workspace management, memberships)

**Code Quality**:

- ✅ Hexagonal architecture (domain/application/infrastructure layers)
- ✅ CQRS pattern via mediator (`SpringMediator`)
- ✅ Repository pattern with R2DBC reactive database persistence
- ✅ Prefixed UUID v4 entity identifiers (`varchar(64)`)
- ✅ Resource creation via POST endpoints
- ✅ Reactive programming (coroutines + R2DBC)
- ✅ Unit tests (domain logic) & Integration tests (R2DBC repositories)

---

Last updated: 2026-09-03
