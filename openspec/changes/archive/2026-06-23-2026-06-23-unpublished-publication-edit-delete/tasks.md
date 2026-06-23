# Tasks: Unpublished Publication Edit/Delete

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 450-700 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 backend delete API/tests → PR 2 frontend store/UI wiring → PR 3 frontend tests/verification |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Backend delete contract, handler, persistence, controller, tests | PR 1 | Base main; backend-only slice |
| 2 | Frontend store API integration + modal/scheduler gating | PR 2 | Depends on PR 1 API shape |
| 3 | Frontend unit/e2e updates for edit/delete truth and rollback | PR 3 | Depends on PR 2 UI/store behavior |

## Phase 1: Backend Foundation

- [x] 1.1 Add `DeletePublicationCommand` and mediator wiring in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`.
- [x] 1.2 Extend `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt` with one shared pre-delivery edit/delete guard for `DRAFT|QUEUED|SCHEDULED`.
- [x] 1.3 Add delete port to `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt` for unpublished publication cleanup.

## Phase 2: Backend Implementation

- [x] 2.1 Implement `DeletePublicationHandler` in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` with workspace/auth lookup and status enforcement.
- [x] 2.2 Implement delete persistence in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`, removing publication rows, `publication_asset_links`, and unclaimed job state.
- [x] 2.3 Expose `DELETE /api/publishing/publications/{publicationId}` in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` using existing result mapping.

## Phase 3: Frontend Integration

- [x] 3.1 Replace local-only `deletePost()` and `updatePost()` in `apps/web/app/src/stores/publishing.ts` with async API-backed actions, rollback, and PATCH/DELETE response mapping.
- [x] 3.2 Update `apps/web/app/src/components/PostDetailModal.vue` to show editable fields plus save/delete actions only for `DRAFT|QUEUED|SCHEDULED`.
- [x] 3.3 Update `apps/web/app/src/views/SchedulerView.vue` so card and modal actions always call the integrated store delete/edit flows.

## Phase 4: Testing and Verification

- [x] 4.1 Extend `server/smp/src/test/.../PublishingHandlersTest.kt` for delete allowed/rejected/auth cases and edit job replacement before claim.
- [x] 4.2 Extend `server/smp/src/test/.../PublishingControllersTest.kt` and `server/smp/src/test/.../R2dbcPublishingRepositoriesUnitTest.kt` for DELETE dispatch, persistence cleanup, and scheduler query truth.
- [x] 4.3 Extend `apps/web/app/src/stores/publishing.test.ts` and `apps/web/app/src/components/PostDetailModal.test.ts` for backend success, rollback, gating, and PATCH/DELETE error states.
- [x] 4.4 Update `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` to verify editable statuses, disallowed status gating, delete persistence, and refresh-safe edit results.
