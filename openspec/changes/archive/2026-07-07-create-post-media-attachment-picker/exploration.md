## Exploration: Create Post Media Attachment Picker

### Current State

The Create Post Modal (`CreatePostModal.vue`) has two disconnected media attachment mechanisms:

1. **Direct upload area** (drag-and-drop + file picker): Uploads a local file via the CAS pipeline,
   creates a transient blob preview, and adds the resulting asset to the media store selection.
   Works independently but only supports one file at a time and shows a bare drop zone.

2. **Media Picker Shell** (`ComposerMediaPickerShell.vue`): A reusable dialog shell with
   search/filter controls, an asset grid, and an optional Unsplash provider tab. **However, it's
   completely non-functional in practice**:
    - `CreatePostModal` passes `assets={[]}` (line 1186) and `state='loading'` (line 321-323)
    - The asset grid is **never visible** — always stuck in LOADING state
    - The library tab exists in the UI but shows nothing
    - The Unsplash provider tab (`MediaProviderPanel.vue`) works and can import photos

The `ComposerMediaPickerShell` has a clean parent-owned interaction contract (no store/API coupling
per the spec), but the parent (`CreatePostModal`) never provides real data. The shell's existing
tests cover open/close, accessibility, state rendering, and event emissions — but not asset grid
interaction since assets were never wired up.

The full media library (`MediaLibraryView.vue`) has search, filter, sort, multi-select, upload, and
delete — but it's a separate page, not accessible from the composer.

### Affected Areas

- `apps/web/app/src/components/CreatePostModal.vue` — **Primary file**: Media Attachment section,
  media picker trigger, open/close/handler wiring, `mediaPickerState` hardcoded to `LOADING`,
  `assets={[]}` pass-through
- `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` — Expected shell boundary for
  the consolidated picker, but verified absent from the current tree during later design review;
  likely must be created
- `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` — Expected shell test
  file, also absent from the current tree during design review; likely must be created
- `apps/web/app/src/components/composer/composer-media-picker.types.ts` — Expected shared picker
  types file, absent from the current tree during design review; likely must be created
- `apps/web/app/src/stores/media.ts` — Media store already has `selectedAssetIds`, `addToSelection`,
  `removeFromSelection` but the picker doesn't use them
- `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue` — Expected
  parent-owned Unsplash panel boundary, absent from the current tree during design review; likely
  must be created
- `apps/web/app/src/components/CreatePostModal.test.ts` — Tests for media picker integration (
  open/close/focus behavior exists but no asset interaction tests)
- `apps/web/app/src/lib/media-api.ts` — Asset API types (e.g. `MediaAssetSummary`) used for asset
  grid rendering
- `openspec/specs/composer-media-picker/spec.md` — Existing spec that defines the shell-only
  contract; may need delta if scope expands
- `openspec/specs/media-library/spec.md` — Existing media library spec; defines asset lifecycle and
  retrieval
- `openspec/specs/media-provider-unsplash/spec.md` — Existing Unsplash provider spec

### Approaches

1. **Wire existing assets into the picker (minimum viable)**
    - Load media store assets into `ComposerMediaPickerShell` props
    - Change `mediaPickerState` from `'loading'` → `'ready'` when assets are loaded
    - Add asset selection via click (reusing `mediaStore.addToSelection`)
    - Show actual thumbnail previews from `asset.previewUrl`
    - Maintain the shell's parent-owned contract
    - **Pros**: Fastest path, minimal new code, leverages existing store and shell
    - **Cons**: Doesn't address the dual UX (upload area + picker), still two separate interaction
      paths
    - **Effort**: Low

2. **Consolidated media attachment flow (recommended)**
    - Merge the upload flow INTO the media picker shell
    - Remove the separate drag-drop area from CreatePostModal's main layout
    - Make the picker the single entry point for ALL media attachment: browse library, upload new,
      or import from provider
    - Add upload tab or inline upload capability inside the picker dialog
    - Show selected assets with thumbnails in a compact bar below the composer text area
    - **Pros**: Coherent UX, single interaction model, aligns with user expectations
    - **Cons**: More work, changes the spec contract (shell would need upload capability or a new
      upload tab), larger diff
    - **Effort**: Medium–High

3. **Two-panel composer (future-oriented)**
    - Replace the modal with a split-view composer page
    - Left panel: text editor + schedule controls
    - Right panel/sidebar: media library browser with search, filter, multi-select, drag-to-attach
    - **Pros**: Most powerful UX, matches professional tools (Buffer, Hootsuite)
    - **Cons**: Major architectural change, would require new route/layout, scope creep for current
      needs
    - **Effort**: High

### Recommendation

**Approach 2 — Consolidated media attachment flow.**

The current defect is not limited to missing asset wiring. The composer exposes fragmented
attachment paths and does not provide a coherent source-selection model. Implement the consolidated
flow within the existing Create Post modal, while keeping the broader split-view redesign out of
scope.

The recommendation is:

1. Replace the persistent dropzone with a compact attachment rail and a single `Add Media` entry
   point
2. Keep `Library` and feature-flagged `Unsplash` as browsable sources inside the picker
3. Treat `Upload` as an action within the picker flow, not as an artificial browsable source
4. Preserve staged multi-selection until explicit confirmation, then apply the selection to the
   draft attachments
5. Reuse the existing CAS-backed upload/import pipeline and refresh or upsert newly created assets
   back into the active picker session
6. Keep split-view and new third-party sources out of scope for this change

### Risks

- The existing `composer-media-picker` spec explicitly scopes the shell as a **non-attaching,
  non-uploading presentation component**. Adding selection or upload would change this contract and
  require a spec delta.
- The shell's `provider-import` event currently closes the picker on import. If we add multi-select,
  this behavior needs reconsideration.
- Thumbnail loading depends on `previewUrl` being available from the backend — assets that are still
  `PROCESSING` won't have previews.
- The media store's `selectedAssetIds` is shared state — if the picker uses it directly, closing the
  picker without committing would mutate state. Need a "staging" selection pattern.
- Composer tests will need updates if the media picker behavior changes.

### Ready for Proposal

**Yes** — sufficient information gathered to move to `sdd-propose`. The orchestrator should tell the
user:

- The media picker shell exists but was never wired to real data
- The product problem is broader than missing wiring: Create Post still exposes fragmented
  attachment paths
- The recommended change is the consolidated media attachment flow inside the current modal, not a
  wire-assets-first stopgap
- The proposal should widen the spec deltas across `composer-media-picker`, `media-library`,
  `media-provider-unsplash`, and `publishing`
