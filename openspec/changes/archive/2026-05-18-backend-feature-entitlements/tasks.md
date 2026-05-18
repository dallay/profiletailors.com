# Tasks: Backend Feature Entitlements

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Review budget | 400 changed lines unless project config says otherwise |
| Estimated workload | Medium |
| Chained PRs recommended | No |
| Proposed delivery strategy | single-pr |
| Work-unit balance | One schema/resolver work unit, one enforcement/audit work unit, one proving-suite work unit so each slice stays reviewable and behavior-complete. |

## Phase 1: Persistence Foundation

- [x] 1.1 Create `server/smp/src/main/resources/db/changelog/authorization/006-create-workspace-entitlements.yaml` with one authoritative `(workspace_id, entitlement_key)` row model and explicit `enabled` flag only.
- [x] 1.2 Update `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` to include the new authorization changelog in order, without introducing package/billing or admin schema breadth.
- [x] 1.3 Create `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/R2dbcWorkspaceEntitlementResolver.kt` to load workspace-scoped entitlement rows through the existing `EntitlementResolver` seam and return minimal domain entitlements.
- [x] 1.4 Update `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt` to wire the R2DBC resolver instead of `NoOpEntitlementResolver`.

## Phase 2: Authorization Enforcement and Proof Semantics

- [x] 2.1 Update `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryQuery.kt` to declare the single proving-slice entitlement key explicitly for `/api/authorization/workspace-access/current`.
- [x] 2.2 Update `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` to add a dedicated missing-entitlement authorization reason code for runtime explainability.
- [x] 2.3 Update `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` so current-slice allow requires both entitlement success and permission success, while deny paths preserve direct deny and missing-permission behavior.
- [x] 2.4 Verify the current handler/controller proof path keeps surfacing the returned reason code on the existing endpoint without adding new endpoints or entitlement CRUD flows.

## Phase 3: Unit Proof

- [x] 3.1 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt` to prove entitled + authorized allow on the current slice.
- [x] 3.2 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt` to prove authorized but non-entitled deny returns `MISSING_ENTITLEMENT` and remains distinct from `MISSING_PERMISSION`.
- [x] 3.3 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/GetCurrentWorkspaceAccessSummaryHandlerTest.kt` to prove audit facts preserve exact deny reasons for missing entitlement versus missing permission.

## Phase 4: Proving-Slice Integration Verification

- [x] 4.1 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` with H2 scenarios for entitled allow, non-entitled deny, and unauthorized deny on `/api/authorization/workspace-access/current`.
- [x] 4.2 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` with the same matrix against PostgreSQL-backed Liquibase + R2DBC state.
- [x] 4.3 Keep test seeding limited to the single workspace entitlement key and current proving slice; explicitly avoid CRUD/admin APIs, multi-context entitlements, quotas/usage semantics, and new protected endpoints.
