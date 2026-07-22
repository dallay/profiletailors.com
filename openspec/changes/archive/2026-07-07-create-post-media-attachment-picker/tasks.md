# Tasks: Create Post Media Attachment Picker

## Review Workload Forecast

| Field                   | Value                                                                                      |
|-------------------------|--------------------------------------------------------------------------------------------|
| Estimated changed lines | 450-700                                                                                    |
| 400-line budget risk    | High                                                                                       |
| Chained PRs recommended | Yes                                                                                        |
| Suggested split         | PR 1 library slice → PR 2 upload reconciliation → PR 3 Unsplash + capabilities/regressions |
| Delivery strategy       | chained PRs                                                                                |
| Chain strategy          | approved-recommended                                                                       |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: approved-recommended
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                      | Likely PR | Notes                                                                        |
|------|-----------------------------------------------------------|-----------|------------------------------------------------------------------------------|
| 1    | Picker foundation + existing Library vertical slice       | PR 1      | functional slice: open, load workspace assets, stage/apply/cancel/reopen     |
| 2    | Upload reconciliation inside active picker session        | PR 2      | depends on PR 1; bounded polling, auto-stage once, manual deselect respected |
| 3    | Unsplash integration + channel capabilities + regressions | PR 3      | depends on PR 2; feature-flagged provider and strictest-limit enforcement    |

## Preconditions Before Apply

- [ ] Verify the real source of channel `maxAttachments` and record which path applies:
    - frontend metadata already exists;
    - frontend mapping must be extended from an existing backend contract;
    - temporary frontend capability registry is required.
- [ ] Keep Unsplash import orchestration in `CreatePostModal.vue`; `MediaProviderPanel.vue` remains
  provider-specific presentation only.

## Phase 1: Foundation + Existing Library Vertical Slice

- [x] 1.1 RED: Add failing tests in
  `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` for typed events,
  conditional provider tab rendering, library collection states (`LOADING`, `READY`, `EMPTY`,
  `ERROR`), asset states (`READY`, `PROCESSING`, `FAILED`), and READY-without-preview fallback.
- [x] 1.2 RED: Extend `apps/web/app/src/components/CreatePostModal.test.ts` for compact attachment
  rail, `Add Media` entry, real workspace asset loading on picker open, empty/error rendering,
  reopen-from-draft, cancel-discard, and apply-replace semantics.
- [x] 1.3 GREEN: Create `apps/web/app/src/components/composer/composer-media-picker.types.ts` and
  `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` with parent-owned
  props/events, staged grid UI, apply/cancel actions, collection-state rendering, and disabled
  non-ready assets.
- [x] 1.4 GREEN: Modify `apps/web/app/src/components/CreatePostModal.vue` to replace the dropzone
  with the compact rail, open the picker, load/consume existing workspace assets through the current
  media-library contract, map them to picker view models, and own `draftAttachmentIds`/
  `pickerSelectionIds` locally.
- [x] 1.5 REFACTOR: Extract local modal helpers for picker session lifecycle and asset-view-model
  mapping without introducing a global picker store.

## Phase 2: Upload Reconciliation in the Active Picker Session

- [x] 2.1 RED: Add failing tests in `apps/web/app/src/components/CreatePostModal.test.ts` for upload
  action orchestration, same-session persisted asset appearance, bounded polling/cancellation,
  auto-stage once when the asset becomes selectable, and manual deselection remaining respected
  after later refreshes.
- [x] 2.2 GREEN: Modify `apps/web/app/src/stores/media.ts` and `apps/web/app/src/lib/media-api.ts`
  to add targeted asset reconciliation primitives (`upsert`, `load`, per-asset polling/refresh
  hooks) needed by the active picker session.
- [x] 2.3 GREEN: Modify `apps/web/app/src/components/CreatePostModal.vue` to orchestrate Upload as
  an action, start bounded per-asset polling for newly created non-selectable persisted assets, stop
  polling on close/unmount/timeout/failure, and auto-stage each asset at most once when it becomes
  selectable.
- [x] 2.4 REFACTOR: Unify upload reconciliation and placeholder mapping so active-session assets
  share one path for appearance, polling termination, timeout handling, and one-time auto-stage
  bookkeeping.

## Phase 3: Unsplash Integration + Capability Resolution + Regressions

- [x] 3.1 RED: Add failing tests in `apps/web/app/src/components/CreatePostModal.test.ts` for
  parent-owned Unsplash feature flag visibility, provider search/import keeping the picker open,
  persisted asset reconciliation after import, and strictest-channel attachment limit enforcement
  including invalid-after-channel-change without auto-removal.
- [x] 3.2 GREEN: Create `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue`
  as provider-specific presentation, then wire `CreatePostModal.vue` to own Unsplash search/import
  orchestration and same-session persisted asset reconciliation behind the parent-controlled feature
  flag.
- [x] 3.3 GREEN: Implement channel capability resolution for `maxAttachments` using the verified
  precondition path, then enforce
  `effectiveAttachmentLimit = min(selectedChannels[].maxAttachments)` in `CreatePostModal.vue`,
  blocking apply/publish/schedule above the limit while preserving attachments and surfacing
  resolution guidance after channel changes.
- [x] 3.4 REFACTOR: Tighten regression coverage across `CreatePostModal.test.ts` and
  `ComposerMediaPickerShell.test.ts` for cancel/apply/reopen, preview fallback, provider-open
  continuity, publish/schedule blocking, and collection-state rendering.
