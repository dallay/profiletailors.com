# Proposal: Extract Composer Media Picker Composable

## Intent

`CreatePostModal.vue` has grown into a ~1500-line god component that mixes modal chrome, scheduling,
submit, and the entire composer media-picker orchestration (staged selection refs, auto-stage
tracking, reconciliation polling, provider search/import flow, attachment-limit enforcement). This
change extracts that orchestration into a dedicated `useComposerMediaPicker` composable so the modal
shrinks back to wiring + scheduling/submit, while the public contract (emitted events, testids,
lifecycle, behavior) stays byte-for-byte identical. The goal is structural hygiene only — no
product, UX, or behavior change.

## Scope

### In Scope

- Add `apps/web/app/src/composables/useComposerMediaPicker.ts` (or co-located at
  `apps/web/app/src/components/composer/useComposerMediaPicker.ts`) that owns:
    - State: `isMediaPickerOpen`, `mediaPickerCollectionState`, `draftAttachmentIds`,
      `pickerSelectionIds`, `autoStagedAssetIds`, `manuallyDeselectedAutoStageIds`,
      `pendingPickerAssets`, `pickerSessionActiveAssetIds`, `reconciliationPollers`
    - Methods: `openMediaPicker`, `closeMediaPicker`, `togglePickerAsset`, `applyPickerSelection`,
      `removeDraftAttachment`, `handlePickerUploadSelection`, `handleProviderSearch`,
      `handleProviderImport`, `startAssetReconciliation`, `scheduleAssetReconciliation`,
      `stopReconciliationPoller`, `stopAllReconciliationPollers`, `resetPickerSessionTracking`,
      `clearPendingPickerAsset`, `ensurePickerAssetVisible`, `addPendingPickerAsset`,
      `stageAssetOnce`, `getLibraryCollectionState`, `mapAssetToPickerAsset`, `toPickerAssetStatus`,
      `getPickerAssetStatus`, `isAssetSelectableStatus`
    - Computed: `effectiveProvider`, `effectiveAttachmentLimit`, `isAttachmentLimitExceeded`,
      `isPickerSelectionOverLimit`, `activeChannels`, `pickerAssets`, `draftAttachmentAssets`
    - Provider state: `providerQuery`, `providerResults`, `providerSearching`,
      `providerSearchError`, `providerImportResolution`
    - Constants: `RECONCILIATION_POLL_INTERVAL_MS`, `RECONCILIATION_MAX_ATTEMPTS`
- Reduce `apps/web/app/src/components/CreatePostModal.vue` to: props/emits, scheduling state, submit
  handlers, and wiring that consumes the composable and binds it to `<ComposerMediaPickerShell>` +
  `<MediaProviderPanel>` slots.
- Update `apps/web/app/src/components/CreatePostModal.test.ts` so it keeps passing against the new
  internal structure without changing the external contract (emits, testids, user-observable
  behavior).
- Add `apps/web/app/src/composables/useComposerMediaPicker.test.ts` covering the composable in
  isolation (state transitions, auto-stage idempotency, manual deselect, limit enforcement,
  reconciliation polling start/stop, provider search/import happy + error paths).
- Remove the existing `defineExpose({ __... })` test-leak now that the test seam lives on the
  composable directly.
- No behavior change — pure structural refactor.

### Out of Scope

- Wiring the Unsplash provider to a real backend (still uses the existing DEV/test stub via
  `mediaProviderUnsplash`).
- Changing the source-of-truth for `workspaceId` (still comes from `useWorkspaceStore()`).
- Changing the reconciliation strategy (polling cadence, attempt bounds, timeouts remain identical).
- New product features, UX changes, copy changes, or new media providers.
- Touching `ComposerMediaPickerShell.vue`, `MediaProviderPanel.vue`,
  `composer-media-picker.types.ts`, or the `media` / `publishing` Pinia stores beyond what is
  strictly required for the modal to import the composable.
- Backend, marketing site, shared libs, or infra changes.

## Capabilities

### Modified Capabilities

- `composer-media-picker` — Public contract (lifecycle, emitted events, testids, limit enforcement,
  auto-stage rules, reconciliation semantics) is unchanged. Only the implementation host changes:
  orchestration moves from `CreatePostModal.vue` into the new composable.

### New Capabilities

- None.

## Approach

The composable will be implemented as a plain function that returns reactive state, computeds, and
methods — it does not hold a reference to the modal's component instance. To keep it testable in
isolation, store dependencies (`mediaStore`, `publishingStore`) will be **passed explicitly as
parameters** rather than imported inside the composable. This makes the dependency surface visible
at the call site (`useComposerMediaPicker({ mediaStore, publishingStore, ... })`), lets unit tests
inject fakes/mocks, and avoids hidden coupling to `useWorkspaceStore()` or any other global
side-effect. Reactive inputs that vary per render (`editingPublication`, channel list, attachment
limits) will be passed as `MaybeRefOrGetter` params or as a `props` ref object, so the composable
never closes over `defineComponent` `props` directly — a common foot-gun when extracting
composables.

The composable will preserve the exact existing lifecycle: `openMediaPicker` seeds
`mediaPickerCollectionState`, `applyPickerSelection` snapshots `pickerSessionActiveAssetIds`,
`togglePickerAsset` enforces the `effectiveAttachmentLimit` and the auto-stage / manual-deselect
bookkeeping, and reconciliation polling is bounded by `RECONCILIATION_POLL_INTERVAL_MS` and
`RECONCILIATION_MAX_ATTEMPTS` with explicit `stopAllReconciliationPollers` on unmount and on
`closeMediaPicker`. Provider search/import flows through `providerQuery` / `providerResults` /
`providerImportResolution` exactly as today. Because the existing `defineExpose({ __testOnly... })`
test seam on the modal only existed to poke at orchestration internals, removing it and testing the
composable directly eliminates the leak while preserving every existing assertion (assertions now
drive the composable instead of the modal instance).

`CreatePostModal.vue` will be reduced to: script-setup imports, the modal's own state (open/close,
scheduling form, submit pipeline), a single `const picker = useComposerMediaPicker(...)` call, and
template wiring that binds `picker.*` into `<ComposerMediaPickerShell>` and `<MediaProviderPanel>`
slots. No computed or method moves out unless it is purely picker orchestration — scheduling/submit
stays in the modal because it is the modal's job, not the picker's.

## Affected Areas

| Area                                                          | Impact   | Description                                                                            |
|---------------------------------------------------------------|----------|----------------------------------------------------------------------------------------|
| `apps/web/app/src/components/CreatePostModal.vue`             | Modified | Strip picker orchestration; keep props/emits, scheduling, submit, and slot wiring only |
| `apps/web/app/src/composables/useComposerMediaPicker.ts`      | Added    | New composable owning picker state, methods, computeds, and constants                  |
| `apps/web/app/src/components/CreatePostModal.test.ts`         | Modified | Same external contract; internals updated to drive composable where needed             |
| `apps/web/app/src/composables/useComposerMediaPicker.test.ts` | Added    | Isolated unit tests for the composable                                                 |

## Risks

| Risk                                                                                     | Likelihood | Mitigation                                                                                                                                                                                                                                                          |
|------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Refactor breaks existing modal tests                                                     | Medium     | Preserve the exact emitted events, testids, and user-observable behavior; re-run the full frontend suite (`just frontend-test`) before merge; if any test only passed via the `defineExpose` seam, port its assertion to the composable test instead of deleting it |
| Composable introduces hidden coupling (globals, Pinia singletons, `useWorkspaceStore()`) | Medium     | Pass store dependencies as explicit parameters; document the composable's input surface in JSDoc; forbid implicit `import { useXStore }` inside the composable body (enforced by code review + a lint-friendly rule if cheap)                                       |
| Lifecycle/closure over `props` becomes tricky (stale `editingPublication`, channel list) | Medium     | Pass reactive inputs as `MaybeRefOrGetter` / `Ref` and unwrap with `toValue()`; never read `props.editingPublication` inside the composable; verify with a focused test that mutating the input ref updates the composable's behavior                               |
| Reconciliation timers leak across tests or across open/close cycles                      | Low        | Centralize timer lifecycle inside the composable; always pair `scheduleAssetReconciliation` with `stopReconciliationPoller`; composable test uses Vitest fake timers and asserts no leftover intervals after unmount/close                                          |
| `defineExpose` test seam removal breaks unknown consumers                                | Low        | Audit the repo for any other consumer of the exposed keys before removing; if any exist, keep a thin wrapper or migrate them in the same change                                                                                                                     |

## Rollback Plan

Revert the modal to its pre-refactor inline picker logic and delete `useComposerMediaPicker.ts` plus
its test file. No data migration is required — the refactor is purely structural and the modal
continues to read/write the same Pinia stores with the same shapes. Restore the previous commit of
`CreatePostModal.vue` (or apply the inverse diff) and re-run `just frontend-test` to confirm the
prior state is fully restored.

## Dependencies

- Archived change `create-post-media-attachment-picker` (2026-07-07) — shipped the composer media
  picker flow that this change now restructures.
- Existing `media` and `publishing` Pinia stores — composable accepts them as injected dependencies.
- Existing `useWorkspaceStore()` — still the source-of-truth for `workspaceId` (out of scope to
  change).

## Success Criteria

- [ ] `apps/web/app/src/components/CreatePostModal.vue` drops below 900 lines (target: 700–850) and
  contains no picker-orchestration refs/methods of its own.
- [ ] All existing tests in `CreatePostModal.test.ts` pass unchanged in their assertions (testids,
  emitted events, behavior). Zero flaky regressions.
- [ ] New `useComposerMediaPicker.test.ts` covers at least: open/close lifecycle,
  `togglePickerAsset` + limit enforcement, auto-stage idempotency, manual deselect bookkeeping,
  `applyPickerSelection` snapshot semantics, reconciliation start/stop with fake timers (including
  max-attempts bound), provider search success + error, provider import success + error, and
  `resetPickerSessionTracking` clearing.
- [ ] `defineExpose({ __... })` test-leak is removed from the modal.
- [ ] `just frontend-test` passes locally; `just frontend-lint` passes with no new warnings.
- [ ] No public contract change: emits, testids, prop names, and user-visible behavior remain
  identical.
- [ ] Composable accepts store dependencies as explicit parameters (verifiable by reading the
  signature).