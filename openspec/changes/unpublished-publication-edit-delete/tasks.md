# Tasks: Unpublished Publication Edit/Delete

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250–380 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | auto-chain |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

## Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Full implementation (backend + frontend + tests) | PR 1 | All phases together; all artifacts verified |

## Phase 1: Backend Domain + Application

- [x] 1.1 In `server/smp/src/main/kotlin/…/publishing/domain/PublishingPolicies.kt`, add `PublicationDeletionNotAllowedException` (mirrors `PublicationCancellationNotAllowedException` pattern) and `requireDeletable(draft: PublicationDraft)` that throws for all statuses except `DRAFT`, `QUEUED`, `SCHEDULED`
- [x] 1.2 In `server/smp/src/main/kotlin/…/publishing/application/PublishingApi.kt`, add `DeletePublicationCommand(publicationId: String) : Command`
- [x] 1.3 In `server/smp/src/main/kotlin/…/publishing/application/PublishingHandlers.kt`, add `DeletePublicationHandler` — fetch by workspace+id, call `requireDeletable()`, cancel pending jobs via `replaceForPublication(empty-job)`, call `deleteById()`
- [x] 1.4 In `server/smp/src/main/kotlin/…/publishing/domain/PublishingRepositories.kt`, add `deleteById(workspaceId: String, publicationId: String)` to `PublicationRepository` interface

## Phase 2: Backend Infrastructure

- [x] 2.1 In `server/smp/src/main/kotlin/…/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`, implement `deleteById()` with sequential DELETEs: `delivery_attempts` → `publication_jobs` → `publication_asset_links` → `publications` (no migration; explicit deletes required per FK defaults)
- [x] 2.2 In `server/smp/src/main/kotlin/…/publishing/infrastructure/http/PublishingControllers.kt`, add `DELETE /{publicationId}` endpoint to `PublishingPublicationController` returning `204 No Content`; wire `DeletePublicationCommand` through `commandHandler.handle()`
- [x] 2.3 In `server/smp/src/main/kotlin/…/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt`, add exception handlers for `PublicationNotFoundException` (→ 404) and `PublicationDeletionNotAllowedException` (→ 409 with `errorCode: DELETION_NOT_ALLOWED`, `publicationId`, `currentStatus`)

## Phase 3: Frontend

- [x] 3.1 In `apps/web/app/src/stores/publishing.ts`, refactor `deletePost(id)` — keep optimistic local removal, add `DELETE /api/publishing/publications/{id}` call via `auth.apiFetch()` with `workspaceScoped: true`; on catch, call `fetchCalendar()` to re-hydrate and re-throw
- [x] 3.2 In `apps/web/app/src/stores/publishing.ts`, refactor `updatePost(id, updates)` — keep optimistic merge, call `PATCH /api/publishing/publications/{id}` via `auth.apiFetch()` with `toBackendFormat(updates)`; on success merge server response via `fromBackendFormat()`; on error restore original and re-throw
- [x] 3.3 In `apps/web/app/src/components/PostDetailModal.vue`, verify save button calls `updatePost()` and delete button calls `deletePost()` — confirm no inline mutation bypassing the store

## Phase 4: Backend Tests

- [x] 4.1 In `server/smp/src/test/kotlin/…/application/PublishingHandlersTest.kt`, add tests for `DeletePublicationHandler`: success (DRAFT/QUEUED/SCHEDULED), not-found (404), deletion-not-allowed (throws `PublicationDeletionNotAllowedException`), and workspace boundary isolation — use existing `InMemoryPublicationRepository` and `InMemoryPublicationJobRepository` test doubles
- [x] 4.2 In `server/smp/src/test/kotlin/…/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt`, add tests for `deleteById()`: cascades delete all child rows, deletes only within correct workspace, rowsUpdated = 1 on success
- [x] 4.3 In `server/smp/src/test/kotlin/…/infrastructure/http/PublishingControllersTest.kt`, add test that `DELETE /{publicationId}` dispatches `DeletePublicationCommand` using `CapturingMediator`
- [x] 4.4 In `server/smp/src/test/kotlin/…/infrastructure/http/PublishingProblemDetailsHandlerTest.kt`, add tests for `PublicationNotFoundException` → 404 and `PublicationDeletionNotAllowedException` → 409 with correct `errorCode` property

## Phase 5: Frontend Tests

- [x] 5.1 In `apps/web/app/src/stores/publishing.test.ts`, add `describe('deletePost')` block: verifies `DELETE /api/publishing/…` is called with `method: 'DELETE'`; on success publication is removed from store; on 4xx error `fetchCalendar()` is called to restore state and error is re-thrown
- [x] 5.2 In `apps/web/app/src/stores/publishing.test.ts`, add `describe('updatePost')` block: verifies `PATCH /api/publishing/…` is called with `toBackendFormat()` body; on success local publication is merged with server response; on error original state is restored and error is re-thrown

## Phase 6: E2E / Regression

- [x] 6.1 Inspect existing `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` TC-13 (`@post-delete`) — determine if it exercises backend persistence or only local-state removal; extend or add scenario that verifies a deleted post does not reappear after page refresh (authenticated flow with backend delete)
- [x] 6.2 Add E2E scenario for deleting a `DRAFT` publication and verifying it does not reappear after `page.reload()` — tag `@backend-delete @post-delete`
- [x] 6.3 Add E2E scenario for deleting a `SCHEDULED` publication from the post detail modal and verifying confirmation of removal — tag `@backend-delete @modal`
- [x] 6.4 Verify no regressions in existing edit flow: confirm `scheduler-post-interaction.spec.ts` TC-11, TC-12, TC-15 still pass; if any edit/delete interactions have existing E2E coverage, ensure the delete button is wired and the test still targets the correct element locator
- [x] 6.5 Run `just backend-check` to validate backend tests pass; run `just frontend-test` to validate frontend unit tests pass

## Implementation Order

1. Domain和政策 (Phase 1: 1.1) → defines the rule everything else gates on
2. Application commands/handlers (Phase 1: 1.2–1.4) → uses domain policy, repository contract
3. Persistence (Phase 2: 2.1) → implements repository contract with cascade
4. HTTP layer (Phase 2: 2.2–2.3) → wires controller and error handlers
5. Frontend store (Phase 3: 3.1–3.2) → depends on API contract being live
6. Frontend UI wiring (Phase 3: 3.3) → verify PostDetailModal correctness
7. Tests (Phases 4–6) → verify each layer in isolation before integration
