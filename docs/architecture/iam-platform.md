# IAM Platform Architecture

## Overview

The IAM Platform is the reusable identity and access management foundation for `server/smp`. It is
oriented as a multi-tenant SaaS platform rather than a single-feature auth module — every backend
feature and future product reuses these contracts.

The architecture separates six concerns into bounded contexts: **who** the actor is (Identity),
**where** they operate (Tenancy), **what** they may do (Authorization), **how** they prove
themselves (Credentials), **why** the system decided what it did (Governance), and **how** those
concerns wire together (Platform). Each context follows hexagonal architecture with CQRS dispatch.

---

## Bounded Contexts

### Identity

**Purpose**: Define who the authenticated actor is, independent of how they authenticated or what
they are allowed to do.

**Responsibilities**:

- Own the principal taxonomy (USER, SERVICE_ACCOUNT, API_KEY, SYSTEM, INTEGRATION, AGENT)
- Materialize authenticated principals from validated credentials through repo-local seams
- Manage user registration, email verification lifecycle, and local user sessions
- Provide `AuthenticatedPrincipal` to downstream contexts, never raw framework token types

**Key models**: `Principal`, `PrincipalType`, `User`, `EmailStatus`, `EmailVerificationPolicy`

**Distinctions**:

| Concept       | Answers                | Owned by      |
|---------------|------------------------|---------------|
| Identity      | Who is the actor?      | Identity      |
| Credentials   | How did they prove it? | Credentials   |
| Authorization | What may they do?      | Authorization |

---

### Tenancy

**Purpose**: Model workspace lifecycle and membership separately from authorization roles.

**Responsibilities**:

- Own workspace creation, ownership, and lifecycle state
- Model workspace membership as the relationship between a principal and a workspace
- Support multiple roles per membership in a single workspace
- Resolve the active workspace context for each protected workspace-scoped request
- Keep workspace ownership as a tenancy concept, never collapsed into a role alias

**Key models**: `Workspace`, `WorkspaceMembership`, `WorkspaceOwnership`, `ActiveWorkspaceContext`

**Rules**:

- A principal MUST have an active membership before exercising workspace-scoped permissions
- Each protected workspace-scoped request resolves exactly one explicit active workspace
- Ambiguous or missing workspace context results in denial

---

### Authorization

**Purpose**: Determine what a principal may do in a given resource context.

**Responsibilities**:

- Own the permission format `<domain>:<resource>:<action>` with explicit, stable identifiers
- Model roles as compositions of explicit permissions (no implicit inheritance)
- Support direct permission grants with ALLOW or DENY effect and optional expiration
- Evaluate scopes as permission-reducing constraints (never permission-manufacturing)
- Resolve effective permissions through deterministic flow: membership → role permissions → direct
  grants → scope reduction → feature entitlement gating
- Preserve RBAC-to-ABAC evolution path without redefining the core model

**Key models**: `PermissionKey`, `Role`, `DirectGrant`, `Scope`, `FeatureEntitlement`,
`EffectivePermission`

**Resolution order**:

```
1. Principal identity + resource context
2. Active workspace membership check
3. Role-based permission collection
4. Direct grant evaluation (ALLOW/DENY with expiration)
5. Scope reduction (narrows allowed targets, never expands)
6. Feature entitlement check (workspace-level gating)
7. Explicit DENY overrides any ALLOW path
8. Absence of explicit ALLOW → denial
```

---

### Credentials

**Purpose**: Own how a principal authenticates — the credential forms, validation, and lifecycle.

**Responsibilities**:

- Issue and validate JWT access tokens for USER principals
- Manage refresh credentials for local USER browser sessions (HttpOnly cookies)
- Handle bearer-based service-account authentication against authoritative backend state
- Manage API-key lifecycle: issuance, verification, revocation, and replacement cutover
- Support authoritative revocation enforcement for all credential paths
- Preserve a path for future external federation without redefining core semantics

**Key models**: `AccessToken`, `RefreshCredential`, `ApiKeyCredential`,
`ServiceAccountCredential`, `CredentialState`

**Credential transport**:

| Credential             | Transport                                 | Storage                                               |
|------------------------|-------------------------------------------|-------------------------------------------------------|
| JWT access token       | `Authorization: Bearer` header            | Frontend memory only                                  |
| Refresh credential     | `HttpOnly` + `SameSite` + `Secure` cookie | Backend authoritative state                           |
| Service-account bearer | `Authorization: Bearer` header            | Backend authoritative state                           |
| API key                | Custom header or parameter                | Backend authoritative state (verifier, not plaintext) |

---

### Governance

**Purpose**: Ensure authorization and credential decisions are auditable, deterministic, and
explainable.

**Responsibilities**:

- Provide runtime audit-ready proof for allow and deny outcomes on protected endpoints
- Ensure every authorization decision is attributable to explicit platform facts (membership, role,
  permission, grant, denial, scope, entitlement, credential state)
- Distinguish denial causes: missing permission vs. missing entitlement vs. scope reduction vs.
  revoked credential vs. predecessor-after-replacement
- Preserve deterministic evaluation: equivalent requests against equivalent state produce equivalent
  outcomes

**Key models**: `AuthorizationDecisionFact`, `CredentialStateFact`, `DenialCategory`

---

### Platform

**Purpose**: Own cross-cutting seams required by all other contexts.

**Responsibilities**:

- Provide mediator-style CQRS dispatch for commands and queries
- Own request-context propagation (principal, workspace, trace ID)
- Define the resource context taxonomy: GLOBAL, USER, WORKSPACE, SYSTEM
- Enforce stateless scaling: authorization is deterministic from explicit request input and current
  authoritative state
- Define caching and invalidation principles: caches MUST NOT expand permissions beyond
  authoritative state
- Host the pluggable Storage Abstraction Layer (SAL) for object storage

**Key models**: `ResourceContext`, `RequestContextStore`, `SpringMediator`, `PlatformContracts`

**Caching rules**:

- Caches MUST reduce lookup cost without changing authorization semantics
- Credential revocation is authoritative: a revoked credential MUST NOT continue to authorize
- Invalidated refresh sessions MUST NOT survive cache or node-local state
- Equivalent nodes MUST produce equivalent decisions from equivalent state

---

## Principal Taxonomy

Every authenticated actor recognized by the platform is represented as one of six principal types:

| Type              | Purpose                                        | Phase One                     |
|-------------------|------------------------------------------------|-------------------------------|
| `USER`            | Human users with browser sessions              | ✅ Implemented (JWT + refresh) |
| `SERVICE_ACCOUNT` | Non-human actors with bearer credentials       | ✅ Implemented                 |
| `API_KEY`         | Non-human actors with key-based authentication | ✅ Implemented                 |
| `SYSTEM`          | Internal system-to-system actors               | 🔲 Deferred                   |
| `INTEGRATION`     | External platform integration actors           | 🔲 Deferred                   |
| `AGENT`           | Autonomous or semi-autonomous software agents  | 🔲 Deferred                   |

Principal identity is materialized through repo-local seams, never consumed as raw framework token
structures. Authentication success alone does not imply authorization.

---

## Resource Context Taxonomy

Authorization decisions are evaluated relative to an explicit resource context:

| Context     | Scope                                | Phase One     |
|-------------|--------------------------------------|---------------|
| `GLOBAL`    | Platform-wide, no workspace boundary | 🔲 Deferred   |
| `USER`      | Scoped to the principal's user data  | 🔲 Deferred   |
| `WORKSPACE` | Scoped to a specific workspace       | ✅ Implemented |
| `SYSTEM`    | Internal system resources            | 🔲 Deferred   |

All four contexts are part of the stable taxonomy even when only `WORKSPACE` is implemented.
Permissions, grants, scopes, and policies MUST NOT rely on implicit context inference.

---

## Architecture Principles

### Deny-by-default

Protected API behavior requires successful authentication, applicable context resolution, and
explicit authorization success before access is granted. The absence of any required fact results
in denial. Explicit denial overrides any allow path.

### Explicit-over-implicit

The platform MUST NOT infer access from role names, token presence, credential type, or
unspecified defaults. Permissions are explicit `<domain>:<resource>:<action>` identifiers with no
implicit hierarchy or prefix-based inheritance.

### Deterministic evaluation

Equivalent requests against equivalent authoritative state MUST produce equivalent authorization
outcomes. Outcomes MUST NOT depend on instance-local state, cache timing, request ordering, or
which node processed a prior request.

### Separation of concerns

```
Identity  ≠  Credentials  ≠  Authorization
   who         how              what
```

These three concerns MUST remain in separate bounded contexts. No single context absorbs
unrelated responsibilities merely for convenience.

### Scopes reduce, never create

A scope MUST narrow what an otherwise allowable principal may do. A scope MUST NEVER create or
expand permissions not already available through roles or direct grants.

### Stateless scaling

Authorization and identity evaluation for API requests is deterministic from explicit request
input and current authoritative platform state. Caches are performance optimizations, not
semantic dependencies — invalidation converges to the authoritative state.

---

## Phase One Proving Slice

Phase one validates the platform architecture through a narrow vertical slice, not by implementing
full breadth. The proving slice proves end-to-end behavior across all six bounded contexts.

### Endpoints

| Endpoint                                                | Purpose                                                                                                                   | Status        |
|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|---------------|
| `GET /api/authorization/workspace-access/current`       | Prove principal materialization, membership, RBAC, direct grants, feature entitlements, deny-by-default, governance proof | ✅ Implemented |
| `GET /api/authorization/resources/{resourceId}/preview` | Prove target-aware scope reduction with explicit `targetResourceId`                                                       | ✅ Implemented |
| `POST /api/auth/login`                                  | USER login with JWT + refresh cookie issuance                                                                             | ✅ Implemented |
| `POST /api/auth/refresh`                                | Refresh credential validation and new JWT issuance                                                                        | ✅ Implemented |
| `POST /api/auth/logout`                                 | Authoritative refresh session invalidation                                                                                | ✅ Implemented |
| `POST /api/auth/register`                               | User registration with email verification token                                                                           | ✅ Implemented |
| `POST /api/auth/verify-email`                           | Email verification token validation after SPA link entry                                                                  | ✅ Implemented |
| `POST /api/auth/resend-verification`                    | Resend verification email                                                                                                 | ✅ Implemented |
| API-key replacement                                     | Replace an active API key with cutover (no overlap)                                                                       | ✅ Implemented |

### What it proves

- Repo-local principal materialization for USER, SERVICE_ACCOUNT, and API_KEY types
- Active workspace resolution from explicit request input
- Workspace membership lookup and role-based permission composition
- Persisted direct-grant evaluation (ALLOW, DENY override, expired exclusion)
- Scope reduction over explicit `targetResourceId` (narrows, never manufactures)
- Workspace-scoped feature entitlement gating
- Deterministic deny-by-default behavior
- Runtime audit-ready allow/deny proof
- API-key replacement with no-overlap cutover
- PostgreSQL-backed verification of all authorization paths
- Stateless node equivalence: any node produces the same outcome from the same state

---

## Key Design Decisions

| Decision                               | Choice                                            | Alternatives                                    | Rationale                                                                                        |
|----------------------------------------|---------------------------------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Modular monolith                       | Single deployable unit with Spring Modulith       | Microservices                                   | Simpler operations; can extract later; keeps cross-context dispatch cheap                        |
| CQRS via mediator                      | `SpringMediator` dispatches commands and queries  | Direct service injection                        | Allows cross-cutting concerns (auth, context) to surround dispatch without changing feature code |
| Permission format                      | `<domain>:<resource>:<action>` strings            | Enum-based, integer bitfields                   | Explicit, extensible, debuggable — no enum recompilation for new permissions                     |
| Scope semantics                        | Reduction-only, after base-permission allow       | Scope-first, context-wide filters               | Prevents scope escape: a scope can never manufacture access the base permission does not grant   |
| Resource context                       | Explicit `X-Workspace-Id` header                  | Derived from JWT claims, inferred from URL      | Cannot be forged or confused; stays in tenancy context, not mixed into credentials               |
| JWT for USER, bearer+key for non-human | Multiple credential paths, single principal model | Unified token format for all principal types    | Each credential path has appropriate security properties; entity model stays stable              |
| Refresh cookie                         | `HttpOnly` + `SameSite` + `Secure`                | `localStorage`, `sessionStorage`, opaque tokens | XSS-resistant; refresh credential never exposed to JavaScript                                    |
| Email verification                     | Gating at feature level, not auth level           | Block login until verified                      | Users can authenticate but are gated from sensitive features until verified                      |

---

## Deferred Capabilities

These capabilities are part of the durable platform model but explicitly deferred beyond phase one:

| Capability                                                   | Reason                                                                    |
|--------------------------------------------------------------|---------------------------------------------------------------------------|
| SYSTEM, INTEGRATION, AGENT principal execution               | Phase one proves USER, SERVICE_ACCOUNT, and API_KEY paths                 |
| GLOBAL, USER, SYSTEM resource contexts                       | Phase one proves WORKSPACE context                                        |
| Full governance persistence and compliance reporting         | Phase one proves runtime audit-ready proof without durable storage        |
| Generic scope engine, wildcards, inheritance                 | Phase one proves scope reduction for one capability                       |
| Feature entitlement CRUD, admin APIs, catalog/billing        | Phase one proves one persisted workspace-scoped key                       |
| Service-account rotation and credential-family management    | Phase one proves API-key replacement only                                 |
| Dual-active rollover windows and grace periods               | Phase one proves no-overlap cutover                                       |
| Generalized policy administration UI                         | Phase one proves API-level evaluation only                                |
| ABAC condition attributes                                    | Phase one uses deterministic RBAC; model permits future ABAC augmentation |
| Remembered workspace defaults and multi-workspace evaluation | Phase one resolves one explicit workspace per request                     |

---

## Related Documents

- [Component Diagram](c4/03-component.md) — C4 Level 3 view of bounded contexts
- [ADR-0009: JWT & HttpOnly Cookie Authentication](adr/0009-jwt-and-httponly-cookie-authentication.md)
- [Architecture Overview](README.md)
- [openspec/specs/](../../openspec/specs/) — Detailed Gherkin specifications per context

---

Last updated: 2026-08-29
