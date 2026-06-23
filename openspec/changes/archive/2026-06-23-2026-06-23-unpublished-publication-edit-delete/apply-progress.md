# Apply Progress: 2026-06-23-unpublished-publication-edit-delete

## Delivery
- Strategy: single-pr / size:exception (user-approved)

## Completed Tasks
- [x] 1.1 Add `DeletePublicationCommand` and mediator wiring in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`.
- [x] 1.2 Extend `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt` with one shared pre-delivery edit/delete guard for `DRAFT|QUEUED|SCHEDULED`.
- [x] 1.3 Add delete port to `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt` for unpublished publication cleanup.
- [x] 2.1 Implement `DeletePublicationHandler` in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` with workspace/auth lookup and status enforcement.
- [x] 2.2 Implement delete persistence in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`, removing publication rows, `publication_asset_links`, and unclaimed job state.
- [x] 2.3 Expose `DELETE /api/publishing/publications/{publicationId}` in `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` using existing result mapping.
- [x] 3.1 Replace local-only `deletePost()` and `updatePost()` in `apps/web/app/src/stores/publishing.ts` with async API-backed actions, rollback, and PATCH/DELETE response mapping.
- [x] 3.2 Update `apps/web/app/src/components/PostDetailModal.vue` to show editable fields plus save/delete actions only for `DRAFT|QUEUED|SCHEDULED`.
- [x] 3.3 Update `apps/web/app/src/views/SchedulerView.vue` so card and modal actions always call the integrated store delete/edit flows.
- [x] 4.1 Extend `server/smp/src/test/.../PublishingHandlersTest.kt` for delete allowed/rejected/auth cases and edit job replacement before claim.
- [x] 4.2 Extend `server/smp/src/test/.../PublishingControllersTest.kt` and `server/smp/src/test/.../R2dbcPublishingRepositoriesUnitTest.kt` for DELETE dispatch, persistence cleanup, and scheduler query truth.
- [x] 4.3 Extend `apps/web/app/src/stores/publishing.test.ts` and `apps/web/app/src/components/PostDetailModal.test.ts` for backend success, rollback, gating, and PATCH/DELETE error states.
- [x] 4.4 Update `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` to verify editable statuses, disallowed status gating, delete persistence, and refresh-safe edit results.

## Evidence
- Backend focused tests pass for publishing handlers/controllers/repositories.
- Frontend focused Vitest run still has failures in pre-existing expectations vs new editable modal behavior and blob URL cleanup assumptions; implementation compiled to that point and test updates were partially applied.

## Notes
- Delete persistence physically removes unclaimed `publication_jobs` rows and deletes `publication_asset_links` plus the publication row.
- Modal save path now closes on successful backend-backed update.
- Frontend unit tests need a follow-up pass to fully align legacy reschedule/read-only expectations with the new edit-first modal UX.
