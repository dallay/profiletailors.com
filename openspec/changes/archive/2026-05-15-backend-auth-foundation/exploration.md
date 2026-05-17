## Exploration: backend-auth-foundation

### Current State

`server/smp` is a bootstrap-level Spring Boot 4 backend with only the application entrypoint, a
context-load test, minimal `application.yaml`, a generic local Postgres compose file, and dependency
wiring for WebFlux, Spring Security, R2DBC, Spring Modulith, OpenAPI, and coroutine support. There
is no domain model, no package-by-feature structure, no Liquibase setup yet, no security
configuration, no JWT resource-server wiring, no tenant/workspace context propagation, no
persistence adapters, and no module boundaries beyond the root package.

The repo already carries strong internal guidance in `.agents/skills/backend-platform/spring-boot/`
that assumes
hexagonal architecture, CQRS handlers, thin HTTP adapters, framework-agnostic application/domain
layers, and mediator-driven dispatch. That guidance aligns with the external CVIX shared libraries
and gives a useful target style for the new backend foundation.

The external references show two reusable layers:

- `shared/common`: framework-agnostic CQRS/mediator contracts, pipeline behaviors, domain
  annotations, value objects, outbox abstractions, and a minimal workspace authorization seam.
- `shared/spring-boot-common`: Spring auto-configuration, Spring-backed dependency provider for the
  mediator, API controller helpers, workspace reactive context propagation, global exception
  handling, R2DBC-oriented support, and Liquibase common migrations for the outbox.

These references are useful as architectural input, but they are not a drop-in copy target. They
carry CVIX-specific packages, naming, assumptions (`X-Workspace-Id`, Keycloak-oriented token access,
RLS-oriented context comments, versioned vendor media types, outbox defaults), and some abstractions
are still too narrow for the target auth foundation.

### Affected Areas

- `server/smp/build.gradle.kts` — foundation dependencies exist, but missing auth foundation wiring
  such as JWT resource server and Liquibase integration.
- `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` — current only entrypoint;
  future package scan boundaries and module roots will hang from here.
- `server/smp/src/main/resources/application.yaml` — currently empty of security, datasource,
  Liquibase, JWT, multitenancy, and app settings.
- `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt` — only smoke test; no
  architecture, security, or module tests.
-
`/Users/acosta/Downloads/profiletailors-main/shared/common/src/main/kotlin/com/profiletailors/common/domain/bus/*` —
source of reusable mediator/CQRS contracts and pipeline behavior ideas.
-

`/Users/acosta/Downloads/profiletailors-main/shared/common/src/main/kotlin/com/profiletailors/common/domain/security/WorkspaceAuthorization.kt` —
important seam, but too narrow alone for granular permission authorization.

-

`/Users/acosta/Downloads/profiletailors-main/shared/common/src/main/kotlin/com/profiletailors/common/domain/model/WorkspaceId.kt` —
good example of promoting workspace identity to a first-class value object.

-

`/Users/acosta/Downloads/profiletailors-main/shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/AppAutoConfiguration.kt` —
reference for mediator Spring wiring and shared auto-config style.

-

`/Users/acosta/Downloads/profiletailors-main/shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/ApiController.kt` —
useful controller seam ideas, but currently biased to JWT subject extraction and fixed workspace
header conventions.

-

`/Users/acosta/Downloads/profiletailors-main/shared/spring-boot-common/src/main/kotlin/com/profiletailors/config/WorkspaceContextWebFilter.kt` —
strong reference for tenant/workspace propagation through reactive context.

-

`/Users/acosta/Downloads/profiletailors-main/shared/spring-boot-common/src/main/kotlin/com/profiletailors/controllers/GlobalExceptionHandler.kt` —
mature error-handling pattern to adapt, not copy blindly.

-

`/Users/acosta/Downloads/profiletailors-main/shared/spring-boot-common/src/main/resources/db/changelog/common-migrations/001-create-outbox.yaml` —
reusable example for Liquibase convention and shared foundation migrations.

### Approaches

1. **Adopt-and-adapt shared foundation inside `server/smp`** — Port the reusable concepts from CVIX
   into repo-local packages, renaming and reshaping them for Profile Tailors.
    - Pros: Fastest path to a consistent foundation; matches existing team conventions; reduces
      architecture invention risk; fits current bootstrap maturity.
    - Cons: Requires discipline to avoid dragging in CVIX assumptions; may over-import utilities not
      needed for auth foundation; needs explicit pruning.
    - Effort: Medium

2. **Build a smaller auth-only foundation from scratch** — Create just JWT, security config, auth
   domain, membership model, and minimal supporting abstractions.
    - Pros: Lower initial surface area; fewer borrowed assumptions; simpler first delivery.
    - Cons: High risk of re-discovering solved CQRS/multitenancy/cross-cutting patterns later;
      likely to cause rework when more modules arrive.
    - Effort: Medium/High

3. **Extract shared foundation as internal reusable module(s) first** — Create internal `common` /
   `spring-boot-common` style submodules before building auth.
    - Pros: Cleanest long-term reuse; explicit separation between framework-agnostic and
      Spring-specific concerns.
    - Cons: Higher upfront design and build complexity for a backend that still has almost no
      product code; may be premature if only `server/smp` consumes it initially.
    - Effort: High

### Recommendation

Recommend **Approach 1: adopt-and-adapt shared foundation inside `server/smp`**, with a **clear seam
toward later extraction** if reuse becomes real.

Concretely:

- Reuse the **concepts and contracts**, not the CVIX package tree wholesale.
- Start with two internal foundation slices:
    - **application/common**: mediator, command/query contracts, pipeline behaviors, domain-service
      marker only if truly needed.
    - **infrastructure/common**: Spring wiring, reactive security/context helpers, ProblemDetail
      handling, persistence support, Liquibase conventions.
- Model auth as a first real bounded area on top of that foundation, not mixed into the foundation
  itself.

Recommended package/module seams for this repo:

- `com.profiletailors.smp.foundation.domain` — shared value objects and cross-cutting domain
  contracts that are truly generic (`WorkspaceId`, `UserId`, `Permission`, `RoleKey`, maybe auditing
  primitives).
- `com.profiletailors.smp.foundation.application` — mediator/CQRS contracts, pipeline behaviors,
  cross-cutting application interfaces.
- `com.profiletailors.smp.foundation.infrastructure` — Spring auto-config style classes, security
  principal extraction helpers, exception mapping, reactive context propagation, persistence
  utilities.
- `com.profiletailors.smp.auth.domain` — user, credential identity, workspace membership, role
  assignment, permission composition, auth/session token concepts.
- `com.profiletailors.smp.auth.application` — login/refresh/me/authorize use cases, membership
  resolution, permission evaluation interfaces.
- `com.profiletailors.smp.auth.infrastructure` — JWT encoder/decoder integration, Spring Security
  config, persistence entities/repos, HTTP adapters.
- `com.profiletailors.smp.workspace.domain` — workspace aggregate/value objects if workspace
  lifecycle is expected to grow beyond auth membership concerns.

For authorization seams, do **not** stop at a
`WorkspaceAuthorization.ensureAccess(workspaceId, userId)` interface. That is useful but
insufficient. The foundation should support:

- authenticated principal extraction,
- active workspace resolution,
- membership lookup,
- role-to-permission composition,
- resource/action authorization checks via stable permission identifiers,
- optional controller/use-case level authorization guards without coupling feature modules to Spring
  Security internals.

A good target seam is something like:

- `CurrentPrincipalProvider`
- `WorkspaceContextResolver`
- `MembershipRepository`
- `PermissionEvaluator`
- `AuthorizationService` / `Authorizer`
- permission keys defined close to app areas but consumed through generic authorization contracts

This keeps JWT as only an authentication transport concern while authorization remains
domain/application-driven.

### Risks

- Copying CVIX abstractions too literally may import hidden product assumptions (header naming,
  Keycloak/JWT claim expectations, media type conventions, outbox defaults, package structure).
- Over-design risk: introducing too many generic foundation pieces before the first auth flow can
  slow delivery.
- Under-design risk: implementing JWT login first without membership/permission seams will hard-code
  authorization into Spring Security annotations or token claims prematurely.
- Workspace scoping is still ambiguous: whether active workspace always comes from header, token
  claim, path, or server-side selection is not yet decided.
- Permission model granularity is not yet specified; without naming rules and ownership boundaries,
  permissions can sprawl and couple modules.
- JWT strategy is underspecified: issuer ownership, signing method, refresh token handling, claim
  shape, token revocation posture, and session model remain open.
- Multitenancy storage strategy is undecided: application-layer filtering only vs PostgreSQL
  RLS/context propagation vs hybrid.
- Spring Modulith is present as a dependency, but no module strategy exists yet; using it well
  requires explicit package/module decisions and tests.
- Liquibase is planned but absent; schema ownership, changelog structure, and bootstrap migrations
  need definition before auth entities land.
- Social login later may pressure the domain model if the initial user identity model is too
  username/password-centric.

### Ready for Proposal

Yes — enough information exists to move into proposal, provided the proposal explicitly frames this
as **foundation-first for auth/authz**, not “implement login now”. The proposal should lock scope
around internal architecture, multitenant seams, JWT integration boundaries, package/module layout,
and decisions deferred to spec/design.
