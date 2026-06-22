# Exploration: Unpublished Post Edit/Delete Backend Flow

**Change:** `unpublished-publication-edit-delete`
**Explored:** GitHub issue #131 — fixing frontend-only delete and tightening edit guard for published posts
**Date:** 2026-06-22

---

## Current State

### Backend — Publication lifecycle (solid)

The backend already has a complete lifecycle model with explicit status-based guard functions in `PublicationLifecyclePolicy`:

| Function | Allowed statuses | Throws |
|---|---|---|
| `requireEditable()` | `DRAFT`, `QUEUED`, `SCHEDULED` | `PublicationEditNotAllowedException` |
| `requireCancellable()` | `DRAFT`, `QUEUED`, `SCHEDULED` | `PublicationCancellationNotAllowedException` |
| `requireRetryable()` | `FAILED` only | `PublicationRetryNotAllowedException` |

These are wired into the existing handlers:
- `EditPublicationHandler` → calls `requireEditable()` at line 473
- `ReschedulePublicationHandler` → calls `requireEditable()` at line 677
- `CancelPublicationHandler` → calls `requireCancellable()` via `PublicationLifecyclePolicy.cancel()`

**Backend HTTP layer** (`PublishingPublicationController`):
- `PATCH /api/publishing/publications/{publicationId}` → `editPublication()` — exists, protected by `requireEditable()`
- `POST /api/publishing/publications/{publicationId}/cancel` → `cancelPublication()` — exists
- `PATCH /api/publishing/publications/{publicationId}/reschedule` → `patchReschedulePublication()` — exists
- `DELETE /api/publishing/publications/{publicationId}` — **MISSING**

**Backend repository** (`R2dbcPublicationRepository`):
- `markCancelled()` exists (soft-transition to `CANCELLED` status)
- `updateEditableDraft()` exists (delete+insert via `insertOrUpdate()`)
- `findByWorkspaceAndId()` exists
- `delete()` — **MISSING** (no `DELETE FROM publications` SQL anywhere)

**Backend application layer** (`PublishingApi.kt`):
- `EditPublicationCommand` — exists
- `CancelPublicationCommand` — exists
- `DeletePublicationCommand` — **MISSING**

### Frontend — Delete only (broken)

**`usePublishingStore.deletePost(id)`** (`publishing.ts:757`):
```typescript
function deletePost(id: string) {
  const url = objectUrls.get(id)
  if (url) { URL.revokeObjectURL(url); objectUrls.delete(id) }
  publications.value = publications.value.filter((p) => p.id !== id)
  saveToStorage()
}
```
- Mutates only local Pinia state and `localStorage`
- Makes **zero API calls** to the backend
- Called from three places in `SchedulerView.vue` (calendar day, day view, list mode) and one place in `PostDetailModal.vue`
- All call sites are guarded by `v-if="pub.status !== 'PUBLISHED'"` in the UI — but this is a local-only check

**`updatePost(id, updates)`** (`publishing.ts:776`):
- Pure local mutation — no API call
- Not currently wired to any UI save button

---

## Affected Areas

### Backend — New files/changes

| File | Change | Layer |
|---|---|---|
| `server/smp/.../publishing/domain/PublishingRepositories.kt` | Add `deleteById(workspaceId, publicationId)` to `PublicationRepository` interface | Domain |
| `server/smp/.../publishing/domain/PublishingPolicies.kt` | Add `requireDeletable()` policy function | Domain |
| `server/smp/.../publishing/domain/PublishingPolicies.kt` | Add `PublicationDeletionNotAllowedException` | Domain |
| `server/smp/.../publishing/application/PublishingApi.kt` | Add `DeletePublicationCommand` | Application |
| `server/smp/.../publishing/application/PublishingHandlers.kt` | Add `DeletePublicationHandler` | Application |
| `server/smp/.../publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | Implement `deleteById()` — `DELETE FROM publications WHERE workspace_id= AND id=` | Infrastructure |
| `server/smp/.../publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | Cascade delete: `publication_asset_links` and `publication_jobs` (or handle via FK/ON DELETE) | Infrastructure |
| `server/smp/.../publishing/infrastructure/http/PublishingControllers.kt` | Add `DELETE /api/publishing/publications/{publicationId}` endpoint | Infrastructure |
| `server/smp/.../publishing/infrastructure/http/PublishingControllersTest.kt` | Add controller tests for delete endpoint | Tests |
| `server/smp/.../publishing/application/PublishingHandlersTest.kt` | Add handler tests: success, not-found, deletion-not-allowed | Tests |
| `server/smp/.../publishing/domain/PublicationLifecyclePolicyTest.kt` | Add `requireDeletable` tests | Tests |

### Frontend — New/modified files

| File | Change |
|---|---|
| `apps/web/app/src/stores/publishing.ts` | Wire `deletePost(id)` → `DELETE /api/publishing/publications/${id}` when authenticated; fallback to local-only on error |
| `apps/web/app/src/stores/publishing.test.ts` | Add test: delete calls API, removes from local state on success, rolls back on error |

### Existing files that must be verified/updated

| File | Notes |
|---|---|
| `server/smp/.../publishing/domain/PublishingModels.kt` | `PublicationDraft` — used as-is; no model change needed |
| `server/smp/.../publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | `R2dbcPublicationJobRepository.cancel()` bug — passes `publicationId` as `jobId` param (line 711). When adding job deletion, fix this bug too. |
| `server/smp/src/main/resources/db/migration/` | Check if FK cascades are defined; if not, ensure asset links and jobs are deleted explicitly |
| `apps/web/app/src/stores/publishing.test.ts` | No delete tests currently exist — add |

---

## Domain Rules (inferred from code, with uncertainty noted)

**Confirmed** from `PublicationLifecyclePolicy`:
- Editable statuses: `DRAFT`, `QUEUED`, `SCHEDULED`
- Deletable: **should** be `DRAFT`, `QUEUED`, `SCHEDULED` (same set as cancellable — unpublished only)
- After `PROCESSING` starts: cannot edit, cannot delete (already in-flight)
- After `PUBLISHED`: cannot edit, cannot delete (already on the social network)
- After `CANCELLED`, `FAILED`, `BLOCKED`: business decision needed — likely NOT deletable since they are terminal records for audit purposes, OR they could be deletable since they never made it to the social network. **This needs explicit clarification from product.** The current `requireCancellable()` blocks FAILED/CANCELLED from cancellation but the delete path is new.

**Uncertainty:** Should `BLOCKED` posts be deletable? The BLOCKED state is recoverable (auto-retry when account restores). If a user wants to abandon a BLOCKED post, they can already cancel it — but cancel transitions to `CANCELLED` which is terminal. Deleting BLOCKED might be preferable to cancelling. **Recommend**: treat BLOCKED as deletable alongside DRAFT/QUEUED/SCHEDULED.

---

## Approaches

### 1. **Add delete endpoint with `requireDeletable()` policy (Recommended)**

Add `DELETE /api/publishing/publications/{id}` backed by a new `DeletePublicationHandler`. Policy gate uses same guard set as cancellable (DRAFT, QUEUED, SCHEDULED).

- **Pros**: Full backend enforcement, consistent with existing lifecycle pattern, audit trail via jobs/attempts, proper workspace scoping
- **Cons**: Requires new repository method, cascade delete logic, new test suite
- **Effort**: Medium

### 2. **Reuse cancel endpoint for "delete"**

The `CancelPublicationHandler` already transitions to `CANCELLED` and cancels the job. Frontend could call cancel instead of delete for unpublished posts.

- **Pros**: No new endpoint needed, existing infrastructure handles it
- **Cons**: Cancelled posts persist in DB forever (audit/analytics); users who think they're "deleting" see "Cancelled" status; frontend status badge shows "CANCELLED" not removal; UX mismatch
- **Effort**: Low — but wrong UX

### 3. **Frontend-only delete, no backend change**

Keep the current local-only behavior.

- **Pros**: Zero backend work
- **Cons**: Deletes reappear on next `fetchCalendar()` refresh; cross-session deletes are impossible; ghost posts accumulate; security risk (any user can delete any post client-side)
- **Effort**: None
- **Verdict**: Not acceptable — this is the bug being fixed

---

## Recommendation

**Approach 1** — Add a proper `DELETE /api/publishing/publications/{id}` endpoint with a `requireDeletable()` policy gate, implemented consistently with the existing `CancelPublicationHandler` pattern.

The `CancelPublicationHandler` and `DeletePublicationHandler` will be structurally similar: fetch by workspace+id, check policy, persist transition, cancel job. The key difference is that delete does a hard row deletion rather than a soft status transition to CANCELLED.

### Scope for this change:
1. **Backend**: New command + handler + repository method + HTTP endpoint + policy guard
2. **Frontend**: Wire existing `deletePost()` to call the new endpoint (with local fallback)
3. **Existing `EditPublicationHandler`**: Already protected by `requireEditable()` — no change needed; just verify the guard covers all cases per confirmed business rule

---

## Risks

1. **Cascade delete complexity**: Deleting a publication must handle `publication_jobs` (via `publicationId` FK) and `publication_asset_links` (via `publicationId` FK). If FK cascades aren't configured in the DB schema, all three tables need explicit DELETE statements. Need to inspect the migration/schema to confirm.
2. **Job cancellation race**: When a delete is requested for a QUEUED publication, the job might be claimed by the worker concurrently. The job cancellation should happen in the same transaction or be idempotent.
3. **Existing job cancel bug**: `R2dbcPublicationJobRepository.cancel()` at line 711 passes `publicationId` as the `jobId` parameter — `WHERE publication_id = :publicationId` uses the wrong column. This is a pre-existing bug that the delete flow should NOT replicate; the delete handler should correctly cancel the job by `publicationId`.
4. **Frontend offline/delete race**: `deletePost()` will call the API but also immediately removes from local state. If the API call fails, the user sees the post vanish from their calendar then reappear on the next refresh — acceptable fallback behavior.
5. **Deletable status for BLOCKED/FAILED**: Requires explicit product decision. If BLOCKED/FAILED are NOT deletable (audit records), the policy should explicitly reject them.
6. **Test coverage gap**: `PublicationLifecyclePolicy.requireEditable()` has no covering unit tests (⚠️ flagged by codegraph). Adding `requireDeletable()` should include a new test file or section in `PublicationLifecyclePolicyTest.kt`.

---

## Ready for Proposal

**Yes** — the investigation is complete enough to write a proposal. The business rule (unpublished only = DRAFT, QUEUED, SCHEDULED) is confirmed from code. The key uncertainties are:
- Cascade delete schema details (need to inspect DB migration files)
- Whether BLOCKED/FAILED/CANCELLED should be deletable (product decision)
- Whether to add a `requireDeletable()` method to the policy or handle the check inline in the handler (consistent pattern favors adding it to the policy)
