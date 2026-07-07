# Tasks: refactor-composer-extract-media-picker-composable

## Phase 1 — Composable skeleton (no behavior change yet)

- [x] 1.1 RED — add `apps/web/app/src/composables/useComposerMediaPicker.test.ts` that mounts the composable via Vue `withSetup` and asserts the composable returns the documented refs/computeds (isMediaPickerOpen starts false, draftAttachmentIds starts [], pickerAssets is empty array, effectiveProvider is null when flag off)
- [x] 1.2 GREEN — create `apps/web/app/src/composables/useComposerMediaPicker.ts` with the documented API surface; pass tests
- [x] 1.3 REFACTOR — extract helpers (toPickerAssetStatus) and shared types into the composable; pass tests

## Phase 2 — Move orchestration methods into the composable (TDD per method)

- [x] 2.1 RED — test openMediaPicker loads assets via mediaStore.loadAssets and sets isMediaPickerOpen=true; assert collection state transitions
- [x] 2.2 GREEN — implement openMediaPicker in the composable
- [x] 2.3 RED — test closeMediaPicker stops polling and clears staged selection
- [x] 2.4 GREEN — implement closeMediaPicker
- [x] 2.5 RED — test applyPickerSelection respects effectiveAttachmentLimit and writes draftAttachmentIds
- [x] 2.6 GREEN — implement applyPickerSelection
- [x] 2.7 RED — test togglePickerAsset auto-stage handling and manual deselect tracking
- [x] 2.8 GREEN — implement togglePickerAsset
- [x] 2.9 RED — test handlePickerUploadSelection uses createAndUpload, upserts, and starts reconciliation
- [x] 2.10 GREEN — implement handlePickerUploadSelection

## Phase 3 — Reconciliation (TDD with fake timers)

- [x] 3.1 RED — test startAssetReconciliation caps at RECONCILIATION_MAX_ATTEMPTS
- [x] 3.2 GREEN — implement bounded polling in the composable
- [x] 3.3 RED — test that picker reopen re-arms reconciliation for pending assets
- [x] 3.4 GREEN — implement reopen behavior

## Phase 4 — Provider search/import (parent-owned)

- [x] 4.1 RED — test handleProviderSearch updates providerQuery and synthesizes dev/test results only
- [x] 4.2 GREEN — implement handleProviderSearch with DEV/test guard and providerSearchError for prod
- [x] 4.3 RED — test handleProviderImport upserts synthetic asset and triggers reconciliation in dev/test only
- [x] 4.4 GREEN — implement handleProviderImport
- [x] 4.5 REFACTOR — extract useMediaStoreWorkspaceId helper into a private composable-side function

## Phase 5 — Wire the composable into CreatePostModal

- [x] 5.1 GREEN — replace inline picker orchestration in `apps/web/app/src/components/CreatePostModal.vue` with useComposerMediaPicker; preserve template exactly; no assertion changes in existing tests needed
- [x] 5.2 REFACTOR — grep `defineExpose` in CreatePostModal.vue; if only the `__` test-leak keys remain (no real consumer), remove `defineExpose({ __... })` block entirely

## Phase 6 — Validation gate

- [x] 6.1 — run `pnpm --filter @profile-tailors/app vitest run --reporter=verbose` on focused suite (ComposerMediaPickerShell, MediaProviderPanel, CreatePostModal, useComposerMediaPicker)
- [x] 6.2 — run `pnpm --filter @profile-tailors/app biome check --write` over changed files
- [ ] 6.3 — confirm CreatePostModal.vue line count drops below 900 lines (proposal target) — currently 1138 lines, so this proposal target is not met yet
- [x] 6.4 — run `just frontend-test` for full suite
