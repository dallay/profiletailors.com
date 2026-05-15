# Tasks: Backend Auth Hardening

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Review budget | 400 changed lines |
| Estimated workload | Low |
| Chained PRs recommended | No |
| Proposed delivery strategy | single-pr |
| Work-unit balance | One proving slice: audit fact seam + handler/service wiring + focused H2/PostgreSQL verification |

## Phase 1: Audit Fact Seam

- [x] 1.1 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` to add the minimal structured authorization audit callback and slice-local fact/result types for `workspace:access:read`.
- [x] 1.2 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/PlatformBootstrapConfiguration.kt` so the default no-op `AuditHook` implements the new callback without adding persistence or broader governance behavior.

## Phase 2: Proving-Slice Authorization Wiring

- [x] 2.1 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` to expose a detailed decision path for the current slice with explicit allow/deny reason metadata.
- [x] 2.2 Modify `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` to call the detailed decision path and emit audit-ready allow/deny facts before returning success or throwing denial.
- [x] 2.3 Keep `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/SpringMediator.kt` unchanged unless a tiny compatibility adjustment is strictly required for the existing request-level audit flow; do not broaden mediator governance behavior.

## Phase 3: Fast Integration Proof for Runtime Audit Facts

- [x] 3.1 Create `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/CapturingAuditHook.kt` as a tiny in-memory test hook that records emitted authorization facts for assertions.
- [x] 3.2 Modify `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` to override `AuditHook` with the capturing hook and assert one allow fact for the 200 path.
- [x] 3.3 In the same test class, assert one deny fact for the 403 path and verify the fact is attributable to `/api/authorization/workspace-access/current` with the protected permission context.

## Phase 4: PostgreSQL Proving Slice Verification

- [x] 4.1 Modify `server/smp/build.gradle.kts` only if needed to add the minimal container-backed PostgreSQL test support for one dedicated integration class.
- [x] 4.2 Create `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` to run the same endpoint against real PostgreSQL with dynamic Spring/Liquibase wiring and assert the authorized 200 path.
- [x] 4.3 In the same PostgreSQL test class, assert the denied 403 path and confirm the runtime audit fact capture still works on the target engine.

## Phase 5: Scope Guard and Verification

- [x] 5.1 Keep `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt` and `server/smp/compose.yaml` unchanged unless the PostgreSQL proving class reveals a direct blocker for this slice.
- [x] 5.2 Explicitly leave Spring Modulith / architecture dependency tests, audit persistence, compliance workflows, and broader governance coverage out of this change.
