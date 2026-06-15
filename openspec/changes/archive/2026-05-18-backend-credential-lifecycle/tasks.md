# Tasks: Backend API-Key Credential Replacement Lifecycle

## Review Workload Forecast

| Field                      | Value                                                                                                                                                                                           |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines                                                                                                                                                                               |
| Estimated workload         | Medium                                                                                                                                                                                          |
| Chained PRs recommended    | No                                                                                                                                                                                              |
| Proposed delivery strategy | single-pr                                                                                                                                                                                       |
| Work-unit balance          | One narrow cutover unit: additive schema + replacement use case + runtime enforcement + H2/PostgreSQL proof. Keep deferred inventory, overlap, service-account, and family-management work out. |

## Phase 1: Persistence and lifecycle foundation

- [x] 1.1 Update
  `server/smp/src/main/resources/db/changelog/credentials/002-create-api-key-credentials.yaml` with
  only additive API-key replacement metadata (`replaced_credential_id`, optional
  `replaced_by_credential_id`, `replaced_at`) needed for predecessor/successor semantics.
- [x] 1.2 Extend
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeyCredentialStateLookup.kt`
  so authoritative state can represent a completed replacement denial without broadening into
  generic credential-family management.
- [x] 1.3 Update
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt`
  to deny replaced predecessors and keep successor validation on normal lookup-key + verifier rules.

## Phase 2: Narrow replacement command/use case

- [x] 2.1 Add one focused replacement command/result and gateway under
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/` for “replace one active API-key
  credential” only.
- [x] 2.2 Implement the handler/persistence flow under
  `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/` to load one active predecessor,
  create one successor, persist lineage, and invalidate the predecessor in the same transaction with
  no overlap window.
- [x] 2.3 Add a small proving/test seam to invoke the replacement use case without introducing
  inventory/list/detail APIs, broad issuance surfaces, service-account rotation, or generalized
  lifecycle management.
- [x] 2.4 Modify
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
  only as needed so predecessor-after-replacement denial remains observable through the existing
  revoked/inactive audit-ready runtime path.

## Phase 3: Proving-slice verification

- [x] 3.1 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`
  with the H2 proving sequence: old key `200` before replacement, run replacement, old key `401`
  after replacement, new key `200` after replacement.
- [x] 3.2 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt`
  with the same before/after old/new key proof for PostgreSQL.
- [x] 3.3 Add focused assertions in the same proving slice that replacement completion leaves no
  dual-active overlap and that protected execution does not proceed for the predecessor after
  cutover.

## Phase 4: Finish and guard scope

- [x] 4.1 Review touched code/tests for strict scope: keep service-account rotation, overlap
  windows, inventory/list/detail APIs, issuance breadth, last-used tracking, and generalized
  credential-family management out of this change.
- [x] 4.2 Update `openspec/changes/backend-credential-lifecycle/tasks.md` checkboxes during
  `sdd-apply` so each completed work unit stays reviewable and verification-backed.
