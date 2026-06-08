# Design: Backend Scopes Execution

## Technical Approach

Add one new protected backend proving capability for resource preview by `resourceId`, and make
scopes executable only for that capability.

The design keeps the current IAM flow intact and adds the smallest set of changes needed to prove
the scope invariant end to end:

- base permission `workspace:resource:read` is resolved first through the existing
  membership/role/direct-grant path,
- only after a valid base allow path exists does the platform resolve persisted workspace-scoped
  target scope state,
- the scope reduces reachable `targetResourceId` values for one resource type,
- a scope-caused deny gets its own reason code so it remains distinguishable from
  `MISSING_PERMISSION`.

This is intentionally NOT a generic scope engine. It is one capability-specific scope proof using
the `ResourceContext` fields the platform already has.

## Architecture Decisions

### Decision: Persist scope records in one capability-specific workspace scope table

**Choice**: Add a dedicated authorization table for persisted workspace-scoped target reduction
records for the resource-preview proving capability.

**Alternatives considered**:

- Reuse `workspace_direct_grants` with encoded conditions.
- Introduce a generic `authorization_scopes` engine with multiple dimensions, wildcard support, and
  reusable matching.

**Rationale**: `workspace_direct_grants` expresses allow/deny grants, not post-allow reduction.
Encoding scope reduction there would blur the difference between grant semantics and scope
semantics. A generic engine would expand into the exact platform redesign the proposal forbids. A
small dedicated table keeps semantics honest and scope tight.

### Decision: Model exactly one executable scope shape: allowed target resource IDs for
`workspace:resource:read`

**Choice**: Persist only the fields required to evaluate whether a requested `targetResourceId` is
allowed for base permission `workspace:resource:read` in WORKSPACE context.

**Alternatives considered**:

- Support multiple permissions in one model.
- Support wildcard resource types, pattern matching, inheritance, or hierarchies.
- Support operation variants beyond preview-by-id.

**Rationale**: The approved proving capability is already confirmed: resource preview by
`resourceId`, base permission `workspace:resource:read`, scope reduction by allowed target IDs. The
minimal model should map directly to that proof and nothing broader.

### Decision: Use `ResourceContext.targetResourceType` and
`targetResourceId` explicitly, not implicitly

**Choice**: The new proving slice constructs a `ResourceContext` with:

- `type = WORKSPACE`
- `workspaceId = <active workspace>`
- `targetResourceType = "RESOURCE"` (or equally explicit repo-local constant)
- `targetResourceId = <requested resourceId>`

**Alternatives considered**:

- Infer target resource from path variables later in authorization.
- Hide target matching in scope hints or controller-local logic.

**Rationale**: The platform spec requires explicit context. Using the target fields directly makes
the proving slice deterministic and auditable, and it prevents scope matching from becoming
undocumented side logic.

### Decision: Evaluate base permission first, then scope reduction

**Choice**: `WorkspaceAuthorizationService` continues to resolve membership, roles, direct grants,
and entitlements first. Only if the result is otherwise `ALLOW` does it evaluate applicable scope
records for the current target-aware capability.

**Alternatives considered**:

- Evaluate scopes before base permission.
- Blend permission and scope into one opaque matching step.
- Let scope records create allow paths when permission is missing.

**Rationale**: The spec requires scopes to reduce but never manufacture access. The evaluation order
itself is the proof of that rule.

### Decision: Add one distinct authorization reason for scope-caused denial

**Choice**: Add a dedicated reason code such as `SCOPE_REDUCED_TARGET` for deny outcomes caused by
scope mismatch after base permission succeeds.

**Alternatives considered**:

- Reuse `MISSING_PERMISSION`.
- Reuse `DIRECT_DENY`.

**Rationale**: Governance requires that scope-caused deny remain distinguishable from
missing-permission deny. Reusing an existing reason would lose that explainability.

## Data Flow

### End-to-end request flow

```text
Client request
  └─ GET /api/authorization/resources/{resourceId}/preview
       Header: X-Workspace-Id = workspace-1
                │
                ▼
Authentication (existing USER / SERVICE_ACCOUNT / API_KEY paths)
                │
                ▼
Target-aware controller / query handler
                │
                └─ establish ResourceContext:
                     WORKSPACE + workspaceId + targetResourceType + targetResourceId
                                │
                                ▼
WorkspaceAuthorizationService.decideDetailed(
  requiredPermission = workspace:resource:read
)
    │
    ├─ resolve active membership
    ├─ resolve roles
    ├─ resolve direct grants
    ├─ resolve entitlements if needed
    ├─ determine base decision
    │     └─ deny immediately if no allow path
    └─ if base decision == ALLOW:
          resolve workspace target scopes
             ├─ target allowed -> ALLOW
             └─ target not allowed -> DENY (scope reason)
                │
                ▼
AuditHook receives allow/deny fact with explicit reason
                │
                ▼
Controller returns preview response or 403
```

### Scope evaluation flow

```text
Base permission check passes
        │
        ▼
ScopeResolver.resolve(principalContext, resourceContext)
        │
        ▼
Filter for applicable scopes matching:
- permission = workspace:resource:read
- resourceContextType = WORKSPACE
- targetResourceType = RESOURCE
        │
        ├─ no applicable scope rows → keep ALLOW (no reduction applies)
        │
        └─ applicable scope rows exist
              │
              ├─ allowedTargetResourceIds contains targetResourceId → keep ALLOW
              └─ does not contain → DENY with SCOPE_REDUCED_TARGET
```

## File Changes

| File                                                                                                                    | Action                                        | Description                                                                                                             |
|-------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/archive/2026-05-18-backend-scopes-execution/design.md`                                                | Create                                        | Technical design artifact for this change.                                                                              |
| `server/smp/src/main/resources/db/changelog/authorization/007-create-workspace-target-scopes.yaml`                      | Create                                        | Minimal persisted workspace-scoped target-reduction records for the proving capability.                                 |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`                                                   | Modify                                        | Include the new scope changelog.                                                                                        |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt`                         | Modify                                        | Enrich `AuthorizationScope` from placeholder key-only shape to a minimal executable target-aware scope model.           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`          | Modify                                        | Apply scope reduction only after a valid base allow path exists and surface a scope-specific deny reason.               |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` | Modify                                        | Replace `NoOpScopeResolver` with a persistence-backed resolver for this slice.                                          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/R2dbcWorkspaceTargetScopeResolver.kt`   | Create                                        | Resolve persisted workspace target scopes for the new proving capability.                                               |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/domain/ResourceContext.kt`                                  | Keep shape, tighten usage                     | Existing target fields are sufficient; the new slice uses them explicitly rather than changing the model broadly.       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt`                           | Modify                                        | Add distinct reason code for scope-caused deny.                                                                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetResourcePreviewQuery.kt`                | Create                                        | New target-aware proving request/handler contract.                                                                      |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/http/ResourcePreviewController.kt`      | Create                                        | Expose the new proving capability over HTTP.                                                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/...`                                         | Modify lightly if needed                      | Set explicit target resource context for the new request path only.                                                     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`      | Modify                                        | Add unit proof for base permission first, matching target allow, non-matching target deny, and missing-permission deny. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/ResourcePreviewEndpointIntegrationTest.kt`               | Create or extend existing integration package | H2 proving coverage for the target-aware capability.                                                                    |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/ResourcePreviewEndpointPostgresIntegrationTest.kt`       | Create or extend existing integration package | PostgreSQL proving coverage for the same matrix.                                                                        |

## Interfaces / Contracts

### Minimal persisted schema

```sql
workspace_target_scopes (
  id varchar(64) primary key,
  workspace_id varchar(64) not null references workspaces(id),
  principal_id varchar(64) not null references principals(id),
  principal_type varchar(32) not null,
  permission_id varchar(64) not null references permissions(id),
  target_resource_type varchar(64) not null,
  allowed_target_ids_json text not null,
  created_at timestamp with time zone not null default CURRENT_TIMESTAMP,
  unique (workspace_id, principal_id, principal_type, permission_id, target_resource_type)
)
```

### Schema notes

- `workspace_id`: binds scope to the active workspace context.
- `principal_id` + `principal_type`: binds scope to one persisted principal.
- `permission_id`: binds the scope to the explicit base permission `workspace:resource:read`.
- `target_resource_type`: keeps the row capability-specific and explicit.
- `allowed_target_ids_json`: a small persisted set representation for the one approved narrowing
  dimension.

This is intentionally narrow. It does not add:

- inheritance,
- wildcard patterns,
- nested conditions,
- reusable dimensions,
- multi-capability expression trees,
- admin metadata.

### Minimal domain model

```kotlin
data class AuthorizationScope(
    val permission: PermissionKey,
    val resourceContextType: ResourceContextType,
    val targetResourceType: String,
    val allowedTargetResourceIds: Set<String>,
)
```

This replaces the current placeholder `key: String` shape only as much as needed to execute the
proving rule.

### Scope resolver contract

```kotlin
interface ScopeResolver {
    suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<AuthorizationScope>
}
```

Resolver behavior for this change:

- only returns rows for `resourceContext.type == WORKSPACE`
- only returns rows bound to the current principal
- only returns rows for `targetResourceType` matching the explicit proving capability
- only returns rows for permission `workspace:resource:read`

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
    SCOPE_REDUCED_TARGET,
}
```

### Proving capability contract

```kotlin
GET /api/authorization/resources/{resourceId}/preview
Header: X-Workspace-Id: <workspace-id>
```

Response can stay intentionally synthetic and minimal, for example:

```json
{
  "workspaceId": "workspace-1",
  "resourceId": "resource-1",
  "principalId": "principal-1",
  "previewAllowed": true
}
```

The payload exists only to prove target-aware authorization execution. It must not evolve into
product-grade resource modeling in this change.

## Detailed Technical Design

### 1. Minimal schema additions for persisted workspace-scoped scope records

The design uses one new authorization table rather than a generic scope engine. Each row represents:

- one workspace,
- one principal,
- one base permission,
- one target resource type,
- one reduced set of allowed target IDs.

That is enough to answer the only scope question this change cares about:

> “Given that the principal is otherwise allowed to read workspace resources, is this specific
`resourceId` inside the allowed reduced set?”

No broader persistence breadth is required.

### 2. Modeling one target-aware capability without building a generic scope engine

The proving capability is deliberately small:

- one HTTP endpoint,
- one base permission,
- one target resource type,
- one target narrowing rule.

The domain model does not attempt to solve future scope problems. It only describes the exact
current rule.

This means:

- no generic scope DSL,
- no universal matcher,
- no wildcard expansion,
- no cross-context evaluation,
- no policy composition language.

If a field or abstraction does not serve `workspace:resource:read` + `resourceId` reduction
directly, it should be deferred.

### 3. Explicit use of `ResourceContext` target fields

The new query handler should establish `ResourceContext` explicitly before authorization runs.

Expected populated context:

```kotlin
ResourceContext(
  type = ResourceContextType.WORKSPACE,
  workspaceId = resolvedWorkspaceId,
  targetResourceType = "RESOURCE",
  targetResourceId = requestedResourceId,
)
```

This matters because scope reduction is evaluated against `targetResourceId`. If the target value
stays outside `ResourceContext`, then scope execution becomes hidden controller logic instead of a
platform authorization concern.

The existing `WorkspaceContextWebFilter` already sets the workspace portion. The new proving path
should complement that with explicit target context from the route parameter or request object.

### 4. Base permission first, then scope reduction

`WorkspaceAuthorizationService` is already the pivot point, so the design keeps the logic there.

Updated decision order:

1. Require principal context.
2. Require resource context.
3. Resolve active workspace membership.
4. Resolve roles and direct grants.
5. Resolve entitlements if applicable.
6. Compute the base authorization decision exactly as today.
7. If base decision is `DENY`, return immediately.
8. If base decision is `ALLOW`, resolve applicable scopes.
9. If no applicable scope rows exist for this capability, the base ALLOW decision stands unchanged —
   absence of scope rows means "no reduction applies." This is the chosen rule, implemented in
   `evaluateScopeReduction` via `applicableScopes.isEmpty() || ...`. Scopes are an opt-in
   restriction: they only narrow access when scope rows have been explicitly persisted for the
   matching permission, resource context type, and target resource type.
10. If the requested `targetResourceId` is not in the allowed set, return `DENY` with
    `SCOPE_REDUCED_TARGET`.
11. Otherwise, return the original base allow reason.

This preserves the core invariant: scopes never create access, only reduce an already valid one.

### 5. Keeping scope-caused deny distinguishable

Today the system can already distinguish missing membership, missing permission, direct deny,
missing entitlement, and revoked credential. Scope denial needs the same treatment.

Behavior split:

- Base permission missing → `DENY` + `MISSING_PERMISSION`
- Base permission present but target outside allowed scope → `DENY` + `SCOPE_REDUCED_TARGET`

The audit hook already captures `AuthorizationDecisionAuditFact`, so no new governance subsystem is
required. The only needed change is exposing the new reason code through the same runtime audit
seam.

### 6. Exposing the proving capability

The current proving endpoint `/api/authorization/workspace-access/current` remains untouched.

A new endpoint exposes the scope proof, for example:

```text
GET /api/authorization/resources/{resourceId}/preview
```

Why this shape works:

- it is explicitly target-aware,
- it carries the `resourceId` naturally,
- it is narrow and binary enough for proof,
- it does not force a real product resource domain.

The handler should:

- read the path `resourceId`,
- require `X-Workspace-Id`,
- construct explicit target-aware `ResourceContext`,
- request authorization for `workspace:resource:read`,
- return a minimal synthetic preview payload.

### 7. H2 and PostgreSQL proving strategy

The repo already has a strong integration pattern for H2 and PostgreSQL. This change should reuse
that pattern instead of inventing a new harness.

Required end-to-end matrix in both H2 and PostgreSQL:

#### Allow: base permission + matching scope

Seed:

- authenticated principal,
- active workspace membership,
- role or direct grant yielding `workspace:resource:read`,
- persisted scope row whose allowed target set contains `resource-1`.

Request:

- `GET /api/authorization/resources/resource-1/preview`

Result:

- `200 OK`
- audit fact showing `ALLOW` with base allow reason (`ROLE_PERMISSION` or `DIRECT_ALLOW`)

#### Scope deny: base permission + non-matching target

Seed:

- same valid base permission,
- persisted scope row whose allowed target set does not contain `resource-9`.

Request:

- `GET /api/authorization/resources/resource-9/preview`

Result:

- `403 Forbidden`
- audit fact showing `DENY` with `SCOPE_REDUCED_TARGET`

#### Missing permission deny: no base allow path + scope exists

Seed:

- authenticated principal,
- active membership,
- no `workspace:resource:read` permission,
- persisted scope row does exist.

Request:

- `GET /api/authorization/resources/resource-1/preview`

Result:

- `403 Forbidden`
- audit fact showing `DENY` with `MISSING_PERMISSION`

This last case is critical because it proves scope state does not manufacture access.

#### Allow without scope rows: base permission alone is sufficient

Seed:

- authenticated principal,
- active workspace membership,
- role yielding `workspace:resource:read`,
- **no** persisted scope rows for this principal/capability.

Request:

- `GET /api/authorization/resources/resource-1/preview`

Result:

- `200 OK`
- audit fact showing `ALLOW` with `ROLE_PERMISSION`

This case proves that scope rows are opt-in: a principal with the base permission but no scope
restrictions has unrestricted access within the proving capability.

## Testing Strategy

| Layer       | What to Test                                                         | Approach                                                                                                                               |
|-------------|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Unit        | Base allow path resolves before scope reduction                      | Extend `WorkspaceAuthorizationServiceTest` with fixed scope resolver inputs and assert evaluation order outcomes via decision reasons. |
| Unit        | Matching target remains allowed                                      | Add scope-aware decision test where permission exists and requested target is in `allowedTargetResourceIds`.                           |
| Unit        | Non-matching target is denied by scope                               | Add scope-aware decision test asserting `SCOPE_REDUCED_TARGET`.                                                                        |
| Unit        | Missing permission remains missing permission even when scope exists | Add test proving scope rows do not create access.                                                                                      |
| Integration | H2 end-to-end target-aware allow/deny matrix                         | Add or create an H2 integration suite for the new preview endpoint with seeded permissions and persisted scope rows.                   |
| Integration | PostgreSQL end-to-end target-aware allow/deny matrix                 | Mirror the same scenarios in PostgreSQL-backed tests.                                                                                  |
| E2E         | Not required beyond backend integration                              | Existing backend integration style is sufficient for this proving slice.                                                               |

## Migration / Rollout

Add one Liquibase changelog for the new scope table and include it from `db.changelog-master.yaml`.

No data migration is required because executable scope persistence does not exist yet.

Rollout steps:

1. Add new scope schema.
2. Wire persistence-backed scope resolution.
3. Add the new proving endpoint and explicit target context construction.
4. Prove the matrix in H2 and PostgreSQL.

Rollback stays narrow:

1. Remove the new proving endpoint.
2. Restore `NoOpScopeResolver` wiring.
3. Leave the new table inert or revert the migration in pre-promotion environments.

## Open Questions

- [x] ~~Should absence of a persisted scope row for this capability mean "no reduction applies"...~~
  **Resolved:** absence of applicable scope rows means "no reduction applies" — the base ALLOW
  stands. This is enforced in `evaluateScopeReduction` (line 196 of
  `WorkspaceAuthorizationService.kt`) via `applicableScopes.isEmpty() || ...` and proved by
  integration test `allows resource preview when base permission exists and no scope row`.
- [ ] What repo-local constant should represent `targetResourceType` for this proving capability so
  it stays explicit without inventing a broader resource taxonomy too early?
- [ ] Should the target-aware preview endpoint live under `authorization` infrastructure for proof
  discipline, or under a small synthetic `resources` surface that still delegates into the same
  authorization service?
