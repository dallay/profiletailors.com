# Design: Backend Feature Entitlements

## Technical Approach

This change makes one workspace-scoped entitlement executable on the existing proving slice without
turning entitlements into a package, billing, or admin subsystem.

The implementation stays inside the current authorization seam:

- persist one authoritative workspace entitlement record in backend storage
- resolve that record through the existing `EntitlementResolver` seam
- enforce one combined rule in `WorkspaceAuthorizationService`: **allow only when the workspace has
  the required entitlement and the principal has the required permission**
- preserve explicit runtime proof by surfacing a distinct denial reason for missing entitlement
- prove the behavior only on `GET /api/authorization/workspace-access/current` in both H2 and
  PostgreSQL integration suites

This directly follows the proposal and the existing main specs:

- `openspec/specs/authorization/spec.md` requires entitlement and permission to remain separate
  concerns
- `openspec/specs/platform/spec.md` requires decisions to come from authoritative state and remain
  cache-safe
- `openspec/specs/governance/spec.md` requires explicit, explainable runtime proof for deny reasons

## Architecture Decisions

### Decision: Persist entitlement state in one narrow workspace-entitlement table

**Choice**: Add one authorization-owned table that stores workspace-scoped feature entitlement state
keyed by `(workspace_id, entitlement_key)` with an explicit enabled flag.

**Alternatives considered**:

- Reuse permissions or direct grants tables for entitlements
- Add a more generic package/plan/subscription model now
- Keep entitlement state non-persisted and configured in code/tests

**Rationale**:
Entitlements answer feature availability, not principal access, so they should not be collapsed into
permissions or direct grants. A single narrow table gives authoritative persisted state with minimal
schema breadth. It also avoids premature package/billing modeling while remaining compatible with
later evolution.

### Decision: Keep the domain entitlement model minimal

**Choice**: Retain the existing domain-level `Entitlement(key, enabled)` shape and add only the
repository/adapter data needed to load it from persistence.

**Alternatives considered**:

- Enrich `Entitlement` now with package ids, source metadata, time windows, quota fields, or audit
  metadata
- Introduce separate aggregate/domain hierarchies for entitlement catalogs and assignments

**Rationale**:
The current change only needs to answer one question: "is workspace X enabled for entitlement Y?"
The existing domain shape is already enough for the authorization seam. Extra fields would create
speculative architecture with no executable value in this slice.

### Decision: Resolve one entitlement key authoritatively through `EntitlementResolver`

**Choice**: Implement an `R2dbcWorkspaceEntitlementResolver` that reads enabled/disabled state for
the current workspace and returns the matching `Entitlement` set through the existing
`EntitlementResolver` interface.

**Alternatives considered**:

- Add a new entitlement-specific service and bypass `EntitlementResolver`
- Hardcode the proving entitlement in `WorkspaceAuthorizationService`
- Resolve entitlement inside the HTTP controller or query handler

**Rationale**:
The current seam already exists for this responsibility. Using it keeps the architecture honest:
application authorization depends on an entitlement port, not on HTTP or persistence details. It
also keeps scope narrow by avoiding a second abstraction layer.

### Decision: Combine entitlement and permission in
`WorkspaceAuthorizationService`, not in the handler

**Choice**: `WorkspaceAuthorizationService` remains the single authorization decision point for the
slice and evaluates entitlement availability before final allow.

**Alternatives considered**:

- Gate in `GetCurrentWorkspaceAccessSummaryHandler`
- Gate in the controller
- Create a second authorization service just for feature-gated queries

**Rationale**:
The service already owns membership, role, direct grant, and deny-by-default evaluation. Adding the
entitlement check there preserves one coherent decision flow and avoids broadening the protected
slice into multiple decision paths.

### Decision: Add a dedicated missing-entitlement reason code

**Choice**: Extend `AuthorizationReasonCode` with a distinct entitlement denial reason, e.g.
`MISSING_ENTITLEMENT`.

**Alternatives considered**:

- Reuse `MISSING_PERMISSION`
- Reuse a generic deny code
- Emit entitlement detail only in log messages or exception text

**Rationale**:
Governance proof requires denials to be attributable to explicit facts. If missing entitlement and
missing permission share the same reason code, runtime proof stops being trustworthy. A dedicated
reason preserves explainability without adding broad audit infrastructure.

### Decision: Tie the proving slice to one explicit entitlement key

**Choice**: `GetCurrentWorkspaceAccessSummaryQuery` declares or references one entitlement key
dedicated to this proving slice, for example `workspace.access.summary` or equivalent repo-local
naming.

**Alternatives considered**:

- Make all workspace reads implicitly entitled
  n- Allow the resolver to infer keys from permission names
- Add support for multiple entitlement keys or composite rules now

**Rationale**:
One explicit key is the smallest executable proof. It keeps feature availability separate from
permission naming and avoids accidental generalization into a full entitlement engine.

## Data Flow

### Runtime decision flow

```text
HTTP request
  -> WorkspaceAccessSummaryController
  -> GetCurrentWorkspaceAccessSummaryHandler
  -> WorkspaceAuthorizationService
       -> PrincipalContextProvider.require()
       -> ResourceContextProvider.require()
       -> WorkspaceMembershipResolver.resolve(...)
       -> WorkspaceMembershipRoleResolver.resolve(...)
       -> DirectGrantResolver.resolve(...)
       -> EntitlementResolver.resolve(resourceContext)
       -> combined decision
  -> AuditHook.onAuthorizationDecision(...)
  -> allow response or deny
```

### Combined authorization rule

```text
1. Resolve principal + active workspace context
2. Require active workspace membership
3. Resolve roles and direct grants for required permission
4. Resolve workspace entitlements for current workspace
5. If required entitlement is missing or disabled -> DENY (MISSING_ENTITLEMENT)
6. Else if direct DENY exists -> DENY (DIRECT_DENY)
7. Else if direct ALLOW exists -> ALLOW (DIRECT_ALLOW)
8. Else if role permissions contain required permission -> ALLOW (ROLE_PERMISSION)
9. Else -> DENY (MISSING_PERMISSION)
```

This preserves the current authorization semantics while adding one narrow prerequisite: feature
availability must already be true before permission can matter.

### Sequence diagram

```text
Client
  -> Controller: GET /api/authorization/workspace-access/current
Controller
  -> Handler: dispatch query
Handler
  -> WorkspaceAuthorizationService: decideDetailed(workspace:access:read)
WorkspaceAuthorizationService
  -> MembershipResolver: resolve active membership
WorkspaceAuthorizationService
  -> RoleResolver: resolve membership roles
WorkspaceAuthorizationService
  -> DirectGrantResolver: resolve direct grants
WorkspaceAuthorizationService
  -> EntitlementResolver: resolve workspace entitlements
EntitlementResolver
  -> workspace_entitlements table: read by workspace_id + entitlement_key
workspace_entitlements table
  -> EntitlementResolver: enabled/disabled row or no row
EntitlementResolver
  -> WorkspaceAuthorizationService: Set<Entitlement>
WorkspaceAuthorizationService
  -> Handler: AuthorizationDecisionResult(reasonCode=...)
Handler
  -> AuditHook: onAuthorizationDecision(fact)
Handler
  -> Client: 200 OK or 403 Forbidden
```

## File Changes

| File                                                                                                                         | Action | Description                                                                                                            |
|------------------------------------------------------------------------------------------------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/resources/db/changelog/authorization/006-create-workspace-entitlements.yaml`                            | Create | Minimal schema for persisted workspace-scoped entitlement state.                                                       |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`                                                        | Modify | Include the new entitlement changelog in the existing Liquibase order.                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/R2dbcWorkspaceEntitlementResolver.kt`        | Create | Persistence-backed resolver implementing the existing `EntitlementResolver` seam.                                      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt`      | Modify | Replace `NoOpEntitlementResolver` bean wiring with the R2DBC resolver bean.                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`               | Modify | Enforce entitlement presence alongside existing permission logic and return distinct denial reasons.                   |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt`                                | Modify | Add a dedicated authorization reason code for missing entitlement.                                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt`       | Modify | Declare the proving entitlement requirement and preserve audit proof for allow/deny paths.                             |
| `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`           | Modify | Add unit coverage for entitlement-available allow, missing-entitlement deny, and permission-vs-entitlement separation. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryHandlerTest.kt` | Modify | Assert audit facts preserve distinct missing-entitlement and missing-permission reason codes.                          |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`             | Modify | Add H2 proving scenarios for entitled allow, non-entitled deny, and unauthorized deny on the existing slice.           |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`     | Modify | Add PostgreSQL proving scenarios for the same allow/deny matrix.                                                       |

## Interfaces / Contracts

### Persisted entitlement table

```sql
workspace_entitlements (
  id varchar(64) primary key,
  workspace_id varchar(64) not null references workspaces(id),
  entitlement_key varchar(128) not null,
  enabled boolean not null,
  created_at timestamp with time zone not null default current_timestamp,
  updated_at timestamp with time zone not null default current_timestamp,
  unique (workspace_id, entitlement_key)
)
```

Notes:

- `enabled` is explicit so the authoritative state can represent both enabled and disabled rows.
- No package, plan, source-system, quota, usage, validity-window, or admin metadata is included.
- The unique constraint keeps one authoritative record per workspace/key pair.

### Resolver contract usage

No new application interface is required. The existing port remains the seam:

```kotlin
interface EntitlementResolver {
    suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement>
}
```

Adapter behavior for this change:

- if `resourceContext` is not `WORKSPACE` or lacks `workspaceId`, return `emptySet()`
- query persisted rows for the workspace
- map rows to `Entitlement(key, enabled)`
- let the service decide whether the required key is present and enabled

### Authorization reason extension

```kotlin
enum class AuthorizationReasonCode {
    ROLE_PERMISSION,
    DIRECT_ALLOW,
    DIRECT_DENY,
    MISSING_MEMBERSHIP,
    MISSING_PERMISSION,
    MISSING_ENTITLEMENT,
    REVOKED_CREDENTIAL,
}
```

### Proving entitlement constant

The existing query/handler should own or reference one explicit entitlement key for the current
slice.

```kotlin
private const val CURRENT_WORKSPACE_ACCESS_ENTITLEMENT = "workspace.access.summary"
```

The exact string can follow repo-local naming conventions, but it MUST be explicit and MUST NOT be
inferred from the permission value.

## Testing Strategy

| Layer                    | What to Test                                                                                                                           | Approach                                                                                                                             |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| Unit                     | `WorkspaceAuthorizationService` denies when entitlement is missing even if permission allow exists                                     | Add focused tests with fixed membership/role/direct-grant resolvers plus fixed entitlement resolver.                                 |
| Unit                     | `WorkspaceAuthorizationService` still distinguishes `DIRECT_DENY`, `DIRECT_ALLOW`, `MISSING_PERMISSION`, and new `MISSING_ENTITLEMENT` | Extend current decision-reason tests rather than creating a second decision engine.                                                  |
| Unit                     | `GetCurrentWorkspaceAccessSummaryHandler` emits audit facts with the exact denial reason code returned by the service                  | Extend current handler audit assertions for missing entitlement vs missing permission.                                               |
| Integration (H2)         | `/api/authorization/workspace-access/current` allows entitled + authorized principal                                                   | Seed workspace entitlement row plus current membership/role data and assert `200 OK` with `ROLE_PERMISSION` or `DIRECT_ALLOW`.       |
| Integration (H2)         | `/api/authorization/workspace-access/current` denies authorized principal when workspace lacks entitlement                             | Seed permission allow path but omit or disable entitlement row; assert `403` and `MISSING_ENTITLEMENT`.                              |
| Integration (H2)         | `/api/authorization/workspace-access/current` denies entitled principal without permission                                             | Seed entitlement row but remove permission allow path; assert `403` and `MISSING_PERMISSION`.                                        |
| Integration (PostgreSQL) | Same allow/deny matrix against real PostgreSQL                                                                                         | Mirror the H2 scenarios in `WorkspaceAccessSummaryEndpointPostgresIntegrationTest` to prove Liquibase, SQL, and R2DBC compatibility. |
|

## Migration / Rollout

No user-facing rollout is required.

Migration plan:

1. Add the new Liquibase changelog for `workspace_entitlements`.
2. Include it in `db.changelog-master.yaml` after the existing authorization tables.
3. Seed entitlement rows only inside tests for this change.
4. Switch Spring wiring from `NoOpEntitlementResolver` to the R2DBC-backed resolver.

Operationally, this is a narrow runtime-behavior change on an internal proving slice. No admin UI,
billing sync, or backfill workflow is introduced now.

## Scope Guardrails / Deferred Work

The following items are explicitly deferred and MUST NOT be pulled into this change:

- package, plan, SKU, bundle, or subscription modeling
- billing/provider integrations
- entitlement assignment CRUD or admin/operator APIs
- multi-context entitlement breadth beyond `WORKSPACE`
- entitlement inheritance, fallback chains, or composite resolution
- quotas, usage metering, rate consumption, or time-window semantics
- additional protected endpoints or new customer-facing feature surfaces
- cache/invalidation implementation beyond preserving the seam for future work

## Open Questions

- [ ] Exact entitlement key string for the proving slice should be chosen and kept repo-local, but
  this does not block the design.
- [ ] Decide whether tests should prove both "row missing" and "row present but enabled = false" as
  missing-entitlement denial inputs, or keep one path for narrowest scope.
