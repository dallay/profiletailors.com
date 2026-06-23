# Apply Progress: unpublished-publication-edit-delete

## Status
Completed all planned implementation tasks for unpublished publication edit/delete across backend, frontend, and targeted scheduler E2E coverage.

## Completed Tasks
- [x] 1.1 Add `PublicationDeletionNotAllowedException` + `requireDeletable()`
- [x] 1.2 Add `DeletePublicationCommand`
- [x] 1.3 Add `DeletePublicationHandler`
- [x] 1.4 Add `deleteById(workspaceId, publicationId)` to `PublicationRepository`
- [x] 2.1 Implement `R2dbcPublicationRepository.deleteById()` with explicit child-row cleanup
- [x] 2.2 Add `DELETE /api/publishing/publications/{publicationId}` endpoint
- [x] 2.3 Add problem-details mapping for publication delete 404/409 cases
- [x] 3.1 Persist store `deletePost()` via backend DELETE with rollback/re-hydration on error
- [x] 3.2 Persist store `updatePost()` via backend PATCH with optimistic merge + rollback
- [x] 3.3 Keep modal/scheduler delete interactions routed through the store behavior
- [x] 4.1 Add handler tests for delete success, workspace boundary, non-deletable status, strict email gate
- [x] 4.2 Add repository delete cascade/workspace-scoping tests
- [x] 4.3 Add controller test for DELETE command dispatch
- [x] 4.4 Add problem-details tests for delete-related 404/409 mappings
- [x] 5.1 Add frontend store tests for delete persistence and failure rollback behavior
- [x] 5.2 Add frontend store tests for update persistence and rollback behavior
- [x] 6.1 Extend scheduler E2E coverage beyond local-only delete behavior
- [x] 6.2 Add refresh-persistence E2E for deleted draft publication
- [x] 6.3 Add modal delete E2E for scheduled publication
- [x] 6.4 Re-verify existing scheduler interaction flows in targeted Playwright spec
- [x] 6.5 Run backend and frontend verification commands

## Evidence
### Files changed
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/PublicationLifecyclePolicyTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllersTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingSchedulingConfigurationTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorkerTest.kt`
- `apps/web/app/src/stores/publishing.ts`
- `apps/web/app/src/stores/publishing.test.ts`
- `apps/web/app/src/views/SchedulerView.vue`
- `apps/web/app/e2e/fixtures/scheduler-mocks.ts`
- `apps/web/app/e2e/pages/post-detail-modal-page.ts`
- `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts`
- `openspec/changes/unpublished-publication-edit-delete/tasks.md`

### Test commands run
- `just backend-check`
- `cd apps/web/app && pnpm test:run`
- `cd apps/web/app && pnpm test:e2e:scheduler -- scheduler-post-interaction.spec.ts`
- `just frontend-test` (note: this repo command targets `apps/web/marketing`, not the SPA app)

### Test outcomes
- `just backend-check` ✅ passed
- `cd apps/web/app && pnpm test:run` ✅ passed (54 files, 463 tests)
- `cd apps/web/app && pnpm test:e2e:scheduler -- scheduler-post-interaction.spec.ts` ✅ passed (8 tests)
- `just frontend-test` ✅ passed for marketing app Vitest suite

## Notes
- The repo-level `just frontend-test` command currently runs `apps/web/marketing` tests, so SPA verification required running `apps/web/app` Vitest directly.
- During implementation, a locator collision in the modal Playwright page object surfaced under real execution; tightening the locator to the dialog-scoped delete button resolved it and improved E2E robustness.
- No schema migration was added; explicit child-row deletes remain in the repository adapter per the design.
