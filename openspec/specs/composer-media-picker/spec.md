# Delta for composer-media-picker

## MODIFIED Requirements

### Requirement: Parent-owned interaction contract

The picker MUST accept typed presentation inputs and MUST emit typed search, filter, selection, apply, and close interactions. It MUST NOT fetch, persist, or publish media assets itself. The picker SHALL support explicit browsable sources `Library` and `Unsplash` when the parent supplies that provider, and SHALL treat `Upload` as an action within the picker flow rather than as a browsable source. The parent MUST supply `provider="unsplash"` only when the provider is configured and enabled.
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

Opening the picker MUST copy current `draftAttachmentIds` into transient `pickerSelectionIds`. Any dismissal that occurs without a successful apply MUST discard staged changes. Confirm or apply MUST replace the draft attachment set with the staged selection exactly, and only then may the picker close.

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
