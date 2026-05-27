# Proposal: Backend Scopes Execution

## Intent

Make scope reduction executable with one small, real backend slice instead of adding another
placeholder seam. This change should prove the core authorization invariant end to end: a principal
first needs a valid base permission for a protected capability, and only then may a persisted
workspace-scoped scope reduce which target resource IDs are allowed for that capability.

The approved direction is a target-aware proving capability built around a
resource-preview-by-resourceId style operation. The goal is to demonstrate real allow/deny semantics
on explicit target resources without expanding into generic scope engines, broad policy redesign, or
admin/product workflows.

## Scope

### In Scope

- Add exactly one new protected target-aware backend proving capability using a
  resource-preview-by-resourceId style operation with explicit `targetResourceId` context.
- Define exactly one explicit base permission for that capability in the existing
  `<domain>:<resource>:<action>` format.
- Persist one minimal workspace-scoped scope model that binds workspace, principal, base permission,
  target resource type, and an allowed target resource-id set or equivalent narrowing representation
  for this one capability.
- Replace the current no-op scope path with executable scope resolution for this slice only.
- Enforce the runtime rule that scopes may only reduce an otherwise valid allow path and may never
  manufacture access.
- Surface a distinguishable runtime deny reason when a request fails due to scope reduction rather
  than missing permission.
- Prove allow/deny behavior with H2 and PostgreSQL integration coverage for:
    - base permission + matching scope -> allow
    - base permission + non-matching target -> deny by scope reduction
    - missing base permission + any scope -> deny

### Out of Scope

- Generic scope engines, scope catalogs, scope templates, or reusable multi-shape evaluators.
- Wildcards, inheritance, hierarchical scope matching, or set-composition semantics beyond the one
  chosen narrowing rule.
- Multi-context scopes across GLOBAL, USER, or SYSTEM.
- Scope CRUD, admin/operator workflows, assignment APIs, or UI.
- Quotas, billing, packages, entitlements combinations, usage ceilings, or commercial policy
  semantics.
- Broad RBAC/ABAC or policy-platform redesign.
- Retrofitting scopes across existing protected endpoints, including
  `/api/authorization/workspace-access/current`.
- More than one target-aware proving capability in this change.

## Approach

Introduce one tightly bounded protected operation whose request includes explicit workspace context
and explicit target resource context, so scope reduction has a natural execution surface. The
capability should follow a resource-preview-by-resourceId pattern because it is target-aware, binary
enough to verify clearly, and narrow enough to avoid redesigning the platform.

The authorization flow should remain disciplined:

1. authenticate principal
2. resolve active workspace
3. resolve base permission through current membership/role/direct-grant rules
4. if no allow path exists, deny immediately
5. if an allow path exists, resolve applicable workspace-scoped scope data for this capability
6. allow only if the requested target resource ID is inside the scope-reduced allowed set; otherwise
   deny with a scope-specific reason

This keeps the model honest: the permission grants capability access in principle, while the scope
narrows reachable targets in practice.

The persisted scope shape should be minimal and capability-specific, not generic. It only needs
enough structure to prove one narrowing rule for one target resource type in WORKSPACE context. The
change should enrich the current `AuthorizationScope` model only as far as necessary to execute that
rule and keep explainability intact.

## Affected Areas

| Area                                                                                                                    | Impact   | Description                                                                                                                                              |
|-------------------------------------------------------------------------------------------------------------------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/specs/authorization/spec.md`                                                                                  | Modified | Add the first executable scope proof boundary, one explicit target-aware capability, and the rule that scopes only reduce an otherwise valid allow path. |
| `openspec/specs/platform/spec.md`                                                                                       | Modified | Clarify explicit target resource context usage for the proving capability and preserve deterministic evaluation against authoritative state.             |
| `openspec/specs/governance/spec.md`                                                                                     | Modified | Require runtime explainability for scope-caused deny versus missing-permission deny on the new proving slice.                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`          | Modified | Make scope resolution executable only after base authorization succeeds and enforce target-aware reduction semantics.                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt`                         | Modified | Enrich the minimal scope model enough to express one persisted workspace-scoped target-resource reduction rule.                                          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` | Modified | Replace `NoOpScopeResolver` wiring with a persistence-backed resolver for this slice.                                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/domain/ResourceContext.kt`                                  | Modified | Use or tighten explicit `targetResourceType` and `targetResourceId` semantics for the new capability.                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt`                           | Modified | Add or expose a distinguishable authorization reason for scope-reduced denial if not already present.                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/**`                                                                  | Modified | Add one new query/handler/controller path for the target-aware resource-preview proving capability within existing module/package boundaries.            |
| `server/smp/src/main/resources/db/changelog/authorization/*.yaml`                                                       | Modified | Add minimal persistence for workspace-scoped executable scope records for the one target-aware capability.                                               |
| `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`      | Modified | Add unit proof that scopes reduce but never create access.                                                                                               |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/*IntegrationTest.kt`                                     | Modified | Add H2-backed end-to-end proof for target-aware allow/deny behavior.                                                                                     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/*PostgresIntegrationTest.kt`                             | Modified | Add PostgreSQL-backed proof for the same allow/deny matrix against real database behavior.                                                               |

## Risks

| Risk                                                                                 | Likelihood | Mitigation                                                                                                                 |
|--------------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------|
| The new capability drifts into product redesign instead of remaining a proving slice | Medium     | Keep the operation narrowly framed as resource preview by resource ID, with no broader domain workflow or listing surface. |
| Scope persistence/modeling becomes a generic engine too early                        | Medium     | Limit the model to workspace scope + one base permission + one target resource type + one narrowing representation.        |
| Scope denial becomes indistinguishable from missing permission denial                | Medium     | Require an explicit runtime reason and end-to-end deny proof for both cases.                                               |
| Evaluation order is implemented incorrectly and scope appears to grant access        | Low/Medium | Make the proposal/spec require base allow-path resolution before any scope reduction logic runs.                           |
| H2 and PostgreSQL behave differently for persisted scope matching assumptions        | Low/Medium | Require the same allow/deny matrix on both H2 and PostgreSQL integration suites.                                           |

## Rollback Plan

If the new slice introduces incorrect denials, ambiguous runtime reasons, or unstable persistence
behavior, revert the new target-aware proving capability and restore scope handling to the current
non-executable state. Remove the persistence-backed scope resolver wiring, back out the minimal
scope schema/changelog for this slice, remove the new base permission/capability enforcement, and
rerun the existing authorization proving suites to confirm the platform returns to the pre-scope
behavior with no executable scope reduction path.

## Dependencies

- Existing exploration artifact: `openspec/changes/backend-scopes-execution/exploration.md`
- Existing main specs in `openspec/specs/authorization/spec.md`, `openspec/specs/platform/spec.md`,
  and `openspec/specs/governance/spec.md`
- Existing backend authorization seams in `server/smp`, especially `WorkspaceAuthorizationService`,
  `ScopeResolver`, and `ResourceContext`
- Existing H2 and PostgreSQL integration harness patterns already used by current authorization
  proving slices

## Success Criteria

- [ ] The proposal remains limited to one new protected target-aware proving capability using a
  resource-preview-by-resourceId style operation.
- [ ] The change defines exactly one explicit base permission for that capability.
- [ ] The change defines one persisted workspace-scoped scope model that can reduce allowed target
  resource IDs for that capability.
- [ ] The resulting spec/design can require that scope evaluation only runs after an otherwise valid
  allow path exists.
- [ ] The resulting spec/design can require deny outcomes for both `missing base permission` and
  `scope reduced target out of allowed set`, with those outcomes remaining distinguishable.
- [ ] The resulting tasks can prove the allow/deny matrix end to end on both H2 and PostgreSQL.
- [ ] Deferred items remain explicit: generic scope engines, wildcards, inheritance, multi-context
  scopes, admin CRUD, quotas/billing/entitlements combinations, and broad policy redesign.
