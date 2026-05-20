# Proposal: Backend Auth Hardening

## Intent

Close the remaining high-value confidence gaps from `backend-auth-foundation` before phase 2 expands
backend breadth. This change focuses on proving that the existing protected workspace-access slice
can surface audit-ready allow/deny facts at runtime and that the same slice works against real
PostgreSQL rather than only H2 PostgreSQL compatibility mode.

## Scope

### In Scope

- Add a narrow runtime-proof path for the existing protected workspace-access slice so authorized
  and denied outcomes can be asserted as audit-ready facts.
- Add PostgreSQL-backed verification for the same `/api/authorization/workspace-access/current`
  slice, covering one allow path and one deny path beyond H2 compatibility mode.
- Make the smallest seam changes needed to carry authorization-relevant audit facts for this slice
  without introducing audit persistence or broader governance workflows.
- Align test/runtime wiring needed to execute this slice reliably against PostgreSQL in the current
  backend module.

### Out of Scope

- Spring Modulith or architecture dependency tests for bounded-context enforcement.
- Broad governance build-out such as audit persistence, compliance reporting, or policy
  administration.
- Full backend test-suite migration from H2 compatibility mode to PostgreSQL.
- New authorization capabilities, new protected endpoints, or phase-2 breadth expansion.
- Refactoring the bounded-context architecture beyond the minimum needed for this proving slice.

## Approach

Use the existing protected authorization slice as the proving surface and harden only that path.
Enrich the current audit seam just enough to express decision-relevant facts for allow/deny
outcomes, wire it into the request flow where the protected query is dispatched or resolved, and
verify the emitted facts through focused runtime tests. In parallel, add one PostgreSQL-backed
integration path for the same endpoint so Liquibase execution, R2DBC access, and SQL assumptions are
proven on the target engine without converting the broader suite.

## Affected Areas

| Area                                                                                                                   | Impact            | Description                                                                                                                     |
|------------------------------------------------------------------------------------------------------------------------|-------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt`                          | Modified          | Narrow audit seam evolution to carry authorization outcome facts needed by the proving slice.                                   |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/PlatformBootstrapConfiguration.kt`          | Modified          | Adjust default/test wiring so audit proof can be captured without widening governance scope.                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/SpringMediator.kt`                          | Modified          | Add the minimal hook point needed to surface runtime allow/deny proof around protected dispatch.                                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` | Modified          | Keep the proving slice anchored to the current protected query only.                                                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`         | Modified          | Surface explicit authorization decision facts already known in the slice.                                                       |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`       | Modified          | Extend proving-slice verification to assert runtime audit facts and PostgreSQL-backed allow/deny coverage.                      |
| `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt`                | Possibly Modified | Keep or supplement static migration checks as needed once PostgreSQL-backed slice coverage exists.                              |
| `server/smp/build.gradle.kts`                                                                                          | Possibly Modified | Add only the minimal test/runtime support required for PostgreSQL-backed verification if current dependencies are insufficient. |
| `server/smp/compose.yaml`                                                                                              | Possibly Modified | Align local Postgres test wiring only if needed to support the proving slice verification path.                                 |
| `openspec/changes/archive/2026-05-15-backend-auth-foundation/verify-report.md`                                         | Referenced        | Source of the warnings this hardening change intentionally addresses.                                                           |

## Scope Boundaries

This change is intentionally limited to the already-existing workspace-access proving slice. It does
not broaden protected-surface coverage, does not introduce new governance features, and does not
attempt to lock down all module dependencies. If a proposed implementation step does not directly
support runtime audit proof or PostgreSQL-backed verification for
`/api/authorization/workspace-access/current`, it belongs in a later change.

## Non-Goals

- Designing the full governance/audit subsystem.
- Establishing permanent architecture guardrails for all bounded contexts.
- Replacing all H2-based tests.
- Expanding from proving-slice hardening into phase-2 product behavior.

## Risks

| Risk                                                                                    | Likelihood | Mitigation                                                                                             |
|-----------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------|
| Audit seam changes grow into a governance redesign                                      | Medium     | Limit contract evolution to fields required to prove allow/deny facts for one slice only.              |
| PostgreSQL-backed tests introduce environment/setup friction                            | Medium     | Keep coverage to one endpoint slice with minimal fixtures and reuse existing backend test conventions. |
| Real PostgreSQL behavior exposes migration or SQL assumptions not visible under H2 mode | Medium     | Treat this as desired signal; keep rollback small by isolating the new verification path.              |
| Deferred architecture/modulith tests allow structural drift to continue temporarily     | Medium     | Record them explicitly as the first deferred structural follow-up after this hardening slice.          |

## Rollback Plan

If this hardening change causes instability, revert the audit seam additions and PostgreSQL-specific
verification wiring together as one unit, restoring the prior H2-only proving-slice test path and
the previous no-op/coarse audit seam behavior. Because scope is intentionally limited to one
protected slice and supporting test wiring, rollback should not require data migration reversal
beyond removing any test-only PostgreSQL support added for this change.

## Dependencies

- Existing exploration artifact at `openspec/changes/backend-auth-hardening/exploration.md`.
- Existing protected slice at `/api/authorization/workspace-access/current` from
  `backend-auth-foundation`.
- Local or CI-accessible PostgreSQL test runtime suitable for backend integration verification.
- Current Liquibase baseline and R2DBC mappings remaining the authoritative schema/runtime path.

## Success Criteria

- [ ] The proposal remains a narrow proving-slice hardening change with no phase-2 breadth added.
- [ ] The existing protected workspace-access slice has runtime-verifiable audit-ready allow and
  deny proof.
- [ ] The same slice is verified against real PostgreSQL for one authorized and one denied request
  path.
- [ ] Architecture/modulith dependency tests are explicitly deferred and not pulled into this
  change.
- [ ] Rollback remains low-risk and confined to the proving slice plus its supporting seam/test
  wiring.
