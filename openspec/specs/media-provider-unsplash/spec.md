# Media Provider — Unsplash

## Overview

The Unsplash provider exposes a tab in the composer media picker that lets authors
search Unsplash photos and import results into the active picker session. The
provider follows the parent-owned reconciliation pattern: the picker shell renders
the tab and slots the parent-owned panel; the panel emits typed `provider-search`
and `provider-import` events; the parent (CreatePostModal) owns the actual fetch,
persistence, and staged reconciliation. The provider tab is purely presentational
and never fetches, persists, or mutates assets directly.

## Changes

- New domain `media-provider-unsplash` introduced as part of the
  `create-post-media-attachment-picker` change (WU3).
- Provider tab visibility is controlled by a parent-supplied `provider="unsplash"`
  prop combined with an `isUnsplashProviderEnabled` feature flag. The tab MUST
  render only when both conditions are true.
- Provider import is treated as an **action within the picker flow**, not as a
  browsable source replacement. Imports keep the picker open so the author can
  continue staged multi-selection.
- The provider tab is shell-only: no fetching, no persistence, no asset mutation
  inside the picker shell.

## Usage

### Parent-side wiring

- Pass `provider="unsplash"` and `isUnsplashProviderEnabled={true}` to the modal
  when the provider is configured and the user is in a workspace where Unsplash
  is enabled.
- The modal owns the provider-search and provider-import orchestration. It calls
  the backend Unsplash client (when wired) and routes imported assets through the
  same reconciliation pipeline as uploads.
- Until a backend Unsplash client is wired, the modal's synthetic path is
  explicitly guarded behind `import.meta.env.DEV` / `import.meta.env.MODE`
  starting with `test` and returns a clear `providerSearchError` in production.

### Picker shell contract

- The shell renders an Unsplash chip and search form when `provider="unsplash"`.
- The shell does NOT render a demo "Import sample result" button — provider
  imports happen exclusively through the parent-owned panel.
- The shell exposes a `provider` slot for the parent to render its own
  `MediaProviderPanel`.

### Provider panel contract

- The parent renders `MediaProviderPanel` inside the shell's `provider` slot.
- The panel emits typed events:
  - `provider-search` with `{ query: string }` on form submit
  - `provider-import` with `{ externalId: string }` on Import click
- The panel's Import button is `:disabled` whenever the parent marks a result as
  `selectedForImport: true` — the parent owns the in-flight import state and
  controls duplicate emit prevention.

## Troubleshooting

### Symptom: The Unsplash tab is missing even with the flag enabled

- Verify the parent passes both `provider="unsplash"` AND
  `isUnsplashProviderEnabled={true}`. The tab requires both signals.
- Confirm the feature flag is enabled at the workspace level.

### Symptom: Imports close the picker unexpectedly

- This is a regression — provider imports MUST keep the picker open. The
  `keep-picker-open-on-import` rule is enforced by the
  `keeps the picker open while importing a provider result` test in
  `CreatePostModal.test.ts`.

### Symptom: Duplicate provider-import emits after a fast double-click

- The `MediaProviderPanel` Import button is `:disabled` while
  `selectedForImport === true`. The parent MUST mark each result as
  `selectedForImport: true` immediately on emit and reconcile the flag once
  the import completes or fails.

### Symptom: Production users see placeholder/synthetic Unsplash results

- The synthetic search/import path is gated behind `import.meta.env.DEV`. In
  production the modal sets `providerSearchError` to a clear "Unsplash search is
  not configured" message. Wire a real Unsplash backend client before
  enabling the flag in production.

## References

- Source: `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue`
- Source: `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue`
- Source: `apps/web/app/src/components/CreatePostModal.vue` (handleProviderSearch,
  handleProviderImport)
- Tests: `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts`
- Tests: `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.test.ts`
- Tests: `apps/web/app/src/components/CreatePostModal.test.ts` (Unsplash integration)
- Archive: `openspec/changes/archive/2026-07-07-create-post-media-attachment-picker/`