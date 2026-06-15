## Exploration: backend-scopes-execution

### Current State

`server/smp` now has one real end-to-end authorization proving slice at
`GET /api/authorization/workspace-access/current`.

What is executable today:

- USER JWT, SERVICE_ACCOUNT bearer, and API_KEY authentication are real.
- Active workspace resolution is real through `X-Workspace-Id` into
  `ResourceContext(type = WORKSPACE, workspaceId = ...)`.
- Workspace membership, role-permission composition, persisted direct grants, explicit direct deny
  precedence, expired-grant exclusion, and workspace entitlements are real.
- Runtime audit-ready proof is real for allow/deny outcomes on the proving slice, with distinct
  codes for missing membership, missing permission, missing entitlement, direct allow/deny, and
  revoked credential.
- H2 and PostgreSQL integration suites already prove the current slice.

What remains seam-only for scopes:

- `ScopeResolver` is wired as `NoOpScopeResolver` in `AuthorizationBootstrapConfiguration`.
- `WorkspaceAuthorizationService` calls `scopeResolver.resolve(...)` but discards the result.
- `AuthorizationScope` only carries `key: String`; it has no persisted shape, no matching logic, no
  effect on decisions, and no audit reason.
- `ResourceContext` has `targetResourceType`, `targetResourceId`, and `scopeHints`, but the current
  HTTP proving slice never supplies any target resource or operation-specific narrowing input beyond
  the workspace itself.
- The only protected endpoint returns the current caller’s workspace access summary. That is binary
  access to one capability, not a multi-target operation where scopes can naturally reduce a set of
  resources.

So the key truth is this: the platform already preserves the scope seam and the scope invariant in
specs, but there is still no executable place where a scope can reduce access without inventing
synthetic policy semantics.

### Affected Areas

- `openspec/specs/authorization/spec.md` — source of truth for the invariant that scopes can only
  reduce permissions and never manufacture access.
- `openspec/specs/platform/spec.md` — source of truth for explicit `ResourceContext` semantics and
  the rule that permissions, grants, scopes, and policies must not depend on implicit context
  inference.
- `openspec/specs/governance/spec.md` — decisions must remain explainable through explicit platform
  facts, which future scope denials would need to preserve.
- `openspec/changes/archive/2026-05-15-backend-auth-foundation/design.md` — original architecture
  reserved scopes as a first-class authorization concern.
- `openspec/changes/archive/2026-05-15-backend-authorization-breadth/exploration.md` — earlier
  exploration explicitly deferred executable scopes because the proving slice had no natural
  narrowing surface.
- `openspec/changes/archive/2026-05-18-backend-feature-entitlements/exploration.md` and
  `design.md` — confirm the current slice was intentionally kept narrow and still centered on
  `/api/authorization/workspace-access/current`.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` —
current authorization pivot point; scope resolution is present but ignored.
- `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt` —
  `AuthorizationScope` exists but is too minimal to express executable reduction semantics beyond a
  placeholder key.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` —
wires the no-op scope resolver today.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/domain/ResourceContext.kt` — already
  has the target fields a future scope implementation would need, but current requests do not
  populate them meaningfully.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` —
current proving use case; binary capability, not a natural scope-reduction surface.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/http/WorkspaceAccessSummaryController.kt`
and `.../tenancy/infrastructure/http/WorkspaceContextWebFilter.kt` — show the current transport only
establishes workspace context, not narrower target context.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt` —
current unit proof for membership/direct grant/entitlement semantics; no scope behavior tested.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`
and `...PostgresIntegrationTest.kt` — the only end-to-end authorization proof harness currently
available.

### Approaches

1. **Make scopes executable on the current workspace-access-summary endpoint** — persist scope rows
   and let them gate `GET /api/authorization/workspace-access/current`.
    - Pros: Reuses the existing endpoint and verification harness; smallest code surface.
    - Cons: Weak semantics. A binary “can call this endpoint or not” rule does not demonstrate real
      privilege reduction; it collapses scopes into another feature flag or permission alias. This
      would be fake scope value.
    - Effort: Low/Medium

2. **Introduce one narrowly targeted proving capability whose input naturally supports reduction** —
   add one additional protected backend operation where the caller already has a base workspace
   permission, and a persisted scope can narrow the allowed target set or operation subset.
    - Pros: This is the first option that creates real scope semantics instead of pretending. It
      lets the platform prove the core invariant: permission grants base access, scope reduces the
      reachable target(s), and missing base permission still denies.
    - Cons: Requires broadening beyond the current single proving endpoint. Also needs a very
      disciplined capability choice so the repo does not slide into generic policy-engine or product
      redesign work.
    - Effort: Medium

3. **Build generalized scope infrastructure first** — add scope tables, richer scope models,
   resolver logic, management semantics, and matching engine before choosing a proving capability.
    - Pros: Looks architecturally thorough on paper.
    - Cons: Wrong order. It produces more seam and schema than executable value, and the team would
      still need a real slice later to prove semantics.
    - Effort: High

4. **Bundle scopes with broad RBAC/ABAC or platform redesign** — redesign policy evaluation around
   generic attributes, conditions, and scope composition.
    - Pros: Maximum future flexibility.
    - Cons: Immediate sprawl. This would ignore the current repo discipline of proving one narrow
      behavior at a time.
    - Effort: High

### Recommendation

Recommend **Approach 2: introduce one narrowly targeted proving capability whose input naturally
supports real reduction semantics**.

Here is the thing: the next change should **not** force scopes onto
`/api/authorization/workspace-access/current`. That endpoint is about whether the current principal
may inspect its own workspace-access summary. A scope there would only flip access on or off, which
does not prove “scope narrows an otherwise allowed action” in any meaningful way.

The smallest coherent scope change is therefore:

- keep the existing IAM foundation intact,
- add exactly one new protected backend operation with explicit target context,
- require one existing base permission for that operation,
- add one persisted workspace-scoped scope model that can restrict allowed targets or allowed
  operation variants,
- enforce that the scope can only reduce an already-valid allow path.

In practice, the change should be framed as **the first executable scope proof**, not as “scope
support everywhere.”

#### What belongs in this change

- A **single additional proving capability** whose request carries an explicit target that can be
  reduced, for example a target resource id/type or a narrow operation selector.
- Minimal persistence for **workspace-scoped executable scopes** tied to:
    - workspace
    - principal
    - base permission
    - one explicit narrowing dimension for that chosen capability
- A real `ScopeResolver` adapter and runtime evaluation inside `WorkspaceAuthorizationService` (or a
  closely related authorization path) that applies scopes only after an otherwise-valid allow path
  exists.
- Explicit decision behavior that proves:
    - base permission + matching scope -> allow
    - base permission + non-matching/narrower scope -> deny
    - missing base permission + any scope -> deny
- Audit-ready proof that distinguishes a scope-caused deny from missing permission, if the chosen
  capability needs governance-grade explainability.
- H2 and PostgreSQL end-to-end proof for the exact narrowing rule on the chosen capability.

#### What should remain deferred

- Trying to make `GET /api/authorization/workspace-access/current` itself the scope proof surface.
- Generic scope catalogs, scope templates, hierarchy, inheritance, wildcard matching, or
  multi-dimension engines.
- Broad ABAC/policy-engine redesign.
- Scope CRUD/admin APIs, operator workflows, or UI.
- Multi-context scope breadth across GLOBAL, USER, and SYSTEM.
- Combining scopes with quotas, usage ceilings, packages, billing, or commercial entitlement
  semantics.
- Retrofitting scopes across all current credential types/endpoints beyond the one chosen proving
  capability.

### Risks

- The biggest risk is **fake scope semantics**: if the team keeps the current endpoint and merely
  turns scopes into another boolean gate, the change will satisfy architecture vocabulary but not
  real privilege reduction.
- The repo currently has only one executable protected endpoint, so a true scope proof likely
  requires adding one new protected operation. If that operation is chosen poorly, scope work could
  drift into product redesign.
- `AuthorizationScope(key: String)` is probably too thin for real execution. The next design must
  enrich the model only enough for the chosen narrowing rule, not for a generic engine.
- Audit semantics may need one more explicit deny reason if governed distinguishability between
  “missing permission” and “scope reduced access” is required.
- If the next change tries to support many scope shapes at once, persistence and matching rules will
  sprawl quickly.

### Ready for Proposal

Yes — but only if the proposal is framed as **one first executable scope slice on a newly
introduced, tightly bounded target-aware capability**, not as a retrofit of scopes onto the current
workspace-access-summary endpoint and not as a broad policy-platform redesign.
