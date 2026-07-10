# E2E Test Plan — Composer Media Attachments

> Last updated: 2026-07-08  
> Primary UI: `https://pt-app.localhost:1355/` → `Create Post` modal  
> Scope: composer inline attachments, local upload UX, media-library staging, social preview reconciliation, and attachment-limit behavior  
> Evidence: live authenticated browser exploration with `dev@profiletailors.com` on the running frontend

## Overview

### Purpose

This plan defines end-to-end coverage for the Profile Tailors composer media experience inside the
`Create Post` modal. The focus is the authoring flow, not the standalone Media Library route. The
suite MUST prove what a real author sees while attaching local media, selecting workspace media,
reviewing inline thumbnails, reconciling uploads, and validating staged picker behavior.

This plan intentionally separates:

- **observable browser behavior** — inline preview cards, modal states, selection styling, preview swapping, focus retention;
- **network/protocol behavior** — upload progress, failure recovery, persisted asset reconciliation;
- **non-browser invariants** — storage cleanup, exact upload state transitions, backend provider import contracts.

A passing browser test MUST only claim what the browser can observe.

### Live observed baseline

Authenticated exploration on `2026-07-08` established the following current baseline in the live UI.

| Area | Live evidence |
|---|---|
| Composer entry | `New Post` opens `CREATE POST` modal from dashboard |
| Inline tile | The drag-drop/upload tile is a `118x118` dashed card labeled `Drag & drop or select a file` |
| Local image preview | Uploading a local PNG shows an immediate inline preview card before persistence |
| Local preview size | Inline preview card container is `118x118`; rendered image area is `116x116` |
| Local filename rendering | Filename is not rendered as visible text below the preview |
| Remove action | Local preview exposes `aria-label="Remove attachment <filename>"` and removal restores only the upload tile |
| Social preview | Immediately after local selection, LinkedIn preview uses a `blob:` URL without waiting for persistence |
| Media Library default source | Opening the picker from composer lands on `Library` |
| Unsplash availability | This environment did **not** expose an `Unsplash` tab or provider switch |
| Library selection visuals | Clicking a library card adds visual selected styling to the whole card (`border-[#8ccf70]`, darker background, subtle ring) |
| Library deselection | Clicking the same selected card again removes selected styling |
| Multi-select apply | Selecting multiple library items and applying renders inline previews in the composer |
| Draft removal from library assets | Removing one library asset removes only that attachment and leaves the others intact |
| Overflow presentation | With five staged attachments, the composer shows four inline cards plus a `+1` overflow card |
| Overflow sizing | `+1` card is `118x118`, matching the upload tile footprint |
| Social preview with multiple attachments | The visible LinkedIn preview shows a single media image at a time; browser evidence suggests the first visible/primary asset drives preview rendering |

### Evidence-backed status of requested behaviors

The requested scenarios split into three categories:

| Status | Meaning |
|---|---|
| **Observed live** | Verified directly in the browser during exploration |
| **Plannable, not directly observed** | UI exists, but the specific transient/network condition was not induced safely in live exploration |
| **Environment-gated** | Could not be observed because the provider/flag was absent in this environment |

| # | Topic | Status |
|---|---|---|
| 1 | Local select-file shows immediate inline thumbnail | Observed live |
| 2 | Drag & drop local image shows inline thumbnail | Plannable, not directly observed |
| 3 | Local inline thumbnail matches tile size/proportion | Observed live |
| 4 | Filename hidden below thumbnail, only tooltip on hover | Partially observed live; visible filename absent, tooltip not exposed in current DOM |
| 5 | Removing local thumbnail clears transient state and leaves tile only | Observed live |
| 6 | Removing Media Library thumbnail removes only that asset | Observed live |
| 7 | Uploading local thumbnail shows overlay + spinner + state text + progress bar | Plannable, not directly observed |
| 8 | Loader appears only for true `uploading` state | Plannable, not directly observed |
| 9 | Upload progress text/bar update over time | Plannable, not directly observed |
| 10 | Final progress text changes to `Finishing up...` | Plannable, not directly observed |
| 11 | User can keep typing while local upload is in progress | Plannable, not directly observed |
| 12 | Successful local upload reconciles from loader to persisted asset | Plannable, partially evidenced by blob-first behavior |
| 13 | Failed local upload exits uploading state cleanly | Plannable, not directly observed |
| 14 | Library card selection changes full-card visual state | Observed live |
| 15 | Clicking selected library image deselects it | Observed live |
| 16 | Multiple selected/non-selected images remain distinguishable | Observed live |
| 17 | Media modal defaults to `Library` | Observed live |
| 18 | If Unsplash enabled, modal shows `Library` + `Unsplash` and allows switching | Environment-gated |
| 19 | If Unsplash disabled, modal hides `Unsplash` | Observed live in this environment |
| 20 | Switching Library → Unsplash preserves staged selection | Environment-gated |
| 21 | Unsplash search renders results inside same modal container | Environment-gated |
| 22 | Unsplash import keeps modal open and reconciles inside picker flow | Environment-gated |
| 23 | Unsplash import ends as selected composer attachment | Environment-gated |
| 24 | Apply blocked when staged selection exceeds active-channel attachment limit | Plannable, not directly observed |
| 25 | Limit warning appears for workspace assets and external imports alike | External-import half environment-gated |
| 26 | More than four inline attachments show `+N` card | Observed live |
| 27 | `+N` card matches tile and thumbnail size | Observed live |
| 28 | Social preview uses local blob immediately after local selection | Observed live |
| 29 | Social preview swaps from blob to persisted asset after upload completes | Plannable, not directly observed |
| 30 | Local inline flow accepts only first valid file from multiple local files | Plannable, not directly observed |

## Changes

This plan adds a dedicated composer-focused companion to the existing CAS media-library plan. It
covers inline attachment UX that the library-wide plan does not describe in enough detail:

- local inline preview cards next to the drag-drop tile;
- drag-and-drop behavior inside the composer itself;
- upload-progress overlays and non-blocking typing while uploading;
- picker-stage semantics for workspace assets and external provider imports;
- attachment-limit behavior inside the composer modal;
- overflow rendering (`+N`) for inline previews;
- social preview source swapping from local blob to persisted asset.

## Usage

### Preconditions

| Requirement | Check |
|---|---|
| Frontend | `https://pt-app.localhost:1355` is running and reachable |
| Auth | Test user can sign in with local credentials |
| Active channel | Composer opens with at least one active channel selected |
| Media fixtures | Test workspace contains at least 5 READY image assets for picker scenarios |
| Upload fixtures | Local test files include valid image(s), invalid file(s), and multi-file selections |
| Browser support | Playwright worker can upload files and perform drag-and-drop |
| Network control | Mocked mode or interceptable backend is available for progress/failure scenarios |
| Feature flags | Unsplash-enabled and Unsplash-disabled environments are both available for provider coverage |
| Limits | At least one channel exposes a finite `maxAttachments` low enough to test overflow/limit blocking |

### Recommended execution lanes

| Lane | Layer | Purpose | CI |
|---|---|---|---|
| `@composer-ui-mocked` | Playwright with stateful route mocks | Deterministic progress, failure, limit, and provider-state scenarios | PR |
| `@composer-smoke-real` | Playwright against live backend | Prove local preview, library selection, removal, and overflow basics | scheduled/manual |
| `@composer-provider-real` | Playwright against provider-enabled environment | Prove real Unsplash tab/search/import integration | deferred |
| `@backend-contract` | API/integration tests, no browser | True upload-state semantics, provider import lifecycle, attachment-limit business rules | separate |

Use **real-browser** coverage only where browser-visible evidence matters. Use **mocked** coverage
for transient upload states that are too timing-sensitive or expensive to rely on in the live
backend.

## Requirements and Scenarios

### Requirement: Local select-file preview MUST appear immediately inline

Selecting a local image from the composer file input MUST render an inline preview card immediately
next to the drag-drop tile, before persisted reconciliation finishes.

#### Scenario: Immediate inline preview after file picker selection

- **Given** the `Create Post` modal is open
- **When** the author selects a valid local image using `select a file`
- **Then** an inline thumbnail card SHALL appear immediately in the attachments row
- **And** the upload tile SHALL remain visible in the same row
- **And** the thumbnail SHALL render before the persisted asset URL is available

#### Scenario: Social preview uses local blob immediately

- **Given** the author selects a valid local image
- **When** the inline thumbnail appears
- **Then** the visible social preview SHALL use a `blob:` URL immediately
- **And** the preview SHALL NOT wait for backend reconciliation to start showing media

### Requirement: Drag-and-drop MUST behave like select-file for the first valid file

Dragging files onto the composer dropzone MUST behave like file-picker selection for the first valid
file accepted by the current local-inline flow.

#### Scenario: Dragging one local image renders inline preview

- **Given** the composer dropzone is visible
- **When** the author drags and drops one valid local image over the composer
- **Then** an inline thumbnail SHALL appear in the same attachments row as the tile
- **And** the tile SHALL remain visible

#### Scenario: Multiple dropped files use only first valid file

- **Given** the local inline flow currently accepts only one file
- **When** the author drops multiple local files at once
- **Then** only the first valid accepted file SHALL be used
- **And** no second inline preview SHALL be created from the same drop action
- **And** the test report SHALL explicitly call out this current product limitation

### Requirement: Inline local thumbnail MUST match tile footprint and hide visible filename chrome

The inline local preview card MUST match the drag-drop card’s visual footprint and MUST NOT render a
visible filename caption below the image.

#### Scenario: Local preview size and proportion match tile

- **Given** one local image is selected
- **When** the inline preview and the tile are rendered side by side
- **Then** their outer card footprint SHALL match
- **And** the image SHALL preserve cover-style rendering inside the card
- **And** the test SHOULD assert equal container dimensions, not raw image pixels alone

#### Scenario: Filename is not rendered as visible caption

- **Given** one local image is selected
- **When** the inline preview is visible
- **Then** the filename SHALL NOT appear as visible caption text below the image
- **And** only non-visual affordances such as tooltip or accessible label MAY expose the filename

#### Scenario: Hover exposes filename affordance if implemented

- **Given** a local inline preview exists
- **When** the author hovers the preview card and its remove affordance
- **Then** the filename exposure mechanism SHALL match the product contract
- **And** if the contract is tooltip-based, the tooltip text SHALL match the selected filename
- **And** if the current implementation uses only accessible naming on the remove button, the test SHALL flag drift rather than silently passing tooltip expectations

### Requirement: Removing attachments MUST be scoped and consistent

Removing an attachment MUST remove only the targeted asset and MUST leave the rest of the draft
state consistent.

#### Scenario: Removing local inline preview restores tile-only state

- **Given** exactly one local inline preview exists and has not yet become a persisted visible selection
- **When** the author clicks the preview remove button
- **Then** the inline preview SHALL disappear
- **And** transient local attachment state SHALL be cleared
- **And** only the drag-drop tile SHALL remain visible in the row
- **And** the social preview SHALL return to no-media state

#### Scenario: Removing one library attachment preserves others

- **Given** multiple Media Library assets are attached to the draft
- **When** the author removes one specific inline thumbnail
- **Then** only that asset SHALL disappear from the composer draft
- **And** the remaining attached assets SHALL stay visible
- **And** the social preview SHALL remain valid using the remaining primary asset

### Requirement: Upload-progress overlay MUST reflect real upload state, not mere local selection

The inline loader overlay is a true upload-state UI. It MUST NOT appear simply because a local file
was chosen; it MUST map to real `uploading` semantics.

#### Scenario: Loader does not appear on selection alone

- **Given** a local file is selected but upload has not entered `uploading`
- **When** the UI renders the local inline card
- **Then** the progress overlay SHALL NOT appear yet
- **And** a plain local preview state SHALL remain visible

#### Scenario: Loader appears only when upload state becomes `uploading`

- **Given** a local file transitions into upload
- **When** the backing state enters `uploading`
- **Then** the inline thumbnail SHALL show an overlay
- **And** the overlay SHALL contain a round spinner
- **And** the overlay SHALL contain status text
- **And** the overlay SHALL contain a progress bar

#### Scenario: Progress text and bar update during upload

- **Given** the inline overlay is visible in `uploading`
- **When** progress events advance
- **Then** the status text SHALL update with current progress
- **And** the progress bar width/value SHALL update consistently
- **And** updates SHALL be monotonic

#### Scenario: Final upload segment shows `Finishing up...`

- **Given** upload progress enters the product-defined final threshold
- **When** the upload is nearly complete
- **Then** the overlay text SHALL change from `Uploading... X%` to `Finishing up...`
- **And** the change SHALL happen before the asset is declared fully ready

#### Scenario: Typing remains uninterrupted during upload

- **Given** a local upload is actively in progress
- **When** the author continues typing in `Post content`
- **Then** the textarea SHALL remain interactive
- **And** focus SHALL stay in the textarea while typing
- **And** keystrokes SHALL not be dropped
- **And** the rest of the modal SHALL remain responsive

### Requirement: Successful local upload MUST reconcile cleanly from blob preview to persisted attachment

The browser experience MUST transition from local blob preview to persisted asset representation
without flicker, disappearance, or broken preview state.

#### Scenario: Upload success removes loader and keeps attachment visible

- **Given** a local upload completes successfully
- **When** the asset becomes persisted and selectable/ready
- **Then** the loading overlay SHALL disappear
- **And** the inline attachment SHALL remain visible as the persisted draft asset
- **And** the remove affordance SHALL continue to work

#### Scenario: Social preview swaps from blob to persisted URL

- **Given** the social preview is initially using a local blob URL
- **When** backend reconciliation completes
- **Then** the social preview SHALL swap to the persisted asset URL
- **And** the preview SHALL not go blank during the swap
- **And** no broken-image state SHALL appear

### Requirement: Failed local upload MUST recover to a stable UI state

Local upload failure MUST not strand the inline card in a fake uploading state.

#### Scenario: Failed upload exits uploading state

- **Given** a local upload fails after entering uploading
- **When** the failure is surfaced
- **Then** the inline overlay SHALL stop presenting `uploading`
- **And** any progress indicator SHALL stop advancing
- **And** the UI SHALL resolve into a consistent failure or cleared state per product contract
- **And** the author SHALL still be able to continue typing or remove the failed item

### Requirement: Media Library selection MUST be visually unambiguous

The picker must clearly communicate staged selection state at the card level.

#### Scenario: Selecting one library card changes full-card visuals

- **Given** the media picker is open in `Library`
- **When** the author clicks one READY asset card
- **Then** the full card SHALL show selected styling
- **And** selected styling SHALL include card-level visual change such as border, background/overlay, or confirmation affordance

#### Scenario: Clicking selected card deselects it

- **Given** a library card is already selected
- **When** the author clicks that same card again
- **Then** the card SHALL return to its unselected visual state
- **And** the staged selection count SHALL decrease

#### Scenario: Multiple selected states remain distinguishable

- **Given** several library cards are visible and only some are selected
- **When** the picker is inspected visually
- **Then** selected cards SHALL be clearly distinguishable from unselected cards
- **And** ambiguity between hover styling and selected styling SHALL be treated as a defect

### Requirement: Media modal source behavior MUST respect environment flags and preserve stage state

The media modal source tabs MUST reflect enabled providers and MUST preserve staged selection across
source switching.

#### Scenario: Composer opens media modal on `Library` by default

- **Given** the author opens media from the composer
- **When** the modal appears
- **Then** `Library` SHALL be the default active source

#### Scenario: Unsplash hidden when provider disabled

- **Given** the environment does not enable Unsplash
- **When** the modal opens
- **Then** no `Unsplash` source switch SHALL be visible

#### Scenario: Unsplash visible when provider enabled

- **Given** the environment enables Unsplash
- **When** the modal opens
- **Then** both `Library` and `Unsplash` SHALL be available
- **And** the author SHALL be able to switch between them

#### Scenario: Switching sources preserves staged selection

- **Given** the author has already staged one or more assets in `Library`
- **When** the author switches to `Unsplash` and later returns
- **Then** previously staged selection SHALL still be preserved
- **And** switching sources SHALL NOT silently clear staged assets

### Requirement: Unsplash provider interactions MUST stay inside the picker workflow

External provider integration must behave as an extension of the picker, not a context switch.

#### Scenario: Unsplash search renders in the same modal container

- **Given** Unsplash is enabled
- **When** the author searches for a term
- **Then** provider results SHALL render inside the existing picker modal
- **And** the modal shell SHALL remain open throughout the search

#### Scenario: Unsplash import keeps picker open during reconciliation

- **Given** Unsplash results are visible
- **When** the author imports one image
- **Then** the modal SHALL remain open
- **And** the imported asset SHALL enter reconciliation inside the picker workflow
- **And** the author SHALL be able to continue staging other items

#### Scenario: Imported Unsplash asset ends as selected attachment in composer

- **Given** an Unsplash import completes successfully
- **When** the picker selection is applied
- **Then** the imported image SHALL appear in the composer as a selected attachment
- **And** it SHALL behave like a normal Media Library asset thereafter

### Requirement: Attachment limits MUST block invalid staged state before apply

The picker must enforce the active channel’s attachment limit before committing staged selection.

#### Scenario: Apply is blocked when staged selection exceeds limit

- **Given** the active channel has a finite attachment limit
- **And** the staged picker selection exceeds that limit
- **When** the author attempts to apply
- **Then** `Apply` SHALL be blocked or disabled
- **And** the draft attachments SHALL remain unchanged

#### Scenario: Limit warning appears for workspace assets

- **Given** the limit is exceeded using only workspace library assets
- **When** the over-limit state is reached
- **Then** a visible warning SHALL explain the limit breach

#### Scenario: Limit warning appears for external imports too

- **Given** the limit is exceeded using staged external/imported assets
- **When** the over-limit state is reached
- **Then** the same warning behavior SHALL appear
- **And** source type SHALL NOT bypass limit enforcement

### Requirement: Inline attachment overflow MUST collapse to `+N` after four visible cards

When more than four attachments are staged in the composer row, overflow MUST compress into a count
card without disturbing layout consistency.

#### Scenario: Fifth attachment creates `+N` overflow card

- **Given** more than four attachments are attached to the draft
- **When** the composer inline row renders
- **Then** only four visible attachment cards SHALL remain inline
- **And** one overflow card SHALL appear with the correct `+N` count

#### Scenario: `+N` card matches attachment/tile footprint

- **Given** the `+N` overflow card is visible
- **When** its layout is measured against the upload tile and attachment cards
- **Then** the overflow card SHALL use the same visual footprint
- **And** row alignment SHALL remain consistent

### Requirement: Lane Topology

The suite SHALL organize composer scenarios into three Playwright lane projects.

| Tag | Project | Purpose | CI |
|---|---|---|---|
| `@composer-ui-mocked` | `media-mocked-chromium` | Progress / failure / limit / deselection | PR |
| `@composer-smoke-real` | `media-real-chromium` | Real-backend happy paths | scheduled/manual |
| `@composer-provider-real` | `media-real-chromium` | Real Unsplash | deferred |

#### Scenario: Single lane tag per scenario

- GIVEN a scenario carries one tag from the topology table
- WHEN Playwright discovery runs
- THEN the scenario is selected by exactly one config project

#### Scenario: Mocked lane runs in parallel

- GIVEN `@composer-ui-mocked` is selected
- WHEN Playwright executes
- THEN workers SHALL run in parallel with isolated `MediaRouteState` per test

### Requirement: Selectors and Accessibility Contract

Locators SHALL prefer accessible roles, names, and ARIA. Stable `data-testid` MAY be introduced
ONLY for: overflow count, upload-overlay region, preview media source.

#### Scenario: Locators resolve against current markup

- GIVEN locators for inline preview cards, tile, dropzone, picker cards, overlay, `+N`, preview
- WHEN the page object exposes them
- THEN each MUST resolve via accessible role/name or approved `data-testid`

#### Scenario: No Pinia internals mutated

- GIVEN a scenario needs seeded picker state
- WHEN initial state is arranged
- THEN state SHALL be driven via API seeding, mock state, or user interactions
- AND SHALL NOT mutate `$pinia.state` directly

### Requirement: Mocked Fixture Capability Contract

`media-mocks.ts` and `media-mocked-test.ts` MUST expose: deferred upload, advanceable progress,
binary-upload failure, transition queues, external-asset seeding, channel-limit provider,
picker-source switch.

#### Scenario: Deferred upload is controllable

- GIVEN a PUT is queued via `mockNextPut`
- WHEN release has not been signalled
- THEN the inline preview SHALL remain `uploading`
- AND no `Finishing up...` segment SHALL render

#### Scenario: Channel-limit provider is deterministic

- GIVEN a mocked channel advertises `maxAttachments = 4`
- WHEN 5 library assets are staged
- THEN `Apply` SHALL be blocked and a visible warning SHALL render

### Requirement: Real Smoke Isolation Contract

`@composer-smoke-real` SHALL run with a unique run ID, seed assets through the per-run API, and
MUST NOT mutate the shared `dev-workspace-001`.

#### Scenario: Run ID isolates data

- GIVEN a real smoke scenario executes
- WHEN fixtures are created
- THEN assets and cleanup SHALL belong to the run ID namespace
- AND `afterEach` SHALL delete every asset created under that run ID

#### Scenario: No destructive cleanup of shared workspace

- GIVEN the real smoke lane executes
- WHEN teardown runs
- THEN the fixture SHALL NOT call delete endpoints against `dev-workspace-001` assets

### Requirement: Evidence and Reporting Contract

Each lane MUST emit deterministic evidence: HTML report, trace on first retry, screenshot on
failure, and per-scenario tag in the report header.

#### Scenario: Mocked lane emits coverage report

- GIVEN `@composer-ui-mocked` scenarios finish
- WHEN reporters run
- THEN `playwright-media-mocked-report/index.html` SHALL exist with `data-tag=@composer-ui-mocked`

#### Scenario: Real smoke retains failure video

- GIVEN a `@composer-smoke-real` scenario fails in CI
- WHEN the run completes
- THEN a retained-on-failure video SHALL be written under `playwright-media-real-report`

### Requirement: Plan Coverage Mapping

The implementation MUST cover 26 of 30 plan items browser-observable today. Items 18, 20, 21, 22,
23 (Unsplash provider real flows) are environment-gated and deferred.

| Plan items | Lane | Coverage |
|---|---|---|
| 1, 3, 5, 14-17, 19, 26, 27, 28 | `@composer-smoke-real` | Live-observable behavior |
| 2, 4, 6, 7-13, 24, 25, 29, 30 | `@composer-ui-mocked` | Stateful mocked scenarios |
| 18, 20, 21, 22, 23 | `@composer-provider-real` | **Deferred** - needs real Unsplash |

#### Scenario: Deferred rationale is recorded

- GIVEN plan items 18, 20, 21, 22, 23 are not in PR CI
- WHEN the spec is archived
- THEN a deferred-rationale note SHALL live in `verify-report.md`
- AND cite the missing environment/flag

### Requirement: Anti-Overclaim Guardrails

Scenarios MUST NOT assert backend-only invariants (exact state-machine transitions, provider import
internals, storage cleanup correctness) at the browser layer.

#### Scenario: Overclaim is removed or moved

- GIVEN a scenario asserts `PENDING_UPLOAD -> UPLOADING -> READY` transitions
- WHEN the spec is reviewed
- THEN that scenario SHALL be moved to `@backend-contract` or removed

### Requirement: Determinism Without Sleeps

Upload timing SHALL be driven through mock-side deferred responses or a test-controlled transport
seam. `page.waitForTimeout` is prohibited for deterministic assertions.

#### Scenario: No sleep-driven progress assertion

- GIVEN an overlay progress assertion
- WHEN the test waits for a transition
- THEN it SHALL await a deterministic mock release signal
- AND SHALL NOT use `waitForTimeout` to bridge real-time gaps

## Test Data and Fixtures

### Local upload fixtures

| Fixture | Purpose |
|---|---|
| `inline-image.png` | Basic valid local image for select-file and drag-drop flows |
| `inline-image-2.png` | Second valid local image for multi-file ordering checks |
| `inline-image-large.png` | Larger valid image for progress-state scenarios |
| `invalid.txt` | Unsupported file type rejection |
| `multi-first-valid/` manifest | Explicit ordered list for “first valid file only” assertions |

### Workspace media prerequisites

The test workspace SHOULD start with at least these READY assets:

- 5 unique image assets
- stable filenames for deterministic remove-button assertions
- at least one channel with attachment limit `<= 4` for limit scenarios

### Provider environments

Use two distinct environments or flag configurations:

- **provider-disabled**: `Library` only
- **provider-enabled**: `Library` + `Unsplash`

## Implementation Guidance

### Real-browser assertions to keep

Use real backend/browser flows for:

- local file selection rendering immediate blob preview;
- library selection and deselection visuals;
- inline removal of local and library assets;
- `+N` overflow rendering;
- default modal source behavior;
- absence of Unsplash when disabled.

### Mocked-browser assertions to prefer

Use stateful mocks or deterministic network control for:

- true `uploading` overlay timing;
- granular progress percentages;
- `Finishing up...` threshold behavior;
- upload failure recovery;
- focus retention during a deliberately slowed upload;
- over-limit staged-selection warnings;
- first-valid-file-only ordering checks if live backend accepts selection too quickly to observe.

### Backend-contract assertions to move out of browser E2E

Do **not** overclaim these in Playwright:

- exact state-machine transitions among `PENDING_UPLOAD`, `UPLOADING`, and `READY`;
- correctness of provider import persistence internals;
- whether transient state is cleared in every store path after failure;
- upload progress event semantics from the transport layer;
- true attachment-limit enforcement correctness across all channel/provider combinations.

Those belong to backend/store integration tests.

## Troubleshooting

### Modal interaction instability

If modal open/close is animation-sensitive in Playwright:

1. prefer a page-object method that clicks the exact dashboard `New Post` CTA;
2. wait for `CREATE POST` heading and `Post content` textbox instead of generic text;
3. avoid racing against background dashboard buttons also labeled `Create post`.

### Preview assertions

If the social preview media element is unstable:

1. assert the media source URL kind (`blob:` vs `/api/media/assets/...`) rather than pixel screenshots first;
2. add a visual snapshot only after the source transition is stable.

### Library selection styling

If utility-class names change frequently:

1. assert card-level visual delta via computed style or screenshot diff;
2. avoid coupling the test solely to a specific Tailwind class string.

### Provider coverage gaps

If Unsplash is absent in local dev:

1. mark provider-enabled tests as a separate project/profile;
2. keep provider-disabled coverage running in normal local/PR suites.

## References

- `openspec/specs/e2e/cas-media-library-test-plan.md` — broader media-library and CAS coverage
- `apps/web/app/e2e/pages/compose-modal-page.ts` — existing page object with basic composer locators
- `apps/web/app/src/composables/useComposerMediaPicker.ts` — picker staging, source switching, attachment limits, and provider import/search stubs
- `apps/web/app/src/components/composer/composer-media-picker.types.ts` — picker source and asset-state types
