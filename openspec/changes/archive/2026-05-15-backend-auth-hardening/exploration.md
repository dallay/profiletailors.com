## Exploration: backend-auth-hardening

### Current State

`server/smp` now has a working phase-1 auth foundation with six bounded-context roots (`platform`,
`identity`, `tenancy`, `authorization`, `credentials`, `governance`), CQRS/mediator seams,
JWT-backed principal materialization, Liquibase baseline migrations, and one protected proving slice
at `/api/authorization/workspace-access/current`.

The archived verification for `backend-auth-foundation` passed with warnings, and both remaining
warnings point to the same gap: governance/audit behavior exists only as a seam, not as
runtime-proven allow/deny facts. `AuditHook` currently exposes only `requestName` plus coarse
`RequestOutcome.SUCCESS|FAILURE`, and the default bean is a no-op (
`platform/application/PlatformContracts.kt`,
`platform/infrastructure/PlatformBootstrapConfiguration.kt`).

The proving-slice integration coverage is also still H2-in-PostgreSQL-mode only.
`WorkspaceAccessSummaryEndpointIntegrationTest` runs against
`r2dbc:h2:mem:///proving_slice?options=MODE=PostgreSQL`, applies Liquibase manually over H2 JDBC,
and stubs JWT decoding in-process. That gives good fast coverage, but it does not prove the existing
Liquibase + R2DBC + SQL assumptions against a real PostgreSQL engine.

Spring Modulith is present as a dependency, but there are no architecture/module verification tests
yet. The bounded-context markers currently exist only as empty objects, so the codebase communicates
intended seams structurally, but does not enforce them.

### Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` —
  current audit seam is too coarse to prove authorization-specific allow/deny facts.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/PlatformBootstrapConfiguration.kt` —
audit hook wiring is no-op by default; any runtime proof will need test-time replacement or enriched
wiring.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/SpringMediator.kt` —
  current mediator dispatch has no hook invocation around request handling.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` —
proving-slice handler is the narrowest place to verify allow/deny outcomes without broadening scope.
-
`server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` —
allow/deny decisions are made here today, but are not surfaced as audit-ready runtime facts.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` —
current proving-slice end-to-end verification uses H2 compatibility mode only.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt` —
validates changelog composition statically, not against real PostgreSQL execution.
- `server/smp/build.gradle.kts` — already includes Spring Modulith test support, so structural tests
  are feasible without dependency expansion.
- `server/smp/compose.yaml` — existing Postgres compose file is generic and not aligned with current
  app defaults, which matters if real-Postgres verification is introduced.
- `openspec/changes/archive/2026-05-15-backend-auth-foundation/verify-report.md` — source of the
  carry-forward warnings this hardening slice should intentionally address.

### Approaches

1. **Structural hardening first** — add Spring Modulith / architecture tests for bounded-context
   dependency rules.
    - Pros: Protects intended boundaries before phase 2 adds more feature code; leverages existing
      `spring-modulith-starter-test` dependency; low runtime risk.
    - Cons: Does not address the archived verify warning directly; gives confidence in package
      dependencies, not in auth runtime truth or database behavior; boundary rules may churn as
      phase 2 fills sparse contexts.
    - Effort: Medium

2. **Runtime governance proof first** — add a narrow audit assertion path for allow/deny outcomes in
   the existing proving slice.
    - Pros: Directly resolves the archived verify warning; stays tightly scoped to one existing
      query and one existing permission path; highest confidence-per-line for operational
      diagnosability.
    - Cons: The current `AuditHook` contract is request-oriented, not decision-oriented, so even a
      small hardening slice likely needs a modest contract reshape or an additional audit event
      abstraction.
    - Effort: Medium

3. **Persistence realism first** — add PostgreSQL-backed verification for the proving slice beyond
   H2 compatibility mode.
    - Pros: Proves Liquibase migrations, SQL, R2DBC mappings, and schema assumptions against the
      actual target engine; reduces false confidence from H2 PostgreSQL emulation.
    - Cons: Slower and more environment-sensitive than current tests; if done alone, it still leaves
      the governance warning open.
    - Effort: Medium

4. **Focused proving-slice hardening** — combine runtime governance proof with one real-PostgreSQL
   verification path, and defer architecture/modulith enforcement.
    - Pros: Directly addresses the only archived warning and the biggest hidden compatibility risk;
      remains narrow because it stays inside one existing endpoint/query flow; improves confidence
      before phase 2 without reopening broader architecture work.
    - Cons: Slightly larger than a single-item slice; still leaves structural boundary enforcement
      for a later change.
    - Effort: Medium

### Recommendation

Recommend **Approach 4: focused proving-slice hardening**.

For this new change, the smallest high-value scope is:

1. **Add runtime audit proof for the proving slice**
    - Prove that the existing protected query can emit audit-ready allow/deny outcomes for:
        - authorized access
        - denied access due to missing permission (or equivalent explicit deny path already covered)
    - Keep the scope narrow: one request path, one permission, no audit persistence, no full
      governance subsystem.
    - If the current `AuditHook(requestName, outcome)` contract is too coarse, evolve it only enough
      to carry authorization-relevant facts for this slice.

2. **Add PostgreSQL-backed verification for the same proving slice**
    - Re-run the existing end-to-end slice against real PostgreSQL rather than only H2 compatibility
      mode.
    - Keep it limited to the current happy-path and one denial-path so the change stays fast and
      focused.
    - Use this to validate Liquibase execution, R2DBC queries, and SQL assumptions against the
      target engine.

### Deferred from This Change

- **Architecture/modulith dependency tests for bounded contexts** — valuable, but should remain
  deferred.
    - Reason: they harden intended structure, but they do not close the concrete verify warning, and
      the bounded contexts are still sparse enough that rigid dependency rules may be premature
      right before phase 2 expands them.
    - Best follow-up home: either an early phase-2 structural hardening task or a separate
      `backend-architecture-guardrails` change once more feature code exists to justify stable
      rules.

- **Broader governance build-out** (audit persistence, reporting, compliance workflows) — deferred.
    - Reason: outside the “small hardening change” objective.

- **Broad PostgreSQL test migration for all backend tests** — deferred.
    - Reason: this change only needs one proving slice on real PostgreSQL, not a full test-suite
      infrastructure conversion.

### Risks

- Enriching the audit seam too aggressively could accidentally turn a narrow hardening slice into a
  governance redesign.
- Real PostgreSQL verification may expose compose/test-environment friction because
  `server/smp/compose.yaml` is still generic and not aligned with `application.yaml` defaults.
- If the team insists on both full Modulith rules and real-Postgres coverage in the same change,
  scope may stop being “small” and start overlapping with phase 2.
- Leaving architecture dependency tests deferred means bounded-context violations can still slip in
  during early phase 2 unless that follow-up is scheduled deliberately.

### Ready for Proposal

Yes — propose this as a **narrow proving-slice hardening change** whose success criteria are: (1)
audit-ready allow/deny runtime proof for the existing authorization slice, and (2) PostgreSQL-backed
verification for that same slice. Keep architecture/modulith dependency tests explicitly out of
scope and list them as the first deferred structural follow-up.
