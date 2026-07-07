# Proposal: Create Post Media Attachment Picker

## Intent

Replace the fragmented Create Post attachment experience with one consolidated media flow inside the existing modal so authors can add, stage, review, and confirm media from a single predictable interaction model.

## Scope

### In Scope
- Replace the persistent composer dropzone with a compact attachment rail and one `Add Media` entry point inside `CreatePostModal.vue`.
- Consolidate upload, media-library browse, and feature-flagged Unsplash import into one picker flow with staged multi-selection and explicit confirm-before-commit behavior.
- Reuse the existing CAS upload/import pipeline, refresh or upsert library assets after upload/import, stage newly created assets immediately, and enforce channel-capability-aware attachment limits before commit.

### Out of Scope
- Google Drive, Dropbox, Canva, or other new media providers.
- Split-view composer redesign or changes outside the current Create Post modal.

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `composer-media-picker`: evolve from shell-only library browsing to the consolidated Create Post attachment flow with staged selection, upload action entry, library browsing, and optional Unsplash browsing.
- `media-library`: clarify asset refresh/upsert behavior after upload/import so newly available assets become selectable in the active picker flow.
- `media-provider-unsplash`: clarify picker participation as a browsable source only when enabled by feature flag.
- `publishing`: clarify modal attachment commit semantics and channel-aware attachment limit enforcement for persisted `assetIds`.

## Approach

Keep the interaction parent-owned within `CreatePostModal.vue`. Use one Add Media trigger to open a bounded picker that presents library browsing plus upload/import actions without redesigning the composer. Preserve staged selection until explicit confirm, reuse existing media-library and provider contracts, and reconcile uploads/imports back into the library so the picker can immediately select the resulting persisted assets.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/components/CreatePostModal.vue` | Modified | Own compact attachment rail, Add Media entry, staged selection, confirm flow, and limit enforcement |
| `apps/web/app/src/components/composer/*` | Modified | Replace dropzone-first interaction with consolidated picker/rail presentation |
| `apps/web/app/src/stores/media.ts` | Modified | Reconcile refreshed or upserted assets after upload/import for active picker reuse |
| `apps/web/app/src/lib/media/*` | Modified | Reuse existing CAS-backed upload/import clients in the modal flow |
| `openspec/specs/composer-media-picker/spec.md` | Modified | Define consolidated picker interaction contract |
| `openspec/specs/media-library/spec.md` | Modified | Define post-upload/import refresh and selection readiness expectations |
| `openspec/specs/media-provider-unsplash/spec.md` | Modified | Define feature-flagged browsable source behavior in picker |
| `openspec/specs/publishing/spec.md` | Modified | Define attachment commit and channel-capability-aware limit behavior |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Consolidated flow regresses existing attachment behavior | Med | Add spec deltas and focused modal regression tests for stage, confirm, cancel, and replace flows |
| Upload/import results fail to appear in the same session | Med | Require refresh/upsert reconciliation and immediate staging of newly created persisted assets |
| Channel limit rules feel inconsistent across targets | Med | Define capability-aware enforcement before commit with clear user feedback |

## Rollback Plan

Revert the modal back to the previous dropzone-plus-fragmented entry model, remove the new consolidated picker contract changes, and keep existing upload/library behavior on their prior separate paths.

## Dependencies

- Existing media-library list/read behavior and CAS-backed upload/import pipeline
- Existing feature flag and backend adapter behavior for Unsplash

## Success Criteria

- [ ] Create Post exposes one Add Media entry and a compact attachment rail instead of a persistent large dropzone.
- [ ] Authors can stage multiple assets from upload, media library, and feature-flagged Unsplash, then commit them only on explicit confirmation.
- [ ] Newly uploaded or imported assets refresh into the picker and can be selected immediately in the same flow.
- [ ] Attachment commits respect channel capability-aware limits within the current modal flow.
