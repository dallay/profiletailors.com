# Exploration: DALLAY-413 — Bulk Scheduling for Multiple Posts

## Current State

### Publishing Bounded Context

Publishing is a well-isolated Spring Modulith bounded context at
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/` with strict hexagonal layering
`domain <- application <- infrastructure`. `ModuleMetadata.kt` allows dependencies on
`tenancy::application`, `media::application|domain`, `identity::application`,
`authorization::domain`.

- **Domain** (`domain/`): `PublishingModels.kt` defines `PublicationDraft` (@AggregateRoot),
  `PublicationJob`/`PublicationJobClaim`, `PublicationStatus` (
  DRAFT/QUEUED/SCHEDULED/PROCESSING/PUBLISHED/BLOCKED/FAILED/CANCELLED), `ScheduleMode` (
  NOW/SCHEDULED_AT/NEXT_SLOT), `JobStatus`, `PublicationAsset`/`AssetSourceType`/
  `PublicationAssetStatus`, `DeliveryAttempt`. `PublishingPolicies.kt` contains
  `PublicationLifecyclePolicy.validateForCreation/queue/cancel/markPublished/markFailed/markBlocked/prepareBlockedRetry`,
  `PublicationSchedulingPolicy.resolveDueAt`, `ConflictDetectionPolicy` (15-min window, same
  socialAccountId, SCHEDULED/QUEUED only), `DeliveryRetryPolicy`. `PublishingRepositories.kt` ports:
  `PublicationRepository`, `PublicationJobRepository` (enqueue/replaceForPublication/claimNextDue
  with FOR UPDATE SKIP LOCKED + lease + claim_version fencing, stale claim recovery),
  `PublicationAssetRepository`, `DeliveryAttemptRepository`. `RecurringModels.kt` defines
  `RecurrenceRule`/`RecurringSchedule`.
- **Application** (`application/`): `PublishingApi.kt` defines
  `CreatePublicationCommand(socialAccountId, title, bodyText, assetIds, scheduleMode, scheduledFor, nextSlotAfter, priority) -> PublicationResult`
  plus Edit/Cancel/Delete/Retry/Reschedule commands and calendar/list/stale-jobs queries.
  `CreatePublicationHandler.kt` is the canonical creation flow: `requireEmailVerification` (
  PUBLISH_CONTENT + SCHEDULE_POST), `requireWorkspaceContext`, `requireSocialAccount`,
  `resolveAssets` (media-context path with 5s timeout -> `MediaServiceUnavailableException`,
  fallback `publicationAssetRepository.findByWorkspaceAndIds`),
  `PublicationLifecyclePolicy.validateForCreation`, `ProviderCapabilityValidator.validate`,
  `PublicationLifecyclePolicy.queue`,
  `transactionRunner.runAtomically { publicationRepository.createDraft; publicationJobRepository.enqueue(newJobFor) }`.
  Handlers for connection, provider catalog, calendar, publication mutations, recurring schedules,
  social-content sync all follow CQRS/mediator (`CommandWithResultHandler`/`QueryHandler` via
  `Mediator`).
- **Infrastructure**: `http/PublishingControllers.kt` — `PublishingPublicationController` at
  `/api/publishing/publications` (version `1` via Spring versioning,
  `Accept: application/vnd.api.v1+json`): `POST /` -> Create, `PATCH /{id}` -> Edit,
  `POST /{id}/cancel`, `DELETE /{id}`, `POST /{id}/retry`, `GET /calendar`, `POST /quick-create` (
  SCHEDULED_AT, empty assets), `PATCH /{id}/reschedule`, `GET /` list. `RecurringScheduleController`
  at `/api/v1/workspaces/{workspaceId}/recurring` (workspace-path validated via
  `resourceContextProvider.requireWorkspaceContext()` equality check).
  `persistence/R2dbcPublishingRepositories.kt` — `R2dbcPublicationRepository` (insertOrUpdate with
  workspace-scoped guard, asset links via `publication_asset_links` with `position_index`,
  `findBlockedForRecovery` with `FOR UPDATE SKIP LOCKED`, hydration),
  `R2dbcPublicationJobRepository` (claimNextDue with CTE `next_job`, priority_rank DESC due_at ASC,
  claim_version fencing, retry/complete/fail/block/cancel, `findStaleClaims`/
  `releaseExpiredClaims`), `R2dbcDeliveryAttemptRepository`. `scheduling/PublishingWorker.kt` —
  `PublishingJobExecutor.executeClaim` (preflight account-status gate
  DISABLED/REQUIRES_RECONNECT/DELETED/PENDING, media resolve, capability validate, idempotent
  delivery via `operationKey = jobId:attemptNumber` + `findByOperationKey`, provider publish via
  `SocialPublisher`, retry/terminal failure handling) + `PublishingWorker.pollOnce` (
  releaseExpiredClaims, claimNextDue) + BLOCKED-recovery scan.
- **DB** (`resources/db/changelog/publishing/*.yaml`, 20 changesets): `publications`,
  `publication_assets`, `publication_asset_links`, `publication_jobs` (due_at, priority_rank,
  attempt_count, claim_version, lease_expires_at), `delivery_attempts` (operation_key,
  claim_version, phase), `recurring_schedules`, plus secure-credentials,
  social-connections/accounts.
- **No bulk code exists**: `grep Bulk` across `server/smp` and `apps/web` returns zero domain
  symbols (only node_modules hits). No `BulkImportJob`, no `bulk/` endpoint.

### Frontend Publishing Flow

`apps/web/app/src/modules/publishing/` enforces feature-module boundary (`domain/`, `application/`,
`infrastructure/`, `presentation/`, `services/`, `views/`). Stable barrel `application/index.ts`
re-exports `useComposerScheduling`, `useComposerValidation`, `useComposerMediaPicker`,
`useCalendarUrl`, `useQueuedCounts`, `useComposerTextFormatting`.

- **Store** (`infrastructure/publishing.store.ts`, 1245 lines): `usePublishingStore` Pinia store —
  `channels` (loaded via `GET /api/publishing/channels` / `GET /api/publishing/channels/providers`),
  `publications` (localStorage `pt_publications` fallback + calendar API
  `/api/publishing/publications/calendar?from=&to=&timezone=` with `latestCalendarFetchId` staleness
  guard), `activity`/`conflicts`, `recurringSchedules` (`/api/v1/workspaces/{id}/recurring`).
  Mutation actions:
  `schedulePost(post: {content, channels, scheduledAt, scheduleMode, priority, assetIds, socialAccountId})` ->
  validates via `normalizeText`, resolves `socialAccountId` via `findActiveLinkedInChannel`, calls
  `syncPublicationWithApi` (`POST /api/publishing/publications` with scheduleMode mapping), does
  `publicationMutationResultToPublication` reconciliation (authoritative server truth). Also
  `quickCreatePost`, `reschedulePublication` (optimistic), `retryPublication`, `updatePost`/
  `deletePost`/`cancelPost`. Validation via `application/useComposerValidation.ts` (3000 char limit,
  per-channel attachment limits linkedin:9, provider limit map).
- **Composer & scheduling**: `application/useComposerScheduling.ts`, `useComposerValidation.ts`,
  `presentation/components/composer/ComposerSchedulePanel.vue`, `useCalendarUrl.ts` (calendar
  `surface/date/timezone/status/q/channelIds/postId`), `views/SchedulerView.vue` +
  `e2e/pages/scheduler-page.ts`. No bulk UI: no CSV picker, no preview table, no template system, no
  bulk store action. All flows are single-post.
- **Shared/media influence**: Media library is a separate bounded context (
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/`,
  `openspec/specs/media-library/spec.md`) — workspace-scoped asset creation/upload/browse with
  `media_assets` + `workspace_file_blobs` CAS, `MediaAssetResolver.resolveReadyAssets` consumed by
  publishing create handler and worker. Frontend consumes via picker `useComposerMediaPicker`.
  Hashtags (`resources/db/changelog/hashtags/`), ideas-canvas, dashboard-scheduling heatmap (
  `specs/dashboard-scheduling/spec.md`) are adjacent but not bulk-aware.

### Cross-Cutting Constraints

- **Workspace scoping & auth**: Every publishing handler uses
  `resourceContextProvider.requireWorkspaceContext().workspaceId` +
  `principalContextProvider.require().principalId`.
  `RecurringScheduleController.requireWorkspacePath` validates path `workspaceId` equals context
  workspaceId (throws `PublicationValidationException`). `R2dbcPublicationRepository.insertOrUpdate`
  guards `publicationId + workspaceId` cross-workspace writes. New bulk endpoints MUST follow
  `/api/v1/workspaces/{workspaceId}/bulk/*` pattern and reuse this guard; existing
  `/api/publishing/*` endpoints are versioned and workspace-scoped via header but not path-param —
  alignment decision needed.
- **Reactivity**: WebFlux + R2DBC, coroutines `suspend` handlers,
  `AtomicTransactionRunner.runAtomically` for draft+job atomicity, `DatabaseClient` with
  `TransactionalOperator`. Any bulk flow for >1000 rows MUST NOT hold a single R2DBC transaction for
  the whole batch (lock contention, timeout). Batch/chunk + idempotent per-row or per-chunk
  transactions required.
- **Validation pipeline**: `PublicationLifecyclePolicy` (text-or-asset, scheduleMode timing
  `MIN_SCHEDULE_OFFSET=1s`), `ProviderCapabilityValidator` (LinkedIn `w_member_social` scopes, 10MB
  asset limit in publishing vs 500MB media-library), `ConflictDetectionPolicy`, file-size/magic-byte
  checks in media. CSV bulk MUST reuse these, not duplicate.
- **Frontend CSV considerations**: Parsing can be browser-side (PapaParse-like) but validation truth
  must be backend `POST /bulk/validate` (preview). Template system currently absent; recurring
  `frequency/interval/daysOfWeek` pattern could inform schedule distribution, but bulk via CSV
  `scheduledFor` or `media_urls` CSV column needs URL->asset import via media context (external URL
  assets via `AssetSourceType.EXTERNAL_URL` + `ProviderAssetRef` flow in
  `RealLinkedInAssetUploader`).

## Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` — add
  `ValidateBulkPublicationsCommand/Result`, `ScheduleBulkPublicationsCommand/Result`,
  `GetBulkJobQuery`, `BulkJobResult`, `BulkTemplate` DTOs
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingModels.kt` — add
  `BulkImportJob` aggregate + `BulkRowStatus`/`BulkJobStatus` value objects; alternatively new
  `publishing/domain/BulkModels.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt` —
  add `BulkImportJobRepository`; extend `PublicationRepository` with batch
  `createDrafts/batchEnqueue` or chunked writer
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt` — add
  `BulkValidationPolicy` / CSV row validators (scheduledFor parse, media_urls, hashtags) reusing
  lifecycle/capability policies
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/CreatePublicationHandler.kt` —
reuse via composition for each row; bulk handler must handle per-row workspace/account resolution,
partial failure semantics, idempotency key per row/job
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` —
add `BulkPublishingController` at `/api/v1/workspaces/{workspaceId}/bulk` with `POST /validate`,
`POST /schedule`, `GET /jobs/{jobId}`, `GET /templates`, `GET /templates/{id}/csv`
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` —
implement batch inserts, `R2dbcBulkImportJobRepository`, batched `publication_asset_links` writes
- `server/smp/src/main/resources/db/changelog/publishing/` — new changeset(s)
  `021-create-bulk-import-jobs.yaml` + `022-create-bulk-import-rows.yaml` (job header + row results,
  idempotency key, workspace FK)
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` —
no change for sync bulk; if async job processing for >1000 rows, add `BulkImportWorker` or extend
`PublishingWorker` with bulk polling
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/` — `MediaAssetResolver`/`MediaService`
  for `media_urls` CSV column (external URL download/IPA, size/type validation); consider
  `ImportMediaFromUrlCommand` if not present
- `server/smp/src/main/kotlin/com/profiletailors/smp/hashtags/` — hashtag extraction/validation for
  per-row content
- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` — add bulk actions
  `validateBulk(csvRows)`, `scheduleBulk(jobId|rows)`, `fetchBulkJob(jobId)`,
  `fetchBulkTemplates()` + state `bulkJobs`, `bulkTemplates`
- `apps/web/app/src/modules/publishing/application/` — new `useBulkImport.ts` /
  `useBulkCsvParser.ts` / `useBulkValidation.ts` composables (CSV parse, client-side preview, row
  error mapping)
- `apps/web/app/src/modules/publishing/presentation/components/` — new `BulkImportModal.vue`,
  `BulkPreviewTable.vue`, `BulkTemplatePicker.vue`; extend `SchedulerView.vue` with Bulk CTA
- `apps/web/app/src/modules/publishing/domain/` — bulk types `BulkRow`, `BulkJob`, `BulkTemplate`
- `openspec/specs/publishing/spec.md` — delta for bulk validate/schedule/job/template contracts
- `openspec/specs/dashboard-scheduling/spec.md` and `openspec/specs/media-library/spec.md` —
  read-only impact (timezone, media limits)
- `server/smp/src/test/resources/features/publishing-publications.feature` — add bulk BDD scenarios;
  new `bulk-scheduling.feature`

## Approaches

1. **Extend Publishing Bounded Context — Synchronous Chunked Batch (reuse CreatePublicationHandler
   per row)**
    - Add `BulkImportJob` aggregate inside `publishing` (header: id, workspaceId,
      createdByPrincipalId, status PENDING/VALIDATING/VALIDATED/SCHEDULING/SCHEDULED/PARTIAL/FAILED,
      totalRows, validRows, invalidRows, templateId?, createdAt; child `BulkImportRow` with
      rowIndex, rawCsv, parsed fields, validationErrors, publicationId?, status). New repository
      `BulkImportJobRepository` (R2DBC).
    - Endpoints: `POST /api/v1/workspaces/{workspaceId}/bulk/validate` (accepts
      `multipart/form-data` CSV or JSON row array; returns synchronous row-level validation using
      existing policies — bodyText/asset+scheduleMode/provider capability — without persisting
      publications). `POST /api/v1/workspaces/{workspaceId}/bulk/schedule` (accepts `jobId` from
      validate + optional corrections; iterates rows in chunks of e.g. 50-100 inside
      `transactionRunner.runAtomically` per chunk, calling `PublicationLifecyclePolicy` + enqueue
      per row via extracted `PublicationCreationService` to avoid duplicating handler).
      `GET /api/v1/workspaces/{workspaceId}/bulk/jobs/{jobId}` polls job status.
      `GET /api/v1/workspaces/{workspaceId}/bulk/templates` returns static template descriptors +
      CSV download.
    - CSV handling for `media_urls`: for each non-empty URL, call media `resolveReadyAssets` path —
      if URL is external, create `PublicationAsset` via `CreateAssetCommand` (EXTERNAL_URL) or reuse
      existing media import flow; validation must check allowed mediaTypes and file-size before
      enqueue.
    - Frontend: browser parses CSV (papaparse or native), shows `BulkPreviewTable` with per-row
      errors from validate, allows inline fix + re-validate, then calls schedule.
    - Pros: Minimal bounded-context churn; reuses proven lifecycle/capability/retry/job
      infrastructure; transactional safety per chunk; no new async infra; workspace scoping
      trivial (reuse `requireWorkspaceContext` guard); aligns with existing
      `RecurringScheduleController` path-style.
    - Cons: Synchronous schedule blocks on large batches (1000 rows x 50 chunk = 20 transactions
      sequential latency); no true background progress for UX unless polling simulates it; worker
      not needed but UX must handle long HTTP (timeout, retry).
    - Effort: Medium

2. **New Async Bulk Bounded Context — Job-Queue with Background Processing**
    - Introduce separate module/package `server/smp/src/main/kotlin/com/profiletailors/smp/bulk/` (
      or `publishing/bulk/` subpackage) with its own `BulkImportJob` aggregate, `BulkRow` value
      objects, and application services. Publish domain event `BulkImportRequested` and process via
      `BulkImportWorker` (scheduled poll, similar to `PublishingWorker.pollOnce` with
      `FOR UPDATE SKIP LOCKED` per job) chunking 100 rows per transaction, updating job progress
      rows incrementally. API is async: `validate` returns `jobId` immediately (status VALIDATING),
      `schedule` transitions to SCHEDULING then worker drives to SCHEDULED; `GET /jobs/{jobId}`
      returns live counts. Add `bulk_import_jobs`/`bulk_import_rows` tables with idempotency key per
      row/job.
    - Pros: Scales to >>1000 rows without request timeout; natural progress UI (poll job); isolates
      CSV/mapping complexity from core publication lifecycle; allows future retries/resume of
      partial bulk jobs; aligns with durable-queue portability principle from publishing spec.
    - Cons: New bounded-context/module + worker + event wiring + additional migration complexity;
      eventual consistency (client must poll); more to spec/test (job state machine, lease/stale
      recovery for bulk jobs mirroring `publication_jobs` stale lease pattern); higher review
      surface (400-line budget risk).
    - Effort: High

3. **LightweightFrontend-Only Batch via Parallel Single-Creation Calls**
    - No backend bulk API. Frontend parses CSV, validates client-side (char limit, date parse,
      channel select), then calls existing `POST /api/publishing/publications` (or `/quick-create`)
      N times in parallel/batched (concurrency limit 5-10) with existing store `schedulePost`. Add
      tiny backend `POST /api/publishing/publications/batch` that loops
      `CreatePublicationHandler.handle` sequentially but without a `BulkImportJob` table — just
      returns `BulkResult { succeeded: PublicationResult[], failed: {index, error}[] }`.
    - Pros: Fastest to ship; zero new tables/worker; trivial fallback if one row fails (partial
      success naturally).
    - Cons: N HTTP round-trips (rate-limit risk — media `maxCreationsPerHour=200` per workspace, 429
      handling); no server-side CSV validation parity (client/frontend drift); no job history/audit;
      `media_urls` CSV URLs cannot be resolved without extra pre-upload calls; transaction not
      atomic per chunk (each row is its own transaction but caller orchestrates retry, harder to get
      idempotency right); violates DRY — duplicates validation in two places; breaks if publishing
      requires stronger workspace scoping on batch.
    - Effort: Low (but high tech-debt)

## Recommendation

**Approach 1 — Extend Publishing Bounded Context with Synchronous Chunked Validation + Chunked
Schedule** is recommended.

Rationale: It reuses the hardened publication creation path verbatim (no second validation
implementation), keeps workspace/auth/transaction patterns consistent, adds minimal new
infrastructure (two tables + one repository + one controller + frontend composables), and meets the
Linear issue's API contract (`/bulk/validate`, `/bulk/schedule`, `/bulk/jobs/{jobId}`, templates)
without the operational weight of a full async bounded context. For 1000 rows, chunk size 50 yields
20 short transactions (~200-400ms each under R2DBC, well within API timeout if executed sequentially
server-side; if latency concern arises, schedule endpoint can return `jobId` and finish remaining
chunks asynchronously while `GET /jobs/{jobId}` polls — incremental evolution to Approach 2 without
re-architecting). Approach 3 is rejected due to rate-limit exposure (200 creations/hour) and
client/server validation drift. Approach 2 is a valid follow-up if metrics show bulk jobs routinely
exceed HTTP timeout or require resume semantics.

Key design decisions for the proposal:

- CSV `media_urls` resolves via `MediaAssetResolver` inside bulk handler — external URLs create
  `PublicationAsset(EXTERNAL_URL)` via existing publishing asset path, not media `UPLOADED` path (
  enforces LinkedIn `IMAGE/JPEG,PNG,GIF,WEBP,VIDEO/MP4` and 10MB publishing limit, distinct from
  media-library 500MB).
- Hashtags are plain text within `bodyText` (extracted post-persist by hashtags context); bulk does
  not add a separate hashtag bulk API.
- Schedule distribution: `scheduledFor` per row (CSV) with optional `timezone` column; `NEXT_SLOT`
  not supported for bulk rows (publishing already limits it).
- Idempotency: `bulkImportJobs` stores
  `idempotencyKey = sha256(workspaceId + principalId + csvHash)` to reject duplicate uploads within
  24h; per-row publication IDs are `pub-{UUID}` as today.

## Risks

- **Transaction & performance**: Single transaction for 1000 rows would hold R2DBC connection/locks
  too long and risk `TransactionTimeoutException`. Chunking mitigates but still serializes; under
  high concurrency bulk + worker claims may contend on `publication_jobs` (priority_rank/due_at
  index). Load test required.
- **Rate limiting**: Media `200 creations/hour` already enforced via `MediaRateLimitRepository`;
  bulk of 1000 rows would 429 unless limit is raised or bulk bypasses per-asset creation. Publishing
  has no explicit per-publication rate limit but implicit worker throughput does. Proposal must
  decide: bulk-respect vs bulk-exempt with audit.
- **Media URL handling**: CSV `media_urls` may contain private/internal URLs (SSRF risk),
  large/unsupported mime, or transient download failures. Must reuse media
  `uploadWithStreamingValidation` streaming + 500MB guard + magic-byte check, and define retry vs
  mark-row-failed policy.
- **Validation parity**: Frontend CSV parse (line endings, BOM, quoted commas, UTF-8) must not
  diverge from backend `ValidateBulk` errors; otherwise preview table shows green but backend
  rejects. Contract tests needed.
- **Workspace & auth isolation**: Bulk `workspaceId` path param must be validated against
  `ResourceContextProvider` on every endpoint (as `RecurringScheduleController` does). Bulk job
  reads must be workspace-scoped; otherwise cross-workspace job enumeration.
- **Conflict detection**: Bulk rows scheduled within 15-min window for same account will be flagged
  `hasConflict:true` by `ConflictDetectionPolicy` but today `hasConflict` is informational, not
  blocking. Product must decide if bulk schedule should block, warn, or auto-stagger.
- **API surface split**: Existing publishing uses `/api/publishing/*` (version via Accept header),
  new recurring uses `/api/v1/workspaces/{workspaceId}/recurring`. Bulk per issue uses
  `/api/v1/workspaces/{workspaceId}/bulk/*` — aligns with recurring but diverges from
  `/api/publishing/*`. OpenAPI/docs must reconcile; frontend `apiFetch` already supports
  `workspaceScoped:true` + path param.
- **Scope creep — full async**: If milestone 0.3 expects true background jobs with SSE progress (
  like `channelEventStreamRegistry`), Approach 1 will need evolution; proposal should mark async
  worker as **Planned, not in V1**.

## Ready for Proposal

Yes — exploration is conclusive. No clarification needed beyond product decisions that the proposal
can capture as `Undecided` until owner review:

- Confirm bulk throughput target (1000 vs 5000 rows) and whether 200 creations/hour limit applies to
  bulk rows.
- Confirm `media_urls` CSV semantics (external URL import vs requiring pre-uploaded `assetIds`) and
  SSRF allowlist policy.
- Confirm conflict-window behavior for bulk (block vs warn).
- Confirm template set (e.g. LinkedIn weekly content calendar with columns
  `bodyText,scheduledFor,media_urls,hashtags,timezone`).

Next phase: `sdd-propose` to draft `proposal.md` for `dallay-413-bulk-scheduling` with scope, API
contract, bounded-context placement, migration sketch, and phased delivery (validate → schedule
chunked → templates → optional async).
