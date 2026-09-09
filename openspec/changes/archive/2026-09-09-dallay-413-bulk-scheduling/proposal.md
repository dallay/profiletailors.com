# Proposal: DALLAY-413 — Bulk Scheduling for Multiple Posts

## Intent

Bulk CSV scheduling. Publishing is single-post only; need validated preview + chunked atomic schedule without duplicating lifecycle rules.

## Scope

### In Scope
- `POST /bulk/validate` — sync row validation, no persistence
- `POST /bulk/schedule` — chunked 50-100 per `runAtomically`
- `GET /bulk/jobs/{jobId}`, `GET /bulk/templates`, `.../{id}/csv`
- `BulkImportJob`/`BulkImportRow`, `BulkJobStatus`/`BulkRowStatus`, `BulkImportJobRepository`
- Migrations `021`/`022` (`bulk_import_jobs`/`rows`, `idempotencyKey=sha256(ws+principal+csvHash)`)
- Reuse `PublicationLifecyclePolicy`, `ProviderCapabilityValidator`, `ConflictDetectionPolicy`, `MediaAssetResolver`
- Frontend: `useBulkImport`/`useBulkCsvParser`, `BulkImportModal`, `BulkPreviewTable`, `BulkTemplatePicker`

### Out of Scope
- Async `BulkImportWorker`/SSE (V2 if needed)
- `NEXT_SLOT` per-row, recurring distribution, folder/album
- Cross-workspace bulk, background URL queue, rate-limit change

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `publishing`: bulk validate/schedule/job/template, `BulkImportJob` lifecycle, `media_urls` via `EXTERNAL_URL`

## Approach

Sync chunked batch inside publishing (Approach 1). `BulkPublishingController` at `/api/v1/.../bulk/*` reuses `RecurringScheduleController` guard. Extract `PublicationCreationService`; schedule chunks per transaction. Frontend CSV → validate → fix → schedule → poll.

| Alternative | Why not V1 |
|-------------|------------|
| Async bulk context | Adds module/worker/events, 400-line risk |
| Frontend parallel create | Hits 200/h, drift, no audit/SSRF guard |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `publishing/application/PublishingApi.kt` | Modified | Bulk commands |
| `publishing/domain/**` | New/Modified | `BulkImportJob`, repo |
| `publishing/infrastructure/http/*` | Modified | `BulkPublishingController` |
| `publishing/infrastructure/persistence/*` | Modified | Batch inserts |
| `resources/db/changelog/publishing/02*.yaml` | New | Jobs + rows tables |
| `apps/web/app/src/modules/publishing/**` | New/Modified | Composables, Bulk UI |
| `openspec/specs/publishing/spec.md` | Delta | Bulk req + 5 scenarios |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Tx timeout / lock contention | High | Chunk 50-100; load test |
| Media 200/h 429 | Med | Per-row 429; Undecided raise vs exempt |
| SSRF via `media_urls` | Med | Allowlist + magic-byte + 10MB |
| CSV drift frontend vs backend | Med | Backend truth; contract tests |
| Workspace isolation bypass | Low | Path guard + workspace-scoped BDD |
| Path divergence | Low | OpenAPI reconcile |
| Conflict-window 15-min | Med | `hasConflict:true` warn only V1 |

## Rollback Plan

Flag `bulkScheduling.enabled` off. Rollback `022`/`021`. Remove routes. Existing jobs remain; cleanup via `bulk_import_rows` join. Frontend flag-guarded.

## Dependencies

- Publishing Modulith (`media`/`tenancy`/`identity`)
- `media::MediaAssetResolver`

## Success Criteria

- [ ] Gherkin 1 — validate per-row errors, no persistence
- [ ] Gherkin 2 — schedule chunked atomic, partial success
- [ ] Gherkin 3 — `GET /jobs/{jobId}` workspace-scoped counts + errors
- [ ] Gherkin 4 — templates list + CSV correct columns
- [ ] Gherkin 5 — invalid `scheduledFor`/media/dup hash → row error or 409
- [ ] No cross-workspace enumeration
