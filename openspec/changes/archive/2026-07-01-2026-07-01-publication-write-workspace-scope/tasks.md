# Tasks: Publication Write Workspace Scope

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 180-280 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Repository tenancy fix + focused tests | PR 1 | Single backend slice with unit and integration verification |

## Phase 1: Foundation

- [x] 1.1 Review `openspec/changes/2026-07-01-publication-write-workspace-scope/{proposal.md,design.md}` and map required repository/test touchpoints in `server/smp/.../R2dbcPublishingRepositories.kt` and related tests.
- [x] 1.2 Identify the repository exception style for cross-workspace existing-id collisions in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`.

## Phase 2: TDD Repository Coverage

- [x] 2.1 RED: add `R2dbcPublishingRepositoriesUnitTest` for same-workspace `updateEditableDraft()` preserving the row and updating fields without duplicate insert.
- [x] 2.2 RED: add `R2dbcPublishingRepositoriesUnitTest` for `createDraft()` inserting when the current workspace has no matching row.
- [x] 2.3 RED: add `R2dbcPublishingRepositoriesUnitTest` proving a write for an existing id in another workspace leaves that row unchanged and fails fast.
- [x] 2.4 GREEN: update `R2dbcPublicationRepository.insertOrUpdate()` to scope `UPDATE publications` by `id` and `workspace_id`, then guard the insert fallback with an existing-id cross-workspace check.

## Phase 3: Integration / Wiring

- [x] 3.1 RED: extend `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkspaceIsolationIntegrationTest.kt` for cross-workspace update isolation through the real repository flow.
- [x] 3.2 GREEN: align repository wiring so same-workspace edits still persist asset links and cross-workspace edits surface the chosen failure path.

## Phase 4: Verification

- [x] 4.1 Update focused publishing spec wording only if implementation reveals a contract mismatch; otherwise keep spec unchanged.
- [x] 4.2 Run focused backend tests for `R2dbcPublishingRepositoriesUnitTest` and `PublishingWorkspaceIsolationIntegrationTest`; confirm same-workspace update preservation, cross-workspace isolation, and wrong-workspace existing-id failure behavior.
