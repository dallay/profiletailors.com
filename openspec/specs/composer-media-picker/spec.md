# Delta for composer-media-picker

## MODIFIED Requirements

### Requirement: Parent-owned interaction contract

The picker MUST accept typed presentation inputs and MUST emit typed search, filter, selection, apply, and close interactions. It MUST NOT fetch, persist, or publish media assets itself. The picker SHALL support explicit browsable sources `Library` and `Unsplash` when the parent supplies that provider, and SHALL treat `Upload` as an action within the picker flow rather than as a browsable source. The parent MUST supply `provider="unsplash"` only when the provider is configured and enabled. The host of the picker orchestration MAY move between component inline and a dedicated Vue composable without changing any typed emit, prop, testid, lifecycle, or user-observable behavior of the picker.
(Previously: The shell only emitted search, filter, and close interactions and could not support selection, upload, or draft attachment flows.)

#### Scenario: Emit parent-owned browse and selection interactions

- GIVEN the picker is ready and enabled
- WHEN the author searches, filters, or stages an asset selection
- THEN the picker MUST emit the corresponding typed interaction
- AND it MUST NOT directly access a media store or API

#### Scenario: Keep upload distinct from browsable sources

- GIVEN the picker is open
- WHEN the author chooses Upload
- THEN the picker MUST treat it as an action in the current flow
- AND it MUST NOT present Upload as a Library-like source unless an existing parent contract requires it

#### Scenario: Orchestration host change preserves the typed emit contract

- GIVEN the picker orchestration moves from `CreatePostModal.vue` inline into the `useComposerMediaPicker` composable
- WHEN the modal wires the composable into `<ComposerMediaPickerShell>` and `<MediaProviderPanel>` slots
- THEN the shell's typed emits, prop names, testids, and lifecycle behavior MUST remain byte-identical to the pre-refactor modal
- AND external consumers and existing tests MUST NOT observe any change in the picker surface

### Requirement: Provider tab is shell-only and parent-owned

The composer media picker MUST accept an optional `provider: "unsplash" | null` prop and MUST emit `provider-search` and `provider-import` interactions. The shell MUST NOT call any HTTP endpoint directly, and provider import MUST keep the picker open so the parent can continue staged multi-selection after import.
(Previously: The shell emitted provider interactions, but import completion did not explicitly preserve the open picker session for continued multi-selection.)

#### Scenario: Provider tab is conditional

- GIVEN a parent passes `provider="unsplash"`
- WHEN the picker renders
- THEN a provider tab MUST be visible
- AND when `provider` is `null` or omitted the tab MUST NOT render

#### Scenario: Importing a result preserves the picker session

- GIVEN a provider result is displayed
- WHEN the author clicks Import
- THEN the picker MUST emit `provider-import` with `{ externalId }`
- AND the picker MUST remain open for continued staged selection

### Requirement: Asset region presentation

The picker MUST provide a dedicated asset-grid region for parent-provided media items and MUST support staged multi-selection for draft attachment flows. `READY` assets MUST be selectable. When a `READY` asset has a usable preview it MUST render a thumbnail; when it has no usable preview, or when its preview fails to load, it MUST render fallback visuals without losing selectability. `PROCESSING` assets MUST remain visible with a placeholder or status and MUST NOT be selectable. `FAILED` assets MUST remain visible with fallback or failure presentation and MUST NOT be selectable.
(Previously: The asset region was presentation-only and could show a non-interactive ready state without attachment behavior.)

#### Scenario: Render and stage ready assets

- GIVEN the parent provides one or more `READY` assets
- WHEN the picker renders in a ready state
- THEN the asset-grid region MUST show selectable thumbnails
- AND the author MUST be able to stage multi-selection without mutating the draft yet

#### Scenario: Non-ready or failed assets stay visible but constrained

- GIVEN the parent provides `PROCESSING` or `FAILED` assets
- WHEN the picker renders them
- THEN each asset MUST remain visible with status or fallback visuals
- AND non-ready assets MUST NOT be selectable

#### Scenario: READY asset without preview remains selectable with fallback

- GIVEN the parent provides a `READY` asset without a usable preview, or whose preview fails to load
- WHEN the picker renders that asset
- THEN the asset MUST remain selectable
- AND it MUST render fallback visuals without breaking the grid

## ADDED Requirements

### Requirement: Staged selection lifecycle

Opening the picker MUST copy current `draftAttachmentIds` into transient `pickerSelectionIds`. Any dismissal that occurs without a successful apply MUST discard staged changes. Confirm or apply MUST replace the draft attachment set with the staged selection exactly, and only then may the picker close. The composable that owns this lifecycle MUST preserve the cancel-discards and apply-replaces semantics verbatim, and MUST centralize all reconciliation timer teardown on close and unmount.
(Previously: Opening the picker MUST copy current `draftAttachmentIds` into transient `pickerSelectionIds`. Any dismissal that occurs without a successful apply MUST discard staged changes. Confirm or apply MUST replace the draft attachment set with the staged selection exactly, and only then may the picker close.)

#### Scenario: Reopen starts from current draft attachments

- GIVEN the composer draft currently references attachment IDs `[A, B]`
- WHEN the author opens the picker
- THEN staged `pickerSelectionIds` MUST start as `[A, B]`
- AND the draft attachment set MUST remain unchanged until apply

#### Scenario: Cancel discards staged changes

- GIVEN staged `pickerSelectionIds` differ from `draftAttachmentIds`
- WHEN the author closes or cancels the picker
- THEN the staged changes MUST be discarded
- AND `draftAttachmentIds` MUST remain unchanged

#### Scenario: Apply replaces the draft attachment set

- GIVEN staged `pickerSelectionIds` are `[C, D]`
- WHEN the author confirms the picker
- THEN `draftAttachmentIds` MUST become `[C, D]`
- AND the prior draft attachment set MUST be replaced rather than merged implicitly

#### Scenario: Composable preserves cancel-discards and apply-replaces semantics

- GIVEN `useComposerMediaPicker` owns the staged selection lifecycle
- WHEN the modal invokes the composable's open, close, apply, and cancel paths
- THEN cancel MUST discard staged changes exactly as the inline modal did
- AND apply MUST replace `draftAttachmentIds` with the staged set exactly as the inline modal did
- AND any in-flight reconciliation polling started during the session MUST be stopped on close or unmount

### Requirement: Composability of picker orchestration

The composer media picker orchestration MUST be exposed as a `useComposerMediaPicker` Vue composable. The composable MUST own the picker state, methods, computeds, provider search/import state, and reconciliation constants previously inlined in `CreatePostModal.vue`. The composable MUST accept store dependencies (media, publishing) as explicit parameters rather than importing them internally, and MUST accept reactive inputs (e.g. `editingPublication`, channel list, attachment limits) as `MaybeRefOrGetter` or `Ref` values. The composable MUST NOT close over `defineComponent` `props` directly. Lifecycle methods (`openMediaPicker`, `closeMediaPicker`, `applyPickerSelection`, `togglePickerAsset`, reconciliation start/stop) MUST preserve the existing replace-set semantics and manual deselect tracking. The public contract of the picker (typed emits, props, testids, user-observable behavior) MUST remain byte-identical to the pre-refactor modal.

#### Scenario: Picker orchestration state and methods are exposed via a `useComposerMediaPicker` composable

- GIVEN the modal currently inlines picker orchestration
- WHEN the change is applied
- THEN a `useComposerMediaPicker` composable MUST exist and expose the picker state, methods, computeds, provider state, and reconciliation constants
- AND the modal MUST consume the composable as `const picker = useComposerMediaPicker(...)` and bind `picker.*` into the shell and provider panel slots

#### Scenario: Composable receives store dependencies explicitly to avoid hidden coupling

- GIVEN the composable needs `mediaStore` and `publishingStore`
- WHEN the composable is called
- THEN those store dependencies MUST be passed as explicit parameters
- AND the composable MUST NOT import Pinia stores or call `useXStore()` internally
- AND the input surface MUST be visible at the call site so unit tests can inject fakes

#### Scenario: Lifecycle hooks preserve replace-set semantics and manual deselect tracking

- GIVEN the composable owns `openMediaPicker`, `closeMediaPicker`, `applyPickerSelection`, `togglePickerAsset`, and the reconciliation start/stop methods
- WHEN the modal invokes those lifecycle hooks
- THEN `openMediaPicker` MUST seed `mediaPickerCollectionState` and snapshot `pickerSessionActiveAssetIds` exactly as the inline modal did
- AND `applyPickerSelection` MUST replace `draftAttachmentIds` with the staged set exactly as before
- AND `togglePickerAsset` MUST enforce the `effectiveAttachmentLimit` and the auto-stage / manual-deselect bookkeeping exactly as before
- AND reconciliation polling MUST be bounded by `RECONCILIATION_POLL_INTERVAL_MS` and `RECONCILIATION_MAX_ATTEMPTS` with explicit stop on close and unmount
