# Design: Unpublished Publication Edit/Delete

## Technical Approach

Implement unpublished delete as a first-class backend command handled through the existing publishing CQRS flow, then wire scheduler edit/delete UI to backend truth instead of local-only store mutations. Edit continues to use `PATCH /api/publishing/publications/{publicationId}`; delete adds `DELETE /api/publishing/publications/{publicationId}`. This follows the delta spec status matrix and existing `PublicationLifecyclePolicy` authority.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Delete backend shape | Add `DeletePublicationCommand` + `DeletePublicationHandler` + controller `DELETE` endpoint returning existing `PublicationResult` | Reuse cancel endpoint; hard-delete inside controller | Keeps ADR-0004 mediator pattern, preserves explicit delete semantics, and reuses existing auth/workspace lookup/result mapping. |
| Status gating | Reuse `PublicationLifecyclePolicy` with a new delete guard aligned to editable states (`DRAFT`,`QUEUED`,`SCHEDULED`) | Frontend-only gating; duplicate conditional logic in handlers/repositories | Backend remains source of truth while frontend mirrors the same matrix for UX. One policy reduces drift across edit/cancel/delete. |
| Frontend edit UX | Extend `PostDetailModal.vue` with in-modal editable fields for title/body/schedule and store-backed save | New composer route; list-card inline editing | Smallest change that matches current scheduler entry point and avoids composer redesign. |
| SQL hardening | Keep SQL-level status enforcement out of scope; document as follow-up | Refactor `updateEditableDraft()` and delete path to conditional SQL in this change | Current repository uses delete+insert semantics. Adding DB-level guards now risks expanding into a persistence redesign without clear product value for this fix. |

## Data Flow

```text
Scheduler card / PostDetailModal
        │
        ├── Edit → Pinia store.updatePost(...) → PATCH /publications/{id}
        │                                   │
        │                                   └→ EditPublicationHandler
        │                                       → requireEditable
        │                                       → update publication
        │                                       → replace job
        │
        └── Delete → Pinia store.deletePost(...) → DELETE /publications/{id}
                                            │
                                            └→ DeletePublicationHandler
                                                → require deletable
                                                → delete publication row + asset links
                                                → cancel/remove related unclaimed job state
```

Sequence note: controller → mediator → handler → repository/job repository mirrors existing create/edit/cancel flows.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` | Modify | Add delete command contract. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` | Modify | Add delete handler; keep edit flow authoritative for scheduler updates. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt` | Modify | Add shared delete/status guard or reusable pre-delivery guard. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt` | Modify | Add publication delete port. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` | Modify | Expose `DELETE /api/publishing/publications/{publicationId}`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | Modify | Delete publication plus `publication_asset_links`; coordinate job cleanup with existing job repository. |
| `apps/web/app/src/stores/publishing.ts` | Modify | Replace local-only `deletePost()`/`updatePost()` with API-backed actions, rollback, and response mapping. |
| `apps/web/app/src/components/PostDetailModal.vue` | Modify | Add edit UI and gate edit/delete to unpublished statuses only. |
| `apps/web/app/src/views/SchedulerView.vue` | Modify | Route every delete entry point through the integrated store action; keep modal as edit entry point. |
| `server/smp/src/test/.../PublishingHandlersTest.kt` | Modify | Cover delete allow/reject/auth cases and edit job replacement. |
| `server/smp/src/test/.../PublishingControllersTest.kt` | Modify | Assert DELETE dispatch and existing PATCH mapping. |
| `server/smp/src/test/.../R2dbcPublishingRepositoriesUnitTest.kt` | Modify | Verify delete removes row/linkage and leaves no schedulable job. |
| `apps/web/app/src/stores/publishing.test.ts` | Modify | Cover backend-backed edit/delete success, rollback, and error states. |
| `apps/web/app/src/components/PostDetailModal.test.ts` | Modify | Cover edit affordance, delete gating, and PATCH/DELETE errors. |
| `apps/web/app/e2e/specs/scheduler-post-interaction.spec.ts` | Modify | Replace local delete assumptions with API-backed edit/delete scenarios. |

## Interfaces / Contracts

```kotlin
data class DeletePublicationCommand(val publicationId: String) : CommandWithResult<PublicationResult>

interface PublicationRepository {
    suspend fun deleteUnpublished(workspaceId: String, publicationId: String)
}
```

Frontend store contract keeps `deletePost(id)` and `updatePost(id, updates)` names, but they become async API-backed actions that update local state from server responses.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Delete/edit allowed statuses, auth gate, job replacement/removal | Extend handler tests with in-memory repos. |
| Integration | Repository delete cleanup and controller command dispatch | Extend R2DBC repository and controller tests. |
| E2E | Scheduler modal/card actions obey status gating and persist through refresh | Update Playwright scheduler interaction flows with mocked API truth. |

## Migration / Rollout

No migration required for the scoped fix. Optional SQL-level hardening remains follow-up work, not part of rollout.

## Open Questions

- [x] Should delete physically remove `publication_jobs` rows or mark them cancelled once delete succeeds for unclaimed jobs?
- [x] If PATCH succeeds with server-normalized fields, should the modal stay open with refreshed values or close on save?

## Resolved Questions

| Question | Decision | Rationale |
|---|---|---|
| Should delete physically remove `publication_jobs` rows or mark them cancelled for unclaimed jobs? | Physical deletion of unclaimed (PENDING or RETRY_WAITING) `publication_jobs` rows was chosen. | Since the publication itself is hard-deleted, leaving orphaned job rows would cause confusion in scheduling queries. Cancellation only makes sense when the publication record survives. Physical deletion keeps the job table clean and avoids stale orphaned rows. |
| If PATCH succeeds with server-normalized fields, should the modal stay open with refreshed values or close on save? | Modal closes on save success (same as current behavior). | The backend response already updates the store with normalized values. Keeping the modal open after save adds unnecessary complexity and would require additional UX work for stale-vs-fresh indication. Closing on save is the expected user pattern (edit → save → done). |
