# Design: DALLAY-413 — Bulk Scheduling for Multiple Posts

## Technical Approach

Sync chunked batch inside `publishing`. `BulkPublishingController` at `/api/v1/workspaces/{workspaceId}/bulk/*` reuses workspace guard. Extract `PublicationCreationService` so validate (stateless) and schedule (chunked 50–100 `runAtomically`) share lifecycle/capability/conflict/media logic. Two tables `bulk_import_jobs`/`rows`. Frontend CSV→validate→fix→schedule→poll.

## Architecture Decisions

| Decision | Option | Tradeoff | Choice |
|----------|--------|----------|--------|
| Context | New `bulk` module vs extend `publishing` | Isolate CSV but adds worker/events, >400 lines | **Extend `publishing`** — reuses lifecycle/workspace/R2DBC, minimal churn |
| Aggregate | `BulkImportJob`+`BulkImportRow` vs reuse `PublicationDraft` | Need audit/partial-success | **New `BulkImportJob` @AggregateRoot** + `BulkImportRow` list; pubs created via service as normal `PublicationDraft`+job |
| Tx | Single tx vs chunked 50–100 | Single tx timeout/lock for 1000 rows | **Chunked `runAtomically`** — 20 tx for 1000 rows; partial success; idempotencyKey guards retry |
| Validation | Duplicate logic vs pipeline | Drift risk | **`PublicationCreationService`+`BulkValidationPipeline`** calling existing policies + `MediaAssetResolver(EXTERNAL_URL)` |
| CSV | Backend multipart vs frontend+backend truth | Drift frontend/backend | **Both parse `csvText`; backend is truth**; blank skip, BOM, quoted commas identical; contract test |
| Media | Direct URL vs allowlisted fetch | SSRF | **Allowlist+magic-byte+10MB** via resolver; private IP denied; `INVALID_MEDIA` on fail |
| Idempotency | Row vs job `sha256` | Row resume complex | **Job `sha256(ws+principal+csvHash)`** unique idx; duplicate `POST /schedule`→409+jobId |
| Scoping | Header vs path guard | Path matches recurring | **Path `workspaceId`==`requireWorkspaceContext().workspaceId`**; `GET /jobs/{id}` workspace-filtered→404 |

## Data Flow

```
CSV ─→ useBulkCsvParser ─→ POST /bulk/validate {csvText}
                              → BulkValidationPipeline (skip blanks, sha256 dup, lifecycle/capability/conflict, MediaResolver) → {VALID|INVALID, errors[]} no DB
                          POST /bulk/schedule {csvText, csvHash}
                              → idempotency check → create job PENDING
                              → chunk 50-100 runAtomically → PublicationCreationService per row → rows SCHEDULED|FAILED → job SCHEDULED|PARTIAL|FAILED
                          GET /bulk/jobs/{jobId} ← poll (BulkPreviewTable)
```

Frontend `useBulkImport` orchestrates flow; `BulkTemplatePicker` → `GET /templates` + `/{id}/csv`.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `publishing/domain/BulkModels.kt` | Create | `BulkImportJob`, `BulkImportRow`, `BulkJobStatus`/`BulkRowStatus`, `ImportError`, `BulkTemplate` |
| `publishing/domain/PublishingRepositories.kt` | Modify | Add `BulkImportJobRepository` port |
| `publishing/domain/PublishingPolicies.kt` | Modify | Add `BulkValidationPipeline` reusing policies |
| `publishing/application/PublishingApi.kt` | Modify | `ValidateBulkCommand/Result`, `ScheduleBulkCommand/Result`, `GetBulkJobQuery` |
| `publishing/application/BulkPublishingHandlers.kt` | Create | Validate/schedule/job/templates handlers; chunk loop |
| `publishing/application/PublicationCreationService.kt` | Create | Extracted shared creation path |
| `publishing/infrastructure/http/BulkPublishingController.kt` | Create | 4 endpoints, workspace guard, 403/409 handling |
| `publishing/infrastructure/persistence/R2dbcBulkImportJobRepository.kt` | Create | R2DBC batch inserts, idempotency lookup |
| `resources/db/changelog/publishing/021-create-bulk-import-jobs.yaml` | Create | `bulk_import_jobs` (id, workspace_id FK, principal_id, idempotency_key unique, status, counts, csv_hash) |
| `resources/db/changelog/publishing/022-create-bulk-import-rows.yaml` | Create | `bulk_import_rows` (id, job_id FK, row_index, status, publication_id nullable FK, errors jsonb) |
| `apps/web/app/src/modules/publishing/application/useBulkCsvParser.ts` | Create | CSV parse, blank/BOM handling, header mapping |
| `apps/web/app/src/modules/publishing/application/useBulkImport.ts` | Create | Validate/schedule/poll orchestration |
| `apps/web/app/src/modules/publishing/presentation/components/BulkImportModal.vue` | Create | CSV drop + template picker |
| `apps/web/app/src/modules/publishing/presentation/components/BulkPreviewTable.vue` | Create | Per-row errors, inline fix |
| `apps/web/app/src/modules/publishing/presentation/components/BulkTemplatePicker.vue` | Create | Template list + CSV download |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modify | Add bulk actions |
| `apps/web/app/src/modules/publishing/domain/bulk.ts` | Create | Bulk TS types |

## Interfaces / Contracts

```kotlin
data class ValidateBulkCommand(val workspaceId: String, val csvText: String): CommandWithResult<ValidateBulkResult>
data class ValidateBulkResult(val rows: List<BulkRowValidation>) // rowIndex, VALID|INVALID, errors: List<ImportError>
data class ScheduleBulkCommand(val workspaceId: String, val csvText: String, val csvHash: String): CommandWithResult<ScheduleBulkResult>
data class ScheduleBulkResult(val jobId: String, val totalRows: Int, val scheduledCount: Int, val failedCount: Int, val rows: List<BulkRowResult>)
data class GetBulkJobQuery(val workspaceId: String, val jobId: String): Query<BulkJobResult>
data class BulkJobResult(val jobId: String, val status: BulkJobStatus, val totalRows: Int, val scheduledCount: Int, val failedCount: Int, val rows: List<BulkRowResult>)
enum class BulkJobStatus { SCHEDULING, SCHEDULED, PARTIAL, FAILED }
data class ImportError(val code: String, val message: String) // INVALID_DATE, MISSING_CONTENT, INVALID_MEDIA, DUPLICATE, CAPABILITY_VIOLATION
```
```
POST /api/v1/workspaces/{workspaceId}/bulk/validate          → 200 ValidateBulkResult
POST /api/v1/workspaces/{workspaceId}/bulk/schedule          → 200|207 ScheduleBulkResult, 409 duplicate idempotencyKey
GET  /api/v1/workspaces/{workspaceId}/bulk/jobs/{jobId}      → 200 BulkJobResult, 404 cross-workspace
GET  /api/v1/workspaces/{workspaceId}/bulk/templates         → 200 {templates}
GET  /api/v1/workspaces/{workspaceId}/bulk/templates/{id}/csv → 200 text/csv header bodyText,scheduledFor,timezone,media_urls,hashtags
```
Guards: workspace path==context else 403/404; `emailStatus==VERIFIED` else 403; V1 only `SCHEDULED_AT` future `scheduledFor`.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Pipeline blank/INVALID_DATE/MISSING_CONTENT/DUPLICATE/PDF/conflict/hash | JUnit5+MockK `just backend-test-fast` |
| Integration | 1000 rows 10-20 tx, partial 2/1, 409, 404, SSRF+10MB | R2DBC+Testcontainers `just backend-test-postgres` |
| BDD | 5 Gherkin + cross-workspace + 409 | `bulk-scheduling.feature` `just backend-bdd-fast` |
| E2E | Upload→preview→fix→schedule→poll; CSV header | Playwright `just app-test-e2e-media-mocked` |

TDD per `config.testing.strict_tdd`.

## Migration / Rollout

`021`/`022` additive, unique `idempotency_key`, `errors jsonb`, FK workspace. Rollback drop `022`→`021`. Flag `bulkScheduling.enabled` guards routes+UI. No backfill.

## Open Questions

- [ ] Throughput 1000 vs 5000 — chunk 50 fixed or adaptive?
- [ ] 200/h limit applies to bulk or exempt+audited?
- [ ] SSRF allowlist — domain list or IP deny only?
- [ ] Template set — single LinkedIn calendar or multiple?
- [ ] 15-min conflict warn-only confirmed?
```
