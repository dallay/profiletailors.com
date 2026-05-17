# Design: Backend Auth Hardening

## Technical Approach

This change stays inside the already-existing protected proving slice at
`/api/authorization/workspace-access/current`.

The implementation should harden that slice in two small ways only:

1. **Surface audit-ready authorization facts** for the allow and deny outcomes already produced by
   the workspace-access flow.
2. **Run the same proving slice against real PostgreSQL** for one allow case and one deny case.

The smallest practical design is:

- keep the current request-level audit seam intact,
- add **one additional structured authorization-fact hook** to the existing `AuditHook`,
- evolve the authorization service just enough to return a **decision plus reason metadata** for
  this slice,
- emit the fact from the existing workspace-access query handler before returning success or
  throwing denial,
- verify emitted facts with a test-time capturing hook,
- add a dedicated PostgreSQL-backed integration test for this endpoint only.

This avoids a governance redesign, avoids new persistence, avoids a global event bus, and avoids
broad test-infrastructure churn.

## Scope Guardrails

To keep this change narrow, the implementation MUST stay within these boundaries:

- Only the existing `GetCurrentWorkspaceAccessSummaryQuery` flow is in scope.
- Only **allow** and **deny** facts for `workspace:access:read` are in scope.
- No audit persistence tables, outbox, reporting, or compliance workflow SHALL be introduced.
- No Spring Modulith or architecture dependency tests SHALL be added in this change.
- No full-suite PostgreSQL migration SHALL be attempted.
- Any refactor that does not directly support audit fact emission or PostgreSQL proving for this
  endpoint belongs in a later change.

## Architecture Decisions

### Decision: Extend the existing audit seam with one structured authorization-fact callback

**Choice**: Keep `AuditHook.onRequestHandled(...)` as-is and add one new method for structured
authorization facts, for example `onAuthorizationDecision(fact: AuthorizationDecisionAuditFact)`.

**Alternatives considered**:

- Replace the current hook completely with a new governance event API.
- Introduce a full event bus or governance module abstraction.
- Encode allow/deny facts into the existing `RequestOutcome.SUCCESS|FAILURE` enum.

**Rationale**: The current hook is too coarse to explain why access was allowed or denied. Replacing
it would broaden blast radius for little gain. Adding one structured callback is the smallest seam
evolution that makes allow/deny audit proof possible without turning this slice into a governance
redesign.

### Decision: Emit audit facts from the workspace-access handler, using detailed authorization results from the authorization service

**Choice**: Evolve `WorkspaceAuthorizationDecider` to expose a detailed decision path for this
slice, and have `GetCurrentWorkspaceAccessSummaryHandler` emit the audit fact after decision
evaluation and before returning or throwing.

**Alternatives considered**:

- Emit from `SpringMediator` around all requests.
- Emit from the HTTP exception handler.
- Persist audit records directly inside `WorkspaceAuthorizationService`.

**Rationale**: `SpringMediator` only knows request type and success/failure, not permission key,
workspace id, principal id, or denial basis. The HTTP exception handler sees denial too late and
does not naturally see allow outcomes. The handler already coordinates the protected proving slice
and has access to the request-local principal/resource context plus the permission being checked.
That makes it the narrowest and cleanest emission point.

### Decision: Add a detailed authorization result instead of replacing the existing
`decide(...)` contract wholesale

**Choice**: Add a detailed result path, such as `decideDetailed(requiredPermission)` returning a
structured result, while preserving the current simple `decide(...)` contract as a compatibility
shim or delegating wrapper.

**Alternatives considered**:

- Change `decide(...)` to return a new type everywhere.
- Keep `decide(...)` returning only `ALLOW|DENY` and derive reasons outside the service.

**Rationale**: Replacing the contract everywhere is unnecessary churn. Deriving reasons outside the
service would duplicate decision logic and weaken correctness. A companion detailed result keeps the
seam small and contains reason logic where the decision is actually made.

### Decision: Use a dedicated PostgreSQL integration test with container-managed database wiring

**Choice**: Add one PostgreSQL-backed integration test class for
`/api/authorization/workspace-access/current`, using an ephemeral PostgreSQL test runtime with
dynamic Spring property wiring.

**Alternatives considered**:

- Reuse the generic `server/smp/compose.yaml` manually.
- Convert the existing H2-based integration test class fully to PostgreSQL.
- Migrate the whole backend test suite to PostgreSQL now.

**Rationale**: The compose file is generic and not aligned to current app defaults, so making it the
automated test dependency would create avoidable friction. Replacing the current H2 suite would slow
feedback and enlarge scope. A dedicated PostgreSQL test class proves Liquibase + R2DBC + SQL
behavior on the target engine while leaving the fast H2 slice intact.

### Decision: Keep structural boundary enforcement deferred

**Choice**: Do not add Spring Modulith / architecture dependency tests in this change.

**Alternatives considered**:

- Add `ApplicationModules` verification now.
- Add package dependency tests alongside PostgreSQL coverage.

**Rationale**: Those tests are valuable, but they do not directly close the audit-runtime warning or
the target-engine gap. Adding them here would dilute the proving-slice focus and increase review
surface. They should be handled in a separate structural hardening change.

## Data Flow

### Authorization fact flow for the proving slice

```text
HTTP GET /api/authorization/workspace-access/current
    -> WorkspaceAccessSummaryController
    -> Mediator.dispatch(GetCurrentWorkspaceAccessSummaryQuery)
    -> GetCurrentWorkspaceAccessSummaryHandler
        -> PrincipalContextProvider.require()
        -> ResourceContextProvider.require()
        -> WorkspaceAuthorizationDecider.decideDetailed(permission)
            -> membership lookup
            -> role permission resolution
            -> direct grant checks
            -> result: ALLOW or DENY + reason facts
        -> AuditHook.onAuthorizationDecision(fact)
        -> if ALLOW:
               resolve active membership for summary payload
               return WorkspaceAccessSummary
           else:
               throw AuthorizationDeniedException
    -> HTTP 200 or 403
```

### Sequence diagram — allow path

```text
Client
  -> WorkspaceAccessSummaryController: GET /current
WorkspaceAccessSummaryController
  -> SpringMediator: dispatch(GetCurrentWorkspaceAccessSummaryQuery)
SpringMediator
  -> GetCurrentWorkspaceAccessSummaryHandler: handle(query)
GetCurrentWorkspaceAccessSummaryHandler
  -> WorkspaceAuthorizationService: decideDetailed(workspace:access:read)
WorkspaceAuthorizationService
  --> GetCurrentWorkspaceAccessSummaryHandler: ALLOW + reason facts
GetCurrentWorkspaceAccessSummaryHandler
  -> AuditHook: onAuthorizationDecision(ALLOW fact)
GetCurrentWorkspaceAccessSummaryHandler
  -> WorkspaceMembershipResolver / RoleResolver: build summary
GetCurrentWorkspaceAccessSummaryHandler
  --> SpringMediator: WorkspaceAccessSummary
SpringMediator
  --> WorkspaceAccessSummaryController: WorkspaceAccessSummary
WorkspaceAccessSummaryController
  --> Client: 200 OK
```

### Sequence diagram — deny path

```text
Client
  -> WorkspaceAccessSummaryController: GET /current
WorkspaceAccessSummaryController
  -> SpringMediator: dispatch(GetCurrentWorkspaceAccessSummaryQuery)
SpringMediator
  -> GetCurrentWorkspaceAccessSummaryHandler: handle(query)
GetCurrentWorkspaceAccessSummaryHandler
  -> WorkspaceAuthorizationService: decideDetailed(workspace:access:read)
WorkspaceAuthorizationService
  --> GetCurrentWorkspaceAccessSummaryHandler: DENY + reason facts
GetCurrentWorkspaceAccessSummaryHandler
  -> AuditHook: onAuthorizationDecision(DENY fact)
GetCurrentWorkspaceAccessSummaryHandler
  -> throws AuthorizationDeniedException
AuthorizationProblemDetailsHandler
  --> Client: 403 Forbidden
```

## File Changes

| File                                                                                                                     | Action             | Description                                                                                                                             |
|--------------------------------------------------------------------------------------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt`                            | Modify             | Extend `AuditHook` with one structured authorization-fact callback and add the minimal fact/result types needed by this slice.          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/PlatformBootstrapConfiguration.kt`            | Modify             | Keep the default no-op hook, but implement the new method so production wiring remains inert unless explicitly overridden.              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`           | Modify             | Add detailed decision output for the proving slice, including explicit allow/deny basis without changing broader authorization breadth. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt`   | Modify             | Call the detailed decision path, emit audit facts, and preserve existing response/exception behavior.                                   |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`         | Modify             | Keep H2-based fast slice coverage and add assertions for captured allow/deny audit facts.                                               |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` | Create             | Add one real-PostgreSQL proving class for the same endpoint with one authorized scenario and one denied scenario.                       |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/CapturingAuditHook.kt`                            | Create             | Tiny test-only in-memory hook to collect emitted authorization facts for assertions.                                                    |
| `server/smp/build.gradle.kts`                                                                                            | Modify             | Add only the minimal PostgreSQL test dependency support required for the dedicated container-backed verification path.                  |
| `server/smp/compose.yaml`                                                                                                | No change expected | Keep deferred unless a later workflow explicitly wants local manual parity; automated proving should not depend on this file.           |
| `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt`                  | No change expected | Retain as static changelog composition coverage; PostgreSQL execution proof lives in the new endpoint integration test.                 |

## Interfaces / Contracts

The exact naming can follow project conventions, but the design intent is:

```kotlin
interface AuditHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)
    suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact)
}

data class AuthorizationDecisionAuditFact(
    val requestName: String,
    val permission: String,
    val principalId: String,
    val workspaceId: String?,
    val decision: AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: List<String> = emptyList(),
)

enum class AuthorizationReasonCode {
    ROLE_PERMISSION,
    DIRECT_ALLOW,
    DIRECT_DENY,
    MISSING_MEMBERSHIP,
    MISSING_PERMISSION,
}
```

And the authorization service should expose a narrow detailed result:

```kotlin
interface WorkspaceAuthorizationDecider {
    suspend fun decide(requiredPermission: PermissionKey): AuthorizationDecision
    suspend fun decideDetailed(requiredPermission: PermissionKey): AuthorizationDecisionResult
}

data class AuthorizationDecisionResult(
    val decision: AuthorizationDecision,
    val reasonCode: AuthorizationReasonCode,
    val roleKeys: Set<String> = emptySet(),
)
```

### Contract notes

- `requestName` should remain tied to the existing request/query identity so emitted facts are
  traceable to the proving slice.
- `permission` must be the explicit permission key already checked by the handler:
  `workspace:access:read`.
- `principalId` and `workspaceId` come from existing request-local context providers; no new context
  mechanism is needed.
- `reasonCode` must stay intentionally small and only cover the allow/deny reasons that the current
  proving slice can already know reliably.
- The design does **not** require a generic audit envelope, correlation IDs, persistence schema, or
  multi-resource taxonomy.

## Testing Strategy

| Layer       | What to Test                                   | Approach                                                                                                                                                    |
|-------------|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Unit        | Detailed authorization result mapping          | Extend existing authorization service tests to assert `decision + reasonCode` for allow and deny paths.                                                     |
| Integration | Audit fact emission for H2-based proving slice | Override `AuditHook` with a capturing implementation in the existing endpoint integration test and assert one emitted allow fact and one emitted deny fact. |
| Integration | PostgreSQL-backed proving slice                | Add a dedicated `@SpringBootTest` + container-backed PostgreSQL test class that runs the same endpoint flow for one 200 path and one 403 path.              |
| E2E         | None                                           | Not needed for this hardening slice.                                                                                                                        |

### PostgreSQL-backed verification plan

The PostgreSQL proving class should be intentionally small:

- use an ephemeral PostgreSQL runtime managed by the test,
- wire `spring.r2dbc.url`, `spring.r2dbc.username`, `spring.r2dbc.password`, and Liquibase JDBC
  properties dynamically,
- keep the same JWT stub strategy already used by the H2 integration test,
- reuse the same endpoint `/api/authorization/workspace-access/current`,
- cover exactly:
    - **authorized member returns 200**, and
    - **member without required permission returns 403**.

This proves:

- Liquibase changelog execution on real PostgreSQL,
- R2DBC queries and mappings on the target engine,
- SQL assumptions hidden by H2 PostgreSQL compatibility mode,
- audit fact emission still works on the real database path.

It deliberately does **not** prove every backend flow on PostgreSQL.

## Migration / Rollout

No migration required.

This is a local seam-and-test hardening change only. Existing database changelogs remain
authoritative. No production feature flag is needed because no new external behavior is being
introduced; only diagnosability and test confidence improve.

## Deferred Work

The following explicitly stay out of this change:

- Spring Modulith / architecture dependency tests.
- Audit persistence, audit reporting, and governance workflow expansion.
- Broader `governance/**` domain build-out.
- Full backend integration-suite migration to PostgreSQL.
- Generalized cross-request audit event infrastructure.
- Additional protected endpoints or phase-2 authorization breadth.
- Compose-file normalization for all local workflows.

## Open Questions

- [ ] CI/runtime environment must support Docker or an equivalent ephemeral PostgreSQL runtime for
  the dedicated proving test.
- [ ] If future slices need richer denial explanations than `reasonCode + roleKeys`, a follow-up
  design should introduce a broader governance model rather than expanding this slice ad hoc.
