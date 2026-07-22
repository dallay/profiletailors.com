# Design: Create Post Media Attachment Picker

## Overview

This design consolidates the Create Post media flow around a parent-owned picker
session in `CreatePostModal.vue`, with a compact rail, single `Add Media` trigger,
and replace-set semantics. The `ComposerMediaPickerShell` stays presentation-only
(no fetch/persist), `useMediaStore()` keeps library asset cache/upload state, and
`draftAttachmentIds` is the only state committed into publication `assetIds` on
apply/submit. The design covers four domains: `composer-media-picker`,
`media-library`, `media-provider-unsplash`, and `publishing`.

## Changes

### Architecture Decisions

#### Decision: Keep picker session state local to `CreatePostModal`

**Choice**: Add modal-local `draftAttachmentIds`, `pickerSelectionIds`, picker
open/source/status flags, and derived `effectiveAttachmentLimit`; keep
`mediaStore.assetsById/assetIds/uploads` as shared library data only.

**Alternatives considered**: Reuse `mediaStore.selectedAssetIds`; add a new
global picker store.

**Rationale**: The current store selection is global draft state today. Reusing
it would leak staged changes across cancel/dismiss. A new store violates the
constraint and is unnecessary because the picker is scoped to one modal.

#### Decision: Apply replace-set semantics, never incremental commit

**Choice**: Opening copies `draftAttachmentIds → pickerSelectionIds`;
select/deselect mutates staged IDs only; cancel discards; apply replaces
`draftAttachmentIds` exactly.

**Alternatives considered**: Live-binding picker clicks to draft; merge-on-apply.

**Rationale**: This directly satisfies the picker lifecycle requirements and
preserves predictable undo-by-dismiss behavior.

#### Decision: Reconcile uploads/imports by upsert + targeted readiness polling

**Choice**: Reuse CAS `createAndUpload()` and add store helpers for
`upsertAsset`, `loadAsset`, and optional targeted refresh/poll for returned
asset IDs. Auto-stage once the uploaded/imported asset resolves to a selectable
persisted asset.

**Alternatives considered**: Full library reload after every action; optimistic
staging before persistence.

**Rationale**: Targeted reconciliation is cheaper than full reloads and respects
CAS reality: `PENDING_UPLOAD`/`UPLOADING` assets are visible but not selectable.

#### Decision: Preserve shell boundaries, move upload orchestration to modal

**Choice**: `ComposerMediaPickerShell` stays presentation/event-only;
`CreatePostModal` owns upload action, Unsplash import, capability checks, and
apply logic.

**Alternatives considered**: Making the shell fetch/upload directly; adding
Upload as a first-class browsable tab.

**Rationale**: This preserves the existing shell architecture and keeps Upload
as an action, not a fake provider.

## Usage

### Data Flow

```text
CreatePostModal
  draftAttachmentIds ──open──> pickerSelectionIds
         │                          │
         │                    select/deselect
         │                          ▼
         │                 ComposerMediaPickerShell
         │                    │            │
         │               Library grid   Upload/Unsplash actions
         │                    │            │
         └────apply<──────────┴──────CreatePostModal──────> mediaStore/assets API
                                      │                         │
                                      │                    CAS / import
                                      ▼                         ▼
                               draftAttachmentIds        assetsById/uploads
                                      │
                                      ▼
                              effectiveAttachmentLimit
                                      │
                                      ▼
                              schedulePost / updatePost
```

### Key Symbols

- `CreatePostModal.vue` — parent-owned coordinator; owns draftAttachmentIds,
  pickerSelectionIds, effectiveAttachmentLimit, upload action, and apply logic.
- `ComposerMediaPickerShell.vue` — presentation/event-only shell; emits typed
  events, never fetches/persists.
- `mediaStore` (Pinia) — library asset cache, upload state, and reconciliation
  primitives (`upsertAsset`, `loadAsset`, `createAndUpload`).
- `publishingStore` (Pinia) — owns channel shape with per-provider
  `maxAttachments` and `resolveChannelMaxAttachments(provider)`.

### Channel-aware limit policy

- `effectiveAttachmentLimit = min(activeChannels[].maxAttachments)`.
- Channel change below limit preserves attachments, surfaces invalid state,
  and blocks publish/schedule.
- `effectiveAttachmentLimit` is derived in the modal from the publishing store's
  per-channel limits — no separate backend/temp registry is required.

## Troubleshooting

### Symptom: Picker opens but stays empty after upload

- Verify `mediaStore.createAndUpload()` returns a persisted asset ID and the
  modal calls `mediaStore.upsertAsset()` with the result.
- Confirm the asset status is `READY` (or another selectable status) before
  auto-staging.

### Symptom: Picker closes immediately after import

- Imports MUST keep the picker open. The shell does NOT emit `close` after
  `provider-import`. If the picker closes, check that the parent does not call
  `closeMediaPicker()` from the import handler.

### Symptom: `effectiveAttachmentLimit` does not match per-channel limits

- Verify `publishingStore.channels` includes `maxAttachments` for every active
  channel.
- Confirm the modal derives the limit as `min(...)` across all active channels,
  not just the first one.

### Symptom: Polling never resolves a `PENDING_UPLOAD` asset

- The modal uses bounded polling (max 5 attempts × 2s) before giving up. The
  asset remains visible but not selectable; the modal surfaces an `ERROR`
  collection state.

## References

- Specs:
    - `openspec/specs/composer-media-picker/spec.md`
    - `openspec/specs/media-library/spec.md`
    - `openspec/specs/media-provider-unsplash/spec.md`
    - `openspec/specs/publishing/spec.md`
- Source:
    - `apps/web/app/src/components/CreatePostModal.vue`
    - `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue`
    - `apps/web/app/src/components/composer/composer-media-picker.types.ts`
    - `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue`
    - `apps/web/app/src/stores/media.ts`
    - `apps/web/app/src/stores/publishing.ts`
- Tests:
    - `apps/web/app/src/components/CreatePostModal.test.ts`
    - `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts`
    - `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.test.ts`
    - `apps/web/app/src/stores/media.test.ts`
    - `apps/web/app/src/stores/publishing.test.ts`
