# Tasks: DALLAY-413 — Bulk Scheduling for Multiple Posts

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 950–1200 (additions+deletions) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 (stacked) |
| Delivery strategy | ask-on-risk |
| Chain strategy | github-stacked-prs |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Backend domain+validate | PR1 | trunk=`main`, base=`main`, branch=`feature/dallay-413-bulk-pr1-domain`, pos=1, Linear DALLAY-413; `just backend-test-fast` |
| 2 | Backend schedule+job+infra | PR2 | trunk=`main`, parent=`pr1-domain`, base=`pr1-domain`, branch=`pr2-schedule`, pos=2, depends PR1 |
| 3 | Frontend CSV+UI+E2E | PR3 | trunk=`main`, parent=`pr2-schedule`, base=`pr2-schedule`, branch=`pr3-frontend`, pos=3, depends PR2 |

## Phase 1: Foundation

- [x] 1.1 Migrations `021`/`022` — `bulk_import_jobs` (id, workspace_id FK, principal_id, idempotency_key unique sha256, status, counts, csv_hash) + `bulk_import_rows` (job_id FK, row_index, status, publication_id, errors jsonb) — Req Isolation/Idempotency
- [x] 1.2 `publishing/domain/BulkModels.kt` — `BulkImportJob` @AggregateRoot, `BulkImportRow`, `BulkJobStatus`, `BulkRowStatus`, `ImportError`, `BulkTemplate` — TDD unit
- [x] 1.3 Ports: `BulkImportJobRepository` + `PublishingApi.kt` `ValidateBulk/ScheduleBulk/GetBulkJob` commands — TDD port contract

## Phase 2: Core — Validation & Creation

- [x] 2.1 `publishing/domain/BulkValidationPipeline` — reuses `PublicationLifecyclePolicy`, `ProviderCapabilityValidator`, `ConflictDetectionPolicy`, `MediaAssetResolver(EXTERNAL_URL)` + blank/BOM/duplicate — Gherkin5, Blank, Duplicate, Capability — TDD RED: blank→2 rows, INVALID_DATE+MISSING_CONTENT, DUPLICATE, PDF
- [x] 2.2 `publishing/application/PublicationCreationService.kt` — extract shared creation (VERIFIED guard, lifecycle/capability/conflict/media) for single+bulk — TDD: existing handler tests pass
- [x] 2.3 `publishing/application/BulkPublishingHandlers.kt` — Validate (stateless), Schedule (sha256→409, chunk 50-100 `runAtomically`, 200/207 partial), GetJob (404), Templates — Gherkin1/2/3/5+409 — TDD MockK

## Phase 3: Infra Wiring

- [x] 3.1 `R2dbcBulkImportJobRepository.kt` — idempotency lookup, batch inserts, FOR UPDATE read — TDD `just backend-test-postgres` 1000-row 10-20tx + 409
- [x] 3.2 `BulkPublishingController.kt` `/api/v1/workspaces/{workspaceId}/bulk/*` 4 endpoints, `requireWorkspaceContext` guard 403/404, 409 `{jobId}`, OpenAPI — TDD WebTestClient

## Phase 4: Frontend

- [x] 4.1 `domain/bulk.ts` + `application/useBulkCsvParser.ts` — csvText parse, BOM/quotes, header `bodyText,scheduledFor,timezone,media_urls,hashtags`, blank skip — TDD Vitest
- [x] 4.2 `application/useBulkImport.ts` + `publishing.store.ts` bulk actions `validateBulk/scheduleBulk/fetchBulkJob/fetchBulkTemplates` + poll — TDD Vitest
- [x] 4.3 `BulkImportModal.vue` + `BulkPreviewTable.vue` (VALID|INVALID+errors, inline fix) + `BulkTemplatePicker.vue` + `SchedulerView.vue` wire — TDD Vitest

## Phase 5: Testing

- [x] 5.1 BDD `bulk-scheduling.feature` 14 scenarios — Gherkin1-5, 403, 404, 409, capability, conflict warn-only — `just backend-bdd-fast`
- [x] 5.2 E2E Playwright upload→preview→fix→schedule→poll + CSV header — `just frontend-test` + `just app-test-e2e-media-mocked`
- [x] 5.3 Arch guard `HexagonalArchTest`/`ModularStructureTest` + `just backend-check` Detekt

## Phase 6: Docs

- [x] 6.1 `docs/api-versioning*.md` OpenAPI, flag `bulkScheduling.enabled`, rollback `022→021`, links
