## Exploration: backend-feature-entitlements

### Current State

`server/smp` now has a real executable authorization core on the single proving slice
`GET /api/authorization/workspace-access/current`.

What is already real in code:

- USER JWT, SERVICE_ACCOUNT bearer, and API_KEY authentication are executable.
- Active workspace resolution and workspace membership checks are executable.
- Role-based permission composition is executable.
- Persisted direct grants are executable through `workspace_direct_grants` plus
  `R2dbcDirectGrantResolver`.
- Deny-by-default, explicit direct deny precedence, expired-grant exclusion, and runtime audit-ready
  proof are executable on both H2 and PostgreSQL integration suites.

What remains seam-only for entitlements:

- `EntitlementResolver` exists, but `AuthorizationBootstrapConfiguration` wires
  `NoOpEntitlementResolver`.
- `WorkspaceAuthorizationService` resolves entitlements but ignores the returned set entirely.
- `Entitlement` is only `key + enabled`; there is no persisted entitlement model, no adapter, no
  decision rule, and no audit reason for entitlement failure.
- There is no feature-gated capability beyond the existing proving slice, and there are no
  entitlement-management/admin workflows.

So the important truth is this: permissions and direct grants are already real platform behavior,
but feature entitlements are still only an architectural seam. The next change should make
entitlement gating executable without reopening broad product/package management.

### Affected Areas

- `openspec/specs/authorization/spec.md` — already defines that entitlements are separate from
  permissions and that gated capabilities may require both checks.
- `openspec/specs/platform/spec.md` — already requires caches and authoritative state to account for
  entitlements when they become real.
- `openspec/specs/governance/spec.md` — governance model already expects explicit decision facts,
  which an entitlement deny would need to surface.
- `openspec/changes/archive/2026-05-15-backend-auth-foundation/design.md` — original architecture
  explicitly reserved entitlements as a first-class extension seam.
- `openspec/changes/archive/2026-05-15-backend-authorization-breadth/exploration.md` — earlier
  exploration correctly noted entitlements were still seam-only and deferred runtime execution.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` —
current pivot point; entitlements are resolved but ignored.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` —
currently wires the no-op entitlement resolver.
- `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt` —
  current entitlement domain model is minimal and likely sufficient for one narrow proving change.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` —
  lacks an entitlement-specific authorization reason code today.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` —
current protected capability and audit proof surface; likely the narrowest place to prove real
entitlement gating.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` —
existing H2 proof harness for the narrowest entitlement execution slice.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` —
existing PostgreSQL proof harness for authoritative entitlement behavior.
- `server/smp/src/main/resources/db/changelog/authorization/*.yaml` — current authorization schema
  has direct grants but no entitlement persistence yet.

### Approaches

1. **Gate the existing proving slice with one persisted workspace entitlement** — add persisted
   workspace-scoped entitlement lookup and require it alongside permission for
   `GET /api/authorization/workspace-access/current`.
    - Pros: Smallest coherent executable entitlement change; no new endpoint required; proves
      permission-vs-entitlement separation with real runtime behavior; reuses current audit and
      integration harness.
    - Cons: The endpoint is a platform proof surface, so the gated feature is slightly synthetic
      from a product perspective.
    - Effort: Low/Medium

2. **Add a new entitlement-gated backend capability** — introduce a second protected endpoint/use
   case whose availability naturally depends on a feature.
    - Pros: More product-natural feature gating story.
    - Cons: Broadens scope into new backend behavior, new endpoint semantics, and likely new product
      decisions; this is not the smallest next change.
    - Effort: Medium/High

3. **Build entitlement persistence plus admin/packaging workflows** — add tables, management APIs,
   plan/workspace assignment semantics, and runtime gating together.
    - Pros: More complete entitlement platform story.
    - Cons: Immediate sprawl across authorization, governance, and product packaging semantics; too
      much breadth for the next coherent change.
    - Effort: High

4. **Add generic entitlement engine for tenant/workspace/product contexts first** — focus on a broad
   abstraction before making any one capability executable.
    - Pros: Cleaner long-term abstraction on paper.
    - Cons: Falls back into seam-heavy architecture without proof; weak immediate value.
    - Effort: Medium/High

### Recommendation

Recommend **Approach 1: gate the existing proving slice with one persisted workspace entitlement**.

This is the smallest coherent next change that adds **real entitlement value** while staying
separate from permissions:

- The repo has only one protected executable capability today, so using that slice is the only way
  to prove runtime entitlement enforcement without inventing unrelated product breadth.
- Permissions are already real and auditable there, which makes it the best place to prove the
  required rule: **permission success is not enough when the workspace lacks the feature entitlement
  **.
- The change can stay narrow by introducing one workspace-scoped entitlement key for that
  capability, one persisted lookup path, one explicit deny outcome, and matching H2/PostgreSQL
  proof.
- This keeps entitlement work focused on **runtime availability gating**, not on plan catalogs,
  billing, packaging, or admin CRUD.

#### What belongs in this change

- One narrow persisted workspace entitlement model in authorization storage, sufficient to answer:
  “is workspace X entitled to feature Y?”
- One executable `R2dbc` entitlement resolver wired through the existing `EntitlementResolver` seam.
- One explicit runtime rule for the existing proving slice: access requires both
    1. entitlement for the capability, and
    2. principal permission for the action.
- One explicit authorization/audit reason for missing entitlement so denials remain explainable.
- H2 and PostgreSQL proof for at least these scenarios:
    - entitled workspace + authorized principal -> allow
    - non-entitled workspace + authorized principal -> deny because entitlement is missing
    - entitled workspace + unauthorized principal -> deny because permission is missing
- Scope guardrails that keep the change limited to **workspace-scoped entitlement gating on the
  existing proving slice**.

#### What should remain deferred

- New product endpoints or non-platform feature surfaces just to “make entitlements feel real.”
- Plan catalogs, subscription packaging, billing integration, bundle resolution, or tenant-wide SKU
  semantics.
- Entitlement CRUD/admin APIs, assignment workflows, list/detail/search endpoints, or operator
  consoles.
- Multi-context entitlement breadth across GLOBAL, USER, and SYSTEM contexts.
- Combination rules like inheritance, fallback chains, quota/usage semantics, or time-windowed
  entitlements.
- Scope enforcement, broader ABAC conditions, and generalized policy composition beyond the one
  gating rule.

### Risks

- Gating the current proving slice is slightly artificial, so proposal/design must state clearly
  that this is a **platform proof change**, not a claim that workspace-access-summary is a
  customer-facing paid feature.
- If entitlement persistence is over-designed now, the work could drift into pricing/package
  architecture instead of runtime gating.
- If the team tries to add entitlement management APIs in the same wave, scope will sprawl quickly.
- Audit and error semantics need care; otherwise entitlement denials may be indistinguishable from
  permission denials.
- Cache/invalidation semantics for entitlements are specified at the platform level, but this change
  should avoid premature caching implementation.

### Ready for Proposal

Yes — propose `backend-feature-entitlements` as a **narrow executable entitlement-gating change**
that makes one workspace entitlement real on the existing proving slice and proves the separation
between feature availability and principal permission. Keep plan/package modeling, entitlement
administration, broader contexts, and new product capabilities explicitly deferred.
