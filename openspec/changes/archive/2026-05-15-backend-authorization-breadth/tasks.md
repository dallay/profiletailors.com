# Tasks: Backend Authorization Breadth

## Review Workload Forecast

| Field                      | Value                                                                                                                  |
|----------------------------|------------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines                                                                                                      |
| Estimated workload         | Medium                                                                                                                 |
| Chained PRs recommended    | No                                                                                                                     |
| Proposed delivery strategy | single-pr                                                                                                              |
| Work-unit balance          | Keep migration+resolver, endpoint behavior, and proving tests as 3 reviewable work units with code and tests together. |

## Scope Guardrails

Out of scope: scopes, entitlements, separate deny subsystem, grant admin flows, broader policy APIs,
and any authorization surface beyond `/api/authorization/workspace-access/current`.

## Phase 1: Persistence Foundation

- [x] 1.1 Create
  `server/smp/src/main/resources/db/changelog/authorization/005-create-workspace-direct-grants.yaml`
  with `workspace_direct_grants`, FKs, unique key, and resolver index only.
- [x] 1.2 Update `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` to include
  the new authorization changelog in baseline order.
- [x] 1.3 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt`
  to prove the new changelog resource and table are part of the baseline.

## Phase 2: Direct-Grant Resolution and Execution

- [x] 2.1 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/R2dbcDirectGrantResolver.kt`
  to query active-workspace rows for current principal and map them to `DirectGrant`.
- [x] 2.2 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt`
  to wire the R2DBC resolver instead of the no-op implementation.
- [x] 2.3 Adjust
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`
  only as needed to keep precedence explicit: membership required, expired grants ignored, direct
  deny wins, direct allow next, role fallback last.
- [x] 2.4 Update
  `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`
  and
  `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/DirectGrantPrecedenceTest.kt`
  to pin `DIRECT_ALLOW`, `DIRECT_DENY`, and expired-grant-ignore semantics.

## Phase 3: Proving-Slice Verification

- [x] 3.1 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`
  with seeded H2 scenarios for direct allow, direct deny override, and expired direct grant ignored
  on `/api/authorization/workspace-access/current`.
- [x] 3.2 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`
  with the same three seeded scenarios against PostgreSQL-backed state.
- [x] 3.3 Verify the proving slice still preserves existing role-only access behavior in the updated
  H2/PostgreSQL endpoint tests when no applicable direct deny is present.
