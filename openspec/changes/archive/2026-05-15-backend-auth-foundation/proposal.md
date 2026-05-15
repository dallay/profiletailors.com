# Proposal: Backend Auth Foundation

## Intent

Establish the first repo-local foundation for a reusable Identity, Authorization, and Workspace
platform inside `server/smp`, designed to support multiple SaaS products rather than only one
product-specific auth flow. This change will adapt the strongest architectural ideas from the
external CVIX `shared/common` and `spring-boot-common` patterns into Profile Tailors-owned platform
contracts, while avoiding blind import of CVIX assumptions and keeping the first implementation wave
deliberately phased.

The change name remains `backend-auth-foundation` because it already anchors the current SDD thread
and still accurately describes the entry point, but the proposal is now explicitly broadened: this
is not just a narrow auth bootstrap. It is the first platform foundation for IAM, workspace tenancy,
and policy-oriented authorization, delivered in a controlled first phase.

## Platform Target Architecture

The long-term target is a reusable IAM/workspace platform that can serve multiple SaaS products and
bounded domains with shared identity, tenancy, and authorization capabilities.

### Target bounded contexts

- **Identity** — principals, profiles, lifecycle, subject identity, cross-product identity
  consistency.
- **Tenancy** — workspaces, ownership, membership, workspace context resolution, tenant scoping.
- **Authorization** — roles, permissions, policies, scopes, direct grants, entitlements, resource
  access evaluation.
- **Credentials** — passwords, API keys, token issuance, token validation, rotation, revocation
  seams, external credential providers.
- **Governance** — audit, compliance-oriented change visibility, administrative controls, policy
  traceability.
- **Platform** — shared contracts, mediator/CQRS, caching, rate limits, observability, and
  integration seams.

### Target principal model

The platform direction must support these principal types as first-class concepts:

- `USER`
- `SERVICE_ACCOUNT`
- `API_KEY`
- `SYSTEM`
- `INTEGRATION`
- `AGENT`

### Target resource context model

Authorization decisions must be evaluable against stable resource contexts:

- `GLOBAL`
- `USER`
- `WORKSPACE`
- `SYSTEM`

### Target platform capabilities

The architecture must leave room for the following platform capabilities even when they are not all
implemented in phase one:

- workspaces and ownership
- memberships
- roles as compositions of permissions
- permission catalogs by feature area
- policies and scopes
- direct grants
- entitlements
- tokens and credential flows
- auditability
- caching
- rate limiting
- observability
- compliance support
- federated authentication direction via Keycloak/OIDC compatibility

### Reference ecosystem

The canonical technical base is aligned with:

- **Backend:** PostgreSQL + Spring Boot/Kotlin
- **Frontend/Admin/Consumer surfaces:** Vue + TypeScript
- **Federated auth direction:** Keycloak/OIDC-compatible integration path

## Scope

### In Scope Now (first implementation phase)

- Define repo-local platform foundation packages in `server/smp` for shared domain, application, and
  infrastructure concerns, shaped for multi-product IAM/workspace reuse rather than one product's
  auth layer.
- Prepare `server/smp` for hexagonal architecture and CQRS/mediator flow with minimal internal
  contracts, handlers, and cross-cutting behaviors suitable for a reusable platform core.
- Establish the initial bounded-context seams for Identity, Tenancy, Authorization, Credentials,
  Governance, and Platform, even if some contexts remain mostly skeletal in the first wave.
- Introduce first-class principal and resource-context modeling so the platform is not hard-coded
  around only human users or only workspace resources.
- Establish the minimum tenancy model for the foundation: workspace identity, ownership/membership
  seams, active workspace context propagation, and workspace-aware authorization evaluation.
- Establish the minimum authorization model for the foundation: permissions, roles as permission
  compositions, extensible permission naming by feature area, and an evaluation seam that can later
  support policies, scopes, grants, and entitlements.
- Add JWT-first authentication boundaries and Spring Security integration appropriate for platform
  foundations, while preserving a future Keycloak/OIDC-compatible federation path.
- Deliver one minimal end-to-end vertical slice that proves the platform architecture works across
  principal resolution, workspace context, membership lookup, and permission-checked access.
- Define initial persistence, migration, and testing conventions required for the platform
  foundation and the proving slice, including Liquibase structure and boundary tests.
- Establish early hooks for audit, observability, caching, and rate-limiting integration at the
  contract/configuration level where doing so clarifies the platform shape without forcing full
  implementation now.

### Deferred to Later Phases

- Full implementation breadth for all bounded contexts, especially Governance and advanced
  Credentials lifecycle.
- Full policy engine behavior, rich scope semantics, entitlement orchestration, and generalized
  direct-grant administration UX/API.
- Broad product-wide authorization rollout across all future feature modules.
- Full user lifecycle breadth: invitations, password reset, MFA, email verification, profile
  management, and advanced session management.
- Full support for every principal type in runtime flows; phase one only needs the contracts and at
  least one proven concrete path, not all six operationalized.
- Full OIDC federation, external IdP brokering, and Keycloak production integration; only the
  compatibility direction and extension seams belong now.
- Production-grade audit analytics, compliance workflows, rate-limit enforcement breadth, and
  platform-wide cache topology.
- Separate reusable Gradle modules or extraction into a standalone shared platform package; phase
  one stays repo-local inside `server/smp`.
- Final decisions for every multitenancy/storage concern such as PostgreSQL RLS strategy, complex
  tenant provisioning, and all future workspace resolution patterns.
- Vue/TypeScript frontend implementation work; this proposal only acknowledges that ecosystem as a
  reference consumer of the platform.

### Explicit Non-Goals for This Change

- Do not turn phase one into a complete enterprise IAM program.
- Do not implement every future social/federated login concern immediately.
- Do not optimize for all products before proving the base contracts through one thin vertical
  slice.
- Do not couple platform authorization semantics directly to Spring Security annotations or token
  claims.
- Do not write frontend/admin console functionality in this change.

## Approach

Use an adopt-and-adapt strategy inside `server/smp`: take the reusable concepts from the CVIX
references, but reframe them as a repo-local IAM/workspace platform foundation owned by this
monorepo. The platform core should separate framework-agnostic domain/application contracts from
Spring/WebFlux/security/persistence adapters, so that future products and modules can depend on
platform semantics without inheriting transport or framework assumptions.

The key discipline is to separate **platform target architecture** from **first implementation phase
**:

- The **target architecture** defines the reusable bounded contexts, principal taxonomy, resource
  contexts, and extension seams.
- The **first implementation phase** proves only the minimum vertical slice needed to validate those
  architectural decisions in running code later.

Key shaping decisions for this proposal:

- Roles are compositions of permissions, not alternative authorization primitives.
- Users belong to workspaces through membership, but the platform must also support non-user
  principals conceptually from day one.
- Authorization is resource-context-aware and must not be reduced to simple “user has role” checks.
- Permissions must be easy to extend by feature area without coupling future modules to Spring
  Security internals.
- JWT is an authentication transport and token format seam, not the home of authorization truth.
- Keycloak/OIDC compatibility is a direction for federation, so local contracts must not hard-code a
  provider-specific identity model.
- Auditability, observability, caching, and rate limits are platform concerns and should appear as
  planned seams even if most operational detail is deferred.
- The repo-local implementation should preserve a later extraction seam if reuse across backend
  modules or products becomes real.

## Affected Areas

| Area                                                                  | Impact       | Description                                                                                                                                                                |
|-----------------------------------------------------------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/build.gradle.kts`                                         | Modified     | Add or prepare platform-foundation dependencies and test support for JWT resource server, Liquibase, architecture/security tests, and future OIDC-compatible seams.        |
| `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` | Modified     | Establish root scanning and package/module boundaries aligned to the IAM/workspace platform foundation.                                                                    |
| `server/smp/src/main/resources/application.yaml`                      | Modified     | Add platform-oriented configuration scaffolding for datasource, security, JWT, workspace context, migrations, observability hooks, and future federation seams.            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/**`       | New          | Shared platform contracts and cross-cutting infrastructure for CQRS/mediator, common abstractions, caching/observability/rate-limit seams, and reusable platform concerns. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/**`       | New          | Identity bounded-context seams and core domain/application concepts for principals and subject identity.                                                                   |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/**`        | New          | Workspace, ownership, membership, and tenant-context seams.                                                                                                                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/**`  | New          | Roles, permissions, policies/scopes/grants/entitlements seams, and authorization evaluation contracts.                                                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/**`    | New          | Token, password, API key, and credential-provider seams required for the first platform wave.                                                                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/**`     | New          | Audit/compliance-oriented contracts and initial extension points, even if implementation remains minimal in phase one.                                                     |
| `server/smp/src/main/resources/db/**`                                 | New          | Liquibase changelog structure and initial migrations for platform/identity/tenancy/authorization foundation tables required by the proving slice.                          |
| `server/smp/src/test/kotlin/com/profiletailors/smp/**`                | Modified/New | Add tests for module boundaries, security flow, tenancy context propagation, and permission-based authorization slice.                                                     |
| `openspec/changes/backend-auth-foundation/**`                         | Modified     | Update proposal and later downstream artifacts to reflect the new canonical IAM/workspace platform direction.                                                              |

## Risks

| Risk                                                                                             | Likelihood | Mitigation                                                                                                                                      |
|--------------------------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| The proposal becomes too broad and loses delivery control                                        | High       | Keep a strict separation between target platform architecture and the first implementation phase, with explicit deferrals.                      |
| CVIX assumptions leak into repo-local platform contracts                                         | Medium     | Copy concepts only, rename aggressively, and reject provider/header/package assumptions unless validated against the canonical platform model.  |
| A supposedly reusable platform is still accidentally modeled around only human users             | Medium     | Introduce principal taxonomy up front and validate phase-one contracts against non-user principal support even if only one path is implemented. |
| Authorization semantics collapse into token claims or Spring annotations                         | High       | Keep permissions, roles, grants, and workspace evaluation in domain/application services and use Spring Security only as an adapter boundary.   |
| Workspace and resource-context semantics remain underspecified                                   | High       | Resolve the first supported context rules in specs/design and defer broader variants explicitly.                                                |
| Bounded contexts are introduced nominally but end up tightly coupled in code                     | Medium     | Define clear package/module boundaries and add modulith/architecture tests early.                                                               |
| Federation direction is blocked by local-first credential assumptions                            | Medium     | Preserve OIDC/Keycloak-compatible seams and avoid provider-specific identity coupling in the core model.                                        |
| Platform concerns like audit, caching, rate limits, and observability are ignored until too late | Medium     | Capture them as explicit phase-one seams and deferred capabilities so later phases do not require architectural backtracking.                   |

## Rollback Plan

If the platform-foundation direction proves incorrect or prematurely broad, rollback should remain
contained to `server/smp` and revert the repo to the current bootstrap baseline without forcing
downstream product rollback.

Rollback would:

1. Remove the new platform-oriented packages introduced by this change (`platform/**`,
   `identity/**`, `tenancy/**`, `authorization/**`, `credentials/**`, `governance/**`) and any
   earlier equivalent foundation packages created during implementation.
2. Revert `build.gradle.kts`, `application.yaml`, and Liquibase changes related to the IAM/workspace
   platform foundation.
3. Drop only the new foundation schema artifacts created for non-production environments and local
   development flows.
4. Preserve OpenSpec artifacts so the team can narrow scope or redesign bounded-context seams
   without keeping the code.

Because the first implementation phase is intentionally thin and repo-local, rollback remains
technically feasible as a contained backend reversal rather than a multi-product migration event.

## Dependencies

- Existing Spring Boot 4, Kotlin, WebFlux, Spring Security, Spring Modulith, R2DBC, and coroutine
  stack in `server/smp`.
- PostgreSQL-backed local development environment already represented by `server/smp/compose.yaml`.
- External CVIX references as inspiration only, not as runtime/build dependencies.
- Canonical platform direction toward Keycloak/OIDC-compatible federation.
- Future OpenSpec spec/design/tasks phases to lock exact bounded-context responsibilities, naming
  rules, and first-wave behaviors before coding.

## Success Criteria

- [ ] `server/smp` has a clear repo-local package structure aligned to a reusable IAM/workspace
  platform rather than a single product auth module.
- [ ] The proposal cleanly separates long-term platform target architecture from the first
  implementation phase.
- [ ] Principal taxonomy and resource-context taxonomy are represented in the planned architecture,
  even if not every type is fully implemented in phase one.
- [ ] Workspace-aware identity and authorization seams are defined so memberships, roles,
  permissions, and future grants/policies/entitlements can evolve without coupling use cases to
  Spring Security internals.
- [ ] Roles are modeled as compositions of permissions, and permission identifiers are extensible by
  feature area.
- [ ] One minimal authenticated and permission-checked vertical slice is planned as the proof point
  for the platform architecture.
- [ ] Liquibase, persistence, and testing conventions are planned for the first platform wave
  without overcommitting to every future domain concern.
- [ ] The proposal explicitly defers broader federation, governance, compliance, and advanced
  credential/authorization breadth into later phases.
