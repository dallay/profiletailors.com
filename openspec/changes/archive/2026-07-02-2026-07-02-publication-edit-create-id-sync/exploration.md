## Exploration: publication edit create-id sync

### Current State

Authenticated publication creation in the app store still generates a local client ID (
`pub-${Date.now()}`) and keeps that placeholder publication even after the backend successfully
creates the real publication. `schedulePost()` and `quickCreatePost()` do not reconcile the
optimistic local record with the backend `PublicationResult`, and `syncPublicationWithApi()` ignores
the server response entirely. Later edit flows PATCH by the visible publication ID from the local
store, so the backend correctly returns `404 Publication not found` for IDs that were never
persisted. This means #225's 404 contract is working, while the client is still operating on
synthetic IDs. The edit composer's invalid/past custom schedule state is part of the same drift:
`initEditMode()` derives `custom` scheduling from the local publication model, but freshly created
local placeholders do not preserve backend-normalized scheduling fields, so queued/next-slot
publications can open with a misleading custom date/time that is already in the past.

### Affected Areas

- `apps/web/app/src/stores/publishing.ts` — root cause: authenticated create/quick-create keep local
  placeholder IDs and ignore backend mutation payloads.
- `apps/web/app/src/components/CreatePostModal.vue` — edit-mode schedule prefill trusts the
  publication model coming from the store, so stale placeholder schedule data opens as invalid
  custom state.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` —
  confirms PATCH is update-only and returns not found when the active-workspace row is missing.
-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` —
PATCH boundary passes through update-only edit semantics.

- `openspec/changes/archive/2026-07-01-publication-edit-not-found-contract/proposal.md` — shows #225
  intended 404 behavior, which matches the observed failure.
- `openspec/changes/archive/2026-07-02-2026-07-02-publication-edit-assets-fix/verify-report.md` —
  confirms #223 and #224/#225 were verified, so the new browser issue is residual client drift after
  those fixes.

### Approaches

1. **Frontend create-result reconciliation** — replace authenticated placeholder publications with
   the backend mutation result immediately after create/quick-create and preserve backend-normalized
   schedule fields.
    - Pros: Fixes the PATCH 404 at the actual source, aligns edit/detail/calendar state with
      persisted IDs, and likely fixes the invalid edit schedule prefill in the same change.
    - Cons: Requires touching optimistic create flows and related tests/E2E fixtures.
    - Effort: Medium

2. **Backend compatibility fallback for unknown PATCH IDs** — attempt to infer or recreate the
   publication on edit when the ID is not found.
    - Pros: Could mask the symptom without changing optimistic UI flows.
    - Cons: Wrong contract, dangerous for data integrity, conflicts with the already-established
      update-only semantics from #225, and would leave schedule drift unresolved.
    - Effort: High

3. **Frontend-only schedule prefill patch without ID reconciliation** — special-case edit modal
   initialization to coerce queued placeholder posts into a safer schedule mode.
    - Pros: Smaller UI change if schedule UX were the only bug.
    - Cons: Does not solve the 404 root cause, so it cannot close the grouped follow-up cleanly.
    - Effort: Low

### Recommendation

Propose a grouped follow-up change centered on **frontend reconciliation of authenticated
publication creation with backend mutation results**. Recommended change name:
`2026-07-02-publication-edit-create-id-sync`. This is the clean fix because the browser failure is a
residual frontend bug/regression after #223/#224/#225, not evidence those backend fixes are still
open. The PR should make authenticated create/quick-create/store refresh paths adopt the real
backend `publicationId`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, `status`, and
`socialAccountId`, then add regression coverage proving a freshly created post can be opened and
edited successfully without falling into invalid custom scheduling.

### Risks

- Optimistic-create behavior may currently be relied on by tests/E2E fixtures that assume local
  `pub-${Date.now()}` IDs.
- Reconciling backend-normalized schedule fields could expose other places in the scheduler that
  assume every queued post behaves like a custom scheduled post.

### Ready for Proposal

Yes — tell the user the backend not-found contract is behaving correctly, but authenticated create
flows still keep synthetic client IDs and stale schedule metadata, so the follow-up PR should target
create/edit state reconciliation rather than reopening #223/#224/#225 as-is.
