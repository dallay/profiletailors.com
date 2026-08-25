## Exploration: PostDetailModal Reschedule Vitest Failures

### Current State

The two reschedule tests fail deterministically in isolation and in the full suite. They are not
caused by leaked suite state: the fixture uses `scheduledAt: '2026-07-01T10:00:00Z'`, while
`confirmReschedule` rejects any `newDate <= new Date()`. On 2026-07-02 the component therefore
returns before calling `publishingStore.reschedulePublication`, displaying
`Please select a valid future date and time.` instead of exercising either mocked success or failure
path. No working-tree change exists in `PostDetailModal.vue` or `PostDetailModal.test.ts`; both
files match `HEAD` byte-for-byte. The publication-edit-assets diff changes CreatePostModal,
media/publishing stores, and backend asset semantics, but does not alter this validation path.

### Affected Areas

- `apps/web/app/src/components/PostDetailModal.test.ts` — lines 302–352 use a calendar-dependent
  date that became past; the assertions cannot reach the mocked Pinia action.
- `apps/web/app/src/components/PostDetailModal.vue` — `confirmReschedule` lines 176–193 correctly
  rejects past dates before invoking the store.
- `apps/web/app/src/stores/publishing.ts` — supplies the Pinia action seen as `wrappedAction`; not
  reached in these failures and not the cause.

### Approaches

1. **Use a deterministic future test date** — replace the stale fixture date with a date guaranteed
   to be future for the test horizon, then retain the current action/error assertions.
    - Pros: Minimal; tests the intended success and rejection branches; consistent with prior
      repository diagnosis.
    - Cons: A hard-coded date can eventually expire again unless computed relative to controlled
      time.
    - Effort: Low

2. **Freeze system time in the two tests** — use Vitest fake time/system time, then restore real
   timers after each test.
    - Pros: Fully deterministic and preserves the fixture.
    - Cons: More global-state cleanup risk; unnecessary if a relative future input is used.
    - Effort: Low

### Recommendation

Treat this as a pre-existing calendar-dependent test baseline, not a regression or full-suite
isolation leak. The minimal next action is test-only: make the reschedule input deterministic (
prefer a future date derived under controlled time), preserve the existing component behavior, and
verify the file plus full suite. Do not change production code.

### Risks

- A far-future literal merely postpones recurrence; controlled time or a relative future date is
  more durable.
- Pinia action spying remains a secondary test fragility (`wrappedAction`), but current evidence
  proves validation returns before that boundary, so changing mock strategy is not justified for
  this failure.

### Ready for Proposal

Yes — the orchestrator should report that phases 0–3 confirmed a deterministic pre-existing
test-data expiration issue, with no evidence of leaked global state or causation by
`2026-07-02-publication-edit-assets-fix`.

---

## Previous Exploration: Publication Edit — Assets Not Shown & Silently Cleared on Save

### Current State

The publication editor is implemented as a single dual-purpose modal, `CreatePostModal.vue`, that
handles both create and edit flows. Switching between the two is driven by the `editingPublication`
prop. The Pinia `publishing` store owns the publication list and persists them via `apiFetch` to the
backend; the Pinia `media` store owns assets and the *selection* of asset IDs that the composer is
currently using.

There is **no separate edit page or view** — editing happens by clicking an existing publication in
the calendar (via `PostDetailModal`) → `handleEditPublication` in `SchedulerView.vue` opens
`CreatePostModal` in edit mode with the publication object passed in.

### Affected Areas

- `apps/web/app/src/components/CreatePostModal.vue`
    - **L125–155** `initEditMode` — populates the form for edit, including the broken
      `addToSelection` loop.
    - **L136–141** — the bug for Part 1 (assets not shown).
    - **L608–642** `handleSchedule` — dispatcher.
    - **L644–661** `handleEditSubmit` — sends `assetIds: [...mediaStore.selectedAssetIds]`
      unconditionally → bug for Part 2.
    - **L663–684** `handleCreateSubmit` — for comparison, also reads from
      `mediaStore.selectedAssetIds`.

- `apps/web/app/src/stores/publishing.ts`
    - **L870–916** `updatePost(id, updates)` — serializes the PATCH body. Includes the fallback
      chain at L885–888 that *does not protect* against an empty array.

- `apps/web/app/src/stores/media.ts`
    - **L75** `assetsById` map of assetId → MediaAssetSummary.
    - **L78** `assetIds` (library list).
    - **L84** `selectedAssetIds` — used as the source of truth for what gets sent.
    - **L94–98** `selectedAssets` computed — joins selection against `assetsById`; returns `[]` for
      IDs that are not in `assetsById`.
    - **L316–328** `addToSelection` / `removeFromSelection` / `clearSelection` — manage the ID list
      only; they do **not** fetch or hydrate asset data.

- `apps/web/app/src/views/SchedulerView.vue`
    - **L432–437** `handleEditPublication` — sets `editingPublication` and opens the modal.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt`
- **L176–192** `editPublication` PATCH endpoint — accepts `PublicationUpsertRequest`, passes
`assetIds` straight into the command.
- **L327–341** `PublicationUpsertRequest` DTO — `assetIds: List<String> = emptyList()`; no
nullable / sentinel to distinguish "absent" from "explicitly empty".

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt`
    - **L432–554** `EditPublicationHandler.handle` — at **L468–476** does
      `current.copy(assetIds = command.assetIds, …)`. **There is no null/empty distinction**: an
      empty list overwrites existing assets. This is the contract the frontend has to live with.

### Approaches (for the eventual fix — not yet chosen)

1. **Frontend-only fix, hydrate IDs into `assetsById` on edit init** (smallest blast radius)
    - In `initEditMode`, after `addToSelection`, fetch the missing asset summaries via
      `mediaStore.getAsset(id)` (already exposed) and call `upsertAsset` so the `selectedAssets`
      computed can resolve them.
    - In `handleEditSubmit`, change `assetIds: [...mediaStore.selectedAssetIds]` so that an
      unchanged selection (matches `editingPublication.assetIds`) sends **no `assetIds` field** at
      all (use a deleted `updates` key), letting the backend PATCH skip the field.
    - Trade-off: touches only `CreatePostModal.vue`; does NOT need any backend change; preserves the
      create flow untouched.

2. **Make `assetIds` truly optional on the backend PATCH**
    - Change `PublicationUpsertRequest.assetIds` to `List<String>? = null` and skip the field on the
      handler when null.
    - Frontend change is the same as option 1 (omit the field when unchanged) plus a default-null in
      the DTO.
    - Trade-off: cross-cutting; requires contract change and tests on the controller + handler.

3. **Distinguish "user cleared" vs "user didn't touch" in the form via a dirty flag**
    - Track `assetsTouched` in the composer; only send `assetIds` when the user actually interacts
      with the media picker.
    - Most correct semantically; biggest test surface.

### Recommendation

**Option 1** — pure frontend, scoped to `CreatePostModal.vue`. It fixes both halves of the bug (no
display on open, no silent clear on save) without changing the backend contract, without breaking
the create flow, and without touching the grouped PRs #224 / #225. The backend already has the right
semantics for option 1: if we simply omit the field from the JSON body, Jackson will deserialize
`assetIds` as its default `emptyList()`, but the handler currently overwrites anyway — so we must
combine it with the frontend "send the previous IDs when the user didn't touch them" rule.

Wait — re-reading the handler at L468–476: it ALWAYS applies
`current.copy(assetIds = command.assetIds, …)`. There is no way to skip that field today. So Option
1's "send no field" trick does **not** work; we must either:

- (1a) Always send the *current* `editingPublication.assetIds` from the frontend when the user
  didn't touch the picker, OR
- (1b) Push for a backend change (Option 2) so the field is truly optional.

Recommendation: **1a**, because it keeps the change scoped to the frontend and is the smallest
possible diff. The downside — a stale ID sent from the frontend — is exactly what the user expects
when they didn't touch the assets. The risk of staleness is the same as today for create (IDs come
from the same media library).

### Risks

- **Single source of truth for selection**: the `mediaStore.selectedAssetIds` is global, and
  `clearSelection()` is called in both `initEditMode` and `initCreateMode`. Any future code that
  depends on selection persisting across modal opens will break. This is pre-existing, but worth
  flagging.
- **Race with `loadDanglingAssets()`**: `initializeComposerForOpen` (L189–195) calls
  `mediaStore.loadDanglingAssets()` AFTER `initEditMode`. If hydration is added inside
  `initEditMode` for missing IDs, a parallel `loadAssets` could overwrite `assetsById` mid-fetch.
  Need to await the hydration BEFORE `loadDanglingAssets` runs, or fetch by ID only.
- **Backward compatibility**: the existing backend `EditPublicationCommand` does NOT distinguish
  empty vs absent. Any fix on the frontend MUST send the existing assetIds on every save unless the
  user explicitly removed them, or the bug recurs.
- **Grouped PR coupling (#224, #225)**: changes here must not break the upcoming edit hardening
  work. Recommended scope: stay inside `CreatePostModal.vue`; do not modify the publishing store or
  the backend.
- **No covering tests for `initEditMode` or `handleEditSubmit`** — the regression is silent and
  would re-emerge easily. Any fix MUST come with at least one Vitest spec asserting (a) assets are
  visible after `initEditMode`, (b) saving without touching the picker preserves existing assets.
- **Asset might already be deleted on the backend**: if a user edits a publication whose asset was
  GC'd, hydration via `mediaStore.getAsset(id)` will throw. Need a graceful fallback (e.g.,
  skip-and-warn, drop the ID from selection).

### Ready for Proposal

Yes. The orchestrator should:

1. Tell the user this is a frontend-only fix.
2. Ask the user to confirm Option 1a (always resend current `editingPublication.assetIds` when the
   user didn't modify the selection) vs. escalating to a backend contract change (Option 2).
3. Once approved, propose the change with explicit regression tests covering both bug halves.