# Tasks: Backend Scopes Execution

## Review Workload Forecast

| Field                      | Value                                                                                                                                                              |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines unless project config says otherwise                                                                                                             |
| Estimated workload         | Medium                                                                                                                                                             |
| Chained PRs recommended    | No                                                                                                                                                                 |
| Proposed delivery strategy | single-pr                                                                                                                                                          |
| Work-unit balance          | One persisted-scope foundation unit, one target-aware enforcement unit, and one proving-suite verification unit so each review slice includes behavior plus proof. |

## Phase 1: Persistence and Scope Wiring

- [x] 1.1 Create
  `server/smp/src/main/resources/db/changelog/authorization/007-create-workspace-target-scopes.yaml`
  with one workspace-scoped target-scope table for principal + permission
  `workspace:resource:read` + explicit target resource type + allowed target IDs only.
- [x] 1.2 Update `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` to include
  the new scope changelog without introducing generic scope-engine, wildcard, inheritance, or admin
  breadth.
- [x] 1.3 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/domain/AuthorizationModels.kt` to
  replace the placeholder scope shape with the minimal executable target-aware model from the
  design.
- [x] 1.4 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/R2dbcWorkspaceTargetScopeResolver.kt`
  and wire it in
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/AuthorizationBootstrapConfiguration.kt`
  instead of `NoOpScopeResolver`.

## Phase 2: Target-Aware Capability and Enforcement

- [x] 2.1 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/GetResourcePreviewQuery.kt`
  for the one new proving capability using base permission `workspace:resource:read` and explicit
  `resourceId` target input.
- [x] 2.2 Create
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/http/ResourcePreviewController.kt`
  and any minimal request-context wiring needed so `ResourceContext` carries WORKSPACE + explicit
  `targetResourceType` + `targetResourceId`.
- [x] 2.3 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/platform/application/PlatformContracts.kt` to
  add a distinct scope-deny reason code for target reduction proof.
- [x] 2.4 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt`
  so base permission allow-path resolution happens first and scope reduction only narrows allowed
  `targetResourceId` values after that allow path exists.

## Phase 3: Unit and Audit Proof

- [x] 3.1 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`
  to prove base permission + matching scope allows the resource preview capability.
- [x] 3.2 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationServiceTest.kt`
  to prove base permission + non-matching target denies with the scope-specific reason, distinct
  from `MISSING_PERMISSION`.
- [x] 3.3 Add or extend the target-aware handler/unit test alongside `GetResourcePreviewQuery.kt` to
  prove missing base permission still denies even when scope rows exist, and that scope cannot
  manufacture access.

## Phase 4: H2 and PostgreSQL Proving-Slice Verification

- [x] 4.1 Create
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/ResourcePreviewEndpointIntegrationTest.kt`
  with H2 scenarios for matching-scope allow, non-matching-target scope deny, and
  missing-base-permission deny.
- [x] 4.2 Create
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/ResourcePreviewEndpointPostgresIntegrationTest.kt`
  with the same allow/deny matrix against PostgreSQL-backed Liquibase + R2DBC state.
- [x] 4.3 Keep all seed data and assertions limited to the one target-aware proving capability and
  persisted workspace target scopes; explicitly exclude generic scope engines, wildcards,
  inheritance, multi-context scopes, admin CRUD, quotas/billing/entitlement combinations, and broad
  policy redesign.
