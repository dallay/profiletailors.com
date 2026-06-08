## Exploration: backend-authorization-breadth

### Current State

`server/smp` is no longer seam-only at the phase-1 level. The current proving slice at
`/api/authorization/workspace-access/current` is executable end to end across JWT principal
materialization, active workspace resolution, membership lookup, role-permission composition,
audit-ready allow/deny facts, Liquibase baseline, and both H2 and PostgreSQL-backed integration
proof.

Authorization breadth, however, is still uneven:

- **Executable today**
    - explicit `PermissionKey` format enforcement
    - workspace membership + role composition
    - deterministic deny-by-default
    - direct grant precedence semantics inside `WorkspaceAuthorizationService`
    - explicit deny overriding allow
    - expired direct grants ignored
    - runtime authorization fact emission for the proving slice
- **Still mostly seam-only or test-only**
    - direct grants are not persisted in PostgreSQL/Liquibase and have no R2DBC adapter
    - scope evaluation exists only as `ScopeResolver`; returned scopes are ignored
    - entitlements exist only as `EntitlementResolver`; returned entitlements are ignored
    - there is no feature-gated endpoint or application path that exercises entitlement separation
    - no persisted deny-rule store separate from direct grants
    - no management/admin flows for grants, scopes, or entitlements

The key implementation reality is that `WorkspaceAuthorizationService` already has the narrow
evaluation order needed for breadth work — membership -> role permissions -> direct grants ->
deny/allow outcome — but only roles are backed by real schema and adapters today. Direct grants,
scopes, and entitlements are architecture-preserved seams, not platform-backed capabilities yet.

### Affected Areas

- `openspec/specs/authorization/spec.md` — source of truth already defines direct grants, deny
  precedence, scope reduction, entitlement separation, and deterministic resolution semantics.
- `openspec/changes/archive/2026-05-15-backend-auth-foundation/specs/authorization/spec.md` — shows
  those breadth concepts were intentionally modeled from day one, even though phase one only
  implemented the proving slice.
- `openspec/changes/archive/2026-05-15-backend-auth-foundation/design.md` — documents the intended
  permission-first architecture with grants, deny rules, scopes, policies, and entitlements as
  future-ready seams.
- `openspec/changes/archive/2026-05-15-backend-auth-hardening/design.md` — confirms the last change
  explicitly avoided breadth expansion and kept work inside the existing proving slice.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` —
current breadth pivot point; direct grants are executable here, while scopes and entitlements are
resolved but not enforced.

- `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt` —
  domain already contains `DirectGrant`, `AuthorizationScope`, and `Entitlement`, but only
  `DirectGrant` has live decision impact.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` —
wires only no-op resolvers for grants, scopes, and entitlements.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/R2dbcWorkspaceMembershipRoleResolver.kt` —
example of the existing persistence pattern to mirror for executable grant breadth.

- `server/smp/src/main/resources/db/changelog/authorization/*.yaml` — current schema supports
  permissions, roles, role-permissions, and membership-roles only; there are no tables yet for
  direct grants, scopes, or entitlements.
-

`server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt` —
proves direct allow/direct deny semantics today, but only through in-memory stub resolvers.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/DirectGrantPrecedenceTest.kt` —
proves expired direct grants are ignored and explicit deny overrides role allow, again without
persistence.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`
and `...PostgresIntegrationTest.kt` — current end-to-end surface that could host the smallest real
breadth expansion without inventing a new endpoint.

### Approaches

1. **Persist and execute direct grants inside the existing proving slice** — Add Liquibase + R2DBC
   support for direct grants and make the current workspace-access endpoint honor them from real
   database state.
    - Pros: Smallest coherent breadth expansion; converts an existing platform concept from
      seam/test-only into real persisted behavior; naturally covers explicit deny and direct allow
      precedence; reuses the current endpoint and verification harness.
    - Cons: Does not yet make scopes or entitlements executable; may tempt adding admin CRUD if
      scope is not guarded.
    - Effort: Medium

2. **Persist direct grants plus introduce first-class scope reduction in the same change** — Add
   direct-grant storage and also make scopes actively constrain decisions, likely via a minimal
   resource-context/scope hint rule on the proving slice.
    - Pros: Delivers two breadth axes at once; starts honoring the “scopes only reduce” invariant in
      runtime logic.
    - Cons: Harder to keep coherent because the current endpoint has no natural target-filtering or
      partial-access surface; easy to invent artificial scope semantics just to satisfy the model.
    - Effort: Medium/High

3. **Persist direct grants plus feature entitlements** — Add grant persistence and feature-gating
   infrastructure together, likely by feature-gating the existing workspace-access slice or a new
   capability.
    - Pros: Demonstrates permission vs entitlement separation with real storage.
    - Cons: Feature-gating the current proving slice is conceptually awkward, and adding a new gated
      capability would broaden scope beyond the smallest safe change.
    - Effort: High

4. **Implement all four breadth targets together** — direct grants, explicit deny rules as separate
   persistence, scopes, and entitlements in one wave.
    - Pros: Broadest platform progress.
    - Cons: This is phase-2 sprawl, not the next smallest coherent change. It would require new
      schema sets, new evaluation semantics, likely new endpoints/tests, and more product decisions
      than the current codebase needs right now.
    - Effort: High

### Recommendation

Recommend **Approach 1: persist and execute direct grants inside the existing proving slice**.

This is the smallest change that adds **real authorization breadth** instead of just deeper
hardening:

- It turns a currently stubbed but already-modeled concept into a real platform capability.
- It delivers both requested subthemes of **direct grants persisted and executable** and **explicit
  deny rules** without requiring a separate deny-rule subsystem yet, because current domain
  semantics already express deny as `DirectGrant(effect = DENY)`.
- It can be proven through the existing `/api/authorization/workspace-access/current` endpoint with
  a minimal set of new scenarios:
    - direct allow grants access when roles do not
    - explicit direct deny blocks access even when roles allow
    - expired persisted direct grants do not apply
- It preserves the clean path for later work on scopes and entitlements without forcing artificial
  runtime behavior now.

#### What belongs in this change

- Liquibase tables for persisted workspace-scoped direct grants, including:
    - principal identity binding
    - permission key binding
    - effect (`ALLOW` / `DENY`)
    - resource/workspace context binding
    - optional expiration timestamp
- R2DBC adapter(s) to resolve active direct grants for the current principal and workspace.
- Integration of that adapter into `WorkspaceAuthorizationService` through the existing
  `DirectGrantResolver` seam.
- End-to-end verification on the existing proving slice for:
    - role-only allow still works
    - direct allow without role permission works
    - direct deny overrides role allow
    - expired direct grant is ignored
- Scope guardrails that keep the change anchored to workspace-scoped direct permission evaluation
  only, not grant management UX or generalized policy authoring.

#### What should remain deferred

- **Separate deny-rule model/store** beyond `DirectGrant(effect = DENY)`.
    - Reason: current model already supports explicit deny semantics cleanly enough for the next
      step.
- **Executable scope reduction semantics**.
    - Reason: current proving slice is binary access to one resource, so it has no natural narrowing
      surface. Adding scope behavior now would likely be synthetic.
- **Feature entitlements with runtime enforcement**.
    - Reason: there is no meaningful feature-gated capability in the current backend slice; adding
      one just to exercise entitlements would broaden product scope.
- **Grant administration endpoints / UI / workflows**.
    - Reason: real execution breadth does not require management breadth yet.
- **General ABAC conditions** beyond carrying forward the `conditions` field on direct grants.
    - Reason: spec preserves the seam, but runtime evaluation can stay deterministic and
      RBAC-plus-direct-grant for this change.

### Risks

- Even a “small” direct-grant change can sprawl if it also tries to introduce grant administration
  flows or generalized policy APIs.
- Modeling deny as a separate subsystem now would duplicate semantics already covered by
  `DirectGrant(effect = DENY)` and raise unnecessary design churn.
- Scope semantics are under-constrained by the current proving slice; forcing them now risks fake
  architecture rather than useful runtime capability.
- Entitlements need a real feature-gated capability to be meaningful; without that, the team could
  ship storage and seams that still are not behaviorally proven.
- New authorization tables will need careful uniqueness and indexing rules to avoid ambiguous
  duplicate grants for the same principal/permission/context/effect combination.
- If direct-grant persistence is introduced without tight integration tests on PostgreSQL, the
  change could regress into another seam-only implementation.

### Ready for Proposal

Yes — propose `backend-authorization-breadth` as a **narrow breadth expansion focused on persisted
direct grants with executable allow/deny behavior in the existing workspace-access proving slice**.
Keep scopes, feature entitlements, separate deny-rule subsystems, and management workflows
explicitly deferred so the change adds one real new authorization dimension without collapsing into
phase-2 sprawl.
