# Media Provider — Unsplash

## Overview

The Unsplash provider exposes a tab in the composer media picker that lets authors
browse editorial examples, search Unsplash, and import a photo into the active
picker session. Search and import use a server-side Unsplash adapter so the access
key is never exposed to the browser. An imported photo becomes a READY external
media asset and its asset ID follows the same publication path as uploaded media.

## Changes

- New domain `media-provider-unsplash` introduced as part of the
  `create-post-media-attachment-picker` change (WU3).
- Opening the Unsplash tab without a query MUST load editorial photos from the
  Unsplash `/photos` endpoint.
- A non-blank query MUST use the Unsplash `/search/photos` endpoint with high
  content filtering.
- Provider credentials and download tracking MUST remain server-side.
- Search results MUST render photographer and Unsplash attribution links.
- Selecting Import MUST download and persist the selected photo as a READY
  external media asset and call the result's `download_location` endpoint.
- Provider import is treated as an **action within the picker flow**, not as a
  browsable source replacement. Imports keep the picker open so the author can
  continue staged multi-selection.
- Import failure MUST keep the result actionable so the author can retry.
- The imported asset ID MUST be included in the publication create payload.
- The provider tab is shell-only: no fetching, no persistence, no asset mutation
  inside the picker shell.

## Usage

### Configuration

- Set `SMP_MEDIAPROVIDER_UNSPLASH_ENABLED=true`.
- Set `UNSPLASH_ACCESS_KEY` to the server-side Unsplash access key.
- Keep `SMP_MEDIAPROVIDER_UNSPLASH_BASE_URL` at the official API URL outside
  controlled tests.
- Configure page size, timeout, and maximum import bytes through the matching
  `SMP_MEDIAPROVIDER_UNSPLASH_*` variables when the defaults are unsuitable.

### Request flow

- `GET /api/media/providers/unsplash/photos` returns editorial examples.
- `GET /api/media/providers/unsplash/photos?query=...` searches photos.
- `POST /api/media/providers/unsplash/photos/{externalId}/import` resolves the
  photo server-side, stores it, records the Unsplash download, and returns a READY
  media asset.
- The browser stages that asset and applies it through the existing picker before
  creating the publication.

### Picker shell contract

- The shell renders an Unsplash chip and search form when `provider="unsplash"`.
- The shell does NOT render a demo "Import sample result" button — provider
  imports happen exclusively through the parent-owned panel.
- The shell exposes a `provider` slot for the parent to render its own
  `MediaProviderPanel`.

### Provider panel contract

- The parent renders `MediaProviderPanel` inside the shell's `provider` slot.
- The panel emits typed events:
    - `provider-import` with `{ externalId: string }` on Import click
- The panel's Import button is `:disabled` whenever the parent marks a result as
  `selectedForImport: true` — the parent owns the in-flight import state and
  controls duplicate emit prevention.

## Troubleshooting

### Symptom: Search reports that Unsplash is not configured

- Verify `SMP_MEDIAPROVIDER_UNSPLASH_ENABLED=true` and `UNSPLASH_ACCESS_KEY` is
  present in the backend environment.
- Restart the backend after changing either value.

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

### Symptom: A result imports but cannot be applied

- Switch back to Library after import. The imported READY asset is staged there;
  Apply commits the staged selection to the composer.
- If import failed, retry from the same result. The picker MUST remain open and
  the result MUST remain actionable.

## References

- Source: `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue`
- Source: `apps/web/app/src/components/composer/MediaProviderPanel.vue`
- Source: `apps/web/app/src/composables/useComposerMediaPicker.ts`
- Source: `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/unsplash/`
- Tests: `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts`
- Tests: `apps/web/app/e2e/specs/composer-media-attachments-mocked.spec.ts`
- Tests: `server/smp/src/test/kotlin/com/profiletailors/smp/media/`
- External: `https://unsplash.com/documentation`
- External: `https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines`
- Archive: `openspec/changes/archive/2026-07-07-create-post-media-attachment-picker/`
