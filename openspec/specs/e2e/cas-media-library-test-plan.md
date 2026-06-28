# E2E Test Plan — CAS Media Library

> Last updated: 2026-06-27  
> Primary UI: `https://pt-app.localhost:1355/media`  
> Scope: browser behavior, CAS network protocol, composer integration, and cross-layer traceability  
> Evidence: live authenticated exploration plus the authoritative CAS architecture contract

## Overview

### Purpose

This plan defines deterministic, browser-oriented coverage for the Profile Tailors Media Library and its content-addressed storage (CAS) upload flow. It MUST distinguish what a browser can observe from storage and database invariants that require non-browser tests. A browser dedup test proves the documented HTTP sequence and visible asset state; it MUST NOT be reported as proof that only one physical blob exists.

### Observed baseline

| Area | Live evidence at `/media` |
|------|---------------------------|
| Initial load | Four `READY` cards; `GET /api/media/assets?status=READY%2CPROCESSING%2CFAILED&pageSize=50` returned `200` |
| Controls | Refresh, multi-file picker, status counters, select visible, search, status/type filters, seven sort choices |
| First `og.png` upload | `PUT 201` → binary `POST 200` → asset `GET 200`; fifth `READY` card |
| Exact duplicate | `PUT 200` → canonical asset `GET`; no binary `POST`; count remained five |
| Browse | Search + Image + filename sort showed `1/5` |
| Delete | Bulk dialog: “Delete selected media assets?”; Cancel, Delete selected; Escape closes |
| Mobile | At `390×844`, sidebar hidden; controls and single-column cards remained usable |
| Accessibility gaps | Four form fields lacked `id`/`name`; card action icon buttons lacked accessible names |
| Composer | One file only; accepts `image/*,video/mp4`; immediate local preview; no library selector |

The live list still requests `PROCESSING`; the authoritative lifecycle is `PENDING_UPLOAD → UPLOADING → READY`, with `FAILED` and soft `DELETED`. Tests SHOULD flag stale `PROCESSING` terminology without treating it as the CAS contract.

### Requirements

| ID | Requirement |
|----|-------------|
| ML-R01 | Unauthenticated users MUST be denied protected media data; authenticated users MUST navigate to and load their workspace library. |
| ML-R02 | Loading, empty, failure, refresh, and cache behavior MUST preserve an accurate, recoverable UI. |
| ML-R03 | Direct library upload MUST accept multiple images, MP4, and PDF and expose progress and final state. |
| ML-R04 | CAS initiation MUST distinguish new upload, dedup, waiting, idempotency, conflict, validation, auth, throttling, and server failures. |
| ML-R05 | The browser MUST upload bytes only when initiation returns an upload-required state and MUST poll `202 WAITING_FOR_BLOB` using `Retry-After`. |
| ML-R06 | READY assets MUST support correct previews, metadata, search, filters, sorting, counters, and pagination. |
| ML-R07 | Single and bulk deletion MUST require intentional confirmation, tolerate cancellation, and persist after refresh. |
| ML-R08 | Composer MUST accept one image or MP4 up to 10 MB, publish only with a READY `assetId`, and block publication after attachment failure. |
| ML-R09 | Dedup MUST be workspace-scoped and workspace switching MUST not leak assets, requests, previews, or cached results. |
| ML-R10 | Core media flows MUST be keyboard operable, announced appropriately, and expose accessible names and dialog semantics. |
| ML-R11 | Media controls and cards MUST remain usable at mobile, tablet, and desktop viewports. |
| ML-R12 | Database, storage, lifecycle-job, and physical single-blob invariants MUST be verified below the browser layer. |

### Test Implementation Requirements

#### Requirement: Media suite organization

The test implementation MUST split CAS Media Library automation into explicit `media-ui-mocked`, `media-smoke-real`, `media-real-extended`, `media-backend-contract`, and `media-large-boundary` groups, with tags/projects matching their execution mode.

##### Scenario: Correct lane selection
- GIVEN a media E2E command runs
- WHEN tests are discovered
- THEN only scenarios matching that lane's tags/projects SHALL execute
- AND real-CAS tests SHALL NOT use mocked CAS route handlers.

##### Scenario: PR-safe coverage
- GIVEN ordinary PR CI runs
- WHEN media tests execute
- THEN mocked UI coverage SHALL be parallel-safe
- AND expensive large-boundary cases SHALL be excluded.

#### Requirement: Deterministic media fixtures

The implementation MUST generate or provide deterministic media fixtures with a manifest recording filename, type, size, SHA-256, and expected relationship.

##### Scenario: Duplicate and mutation validation
- GIVEN `base.png`, `base-copy.png`, and `base-mutated.png`
- WHEN setup validates fixtures
- THEN duplicate files SHALL have equal bytes/hash
- AND mutated files SHALL decode successfully with a different hash.

##### Scenario: Large fixture exclusion
- GIVEN browser CI runs
- WHEN fixture setup starts
- THEN 500 MB boundary fixtures SHALL NOT be loaded by browser suites.

#### Requirement: Auth/session and isolation setup

The implementation MUST provide authenticated media sessions, unique run/workspace namespaces, and idempotent cleanup for posts, assets, routes, and storage prefixes.

##### Scenario: Protected media access
- GIVEN no authenticated session
- WHEN `/media` opens
- THEN protected media data SHALL NOT be exposed.

##### Scenario: Cleanup after failure
- GIVEN a test creates run-marked assets/posts
- WHEN the test fails
- THEN teardown SHALL delete posts before assets
- AND fail if active run-owned records remain.

#### Requirement: Real CAS request ledger assertions

Real-CAS tests MUST capture an ordered per-file request ledger correlated by workspace, assetId, and fixture hash, and MUST assert CAS protocol sequences without global request counts.

##### Scenario: New content sequence
- GIVEN fresh content uploads
- WHEN CAS completes
- THEN the ledger SHALL show PUT `201 PENDING_UPLOAD`, one binary POST, and READY evidence.

##### Scenario: Dedup sequence
- GIVEN equivalent bytes are already READY
- WHEN duplicate upload starts
- THEN the ledger SHALL show READY/dedup initiation and zero binary POSTs
- AND record `200` versus documented `201` as contract drift if observed.

#### Requirement: Stateful route mocks

Mocked UI tests MUST use per-context stateful route mocks for list, upload, polling, preview, auth, rate-limit, error, pagination, deletion, and workspace-switch behavior.

##### Scenario: Mock reset
- GIVEN two mocked tests run in parallel
- WHEN each browser context starts
- THEN each SHALL receive isolated mock state
- AND handlers SHALL be removed after the test.

##### Scenario: Failure modeling
- GIVEN mocked PUT returns `429` or `5xx`
- WHEN upload runs
- THEN POST SHALL NOT follow failed initiation.

#### Requirement: Known-defect handling

The suite MUST expose known product drift and defects without suppressing, normalizing, or treating them as implemented behavior.

##### Scenario: Accessibility defects
- GIVEN unnamed icon actions or fields without stable `id`/`name`
- WHEN accessibility checks run
- THEN failures SHALL be reported as known defects with trace evidence.

##### Scenario: Product limitation
- GIVEN composer media controls are inspected
- WHEN no library selector exists
- THEN the suite SHALL record the limitation, not fail unrelated flows.

#### Requirement: Backend-only invariant exclusion

Browser E2E reports MUST NOT claim proof for physical blob uniqueness, DB locks, GC/reference counting, streaming memory, or 500 MB enforcement; those SHALL belong to backend-contract suites.

##### Scenario: Browser report boundary
- GIVEN a browser dedup test passes
- WHEN results are reported
- THEN the report SHALL state only observable CAS sequence and UI state were proven.

##### Scenario: Backend ownership
- GIVEN a backend-only invariant is required
- WHEN coverage is assigned
- THEN it SHALL map to WebFlux/PostgreSQL/storage tests, not Playwright.

### Surface contract

| Surface | Accepted files | Limits and behavior |
|---------|----------------|---------------------|
| Media Library UI | `image/*`, `video/mp4`, `application/pdf`; multi-file | Browser picker contract; backend maximum is 500 MB |
| Create Post composer | `image/*`, `video/mp4`; one file | Client maximum 10 MB; immediate local preview; no existing-library selector |
| Backend | Images, MP4, PDF, and supported document types | Up to 500 MB; validates declared metadata, bytes, hash, and magic bytes |

### Execution modes and test layers

| Mode | Layer | Purpose | Data policy |
|------|-------|---------|-------------|
| `@real-cas` | Playwright + real backend, PostgreSQL, and test storage | P0 CAS sequences, persistence, tenant isolation, signed previews, composer publication | Unique workspaces/run prefix; real bytes; no route interception of CAS endpoints |
| `@mocked-ui` | Playwright with stateful route handlers | Deterministic loading, empty, cache, progress, partial failures, 401/429/5xx, timeout, accessibility, responsive UI | Per-test in-memory state machine reset before every test |
| `@backend-contract` | WebFlux/integration + PostgreSQL/storage tests; no browser | Validation order, 422 corruption paths, 500 MB boundary, constraints, locks, reference counting, GC | Transactional DB fixtures and isolated storage prefix/container |

`@real-cas` tests SHOULD run serially only inside a scenario that intentionally shares state. All other tests MUST be independent and parallel-safe.

## Changes

This artifact adds the first comprehensive CAS Media Library test plan. Compared with the existing scheduler plan, it adds:

- explicit deterministic binary fixtures and expected relationships;
- three execution modes instead of treating every assertion as browser E2E;
- network-sequence assertions for CAS outcomes;
- workspace/run isolation and cleanup rules;
- requirement-to-scenario traceability;
- documented current limitations and accessibility defects;
- backend-only coverage so browser results are not overstated.

## Usage

### Prerequisites

| Requirement | Check |
|-------------|-------|
| Services | App, Spring Boot backend, PostgreSQL, and configured test object storage are healthy |
| HTTPS | `pt-app.localhost` certificate is trusted by the Playwright worker |
| Accounts | Two authenticated users/workspaces: `WS-A-{runId}` and `WS-B-{runId}` |
| Permissions | Test principal may upload, list, preview, delete, switch workspaces, and create posts |
| Isolation APIs | Seed/cleanup helper can create workspaces and query/delete run-owned assets and posts |
| Browser capture | Trace, screenshot, console, failed response, and request log enabled |
| Clock | Stable test timezone; controllable clock for signed-URL and cache tests where required |

Never run destructive real-backend cases against a shared developer workspace. Each run MUST use assets whose filenames start with `e2e-cas-{runId}-`.

### Deterministic fixtures

Generate fixtures once in test setup and record exact byte size and SHA-256 in a manifest.

| Fixture | Construction | Expected relationship |
|---------|--------------|-----------------------|
| `base.png` | Small valid PNG with fixed bytes | Canonical source |
| `base-copy.png` | Byte-for-byte copy of `base.png` | Same hash, different filename; dedup |
| `base-mutated.png` | Copy with exactly one non-structural byte changed while remaining decodable | Different hash; distinct asset/blob |
| `similar.png` | Independently encoded visually similar image | Similar appearance, different bytes/hash |
| `photo.jpg` | Valid JPEG | Image preview/type filter |
| `clip.mp4` | Short fixed MP4 | Video preview |
| `document.pdf` | One-page fixed PDF | PDF preview |
| `empty.png` | Zero bytes | Rejected before upload |
| `text.txt` | Plain text | Rejected by library UI |
| `png-named.jpg` | PNG bytes, JPEG extension/type declaration for contract injection | Magic/MIME mismatch path |
| `unsafe-name.png` variants | Unicode, spaces, 255 chars, 256 chars, `../`, slash, backslash, null-byte API payload | Filename boundaries and sanitization |
| `composer-10mb.mp4` | Exactly 10 MiB, valid MP4 | Composer boundary accepted |
| `composer-over-10mb.mp4` | 10 MiB + 1 byte, valid MP4 | Composer client rejection |
| `backend-500mb.bin` | Supported valid container padded to exactly configured 500 MB | Backend boundary contract |
| `backend-over-500mb.bin` | Boundary + 1 byte | Backend `413` contract |

The mutation generator MUST assert `sha256(base) != sha256(mutated)` and image decoding before tests start. The duplicate generator MUST assert byte equality and equal hashes. Large fixtures SHOULD be sparse/generated in the backend suite and MUST NOT burden ordinary browser CI.

### Isolation and cleanup

1. Create unique workspaces or a unique run namespace before the suite; never depend on the four assets seen during exploration.
2. Give every asset and post a `runId` filename/content marker. Record returned `assetId` and post ID immediately.
3. Before each independent test, seed only its declared GIVEN state. Do not use test order as setup.
4. After each test, delete created posts first, then media assets. Cleanup MUST be idempotent and run after failures.
5. After the real suite, query by run marker in both workspaces and fail teardown if active assets/posts remain.
6. Storage cleanup MUST target only the run prefix. Physical CAS cleanup assertions belong to storage tests, not browser teardown.
7. Mocked tests MUST create a fresh route-state object per browser context and remove all handlers afterward.
8. Concurrency tests may share one scenario-local fixture across two isolated contexts, but MUST synchronize with barriers rather than sleeps.

### Browser scenario catalog

#### Authentication, navigation, loading, and cache

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-AUTH-001 | P0 | `@real-cas @auth @smoke` | **GIVEN** no authenticated session **WHEN** `/media` is opened **THEN** login/denial is shown and no successful asset-list response exposes data. |
| ML-AUTH-002 | P0 | `@real-cas @navigation @smoke` | **GIVEN** an authenticated WS-A user **WHEN** Media Library is selected **THEN** `/media` loads and the scoped list request returns `200`. |
| ML-LOAD-001 | P1 | `@mocked-ui @loading` | **GIVEN** a delayed list response **WHEN** `/media` opens **THEN** a non-destructive loading state appears and controls do not present stale results as current. |
| ML-LOAD-002 | P1 | `@mocked-ui @empty` | **GIVEN** an empty `200` page **WHEN** loading completes **THEN** the empty state, zero counters, and upload action are available. |
| ML-LOAD-003 | P1 | `@mocked-ui @error` | **GIVEN** list `500` **WHEN** loading completes **THEN** an error is announced, no false empty state appears, and retry is available. |
| ML-LOAD-004 | P1 | `@real-cas @refresh` | **GIVEN** server state changed externally **WHEN** Refresh is activated **THEN** a new GET occurs once and cards/counters reconcile to the response. |
| ML-LOAD-005 | P2 | `@mocked-ui @cache` | **GIVEN** cached cards and a delayed revalidation **WHEN** revisiting `/media` **THEN** cache behavior is explicit, stale data is not duplicated, and revalidation wins. |
| ML-LOAD-006 | P1 | `@mocked-ui @auth` | **GIVEN** list GET returns `401` **WHEN** refresh/re-auth succeeds **THEN** the request is retried once without duplicate cards or an infinite auth loop. |

#### Upload and navigation behavior

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-UP-001 | P0 | `@real-cas @upload @image` | **GIVEN** fresh `base.png` **WHEN** uploaded **THEN** PUT `201 PENDING_UPLOAD` precedes binary POST `200 READY`, asset GET succeeds, one READY card appears, and counters increase once. |
| ML-UP-002 | P0 | `@real-cas @upload @multi` | **GIVEN** fresh PNG, MP4, and PDF **WHEN** selected together **THEN** each has an independent CAS sequence/progress result and all successful cards become READY. |
| ML-UP-003 | P1 | `@mocked-ui @progress @aria-live` | **GIVEN** a throttled binary upload **WHEN** bytes are sent **THEN** progress/loading toast is visible and announced, then resolves exactly once to success. |
| ML-UP-004 | P1 | `@real-cas @navigation` | **GIVEN** a deliberately slow upload **WHEN** the user navigates away and returns **THEN** defined leave behavior is honored, no duplicate initiation occurs, and final server state is reconciled. |
| ML-UP-005 | P1 | `@mocked-ui @multi @partial-failure` | **GIVEN** three files where one fails **WHEN** batch upload completes **THEN** two successes remain, one actionable failure is identified, and aggregate counters are accurate. |
| ML-UP-006 | P2 | `@real-cas @refresh` | **GIVEN** an upload completed **WHEN** the page reloads **THEN** its READY card is fetched from the server without resubmitting PUT/POST. |

#### CAS identity, idempotency, and concurrency

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-CAS-001 | P0 | `@real-cas @dedup @sequential` | **GIVEN** `base.png` is READY **WHEN** the exact file is uploaded again **THEN** PUT returns a ready existing/dedup state (`201` per contract; observed deployment may return idempotent `200`), no binary POST occurs, canonical asset GET succeeds, and visible count does not increase for the observed canonical behavior. |
| ML-CAS-002 | P0 | `@real-cas @dedup @filename` | **GIVEN** `base.png` is READY **WHEN** `base-copy.png` is uploaded **THEN** equal hash drives dedup despite filename difference and no second binary POST occurs. |
| ML-CAS-003 | P0 | `@real-cas @mutation` | **GIVEN** `base.png` is READY **WHEN** the valid one-bit mutation uploads **THEN** a new PUT→POST flow occurs and a distinct READY asset is visible. |
| ML-CAS-004 | P1 | `@real-cas @similar-image` | **GIVEN** `base.png` is READY **WHEN** visually similar independently encoded bytes upload **THEN** they are not deduplicated. |
| ML-CAS-005 | P0 | `@real-cas @reload` | **GIVEN** `base.png` is READY and the browser reloads **WHEN** the duplicate uploads **THEN** server-side dedup still avoids binary POST. |
| ML-CAS-006 | P0 | `@real-cas @concurrency @polling` | **GIVEN** two fresh contexts in WS-A select identical bytes behind a barrier **WHEN** both initiate **THEN** one flow uploads while the other observes `202 WAITING_FOR_BLOB`, honors `Retry-After`, polls until READY, and does not upload duplicate bytes. |
| ML-CAS-007 | P1 | `@backend-contract @idempotency` | **GIVEN** an asset ID/hash exists **WHEN** PUT repeats the same pair **THEN** `200` returns current state and no duplicate asset is created. |
| ML-CAS-008 | P1 | `@backend-contract @conflict` | **GIVEN** an asset ID is bound to hash A **WHEN** PUT reuses it with hash B **THEN** `409 ASSET_HASH_MISMATCH` is returned and original state remains. |
| ML-CAS-009 | P1 | `@mocked-ui @polling-timeout` | **GIVEN** every PUT returns `202` **WHEN** the polling budget expires **THEN** polling stops, timeout/retry guidance is announced, and no POST occurs. |

#### Validation, interruption, and protocol failures

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-VAL-001 | P1 | `@real-cas @validation` | **GIVEN** a zero-byte file **WHEN** selected **THEN** it is rejected with actionable size feedback and no binary POST occurs. |
| ML-VAL-002 | P1 | `@real-cas @validation` | **GIVEN** `.txt` **WHEN** the library picker/drop path receives it **THEN** unsupported type feedback appears and CAS is not initiated. |
| ML-VAL-003 | P1 | `@backend-contract @magic-bytes` | **GIVEN** MIME/extension disagree with magic bytes **WHEN** bytes upload **THEN** the documented detected-type policy is enforced and canonical type is server-authoritative. |
| ML-VAL-004 | P0 | `@backend-contract @integrity` | **GIVEN** declared hash differs from bytes **WHEN** POST completes **THEN** `422 HASH_MISMATCH` and FAILED state result; temp data is removed by storage verification. |
| ML-VAL-005 | P0 | `@backend-contract @integrity` | **GIVEN** declared size differs from received bytes **WHEN** POST completes **THEN** `422 FILE_SIZE_MISMATCH` and FAILED state result. |
| ML-VAL-006 | P0 | `@real-cas @composer @boundary` | **GIVEN** valid files at 10 MiB and 10 MiB + 1 **WHEN** attached in composer **THEN** the boundary file previews and the larger file is rejected before publication. |
| ML-VAL-007 | P1 | `@backend-contract @boundary @large` | **GIVEN** supported files at backend 500 MB and +1 byte **WHEN** PUT is called **THEN** the boundary follows configured acceptance and the larger request returns `413`; no browser proof is claimed. |
| ML-VAL-008 | P1 | `@backend-contract @filename` | **GIVEN** spaces, Unicode, 255/256 chars, traversal separators, and null byte cases **WHEN** PUT validates filenames **THEN** valid names survive safely and invalid names return `400` without state creation. |
| ML-ERR-001 | P1 | `@mocked-ui @network` | **GIVEN** upload transport drops mid-POST **WHEN** the request fails **THEN** progress stops, failure is actionable, no READY card is fabricated, and retry does not duplicate success. |
| ML-ERR-002 | P1 | `@mocked-ui @5xx` | **GIVEN** PUT or POST returns `5xx` **WHEN** upload runs **THEN** phase-specific failure is shown and POST never follows a failed PUT. |
| ML-ERR-003 | P1 | `@mocked-ui @auth` | **GIVEN** PUT/POST returns `401` **WHEN** session refresh succeeds **THEN** the safe phase retries once; failure to refresh returns to authentication without leaking state. |
| ML-ERR-004 | P1 | `@mocked-ui @rate-limit` | **GIVEN** PUT returns `429` with `Retry-After` **WHEN** upload is attempted **THEN** wait guidance is shown, no POST occurs, and automatic retry does not hammer the endpoint. |

#### Browsing, preview, query, and pagination

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-BROWSE-001 | P0 | `@real-cas @preview` | **GIVEN** READY PNG/JPEG **WHEN** cards load/open **THEN** signed content renders as an image with correct name and size. |
| ML-BROWSE-002 | P1 | `@real-cas @preview @video` | **GIVEN** READY MP4 **WHEN** previewed **THEN** video controls/load state work without treating it as an image. |
| ML-BROWSE-003 | P1 | `@real-cas @preview @pdf` | **GIVEN** READY PDF **WHEN** preview/open is activated **THEN** PDF behavior is usable and remains inside the authenticated asset contract. |
| ML-BROWSE-004 | P1 | `@mocked-ui @signed-url` | **GIVEN** expired signed URL **WHEN** preview fails **THEN** the client refreshes metadata/URL or shows recoverable feedback without exposing storage details. |
| ML-BROWSE-005 | P1 | `@real-cas @security` | **GIVEN** a valid signed URL **WHEN** its key/signature is tampered **THEN** content is denied and no other asset is revealed. |
| ML-BROWSE-006 | P1 | `@mocked-ui @non-ready` | **GIVEN** PENDING_UPLOAD, UPLOADING, and FAILED assets **WHEN** listed **THEN** no READY preview URL is used and each state has accurate action/status UI; `PROCESSING` is not treated as canonical. |
| ML-BROWSE-007 | P0 | `@real-cas @search @filter @sort` | **GIVEN** five mixed assets **WHEN** search + Image + filename A–Z applies **THEN** the expected `1/5` subset/order is shown and clearing restores all. |
| ML-BROWSE-008 | P1 | `@real-cas @filters` | **GIVEN** mixed READY/FAILED and image/video/PDF assets **WHEN** each status/type filter is selected **THEN** every visible card matches and combinations intersect correctly. |
| ML-BROWSE-009 | P1 | `@real-cas @sort` | **GIVEN** known timestamps, filenames, sizes, and statuses **WHEN** newest, oldest, A–Z, Z–A, largest, smallest, and status sorts run **THEN** deterministic order matches fixture metadata. |
| ML-BROWSE-010 | P1 | `@mocked-ui @counters` | **GIVEN** status changes and active filters **WHEN** data reconciles **THEN** READY/UPLOADING-or-current/FAILED counters represent defined total scope, not accidental visible subset. |
| ML-BROWSE-011 | P1 | `@mocked-ui @pagination` | **GIVEN** more than 50 assets **WHEN** the next page/continuation loads **THEN** no asset is skipped/duplicated and filters/sorts remain stable. |

#### Delete and references

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-DEL-001 | P1 | `@real-cas @delete @single` | **GIVEN** one selected READY asset **WHEN** single delete is canceled **THEN** no DELETE request occurs and the card remains selected/visible as defined. |
| ML-DEL-002 | P0 | `@real-cas @delete @single` | **GIVEN** one disposable asset **WHEN** deletion is confirmed **THEN** DELETE `200` occurs, card/counters update, and refresh does not restore it. |
| ML-DEL-003 | P1 | `@real-cas @delete @bulk @dialog` | **GIVEN** multiple selected cards **WHEN** bulk delete opens **THEN** “Delete selected media assets?” with Cancel/Delete selected is shown; Escape closes it with no DELETE. |
| ML-DEL-004 | P0 | `@real-cas @delete @bulk` | **GIVEN** three disposable selections **WHEN** Delete selected is confirmed **THEN** each intended asset is deleted once, selection clears, counters reconcile, and refresh preserves removal. |
| ML-DEL-005 | P1 | `@backend-contract @idempotency` | **GIVEN** an already DELETED asset **WHEN** DELETE repeats **THEN** `200` idempotent response occurs without repeated GC scheduling. |
| ML-DEL-006 | P1 | `@mocked-ui @partial-failure` | **GIVEN** one of three DELETEs fails **WHEN** bulk deletion settles **THEN** successful cards stay removed, failed card remains identifiable/selected, and retry targets only failure. |
| ML-DEL-007 | P0 | `@real-cas @references` | **GIVEN** two deduplicated assets reference the same content **WHEN** one is deleted **THEN** the other remains READY and previewable. |
| ML-DEL-008 | P1 | `@real-cas @composer-reference` | **GIVEN** a post references a READY asset **WHEN** library deletion is attempted **THEN** documented reference behavior is enforced and the post never silently points to unusable media. |

#### Create Post integration

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-COMP-001 | P0 | `@real-cas @composer @upload` | **GIVEN** Create Post is open **WHEN** one valid image/MP4 is selected **THEN** immediate local preview appears, CAS completes, and attachment transitions to READY. |
| ML-COMP-002 | P1 | `@real-cas @composer @selection` | **GIVEN** a local preview **WHEN** it is removed then another file selected **THEN** only the final file is attached and stale upload state is not published. |
| ML-COMP-003 | P0 | `@mocked-ui @composer @failure` | **GIVEN** attachment upload fails **WHEN** publication is attempted **THEN** publication is blocked with actionable feedback and no post request references a failed/pending asset. |
| ML-COMP-004 | P0 | `@real-cas @composer @publication` | **GIVEN** attachment is READY **WHEN** a post is published/scheduled **THEN** the post request uses its READY `assetId`, not a local URL or raw bytes. |
| ML-COMP-005 | P0 | `@real-cas @composer @dedup` | **GIVEN** the file already exists in WS-A **WHEN** attached in composer **THEN** dedup avoids binary POST and publication references the READY canonical asset identity returned by the server. |
| ML-COMP-006 | P1 | `@real-cas @navigation` | **GIVEN** composer upload completed **WHEN** the user navigates to Media Library **THEN** server-backed READY state appears without relying on the local preview. |
| ML-COMP-007 | P2 | `@real-cas @limitation` | **GIVEN** Create Post is open **WHEN** media controls are inspected **THEN** one-file `image/*,video/mp4` input exists and no Browse Media Library selector is present; record as current limitation, not a failure. |
| ML-COMP-008 | P1 | `@real-cas @validation` | **GIVEN** PDF or multiple files **WHEN** supplied to composer **THEN** they are unavailable/rejected while the direct library still accepts PDF and multiple files. |

#### Workspace isolation and switching

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-WS-001 | P0 | `@real-cas @tenant-isolation` | **GIVEN** identical bytes are READY in WS-A **WHEN** uploaded in WS-B **THEN** WS-B performs its own upload flow; WS-A dedup state is not reused across tenants. |
| ML-WS-002 | P0 | `@real-cas @workspace-switch` | **GIVEN** distinct fixtures in WS-A and WS-B **WHEN** switching workspaces **THEN** requests use the selected workspace and cards, counters, search, selections, and previews are replaced without leakage. |
| ML-WS-003 | P1 | `@real-cas @authorization` | **GIVEN** a WS-A asset ID/preview URL and WS-B session **WHEN** direct access is attempted **THEN** access is denied without existence-sensitive disclosure. |
| ML-WS-004 | P1 | `@mocked-ui @cache` | **GIVEN** cached WS-A results **WHEN** switching to WS-B under delayed network **THEN** WS-A cards are not rendered as WS-B data. |

#### Accessibility and responsive behavior

| ID | P | Mode / tags | Given / When / Then |
|----|---|-------------|---------------------|
| ML-A11Y-001 | P0 | `@mocked-ui @keyboard` | **GIVEN** keyboard-only input **WHEN** navigating upload, refresh, search, filters, sort, selection, cards, and delete **THEN** focus order is logical and every action is operable with visible focus. |
| ML-A11Y-002 | P0 | `@mocked-ui @dialog` | **GIVEN** bulk dialog opens **WHEN** Tab/Shift+Tab/Escape operate **THEN** focus is trapped, initial focus is safe, Escape cancels, and focus returns to invoker. |
| ML-A11Y-003 | P1 | `@mocked-ui @aria-live` | **GIVEN** upload/progress/success/failure and refresh errors **WHEN** state changes **THEN** concise non-duplicated announcements occur without stealing focus. |
| ML-A11Y-004 | P0 | `@mocked-ui @known-defect` | **GIVEN** media cards **WHEN** accessibility names are inspected **THEN** each icon-only action has a unique accessible name; currently unnamed buttons MUST be reported as a defect. |
| ML-A11Y-005 | P1 | `@mocked-ui @known-defect` | **GIVEN** search/filter/sort/upload fields **WHEN** form semantics are inspected **THEN** each has a label and stable `id` or `name`; the observed four missing fields MUST be reported. |
| ML-A11Y-006 | P1 | `@mocked-ui @semantics` | **GIVEN** counters, cards, selections, previews, and errors **WHEN** inspected with an accessibility snapshot **THEN** roles, states, names, alt text, and selected/disabled status are meaningful. |
| ML-RWD-001 | P0 | `@real-cas @mobile` | **GIVEN** `390×844` **WHEN** `/media` loads and core flows run **THEN** sidebar is hidden, controls remain reachable, and cards form one usable column without horizontal clipping. |
| ML-RWD-002 | P1 | `@real-cas @tablet` | **GIVEN** representative portrait/landscape tablet viewports **WHEN** browsing/selecting/uploading **THEN** controls wrap predictably and dialogs/previews stay within viewport. |
| ML-RWD-003 | P1 | `@real-cas @desktop` | **GIVEN** `1280×800` and `1440×900` **WHEN** large datasets and filters render **THEN** grid density, toolbar, bulk actions, and card metadata remain usable. |
| ML-RWD-004 | P2 | `@mocked-ui @zoom` | **GIVEN** 200% browser zoom **WHEN** core controls and dialog are used **THEN** content reflows without lost actions or two-dimensional page scrolling. |

### Network observability assertions

For every upload scenario, capture an ordered per-file ledger:

| Outcome | Required observable sequence |
|---------|------------------------------|
| New content | `PUT 201 PENDING_UPLOAD` → one binary `POST 200 READY` → optional asset/list GET; exactly one new READY result |
| READY dedup | PUT returns READY/dedup (`201` authoritative contract; live canonical behavior observed `200`) → asset/list GET; **zero binary POSTs** |
| Waiting | `PUT 202 WAITING_FOR_BLOB` + `Retry-After` → delayed PUT poll(s) → READY; losing client sends zero binary POSTs |
| Same asset/hash | repeated PUT `200`; no new asset and no binary POST |
| Asset/hash mismatch | PUT `409 ASSET_HASH_MISMATCH`; no binary POST and no state replacement |
| Integrity failure | upload POST `422 HASH_MISMATCH` or `FILE_SIZE_MISMATCH`; no READY presentation |
| Rate limit | PUT `429` + `Retry-After`; no POST |
| Failed initiation | PUT `4xx/5xx`; no POST |

Assertions MUST correlate requests by workspace, `assetId`, and fixture hash; global request counts are unsafe under parallel execution. Record the documented-versus-live dedup status discrepancy as a contract drift risk until resolved.

### Backend-only invariants and ownership

| Invariant browser E2E cannot prove | Required suite | Evidence |
|------------------------------------|----------------|----------|
| Exactly one physical blob per `(workspace_id,file_hash)` | PostgreSQL + real storage integration | One blob row, one canonical object, multiple asset references |
| Server computes SHA-256 and does not trust client hash | WebFlux/storage integration | Mutated body returns `422`; canonical object absent |
| Temp object deletion after hash/size failure or race | Storage integration | Temp prefix empty after request |
| `storage_key` null before READY and non-null at READY | PostgreSQL constraint test | Insert/update constraint outcomes |
| READY blob metadata non-null | PostgreSQL constraint test | `storage_key`, detected type, size checks |
| Active reference count excludes FAILED/DELETED | PostgreSQL repository integration | Exact counts and GC transition |
| DELETE locks blob before counting | Concurrent PostgreSQL integration | No orphan/delete race |
| Canonical extension derives from detected magic bytes | Storage integration | Canonical key suffix matches detected type |
| 500 MB enforcement and streaming memory behavior | WebFlux large-body/performance test | Boundary statuses and bounded memory |
| Creation rate limit is 200/hour/workspace before writes | Integration with controllable clock | 201/200 behavior through limit, then `429`; row counts unchanged |
| 24-hour PENDING/UPLOADING expiration | Scheduled-job + PostgreSQL test | FAILED reason and reference reevaluation |
| Seven-day GC, batch 100, retry cap 5 | Job + PostgreSQL + storage test | Eligibility, deletion, retained row, failure counts |
| `FOR UPDATE SKIP LOCKED` prevents double GC | Concurrent PostgreSQL test | Each candidate handled once |
| Blob row survives physical garbage collection | PostgreSQL + storage integration | Object gone; row `GARBAGE_COLLECTED` remains |
| Cross-workspace canonical storage separation | PostgreSQL + storage integration | Separate rows and keys for equal hashes |
| Soft DELETED data is excluded from normal lists | Repository/API integration | Default list omits deleted asset |

### Requirement traceability

| Requirement | Scenarios |
|-------------|-----------|
| ML-R01 | ML-AUTH-001–002, ML-LOAD-006, ML-WS-003 |
| ML-R02 | ML-LOAD-001–006, ML-UP-006 |
| ML-R03 | ML-UP-001–006, ML-VAL-001–002 |
| ML-R04 | ML-CAS-001–009, ML-VAL-003–008, ML-ERR-001–004 |
| ML-R05 | ML-UP-001–005, ML-CAS-001–009, network observability matrix |
| ML-R06 | ML-BROWSE-001–011 |
| ML-R07 | ML-DEL-001–008 |
| ML-R08 | ML-COMP-001–008, ML-VAL-006 |
| ML-R09 | ML-WS-001–004 |
| ML-R10 | ML-A11Y-001–006 |
| ML-R11 | ML-RWD-001–004 |
| ML-R12 | Backend-only invariants table |

### Automation grouping

| Group | Contents | Cadence |
|-------|----------|---------|
| `media-smoke-real` | AUTH-002, UP-001, CAS-001/003/006, BROWSE-001/007, DEL-002, COMP-004, WS-001, RWD-001 | Required pre-merge when real services are available; serial CAS subgroup |
| `media-ui-mocked` | Loading/error/cache, progress, partial failure, auth/429/5xx, polling timeout, accessibility, responsive zoom | Every PR; parallel |
| `media-real-extended` | Multi-file, every preview/sort/filter, signed URLs, delete references, composer dedup, workspace switching | Nightly and release candidate |
| `media-backend-contract` | All `@backend-contract` scenarios and backend-only invariants | Every backend PR; PostgreSQL/storage subsets as configured |
| `media-large-boundary` | 500 MB and streaming/resource checks | Nightly/manual due runtime and storage cost |

Retry policy MUST not hide product failures: zero automatic retries for deterministic contract tests; at most one CI retry with trace retention for known infrastructure flake. Always retain request ledger, console, screenshot, and trace on failure.

## Troubleshooting

| Symptom | Investigation |
|---------|---------------|
| Duplicate test unexpectedly POSTs bytes | Confirm fixture hashes are identical, workspace is unchanged, first asset is READY, and cleanup did not remove the canonical reference |
| Concurrent test never sees `202` | Use a backend barrier/delayed first POST; do not add arbitrary sleeps |
| Live dedup returns `200` instead of documented `201` | Preserve body/sequence assertion, report contract drift, and do not silently normalize status |
| List still queries `PROCESSING` | Record stale frontend terminology; authoritative states remain PENDING_UPLOAD/UPLOADING/READY/FAILED/DELETED |
| Preview intermittently fails | Capture signed URL expiry, clock skew, response status, and whether metadata refresh was attempted |
| Cleanup cannot find an asset | Query by recorded `assetId`, workspace, and run marker; treat already DELETED as successful idempotent cleanup |
| Cross-test card count differs | Remove assumptions about shared seed counts; assert deltas within the isolated run workspace |
| Accessibility test fails on icon buttons/fields | Link the known unnamed-button or missing `id`/`name` issue; do not suppress the violation |
| 500 MB case destabilizes browser CI | Move it to `media-large-boundary`; keep browser coverage at practical sizes and verify the limit below the browser |

## References

- [`docs/architecture/media-library-cas-dedup.md`](../../../docs/architecture/media-library-cas-dedup.md) — authoritative CAS lifecycle and API contract
- [`openspec/specs/media-asset-dedup/spec.md`](../media-asset-dedup/spec.md) — detailed CAS requirements and backend scenarios
- [`openspec/specs/e2e/scheduler-posts-test-plan.md`](./scheduler-posts-test-plan.md) — repository E2E plan style reference
- Live exploration: `https://pt-app.localhost:1355/media`, 2026-06-27
