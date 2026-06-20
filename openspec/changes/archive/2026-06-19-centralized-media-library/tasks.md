# Tasks: Centralized Media Library

## Review Workload Forecast

| Field                      | Value                                                                                                     |
|----------------------------|-----------------------------------------------------------------------------------------------------------|
| Review budget              | 400 changed lines                                                                                         |
| Estimated workload         | High                                                                                                      |
| Chained PRs recommended    | Yes                                                                                                       |
| Proposed delivery strategy | stacked-prs                                                                                               |
| Work-unit balance          | Slice by deliverable: media backend API, publishing boundary, SPA media workflow, then verification/docs. |

## Phase 1: Media Foundation

- [x] 1.1 Create `server/smp/src/main/kotlin/com/profiletailors/smp/media/domain/` models and
  lifecycle rules for workspace-scoped assets (`UPLOADED`, `PROCESSING`, `READY`, `FAILED`) with
  deterministic UUID v4 `storageKey` generation. Asset identifiers MUST be UUID v4 — sequential or
  predictable identifiers are explicitly prohibited. Include `uploadStartedAt` (nullable timestamp)
  in the domain model and schema. Document lifecycle transitions: `PROCESSING` → `READY` | `FAILED`,
  `FAILED` → `PROCESSING` (retry).
- [x] 1.2 Add `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/` commands,
  queries, and media-owned publishing resolution port for create, upload completion/failure, list,
  get, and resolve-ready-assets flows.
- [x] 1.3 Implement
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/` repository
  adapters and any minimal schema/repository changes needed to store media-owned records,
  newest-first paging, workspace filtering, missing-id detection, and stale incomplete cleanup.
  Persist `uploadStartedAt` as part of the conditional upload-start update. FAILED transitions MUST
  reset `uploadStartedAt` to NULL to keep FAILED assets immediately retryable. Add
  `workspace_upload_slots` table or equivalent per-workspace concurrent upload counter record used
  for atomic cap enforcement.
- [x] 1.4 Before reusing the existing attachments bucket, audit the following and document
  findings: (1) object lifecycle rules — confirm none auto-expire objects under the `assets/`
  prefix; (2) CORS configuration — confirm it permits backend multipart upload flows; (3) IAM/access
  policies — confirm media assets are not inadvertently exposed to publishing-scoped consumers; (4)
  server-side encryption settings — confirm compatibility. This task is BLOCKED until all four audit
  sub-points are verified and documented (in a comment, ADR, or implementation note). If any
  conflict is found, update the storage configuration (provision a separate media bucket) before
  proceeding to Phase 2. See `docs/architecture/adr-media-library-storage.md` for findings.

## Phase 2: Media API and Upload Flow

- [x] 2.1 Add `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/http/`
  controllers/DTOs for `POST /api/media/assets`, `POST /api/media/assets/{assetId}/upload`,
  `GET /api/media/assets`, and `GET /api/media/assets/{assetId}` using `Mediator`, WebFlux, and
  `X-Workspace-Id` conventions. Configure media context health indicators for DB and storage
  reachability in the Spring Boot Actuator readiness probe. Configure liveness probe in the Spring
  Boot Actuator in addition to the readiness probe.
- [x] 2.2 Wire backend-managed multipart upload through `shared/storage` and any minimal
  `shared/storage/src/main/kotlin/com/profiletailors/storage/` changes needed for streaming upload,
  file-size capture, and best-effort cleanup without full in-memory buffering. The upload endpoint
  MUST enforce a maximum request duration of 10 minutes; timeout closes the connection and
  transitions the asset to `FAILED`. Storage write retry policy for MVP is single-attempt
  streaming — no silent mid-stream retries.
- [x] 2.3 Enforce media allowlist, 500 MB limit (via `Content-Length` pre-check returning HTTP 413
  and streaming byte counter), magic-byte server-side type inspection (JPEG/PNG/GIF/WEBP/MP4) plus
  `Content-Type`+extension cross-check for OOXML formats, upload conflict handling (HTTP 409
  `ASSET_UPLOAD_CONFLICT` for `READY` assets; HTTP 409 `ASSET_UPLOAD_IN_PROGRESS` for actively
  uploading assets; `FAILED` assets are retryable via the conditional `uploadStartedAt` update),
  cross-workspace not-found semantics, and consistent error codes for unsupported type, missing
  asset, not-ready, rate limit exceeded (HTTP 429 + `Retry-After`), and conflict cases. Enforce rate
  limits using distributed DB-level counters (not in-memory per-instance counters): concurrent
  upload cap enforced via atomic workspace-level counter (SELECT FOR UPDATE or atomic
  increment/decrement); per-asset `uploadStartedAt` enforces per-asset slot atomicity. Max 5
  concurrent uploads per workspace, max 200 creation requests per workspace per hour.
- [x] 2.4 Add stale incomplete asset reconciler so abandoned `PROCESSING` assets (older than 2
  hours, no upload activity within the 30-minute grace period) transition to `FAILED`. The
  reconciler MUST attempt best-effort storage object deletion for each stale PROCESSING asset
  transitioned to FAILED. Reconciler MUST also scan FAILED assets with logged failed cleanups and
  retry storage object deletion. The reconciler MUST run at minimum every 15 minutes. The reconciler
  MUST emit structured metrics per run: `recordsScanned`, `recordsTransitioned`, `durationMs`,
  `errors`. An alert MUST be configured to fire when `errors > 0` on 3 consecutive runs. Run history
  retained for 7 days.

## Phase 3: Publishing Integration

- [x] 3.1 Update `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/`
  publication create handler to validate `assetIds` through the media-owned resolve-ready-assets
  port instead of publishing-owned asset lifecycle logic. Handle media context unavailability by
  returning HTTP 503 with `MEDIA_SERVICE_UNAVAILABLE` error code; do not silently skip asset
  validation. Enforce the 5-second timeout for the media resolve-ready-assets port call; throw
  `MediaServiceUnavailableException` on timeout and return HTTP 503. Implement
  `media.context.integration.enabled` feature flag to allow rollback to legacy asset resolution
  without full redeployment.
- [x] 3.1b Update `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/`
  publication edit handler to validate `assetIds` through the media-owned resolve-ready-assets port
  on edit, applying the same `READY` + workspace-scoped validation rules as publication creation.
- [x] 3.2 Update `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/` and
  infrastructure adapters so LinkedIn and worker flows continue consuming storage-backed asset
  metadata from media-owned records without broad provider refactors. Provider adapters MUST handle
  storage-unavailable errors at execution time and propagate them as publication failures, not
  silent success.
- [x] 3.3 Add compatibility handling for any existing publishing callers or reused persistence
  structures so legacy asset rows remain readable during the transition.

## Phase 4: SPA Media Workflow

- [x] 4.1 Create `apps/web/app/src/lib/media-api.ts` with typed media-library reserve, upload, list,
  and get helpers that reuse authenticated workspace-scoped request patterns from `auth-api.ts`
  without co-locating media domain concerns inside auth infrastructure code.
- [x] 4.2 Add a focused media-library store/composable under `apps/web/app/src/stores/` or
  `src/composables/` for create/upload/list/paging/progress/retry state and selected persisted asset
  ids. Implement SPA retry contract: HTTP 5xx → retry up to 3 times with exponential backoff (2s
  start, 30s max); HTTP 409 `ASSET_UPLOAD_CONFLICT` → no retry, show conflict error; HTTP 413 → no
  retry, show file-too-large error; HTTP 400 → no retry, show validation error; HTTP 404 → no retry,
  treat as terminal; network timeout → retry up to 3 times with same backoff.
- [x] 4.3 Update `apps/web/app/src/components/CreatePostModal.vue` to replace local-only attachment
  truth with persisted create-upload-select flow while keeping transient preview URLs only for UX.
  On media picker or composer open, query for `PROCESSING` assets in addition to `READY` assets and
  surface dangling uploads from prior sessions as recoverable or in-progress.
- [x] 4.4 Update `apps/web/app/src/stores/publishing.ts` so `schedulePost` submits real persisted
  `assetIds`, supports reusing existing workspace assets, and no longer sends `assetIds: []` for
  authenticated publishing.

## Phase 5: Testing and Verification

- [x] 5.1 Add backend tests under `server/smp/src/test/kotlin/com/profiletailors/smp/media/` for
  creation, upload success/failure, retry while `PROCESSING` or `FAILED`, ready upload rejection (
  HTTP 409), newest-first paging, cross-workspace not-found semantics, and stale cleanup scenarios.
- [x] 5.2 Update publishing tests under
  `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/` to prove create/edit reject
  missing, cross-workspace, and non-`READY` assets while LinkedIn publishing still resolves stored
  binaries through the existing storage-backed path. Include tests for HTTP 503
  `MEDIA_SERVICE_UNAVAILABLE` when the media context is unavailable.
- [x] 5.3 Add frontend tests in `apps/web/app/src/components/CreatePostModal.test.ts`,
  `apps/web/app/src/stores/publishing.test.ts`, and new media-store tests for persisted
  upload/select flows, retry/error UX, paging, dangling-upload recovery, and `schedulePost`
  submitting real `assetIds`.
- [x] 5.4 Run `./gradlew test`, relevant web test suites, and document any follow-up fixes needed to
  satisfy every media-library and publishing spec scenario before `sdd-verify`.
- [x] 5.5 Add backend tests for the stale `PROCESSING` asset reconciler: threshold boundary (assets
  just under 2h not transitioned; assets just over 2h transitioned), active-upload grace period (
  asset with recent upload activity within 30 minutes is not cleaned up prematurely), storage delete
  failure during cleanup (asset transitions to `FAILED` even when storage delete fails; failure is
  logged), reconciler idempotency (running twice does not double-transition or corrupt records),
  reconciler storage cleanup attempted on stale transitions (storage delete called per transitioned
  asset; delete failure logged, run continues), FAILED asset orphaned storage cleanup retry (
  reconciler retries deletion for FAILED assets with unresolved storage cleanup failures). Ensure
  the reconciler test references the canonical field names defined in the design's observability
  contract: `errors` and `durationMs`.
