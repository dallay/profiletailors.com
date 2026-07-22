# Design: Extract Composer Media Picker Composable

## Overview

`CreatePostModal.vue` is a ~1500-line god component that mixes modal chrome, scheduling,
submit, and the entire composer media-picker orchestration. This change extracts that
orchestration into a dedicated `useComposerMediaPicker` composable, leaving the modal as
a thin wiring + scheduling/submit layer. The public contract (emits, testids, lifecycle,
behavior) stays byte-for-byte identical — this is structural hygiene, not a product
change.

## Goals and Non-Goals

**Goals**

- Move picker state, computeds, methods, and constants out of the modal into a dedicated
  composable.
- Reduce `CreatePostModal.vue` below 900 lines, with no picker-orchestration logic left in
  the modal.
- Preserve the public contract: emits, testids, prop names, observable behavior.
- Make the composable unit-testable in isolation via explicit store injection.
- Eliminate the `defineExpose({ __... })` test seam (no test currently uses it).
- Achieve the existing `composer-media-picker` capability with the implementation host
  changed only.

**Non-Goals**

- No product, UX, or copy change.
- No new media providers or wiring to a real Unsplash backend.
- No changes to `ComposerMediaPickerShell.vue`, `MediaProviderPanel.vue`,
  `composer-media-picker.types.ts`, or the `media` / `publishing` Pinia stores.
- No changes to reconciliation cadence, attempt bounds, or timeouts.

## Architecture / Approach

### Composable boundary

`useComposerMediaPicker` is a plain function exported from
`apps/web/app/src/composables/useComposerMediaPicker.ts`. It returns reactive state,
computeds, and methods. It does **not** import Pinia stores globally — store dependencies
are passed in as parameters so the dependency surface is visible at the call site and the
composable can be unit-tested with mocks. The `useWorkspaceStore()` call used by
`handleProviderImport` (to seed `workspaceId` for the synthetic asset) is allowed *only*
because it represents an infrastructure read of an active workspace id, not feature state —
and is encapsulated behind a tiny internal helper so it remains a single source of truth.

### Inputs (params)

| Param                       | Type                                         | Purpose                                                          |
|-----------------------------|----------------------------------------------|------------------------------------------------------------------|
| `mediaStore`                | `ReturnType<typeof useMediaStore>`           | Injected media store. Required.                                  |
| `publishingStore`           | `ReturnType<typeof usePublishingStore>`      | Injected publishing store. Required (channel list → limit calc). |
| `initialChannelId`          | `MaybeRefOrGetter<string \| null>`           | Optional initial channel id. Read once on setup.                 |
| `editingPublication`        | `MaybeRefOrGetter<Publication \| undefined>` | Reactive so edit-mode flips propagate.                           |
| `isUnsplashProviderEnabled` | `MaybeRefOrGetter<boolean>`                  | Feature flag, reactive.                                          |
| `provider`                  | `MaybeRefOrGetter<'unsplash' \| null>`       | Parent-supplied provider, reactive.                              |

`MaybeRefOrGetter` (Vue 3.3+) is used for reactive inputs to avoid stale-closure bugs when
parent props change after setup. The composable unwraps them with `toValue()` at the
read site.

### Outputs

**Refs**

- `isMediaPickerOpen: Ref<boolean>`
- `mediaPickerCollectionState: Ref<ComposerMediaPickerCollectionState>`
- `draftAttachmentIds: Ref<string[]>`
- `pickerSelectionIds: Ref<string[]>`
- `autoStagedAssetIds: Ref<string[]>`
- `manuallyDeselectedAutoStageIds: Ref<string[]>`
- `pendingPickerAssets: Ref<string[]>`
- `providerQuery: Ref<string>`
- `providerResults: Ref<ProviderSearchResultViewModel[]>`
- `providerSearching: Ref<boolean>`
- `providerSearchError: Ref<string | null>`
- `providerImportResolution: Ref<Record<string, string>>`

**Computeds**

- `effectiveProvider: ComputedRef<'unsplash' | null>`
- `activeChannels: ComputedRef<Channel[]>`
- `effectiveAttachmentLimit: ComputedRef<number>`
- `isAttachmentLimitExceeded: ComputedRef<boolean>`
- `isPickerSelectionOverLimit: ComputedRef<boolean>`
- `pickerAssets: ComputedRef<ComposerMediaPickerAsset[]>`
- `draftAttachmentAssets: ComputedRef<MediaAssetSummary[]>`

**Methods**

- `openMediaPicker()`
- `closeMediaPicker()`
- `togglePickerAsset(assetId: string)`
- `applyPickerSelection(assetIds: string[])`
- `removeDraftAttachment(assetId: string)`
- `handlePickerUploadSelection(files: File[])`
- `handleProviderSearch(payload: { query: string })`
- `handleProviderImport(payload: { externalId: string }): Promise<void>`

**Internal-only (not re-exported, but accessible for tests via the return shape)**

- `startAssetReconciliation`, `scheduleAssetReconciliation`, `stopReconciliationPoller`,
  `resetPickerSessionTracking`, `clearPendingPickerAsset`, `ensurePickerAssetVisible`,
  `addPendingPickerAsset`, `stageAssetOnce`, `getLibraryCollectionState`,
  `mapAssetToPickerAsset`, `toPickerAssetStatus`, `getPickerAssetStatus`,
  `isAssetSelectableStatus`.

**Constants** (exported for tests)

- `RECONCILIATION_POLL_INTERVAL_MS = 1000`
- `RECONCILIATION_MAX_ATTEMPTS = 5`

### Lifecycle ownership

The composable owns the polling maps internally:

- `reconciliationPollers: Map<string, ReturnType<typeof setTimeout>>` — internal.
- `pickerSessionActiveAssetIds: Set<string>` — internal.
- `pendingPickerAssets: Ref<string[]>` — exposed.

It exposes:

- `stopReconciliationPoller(assetId)` — internal
- **`stopAllReconciliationPollers()` — exposed; modal MUST call it from `onUnmounted`.**

`CreatePostModal.vue` wires this via:

```ts
const picker = useComposerMediaPicker({ mediaStore, publishingStore,
  isUnsplashProviderEnabled: () => props.isUnsplashProviderEnabled,
  provider: () => props.provider,
  editingPublication: () => props.editingPublication,
})
onUnmounted(() => picker.stopAllReconciliationPollers())
```

`onScopeDispose` is also called inside the composable as a defensive belt-and-braces in
case a future caller uses `effectScope` (e.g. inside a child component), so timers never
leak. The modal's `onUnmounted` call remains the primary contract.

## File Changes

| File                                                                        | Change        | Notes                                                                                                                                                                                                                                                                                                           |
|-----------------------------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `apps/web/app/src/composables/useComposerMediaPicker.ts`                    | Added         | New composable. Exports `useComposerMediaPicker`, `RECONCILIATION_POLL_INTERVAL_MS`, `RECONCILIATION_MAX_ATTEMPTS`.                                                                                                                                                                                             |
| `apps/web/app/src/composables/useComposerMediaPicker.test.ts`               | Added         | Vitest unit tests with fake timers covering state transitions, auto-stage, manual deselect, limit enforcement, polling start/stop, provider search/import.                                                                                                                                                      |
| `apps/web/app/src/components/CreatePostModal.vue`                           | Modified      | Removes ~500 lines of picker orchestration. Replaces inline refs/methods with `const picker = useComposerMediaPicker(...)`. Removes `defineExpose({ __... })`. Wires `picker.*` into `<ComposerMediaPickerShell>` and `<MediaProviderPanel>`. Calls `picker.stopAllReconciliationPollers()` from `onUnmounted`. |
| `apps/web/app/src/components/CreatePostModal.test.ts`                       | Modified      | Same external contract; internals updated only where the test currently drives the modal via the now-removed `defineExpose` seam. No semantic assertion changes. (Audit confirmed: no test reads the `__...` keys today, so changes are likely zero.)                                                           |
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue`         | **Unchanged** | Public props/emits stay identical.                                                                                                                                                                                                                                                                              |
| `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue` | **Unchanged** | Public props/emits stay identical.                                                                                                                                                                                                                                                                              |
| `apps/web/app/src/components/composer/composer-media-picker.types.ts`       | **Unchanged** | Types already cover the composable surface.                                                                                                                                                                                                                                                                     |
| `apps/web/app/src/stores/media.ts`, `stores/publishing.ts`                  | **Unchanged** | Injected as parameters; no signature change.                                                                                                                                                                                                                                                                    |

## Key Decisions

- **Pass stores as parameters, not as global imports.** Makes the dependency surface
  visible at the call site (`useComposerMediaPicker({ mediaStore, publishingStore, ... })`)
  and lets unit tests inject fakes. Aligns with the proposal's "no hidden coupling" rule
  and makes lint enforcement trivial (the composable body never calls `useMediaStore()` or
  `usePublishingStore()`). The single `useWorkspaceStore()` read remains encapsulated in a
  private helper so the read-site is grep-able.
- **`MaybeRefOrGetter` for reactive inputs.** `editingPublication`, `provider`, and
  `isUnsplashProviderEnabled` are all reactive props on the parent. Passing them as
  `MaybeRefOrGetter` and reading with `toValue()` eliminates the classic stale-closure bug
  where the composable snapshots the first prop value forever. A focused test
  (`reacts to editingPublication flipping to undefined after mount`) prevents regression.
- **Remove `defineExpose({ __... })` leak.** Audit confirmed no test reads the four
  `__...` keys; they were a forward-looking seam that never landed a consumer. Removing
  it shrinks the modal's API surface and makes the test seam the composable itself —
  unit tests can drive the composable directly without mounting a full modal.
- **`onScopeDispose` plus explicit `stopAllReconciliationPollers()`.** Defensive
  ownership: `onScopeDispose` catches future callers using `effectScope`, while the
  modal's `onUnmounted` call is the documented contract. No double-stop risk because
  `stopReconciliationPoller` is idempotent.
- **Provider state stays in the composable, not on the modal.** The proposal lists
  `providerQuery / providerResults / providerSearching / providerSearchError /
  providerImportResolution` as composable state. They're picker-orchestration
  concerns — moving them with the rest keeps the modal clean and preserves the
  `handleProviderSearch` / `handleProviderImport` test surface.
- **`draftAttachmentAssets` and `pickerAssets` as computeds.** Keeps Vue reactivity
  intact; the modal just reads `picker.draftAttachmentAssets` in the template.

## Test Strategy

**Unit tests (new file `useComposerMediaPicker.test.ts`)**

- `vi.useFakeTimers()` + `vi.advanceTimersByTime()` for reconciliation polling.
- Use a minimal store fake object (no full Pinia) — methods are plain `vi.fn()`s the
  composable calls. This matches the proposal's "inject fakes" guidance and is faster
  than mounting Pinia.
- `beforeEach` resets fake timers; `afterEach` calls `picker.stopAllReconciliationPollers()`
  and asserts the internal map is empty.

**Required coverage**

- open/close lifecycle: `openMediaPicker` seeds `mediaPickerCollectionState` and
  `pickerSelectionIds`,
  `closeMediaPicker` clears staged state and stops pollers.
- `applyPickerSelection` snapshot: confirms `pickerSessionActiveAssetIds` is reset and
  picker closes.
- Cancel discards staged: `closeMediaPicker` after `togglePickerAsset` does not mutate
  `draftAttachmentIds`.
- `applyPickerSelection` replaces draft: applying new ids overwrites previous
  `draftAttachmentIds` and sets `assetsTouched` only via the externally observable
  side-effect (we expose a `onAttachmentsChanged?: () => void` callback the modal
  hooks into — see Open Questions).
- Manual deselect preserved: toggling an auto-staged asset pushes to
  `manuallyDeselectedAutoStageIds`; re-toggling does not re-auto-stage.
- Auto-stage idempotency: `stageAssetOnce` is a no-op when the id is already in
  `autoStagedAssetIds`, in `manuallyDeselectedAutoStageIds`, or in `pickerSelectionIds`.
- Limit enforcement: `togglePickerAsset` and `applyPickerSelection` enforce
  `effectiveAttachmentLimit`; `isPickerSelectionOverLimit` flips when selection exceeds
  limit; apply is blocked.
- Reconciliation polling: fake timer advance, mock `loadAsset` returning PROCESSING,
  assert poll count = `RECONCILIATION_MAX_ATTEMPTS`, then asset is dropped from
  `pendingPickerAssets`. Mock `loadAsset` returning READY → asset is auto-staged and
  removed from `pendingPickerAssets`. Mock `loadAsset` rejecting → bounded retries.
- `stopAllReconciliationPollers()` clears the map and no further timers fire.
- `resetPickerSessionTracking()` clears all session maps + refs.
- Provider search: empty query → empty results, no error; non-empty in DEV/test →
  synthetic results; non-empty in prod → `providerSearchError` set, no results.
- Provider import: synthetic asset upserted, `startAssetReconciliation` invoked,
  `mediaPickerCollectionState` flips to READY; prod-guard returns early with error.

**Existing modal tests (`CreatePostModal.test.ts`)**

- All assertions must continue passing with **no semantic changes**. The refactor only
  relocates code, it does not change observable behavior. The audit shows no test reads
  the `__...` keys, so the only test-file edits needed are housekeeping imports if a test
  imported symbols the modal no longer re-exports (none expected).
- Run `just frontend-test` to confirm. Run `just frontend-lint` for new warnings.

## Migration Notes

- No data migration, no DB changes, no flag flips.
- The refactor is purely structural. To roll back: revert `CreatePostModal.vue` to its
  pre-refactor commit, delete `useComposerMediaPicker.ts` and its test file, restore
  `defineExpose({ __... })`. Re-run `just frontend-test` to confirm parity.
- The composable is **additive** — the modal stops importing `ComposerMediaPickerShell`
  and `MediaProviderPanel` from one place (the modal) and the composable takes over the
  binding.
- No public API change for any consumer of the modal; `<CreatePostModal>` is consumed
  by the same parent (the dashboard `PostListPanel` etc.) and its emits stay identical.

## Open Questions

- **`assetsTouched` ownership.** Today the modal sets `assetsTouched.value = true` inside
  `applyPickerSelection` and `removeDraftAttachment`. After extraction, the composable
  shouldn't reach into modal-owned state. Two clean options: (a) expose
  `onAttachmentsChanged?: () => void` in the composable params, called from
  `applyPickerSelection` and `removeDraftAttachment`; (b) have the composable return an
  `attachmentsTouched: ComputedRef<boolean>` that flips when `draftAttachmentIds` diverges
  from a baseline snapshot. **Recommend (a)** — it keeps the composable
  side-effect-free and explicit. Will confirm during apply.
- **`pickerSessionUploadInput` ref.** The template binds `ref="pickerSessionUploadInput"`
  on a hidden file input. This is purely a modal-DOM concern (clearing `target.value` on
  change), not picker orchestration. **Stays in the modal.**
- **`handleFileSelect` branching.** Currently
  `if (isMediaPickerOpen.value) handlePickerUploadSelection(...)`
  else `addFiles(...)`. After extraction, the modal calls
  `picker.handlePickerUploadSelection(files)` when open. The file's `@change` handler
  remains in the modal; only the routing changes.
- **`useWorkspaceStore` in `handleProviderImport`.** Kept in a tiny private helper
  (`useMediaStoreWorkspaceId`) so it's a single grep-able call-site. If a stricter
  "no global reads" rule is enforced, we can pass `workspaceId` as a
  `MaybeRefOrGetter<string>` param. **Defer to apply if a lint rule complains.**
- **Test count target.** Proposal says "at least" the listed cases. We'll add a few extra
  edge cases (e.g., toggling the same asset twice in the same session, polling for an
  asset that's already READY) — these cost ~10 lines each and raise confidence.
