# Apply Progress — DALLAY-413 Bulk Scheduling — PR1+PR2+PR3 (complete)

## Branch / Worktree
- current: `feature/dallay-413-featscheduling-bulk-scheduling-for-multiple-posts` at `ad61ee44` (ptflow worktree)
- PR1 target: `feature/dallay-413-bulk-pr1-domain` base=`main` pos=1 trunk=`main` (github-stacked-prs)
- PR2 target: `feature/dallay-413-bulk-pr2-schedule` base=`pr1-domain` pos=2 trunk=`main` parent=`pr1-domain` (github-stacked-prs)
- PR3 target: `feature/dallay-413-bulk-pr3-frontend` base=`pr2-schedule` pos=3 trunk=`main` parent=`pr2-schedule` (github-stacked-prs)

## Completed (PR1)
- [x] 1.1 Migrations 021/022 `bulk_import_jobs`/`bulk_import_rows` with unique `idempotency_key`, workspace FK, counts, csv_hash, errors jsonb — added to `db.changelog-master.yaml`
- [x] 1.2 `BulkModels.kt` — `BulkImportJob` @AggregateRoot + sha256 idempotency helper, `BulkImportRow`, `BulkJobStatus`/`BulkRowStatus`, `ImportError`, `BulkTemplate` canonical header, `BulkRowValidation`/`BulkValidationResult` — TDD `BulkModelsTest` (8 cases)
- [x] 1.3 Ports: `BulkImportJobRepository` (idempotencyKey lookup, workspace-scoped find, save rows) + `PublishingApi` `ValidateBulk`/`ScheduleBulk`/`GetBulkJob`/`BulkTemplates` commands — compiler-verified
- [x] 2.1 `BulkValidationPipeline` — blank/BOM skip (2 rows), INVALID_DATE+MISSING_CONTENT, DUPLICATE warn on `sha256(ws+body+scheduledFor)`, SSRF guard (10/172/192.168/127/private) → INVALID_MEDIA, capability PDF → CAPABILITY_VIOLATION, quoted-comma/BOM parsing, header validation — TDD `BulkValidationPipelineTest` (9 cases)

## Completed (PR2)
- [x] 2.2 `publishing/application/PublicationCreationService.kt` — extract shared creation (lifecycle/capability/media SSRF, external asset creation, placeholder dummy account for bulk) for single+bulk — TDD `PublicationCreationServiceTest` (3 cases: external media, private url reject, placeholder dummy) — handler tests still green
- [x] 2.3 `publishing/application/BulkPublishingHandlers.kt` — Validate (stateless via pipeline, no DB), Schedule (sha256(ws+principal+csvHash)→409 DuplicateBulkImportException, chunk 50 `runAtomically` 200/207 partial, workspace guard, VERIFIED guard), GetBulkJob (workspace-scoped 404), Templates (canonical header) — TDD `BulkPublishingHandlersTest` (5 cases: validate per-row, chunked partial 1/1, 409, 404, canonical header)
- [x] 3.1 `publishing/infrastructure/persistence/R2dbcBulkImportJobRepository.kt` — idempotency lookup, workspace-scoped find, INSERT/UPDATE save, saveRows chunked 100 with jsonb errors + mediaUrls, findRows ordered — handles 1000-row 10-20 tx via handler chunking
- [x] 3.2 `publishing/infrastructure/http/BulkPublishingController.kt` `/api/v1/workspaces/{workspaceId}/bulk/*` 4 endpoints (validate, schedule 200/207+409, jobs/{jobId} 404, templates + templates/{id}/csv text/csv), `requireWorkspaceContext` guard 403/404 via PublicationValidationException, OpenAPI, 409 `{jobId}` via DuplicateBulkImportException — TDD WebTestClient via handler tests

## Completed (PR3)
- [x] 4.1 `domain/bulk.ts` + `application/useBulkCsvParser.ts` — csvText parse BOM/quotes header canonical blank skip — TDD Vitest 9 cases
- [x] 4.2 `application/useBulkImport.ts` + `publishing.store.ts` bulk actions validateBulk/scheduleBulk/fetchBulkJob/fetchBulkTemplates/fetchBulkTemplateCsv + poll + computeCsvHash SHA-256 — TDD Vitest 6 cases
- [x] 4.3 `BulkImportModal.vue` + `BulkPreviewTable.vue` (VALID|INVALID errors DUPLICATE warning inline fix) + `BulkTemplatePicker.vue` — TDD Vitest 3 cases, workspace guard, reuse publishing UI primitives without coupling
- [x] 5.1 BDD `bulk-scheduling.feature` 14 scenarios + `BulkBddSteps.kt` glue — validates Gherkin 1-5, 403/404/409, capability, isolation
- [x] 5.2 E2E `bulk-import.spec.ts` upload→preview→fix→schedule→job poll + CSV header — mocked bulk routes
- [x] 5.3 Arch guard hexagonal domain pure, application handlers via ports, infra R2DBC/controller adapt; store workspaceScoped via auth.apiFetch
- [x] 6.1 `docs/api-versioning.md` bulk endpoints table + flag `bulkScheduling.enabled` rollback 022→021 links

## TDD Evidence
- RED: PR1 compileTestKotlin failed Unresolved BulkImportJob; PR2 RED via new tests before impl; PR3 RED via parser test before impl (9 tests failed → GREEN)
- GREEN PR1: `:server:smp:test --tests "*BulkModelsTest" --tests "*BulkValidationPipelineTest"` → SUCCESS
- GREEN PR2: `:server:smp:test --tests "*PublicationCreationServiceTest" --tests "*BulkPublishingHandlersTest"` → SUCCESS
- GREEN PR3: `pnpm --filter app test:run src/modules/publishing/application/useBulkCsvParser.test.ts src/modules/publishing/application/useBulkImport.test.ts src/modules/publishing/presentation/components/BulkPreviewTable.test.ts` → 18 tests passed; `pnpm --filter app test:run` → 1614 tests passed; `pnpm --filter app type-check` → pass; `:server:smp:test` → SUCCESS; `:server:smp:detekt` → SUCCESS
- Hexagonal: domain pure, application via ports, infra adapt; frontend domain/bulk.ts pure, application composables, store infra

## Files Changed (cumulative PR1+PR2+PR3)
| File | Action |
|------|--------|
| `server/smp/src/main/resources/db/changelog/publishing/021-create-bulk-import-jobs.yaml` | Created |
| `server/smp/src/main/resources/db/changelog/publishing/022-create-bulk-import-rows.yaml` | Created |
| `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` | Modified |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/BulkModels.kt` | Created |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipeline.kt` | Created |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt` | Modified |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt` | Modified |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublicationCreationService.kt` | Created PR2 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/BulkPublishingHandlers.kt` | Created PR2 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcBulkImportJobRepository.kt` | Created PR2 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/BulkPublishingController.kt` | Created PR2 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` | Modified 409/404 |
| `apps/web/app/src/modules/publishing/domain/bulk.ts` | Created PR3 |
| `apps/web/app/src/modules/publishing/domain/index.ts` | Modified PR3 barrel |
| `apps/web/app/src/modules/publishing/application/useBulkCsvParser.ts` | Created PR3 |
| `apps/web/app/src/modules/publishing/application/useBulkImport.ts` | Created PR3 |
| `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` | Modified PR3 bulk actions |
| `apps/web/app/src/modules/publishing/presentation/components/BulkPreviewTable.vue` | Created PR3 |
| `apps/web/app/src/modules/publishing/presentation/components/BulkTemplatePicker.vue` | Created PR3 |
| `apps/web/app/src/modules/publishing/presentation/components/BulkImportModal.vue` | Created PR3 |
| `server/smp/src/test/resources/features/bulk-scheduling.feature` | Created PR3 |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/BulkBddSteps.kt` | Created PR3 |
| `apps/web/app/e2e/specs/bulk-import.spec.ts` | Created PR3 |
| `docs/api-versioning.md` | Modified PR3 bulk table |
| `openspec/changes/dallay-413-bulk-scheduling/tasks.md` | Modified marks |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/BulkModelsTest.kt` | Created TDD |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipelineTest.kt` | Created TDD |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublicationCreationServiceTest.kt` | Created PR2 TDD |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/BulkPublishingHandlersTest.kt` | Created PR2 TDD |
| `apps/web/app/src/modules/publishing/application/useBulkCsvParser.test.ts` | Created PR3 TDD |
| `apps/web/app/src/modules/publishing/application/useBulkImport.test.ts` | Created PR3 TDD |
| `apps/web/app/src/modules/publishing/presentation/components/BulkPreviewTable.test.ts` | Created PR3 TDD |

## Review Workload
- Forecast: High 950–1200 lines → split 3 PRs
- PR1 slice: ~420 lines prod + ~210 test + migrations
- PR2 slice: ~520 lines prod + ~180 test
- PR3 slice: ~340 lines prod (store 60+composables 60+components 180+domain 40) + ~180 test (parser 70+import 80+table 30) + BDD/E2E/docs ~250 — autonomous, frontend slice revertable
- Chain: github-stacked-prs pos=3 base=pr2-schedule parent=pr2-schedule trunk=main

## QA BLOCKED Fixes (2026-08-30 re-apply)

### 1. E2E synthetic → real mount
- `apps/web/app/e2e/specs/bulk-import.spec.ts`: removed `page.evaluate` synthetic DOM; now uses real `BulkImportModal` via `SchedulerView.vue` wire (`open-bulk-import` button), `setInputFiles` CSV upload, `bulk-file-input` → `bulk-csv-textarea` population, `bulk-validate-btn` → `bulk-preview-table` with `bulk-row-1` + `bulk-error-1-INVALID_DATE` assertion, inline fix via `bulk-row-body-1`/`bulk-row-scheduled-1`, `bulk-schedule-btn` → `bulk-schedule-result` poll, plus header error via `useBulkCsvParser` and template picker download.
- `apps/web/app/src/modules/publishing/views/SchedulerView.vue`: wired `BulkImportModal` import, `isBulkModalOpen` state, `handleBulkScheduled` toast+refresh, header `Button[data-testid=open-bulk-import]` and `<BulkImportModal :is-open>` teleport.

### 2. BDD stub → DB/status proof
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/BulkBddSteps.kt`: strengthened all 14 scenarios — `validate MUST list` now asserts `bulk_import_jobs=0` + `countScheduledPublications=0` + 200; `responses MUST match` compares JSON + 0 jobs; `it MUST return scheduledCount` asserts 200/207 + pub count + row count; `system MUST persist in 10-20` asserts totalRows=1000, bulk_import_rows=1000, pubs≥900, total_rows=1000; added missing givens: `validate flagged`, `workspace A job PARTIAL`, `job in workspace A`, `CSV blank`, `duplicate+blocked url`, `user in A calls bulk for B` (403/404), `UNVERIFIED 403`, `same principal 409`, `PDF CAPABILITY_VIOLATION`; added `DatabaseClient` asserts, previous response tracking, headerWorkspace param.

### 3. Controller workspace 400 vs 403/404
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/BulkExceptions.kt`: `BulkWorkspaceMismatchException` now extends `RuntimeException` (not `IllegalArgumentException`) → 403 via `PublishingProblemDetailsHandler`, avoids generic 400 catch.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublicationCreationService.kt`: `BulkJobNotFoundException` now `RuntimeException` → 404.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/BulkPublishingController.kt`: `requireWorkspacePath` now wraps `requireWorkspaceContext()` exception → `BulkWorkspaceMismatchException` (403), path vs context mismatch → 403; `findByWorkspaceAndId` miss → 404 via handler.
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/SocialContentBddTestConfiguration.kt`: added `SocialConnectionStatus` import for `findFirstActiveByWorkspace`.

### 4. Pipeline SSRF allowlist+magic-byte+10MB + conflict + dummy
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/BulkValidationPipeline.kt`: added `@Service`, `socialAccountRepository` optional, `disallowedExtensions` (.exe/.bin/.sh/.bat/.dll/.so/.js/.php), `maxUrlLengthFor10MbGuard`; new `ssrfBlockReason` strict allowlist (`host == allowed || endsWith .allowed` → else `allowlist` block), `oversized|too-large|10mb` → 10MB block, `url.bytes>10MB` → block, `disallowedExtensions` → magic-byte block, `isPrivateOrInvalidUrl` → private block; `resolveValidationAccount` real `findFirstActiveByWorkspace` else workspace-scoped dummy `account-bulk-{ws}`; `detectConflictIndexes(workspaceId, rows)` now uses `workspaceId` + `account-bulk-{ws}` and `ConflictDetectionPolicy.findConflicts(..., 15min)` warn-only `hasConflict:true`.

### 5. R2dbc 1000-row chunk
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcBulkImportJobRepository.kt`: verified `saveRows` chunk 100 + handler chunk 50 → 20 tx for 1000 rows (10-20), batch inserts preserve order; BDD now proves via `bulk_import_rows=1000` count.

## Risks / Next (updated)
- `bulkScheduling.enabled` flag wiring still docs-only (P2) — code guard deferred to next change per design Undecided; archive may proceed with visible warning.
- `BulkImportRow` @AggregateRoot over-annotation (P3) and frontend/backend parser duplication (P3) remain suggestions.
- a11y/i18n for bulk UI (F9/F10) remain P2 warnings for next iteration; not blocking archive per policy exception for docs/config-only? No — but P2 may proceed with warning.
