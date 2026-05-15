# Design: Backend Auth Foundation

## Technical Approach

Revise `server/smp` from a narrow auth bootstrap toward a **repo-local IAM/workspace platform
foundation** that is reusable across future SaaS products, while still delivering a disciplined
first implementation phase.

The design separates:

- **target platform architecture** — reusable bounded contexts, stable contracts, extension seams,
  and operational hooks
- **phase-one proving slice** — one authenticated, workspace-scoped, permission-checked query path
  that validates the architecture without pretending to implement the whole platform

The platform remains hexagonal:

- **domain** defines meaning
- **application** defines use cases, mediator contracts, and policy evaluation seams
- **infrastructure** adapts Spring Boot 4, WebFlux, coroutines, Security, R2DBC, Liquibase, caching,
  metrics, and rate-limiting concerns

The platform is also **federation-ready but provider-neutral**:

- Spring Security resource server + JWT validates incoming bearer credentials now
- OIDC/Keycloak compatibility is preserved as an adapter boundary
- authorization truth remains server-side and domain/application-driven, not token-claim-driven

## Architecture Decisions

### Decision: Organize the platform by bounded context, not by technical layer alone

**Choice**: Model the platform as six repo-local bounded contexts: `platform`, `identity`,
`tenancy`, `authorization`, `credentials`, and `governance`. Each context may contain `domain`,
`application`, and `infrastructure` packages as needed.

**Alternatives considered**:

- Keep a single `foundation` + `auth` split.
- Organize all code under horizontal folders only (`domain`, `service`, `security`, `persistence`).
- Extract Gradle modules immediately.

**Rationale**: The new proposal explicitly broadens the source of truth beyond auth into reusable
IAM/workspace capabilities. A context-based structure keeps ownership explicit, avoids one giant "
security" module, and maps cleanly to future extraction if reuse becomes real. Immediate Gradle
extraction is still premature for a bootstrap backend.

### Decision: Keep CQRS/mediator contracts in `platform`, with coroutine-first handlers

**Choice**: Put mediator, command/query contracts, behavior pipeline, context-provider interfaces,
and shared execution hooks in `platform.application`. Handlers are coroutine-first; WebFlux adapters
bridge reactive HTTP into suspend-based application execution.

**Alternatives considered**:

- Controller-to-service direct calls.
- Reactor-only application handlers.
- Event-only dispatch model.

**Rationale**: CQRS/mediator is one of the few cross-cutting architectural concepts that truly
belongs to the reusable platform layer. Coroutine-first handlers keep the core readable while
preserving WebFlux compatibility through infrastructure adapters. Direct calls would make
authorization, audit, and observability seams inconsistent across future modules.

### Decision: Treat OIDC/JWT as credential and federation adapters, not authorization truth

**Choice**: Incoming JWT/OIDC validation belongs to `credentials.infrastructure` and
`identity.infrastructure`, where external claims are normalized into an internal principal model.
Authorization remains in `authorization.application` and `authorization.domain`, evaluated against
platform data.

**Alternatives considered**:

- Encode all permissions as token claims and trust them as authoritative.
- Use Spring Security authorities as the domain model.
- Hard-code Keycloak claim structures into core classes.

**Rationale**: Federation direction must stay provider-neutral. Tokens prove authentication context,
but platform authorization must remain explainable, revocable, workspace-aware, and consistent even
when memberships, direct grants, or deny rules change after token issuance.

### Decision: Distinguish workspace ownership from workspace membership roles

**Choice**: Workspace ownership is a tenancy concern with its own semantic meaning and persistence
seam. Membership roles are authorization assignments attached to workspace participation, not
substitutes for ownership.

**Alternatives considered**:

- Treat owner as just another role.
- Store owner only as a permission bundle.
- Ignore ownership until later.

**Rationale**: Ownership carries stronger business semantics than a normal role: bootstrap
authority, governance responsibility, administrative transfer rules, and future billing/compliance
implications. If ownership is modeled as only a role, later platform rules become harder to express
and audit.

### Decision: Use permission-first authorization with extensible grants, deny rules, scopes, policies, and entitlements

**Choice**: The smallest stable unit is `PermissionKey`. Roles compose permissions. Grants may
attach permissions or roles directly to principals in a resource context. Deny rules override allow
paths. Policies and entitlements are modeled as extension seams even if phase one implements only
the narrowest executable subset.

**Alternatives considered**:

- Role-only authorization.
- Direct permission booleans on membership rows.
- Full policy engine implementation in phase one.

**Rationale**: The platform must stay extensible without becoming overbuilt immediately.
Permission-first modeling gives future product areas a stable integration contract. Explicit seams
for direct grants, deny rules, policies, and entitlements prevent architectural backtracking later,
while phase one still focuses on a manageable slice.

### Decision: Make resource context explicit in authorization inputs

**Choice**: Authorization evaluation always receives a `PrincipalContext` plus `ResourceContext`.
`ResourceContext` includes type (`GLOBAL`, `USER`, `WORKSPACE`, `SYSTEM`) and optional resource
identifiers.

**Alternatives considered**:

- Infer context only from endpoint path.
- Assume all protected operations are workspace-scoped.
- Keep authorization APIs parameter-light and rebuild context inside adapters.

**Rationale**: The proposal now targets multi-principal, multi-context authorization. An explicit
resource context contract prevents platform semantics from being hidden inside transport conventions
and gives future services a consistent authorization surface.

### Decision: Build operational hooks early, but defer operational breadth

**Choice**: Introduce platform seams for audit, cache invalidation, observability, and rate limiting
in phase one, but do not implement full enterprise-scale policy engines, distributed caches, or
compliance workflows yet.

**Alternatives considered**:

- Ignore operational seams until after the first feature.
- Implement all operational concerns fully in the first wave.

**Rationale**: Ignoring these concerns would force later invasive redesign. Implementing them fully
now would blow scope. Early hooks preserve architecture without losing delivery control.

## Bounded Context Boundaries and Ownership

### 1. Platform

**Owns**:

- CQRS/mediator contracts
- cross-cutting execution behaviors
- shared identifiers and context primitives only when truly generic
- exception contracts and response mapping seams
- caching, rate limiting, observability extension points
- common infrastructure glue for Spring/WebFlux/coroutines

**Does not own**:

- user identity lifecycle
- workspace membership semantics
- authorization rules
- credential issuance semantics

### 2. Identity

**Owns**:

- principal identity concepts
- user profile identity records
- subject/provider linkage
- normalized principal model for users, service accounts, API keys, integrations, system actors,
  agents
- external identity mapping seams

**Does not own**:

- workspace membership
- permission evaluation
- token validation runtime glue alone

### 3. Tenancy

**Owns**:

- workspaces
- workspace ownership
- membership existence and status
- active workspace resolution seam
- tenant-scoped resource context assembly

**Does not own**:

- role composition rules
- permission catalog
- token issuance or IdP claims

### 4. Authorization

**Owns**:

- permissions
- roles
- grants
- deny rules
- scopes
- policies
- entitlements
- effective authorization evaluation
- authorization decision results and explanation metadata

**Does not own**:

- primary identity/provider linkage
- raw JWT/OIDC validation
- workspace lifecycle

### 5. Credentials

**Owns**:

- credential types and validation seams
- JWT/OIDC token validation adapters
- API key and service-account credential seams
- token/API-key revocation and rotation seams
- password and secret-based credential concepts when needed later

**Does not own**:

- business authorization truth
- workspace membership semantics

### 6. Governance

**Owns**:

- audit event contracts
- actor/action/target traceability
- authorization decision audit hooks
- administrative and compliance-oriented reporting seams
- policy/grant change trace seams

**Does not own**:

- core permission resolution logic
- transport/runtime wiring except through adapters

## Repo-Local Package / Module Mapping for `server/smp`

### Root package structure

```text
server/smp/src/main/kotlin/com/profiletailors/smp/
├── SmpApplication.kt
├── platform/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── identity/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── tenancy/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── authorization/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
├── credentials/
│   ├── domain/
│   ├── application/
│   └── infrastructure/
└── governance/
    ├── domain/
    ├── application/
    └── infrastructure/
```

### Suggested ownership by package

#### `com.profiletailors.smp.platform.domain`

- shared IDs only when cross-context (`PrincipalId`, `WorkspaceId`, `PermissionKey` if kept generic)
- common result/error abstractions
- resource context primitives if truly cross-cutting

#### `com.profiletailors.smp.platform.application`

- `Command`, `Query`, handlers, `Mediator`
- behavior pipeline contracts
- `PrincipalContextProvider`, `ResourceContextProvider`
- cache/metrics/rate-limit hook interfaces

#### `com.profiletailors.smp.platform.infrastructure`

- Spring mediator wiring
- WebFlux/coroutine bridge
- Problem Details mapping
- common observability interceptors/filters
- cache invalidation publisher stubs
- rate-limit adapter interfaces

#### `com.profiletailors.smp.identity.domain`

- `Principal`
- `PrincipalType`
- `UserIdentity`
- `ExternalSubjectLink`
- `ServiceAccountIdentity`, `IntegrationIdentity`, `AgentIdentity` seams

#### `com.profiletailors.smp.tenancy.domain`

- `Workspace`
- `WorkspaceOwnership`
- `WorkspaceMembership`
- membership status/value objects
- tenant context semantics

#### `com.profiletailors.smp.authorization.domain`

- `Permission`
- `Role`
- `Grant`
- `DenyRule`
- `Scope`
- `Policy`
- `Entitlement`
- `AuthorizationDecision`

#### `com.profiletailors.smp.credentials.domain`

- `CredentialType`
- `FederatedTokenCredential`
- `ApiKeyCredential` seam
- `ServiceAccountCredential` seam
- token revocation/rotation concepts

#### `com.profiletailors.smp.governance.domain`

- `AuditEvent`
- `DecisionAuditEntry`
- `AdministrativeChangeRecord`

### Spring Modulith direction

Phase one remains one Gradle module, but modulith/architecture tests should enforce allowed
dependencies roughly as:

```text
platform <- used by all
identity -> platform
tenancy -> platform, identity
authorization -> platform, identity, tenancy
credentials -> platform, identity
governance -> platform, identity, tenancy, authorization, credentials
```

Important guardrails:

- `platform` must not depend on domain rules from the others
- `authorization` may consume identity and tenancy context, but tenancy must not depend on
  authorization policy internals
- `credentials` must not become the place where permission logic lives

## Hexagonal + CQRS + WebFlux + Coroutines Seams

### Application dispatch contracts

The mediator seam remains platform-owned and coroutine-first:

```text
HTTP/WebFlux Adapter
  -> coroutine controller/use-case adapter
  -> platform mediator
  -> behaviors
  -> handler
  -> domain/application ports
  -> infrastructure adapters
```

### Shared request lifecycle

1. WebFlux receives request.
2. credential adapter validates bearer/API-key/service-account credential.
3. identity adapter normalizes authenticated actor into internal principal context.
4. tenancy adapter resolves active workspace/resource context if required.
5. controller adapter dispatches command/query to platform mediator.
6. mediator runs behaviors in order.
7. handler invokes application services and repository ports.
8. authorization service evaluates effective decision.
9. governance hook records audit event.
10. response is mapped back to HTTP.

### Behavior ordering

```text
Controller
  -> Mediator
     -> Trace/Correlation Behavior
     -> Authentication Context Presence Behavior
     -> Resource Context Resolution Behavior
     -> Rate Limit Hook Behavior
     -> Authorization Hook/Check Behavior
     -> Audit Hook Behavior
     -> Handler
```

For phase one, some of these may be implemented as explicit service calls instead of generic
behaviors, but the seam ordering above is the intended platform shape.

## Principal Model

### Principal taxonomy

The platform target must support these principal types:

- `USER`
- `SERVICE_ACCOUNT`
- `API_KEY`
- `SYSTEM`
- `INTEGRATION`
- `AGENT`

### Internal principal context

The runtime application layer should consume a normalized principal context rather than raw Spring
Security types.

Suggested shape:

```text
PrincipalContext
- principalId
- principalType
- subject
- provider
- displayIdentity
- authenticationMethod
- issuedCredentialReference
- attributes (non-authoritative metadata)
```

### Ownership notes

- Identity owns principal normalization.
- Credentials owns how a credential is validated.
- Authorization consumes principal context but does not own authentication mechanics.

## Resource Context Model

### Resource context taxonomy

Authorization decisions must be evaluated against stable resource contexts:

- `GLOBAL`
- `USER`
- `WORKSPACE`
- `SYSTEM`

### Context shape

Suggested runtime concept:

```text
ResourceContext
- contextType
- workspaceId?        # when WORKSPACE
- resourceOwnerId?    # when USER-owned resource matters
- targetResourceType? # optional future extension
- targetResourceId?   # optional future extension
- scopeHints[]        # optional extension for scope/policy evaluation
```

### First-slice rule

Phase one proves **one explicit active workspace per request** for workspace-scoped protected
operations. That workspace is an input to resource context resolution, but authorization still
validates that the principal is allowed to act in that context.

## Keycloak / OIDC-Compatible Federation Boundary

### Boundary definition

The platform must support an OIDC-compatible federation direction, including Keycloak compatibility,
without making Keycloak the core model.

### Adapter responsibilities

`credentials.infrastructure` / `identity.infrastructure` may:

- validate JWT/JWS/JWK metadata
- validate issuer/audience/timestamps
- extract provider subject
- map external claims into internal principal context
- support future provider-specific claim translation

### Core model rules

Core platform contracts must **not** assume:

- Keycloak-specific realm/client structures
- provider-specific role claim names
- provider-specific workspace/organization claim conventions
- provider-specific subject formatting

### Design implication

The platform should define a provider-neutral seam such as:

```text
FederatedIdentityProvider
- validate credential
- normalize external subject
- map provider claims -> principal attributes
```

Phase one only needs the JWT/OIDC-compatible path for authenticated user requests, but the seam must
remain extensible for future providers.

## Workspace Ownership vs Membership Roles

### Ownership model

Workspace ownership is a tenancy concept representing foundational control over the workspace. It is
separate from membership authorization assignments.

Possible semantics of ownership:

- initial administrative authority
- transfer authority
- governance/audit accountability
- future billing/compliance alignment

### Membership role model

Membership roles are authorization assignments that determine permissions inside the workspace. A
workspace owner may also hold roles, but ownership is not reducible to role membership.

### Evaluation rule

A workspace-scoped decision may consider:

1. explicit ownership shortcut or ownership-derived system grants
2. membership existence
3. assigned roles
4. direct grants/denies
5. policy/scope/entitlement overlays

Phase one may implement ownership as a persisted workspace-owned principal relation and still rely
on membership + role checks for the proving slice. The important design rule is semantic separation.

## Authorization Interaction Model

### Concepts

#### Permission

Stable capability identifier owned by a feature area.

Examples:

- `workspace.access.read`
- `workspace.members.manage`
- `authorization.role.assign`
- `governance.audit.read`

#### Role

Named bundle of permissions. Roles are assignable, compositional, and resource-context-aware.

#### Grant

Explicit allow assignment of a permission or role to a principal in a defined resource context.

#### Deny Rule

Explicit deny assignment that overrides lower-precedence allow paths. Deny rules exist to support
exceptions, suspensions, and governance controls.

#### Scope

Constraint wrapper that narrows where or how a permission/grant applies. Scope is a seam now, richer
semantics later.

#### Policy

Evaluatable rule layer that can combine principal type, resource context, scopes, grants, ownership,
or other conditions.

#### Entitlement

Resolved access package or derived access outcome that may come from grants, roles, policies,
subscription level, or product enablement. Entitlements are a seam in phase one, not a full
implemented engine.

### Interaction order

Recommended conceptual model:

```text
Permission Catalog
  -> Roles compose permissions
  -> Grants attach role/permission to principal or membership in context
  -> Deny rules subtract authority
  -> Scopes constrain applicability
  -> Policies add conditional evaluation
  -> Entitlements package or derive broader effective access
```

### Practical phase-one executable subset

Built now:

- permission catalog
- roles as permission composition
- membership-bound role assignment
- effective permission evaluation for a workspace-scoped request
- explicit seam types for grant/deny/scope/policy/entitlement

Deferred later:

- rich conditional policy engine
- broad direct-grant administration
- full entitlement orchestration
- distributed deny propagation semantics

## Effective Authorization Decision Flow

### Decision inputs

An authorization decision should consume:

- `PrincipalContext`
- `ResourceContext`
- requested `PermissionKey`
- optional action metadata
- current time / environment metadata when needed later

### Decision evaluation order

Recommended order:

1. verify authenticated principal context exists
2. verify required resource context exists
3. resolve principal identity record
4. resolve tenancy relation for the context (ownership/membership where relevant)
5. collect assigned roles for the relevant membership or principal binding
6. collect direct grants relevant to the principal and resource context
7. collect explicit deny rules relevant to the principal and resource context
8. resolve permissions from roles + direct grants
9. apply scope filters
10. evaluate policies if any are active
11. apply deny precedence
12. produce `AuthorizationDecision` with outcome and explanation metadata

### Decision semantics

Suggested precedence:

```text
explicit deny > allow from ownership shortcut > allow from direct grant > allow from role composition > default deny
```

This precedence may evolve, but phase-one design should preserve the ability to represent it.

## Direct Grants and Deny Rules

### Direct grants

Direct grants allow targeted exceptions without redefining shared roles.

Examples:

- give one member `workspace.members.manage` temporarily
- give an integration principal `content.post.publish` in one workspace only

Phase-one design implication:

- create domain and persistence seams for direct grants
- runtime execution may defer actual broad use beyond a narrow path

### Deny rules

Deny rules support suspension, governance override, or policy exception handling.

Examples:

- deny one membership from exporting data despite a broader role
- deny an API key from administrative actions in a workspace

Phase-one design implication:

- deny rules should exist as domain/persistence-ready seams
- effective evaluator must be structured so explicit deny can be introduced without redesign

## Token / Service Account / API Key Model Seams

### Token seam

The token seam supports JWT/OIDC-compatible bearer authentication now and broader token issuance
later.

Phase-one executable path:

- incoming bearer JWT validation
- normalized principal mapping for `USER`

Deferred but designed now:

- internal access token issuance
- refresh tokens
- token revocation lists
- service-to-service tokens

### Service account seam

Service accounts are first-class principals, not user impersonation hacks. They should be
representable in the identity and credentials models from day one, even if runtime authentication
for them is deferred.

### API key seam

API keys are distinct credential types that may authenticate a principal different from a human user
session. They require:

- credential identification and hashing/storage seam
- principal binding
- scope/rate-limit/governance hooks
- revocation/rotation seam

### Practical rule

Do not collapse all non-user automation into fake user records. The model must preserve real
principal type distinctions even before all are implemented.

## Audit / Governance Seams

### Audit hooks

Governance should capture at least these event classes conceptually:

- authentication success/failure
- workspace selection and authorization denial
- role/grant/policy changes
- credential creation/revocation/rotation
- administrative override events

### Phase-one minimum

Build seams for:

- publishing audit events from authorization and credential adapters
- recording key decision metadata later
- correlating actor, resource context, requested permission, and outcome

### Why this matters

Authorization platforms become unmanageable without traceability. The governance seam must exist
before platform breadth grows, even if full analytics/compliance workflows remain deferred.

## Caching and Invalidation Seams for Authorization Data

### What may be cached later

- permission catalog by feature area
- role-permission compositions
- effective permission sets for membership/principal + workspace
- direct grant lookup results
- deny rule lookup results

### Phase-one design rule

Caching must be **an optimization layer**, never the source of truth.

### Required seam

Introduce application/infrastructure hooks such as:

- authorization data cache reader/writer
- invalidation publisher on role/grant/deny changes
- cache key strategy using principal/resource context/version markers

### Invalidation triggers

Future invalidation should occur on:

- role-permission changes
- membership-role changes
- direct grant create/update/delete
- deny rule create/update/delete
- workspace ownership changes if owner shortcuts are used

### Deferred

- distributed cache topology
- event-bus-backed invalidation fan-out
- cross-region cache coherence

## Observability and Rate-Limiting Hooks

### Observability hooks

The platform should emit structured hooks for:

- authentication success/failure counts
- authorization allow/deny counts
- decision latency
- cache hit/miss for authorization data
- workspace-context resolution failures
- rate-limit decisions

These hooks should integrate through `platform.infrastructure` and avoid leaking metrics tooling
into domain code.

### Rate-limiting hooks

Rate limiting is a platform concern because different principal types may need different controls.

Early seam shape should allow evaluation based on:

- principal type
- principal identifier
- workspace identifier
- credential type
- endpoint/use-case identifier

Phase one does **not** need a full enforcement matrix, but should define where rate-limit checks can
wrap request execution.

## Persistence and Liquibase Structure

### Changelog ownership

```text
server/smp/src/main/resources/db/
├── changelog/
│   ├── db.changelog-master.yaml
│   ├── platform/
│   │   └── 001-platform-baseline.yaml              # only if truly needed
│   ├── identity/
│   │   ├── 001-create-principals.yaml
│   │   └── 002-create-user-identities.yaml
│   ├── tenancy/
│   │   ├── 001-create-workspaces.yaml
│   │   ├── 002-create-workspace-ownerships.yaml
│   │   └── 003-create-workspace-memberships.yaml
│   ├── authorization/
│   │   ├── 001-create-permissions.yaml
│   │   ├── 002-create-roles.yaml
│   │   ├── 003-create-role-permissions.yaml
│   │   ├── 004-create-membership-roles.yaml
│   │   ├── 005-create-direct-grants.yaml
│   │   └── 006-create-deny-rules.yaml
│   ├── credentials/
│   │   └── 001-create-federated-subject-links.yaml
│   └── governance/
│       └── 001-create-audit-events.yaml           # may be deferred physically if not implemented
```

### First-slice persisted data that must be executable

Must be implemented in phase one:

- principals / user identity linkage sufficient for authenticated user resolution
- workspaces
- workspace ownership relation
- workspace memberships
- permissions
- roles
- role-permission composition
- membership-role assignment

Should exist as seams, but may be thin or deferred in physical implementation if tasks decide so:

- direct grants
- deny rules
- federated subject provider mapping breadth
- audit event persistence

### Table intent summary

#### `principals`

Canonical platform principal registry.

#### `user_identities`

Human-user-specific identity record linked to `principals`.

#### `external_subject_links`

Provider-neutral subject mapping for federated identity seams.

#### `workspaces`

Tenant/workspace registry.

#### `workspace_ownerships`

Explicit ownership relation separate from role assignment.

#### `workspace_memberships`

Workspace participation relation.

#### `permissions`

Canonical permission catalog.

#### `roles`

Named permission bundles.

#### `role_permissions`

Role composition join.

#### `membership_roles`

Role assignment join for workspace memberships.

#### `direct_grants`

Explicit allow assignments.

#### `deny_rules`

Explicit deny assignments.

#### `audit_events`

Governance trace seam.

## End-to-End Sequence: Authenticated Workspace-Scoped Request

### Proving slice target

Use the protected capability defined by spec: **retrieve current workspace access summary** for the
authenticated principal in the active workspace.

### Flow description

1. Client calls protected endpoint with bearer JWT and explicit workspace identifier.
2. credential adapter validates JWT.
3. identity adapter maps JWT subject into internal `PrincipalContext` for a `USER` principal.
4. tenancy resolver extracts the explicit workspace identifier and assembles
   `ResourceContext(WORKSPACE)`.
5. controller dispatches `GetCurrentWorkspaceAccessSummaryQuery` via mediator.
6. handler requests `PrincipalContext` and `ResourceContext` from application-facing providers.
7. authorization service resolves workspace ownership/membership, assigned roles, and effective
   permissions.
8. authorization service evaluates requested permission for `workspace.access.read`.
9. if allowed, handler assembles access summary for that principal/workspace.
10. governance hook emits audit event.
11. controller returns response.

### Sequence diagram

```text
Client
  -> SecurityWebFilterChain: Authorization Bearer JWT
SecurityWebFilterChain
  -> FederatedTokenValidator: validate issuer/audience/signature
FederatedTokenValidator
  --> SecurityWebFilterChain: normalized claims
SecurityWebFilterChain
  -> PrincipalContextAdapter: map external subject -> PrincipalContext(USER)
PrincipalContextAdapter
  --> SecurityWebFilterChain: authenticated principal context
SecurityWebFilterChain
  -> WorkspaceContextWebFilter: continue request
WorkspaceContextWebFilter
  -> ResourceContextResolver: resolve WORKSPACE context from request workspace identifier
ResourceContextResolver
  --> WorkspaceContextWebFilter: ResourceContext(WORKSPACE, workspaceId)
WorkspaceContextWebFilter
  -> Controller Adapter: invoke protected query
Controller Adapter
  -> Mediator: dispatch(GetCurrentWorkspaceAccessSummaryQuery)
Mediator
  -> Trace/RateLimit/Auth Behaviors: apply hooks
Trace/RateLimit/Auth Behaviors
  -> GetCurrentWorkspaceAccessSummaryHandler: handle(query)
GetCurrentWorkspaceAccessSummaryHandler
  -> PrincipalContextProvider: require()
PrincipalContextProvider
  --> Handler: PrincipalContext
GetCurrentWorkspaceAccessSummaryHandler
  -> ResourceContextProvider: require()
ResourceContextProvider
  --> Handler: ResourceContext(WORKSPACE)
GetCurrentWorkspaceAccessSummaryHandler
  -> AuthorizationService: decide(principal, resourceContext, workspace.access.read)
AuthorizationService
  -> TenancyRepository: find ownership/membership for principal in workspace
TenancyRepository
  --> AuthorizationService: ownership/membership data
AuthorizationService
  -> AuthorizationRepository: load roles, role-permissions, direct grants, deny rules
AuthorizationRepository
  --> AuthorizationService: authorization inputs
AuthorizationService
  -> Decision Engine: evaluate precedence and produce decision
Decision Engine
  --> AuthorizationService: ALLOW with explanation
AuthorizationService
  --> Handler: authorized
GetCurrentWorkspaceAccessSummaryHandler
  -> SummaryRepository/Assembler: build access summary
SummaryRepository/Assembler
  --> Handler: workspace access summary
GetCurrentWorkspaceAccessSummaryHandler
  -> AuditHook: publish decision event
AuditHook
  --> Handler: acknowledged
GetCurrentWorkspaceAccessSummaryHandler
  --> Mediator: response DTO
Mediator
  --> Controller Adapter: response DTO
Controller Adapter
  --> Client: 200 OK
```

## File Changes

| File                                                                  | Action        | Description                                                                                                                           |
|-----------------------------------------------------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/build.gradle.kts`                                         | Modify        | Add JWT resource server, Liquibase, security test, and any supporting dependencies for platform seams.                                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` | Modify        | Keep root scanning aligned to bounded contexts and modulith package boundaries.                                                       |
| `server/smp/src/main/resources/application.yaml`                      | Modify        | Add platform-oriented configuration placeholders for datasource, JWT/OIDC validation, Liquibase, observability, and rate-limit hooks. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/**`       | Create        | Add mediator/CQRS contracts, context providers, shared infrastructure hooks, and common runtime glue.                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/**`       | Create        | Add principal and external-subject modeling plus identity ports/adapters.                                                             |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/**`        | Create        | Add workspaces, ownership, membership, and resource/workspace context seams.                                                          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**`  | Create        | Add permission/role/grant/deny/policy/entitlement seams and effective decision services.                                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/**`    | Create        | Add credential-type contracts and JWT/OIDC-compatible validation adapters.                                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/**`     | Create        | Add audit/governance contracts and initial hooks.                                                                                     |
| `server/smp/src/main/resources/db/changelog/**`                       | Create        | Add Liquibase master and bounded-context-owned migrations.                                                                            |
| `server/smp/src/test/kotlin/com/profiletailors/smp/**`                | Modify/Create | Add architecture tests, security/workspace authorization integration tests, and phase-one vertical-slice coverage.                    |

## Testing Strategy

| Layer        | What to Test                                                                                                     | Approach                                                                                                                    |
|--------------|------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Unit         | Mediator dispatch and behavior ordering                                                                          | Coroutine-based unit tests on `platform.application` contracts                                                              |
| Unit         | Principal normalization and resource-context resolution rules                                                    | Pure unit tests for provider-neutral mapping and tenancy context assembly                                                   |
| Unit         | Authorization decision precedence                                                                                | Unit tests covering ownership shortcut, membership-role allow, direct grant allow, explicit deny override, and default deny |
| Unit         | Permission naming/catalog ownership rules                                                                        | Unit tests or contract tests for permission registration conventions                                                        |
| Integration  | Valid JWT + active workspace + required permission returns workspace access summary                              | `@SpringBootTest`/WebFlux test with seeded persistence data                                                                 |
| Integration  | Missing workspace identifier rejects request                                                                     | WebFlux integration test for resource-context resolver + protected endpoint                                                 |
| Integration  | Non-member denied for workspace-scoped request                                                                   | WebFlux integration test for tenancy + authorization path                                                                   |
| Integration  | Member without permission denied                                                                                 | WebFlux integration test for role-permission enforcement                                                                    |
| Integration  | Ownership remains distinct from membership role assignments                                                      | Persistence and application integration test validating separate semantics                                                  |
| Architecture | Bounded-context dependencies follow design                                                                       | Spring Modulith / architecture tests                                                                                        |
| Deferred E2E | OIDC federation, API key flow, service-account flow, distributed cache invalidation, audit persistence analytics | Later phases                                                                                                                |

## Phased Implementation Strategy

### Phase 1 — Build Now

This change should build only the minimum executable platform slice:

1. repo-local bounded-context package structure
2. `platform` mediator/CQRS/contracts and shared runtime glue
3. provider-neutral principal context seam
4. explicit workspace/resource context seam with one workspace per request
5. tenancy persistence for workspaces, ownership, memberships
6. authorization persistence for permissions, roles, role-permission composition, membership-role
   assignment
7. JWT/OIDC-compatible bearer validation path for authenticated `USER` requests
8. protected query: current workspace access summary
9. architecture tests + security/authorization integration tests
10. initial observability/audit/rate-limit hook interfaces without full operational breadth

### Phase 2 — Next likely expansions

- direct grants executable support
- explicit deny rule executable support
- audit event persistence and retrieval
- API key runtime path
- service-account runtime path
- richer principal/provider linking
- permission cache with invalidation hooks

### Phase 3 — Later platform maturity

- richer policy evaluation engine
- scope semantics breadth
- entitlements orchestration
- external IdP federation breadth beyond the first path
- revocation/rotation workflows
- distributed cache invalidation topology
- rate-limit enforcement matrix by principal/workspace/credential type
- governance/compliance reporting breadth

## Intentionally Deferred

The following are intentionally deferred beyond this design's executable phase-one commitments:

- full enterprise IAM breadth
- full Keycloak production integration specifics
- provider brokering across multiple OIDC IdPs
- refresh-token/session-management product flows
- password reset, MFA, email verification, invitations, full profile management
- broad service-account and API-key runtime execution paths
- fully executable direct-grant and deny-rule admin APIs
- full policy DSL or rules engine
- entitlement orchestration and subscription-aware access packaging
- distributed caching and event-driven invalidation fan-out
- broad rate-limit enforcement policies
- compliance dashboards and audit analytics UI
- extraction into separate shared Gradle modules or a standalone platform package

## Open Questions

- [ ] Should `PermissionKey` live under `platform.domain` as a cross-context primitive, or under
  `authorization.domain` as a stricter ownership boundary with shared references outward?
- [ ] In phase one, should workspace ownership produce an explicit allow shortcut in the decision
  engine, or should owners also be required to hold membership-role assignments until ownership
  semantics are implemented more fully?
- [ ] Should direct grants and deny rules be persisted in phase one even if runtime use is deferred,
  or should only the seams be created now to reduce migration scope?
- [ ] What exact request transport should carry the first explicit workspace identifier in phase
  one, given the foundation spec requires a repo-local resolver but rejects unapproved vendor
  assumptions?
