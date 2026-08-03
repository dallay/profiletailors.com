# Tasks: PR #577 Quality-Gate Remediation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 1,200–1,800 |
| 400-line budget risk | High |
| Chained PRs recommended | No — explicit unified-PR constraint |
| Suggested split | One unified PR |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Remediation and validation | PR #577 | Base `main`; branch `feat/app-product-tour`; approval pending |

## Phase 1: Sonar RED Tests

- [x] 1.1 Add failing label tests in new `apps/web/app/src/modules/dashboard/presentation/views/AnalyticsView.test.ts` for `AnalyticsView.vue:91,107,114`.
- [x] 1.2 Add failing label tests in new `apps/web/app/src/modules/ideas/presentation/views/IdeasView.test.ts` for `IdeasView.vue:619,669,745,746,759,760`.
- [x] 1.3 Add a failing saved-set label test in new `apps/web/app/src/modules/publishing/presentation/components/composer/HashtagSuggestionPanel.test.ts` for `HashtagSuggestionPanel.vue:162`.
- [x] 1.4 Add a failing characterization in `CreatePostModal.test.ts` before changing `CreatePostModal.vue:932`.
- [x] 1.5 Add a failing port contract in new `server/smp/src/test/kotlin/com/profiletailors/smp/hashtags/application/HashtagsQueryHandlersTest.kt` before `HashtagAnalysisPort.kt:3`.
- [x] 1.6 Add a failing no-content contract in `HashtagsControllerTest.kt` before `HashtagsController.kt:54`.
- [x] 1.7 Add a failing handler regression in new `server/smp/src/test/kotlin/com/profiletailors/smp/ideas/application/IdeasCommandHandlersTest.kt` before `IdeasCommandHandlers.kt:32`.

## Phase 2: Sonar GREEN Fixes

- [x] 2.1 Add stable `id`/`for` pairs to `AnalyticsView.vue:91,107,114`, `IdeasView.vue:619,669,745,746,759,760`, and `HashtagSuggestionPanel.vue:162`.
- [x] 2.2 Extract the nested ternary at `CreatePostModal.vue:932` without changing output.
- [x] 2.3 Make `HashtagAnalysisPort.kt:3` functional; return `Unit` at `HashtagsController.kt:54`; remove the Elvis at `IdeasCommandHandlers.kt:32`.

## Phase 3: Frontend Coverage (RED first, then minimum GREEN)

- [x] 3.1 Extend `ideas.store.test.ts` for guards/errors, normalization, mutations, rollback, columns, and reset; complete `IdeasView.test.ts` for capture/detail CRUD/convert, columns, drag/drop, lifecycle, loading, and errors.
- [x] 3.2 Create `analytics.store.test.ts`; complete `AnalyticsView.test.ts` for dates, refresh fan-out, loading/empty/error/data, formatting, charts, preview, Blob/download export, and effects.
- [x] 3.3 Create `ai-content-api.test.ts`; extend `CreatePostModal.test.ts` for optimize, validation, empty/generic failures, option normalization, and no-content acceptance.
- [x] 3.4 Create `hashtag-api.test.ts` and `useHashtagSuggestions.test.ts`; complete `HashtagSuggestionPanel.test.ts` for guards, 30-tag limit, async paths, emits, and fallback branches.

## Phase 4: Backend Kover Coverage (RED first, then minimum GREEN)

- [x] 4.1 Add Ideas handler tests, extend `IdeasControllerTest.kt`, and create `R2dbcIdeaRepositoriesPostgresTest.kt` for defaults, normalization, errors, move/delete/convert, JSON, upsert/order, CRUD, and workspace isolation.
- [x] 4.2 Create `AnalyticsHandlersTest.kt`, `AnalyticsControllerTest.kt`, and `R2dbcAnalyticsRepositoryPostgresTest.kt` for dates, pagination, aggregates, zero rates, best-time mapping, CSV, and empty ranges.
- [x] 4.3 Extend `LocalHashtagAnalysisServiceTest.kt` and `HashtagsControllerTest.kt`; create `HashtagsHandlersTest.kt` and `R2dbcHashtagSavedSetRepositoryPostgresTest.kt` for analysis, validation/errors, mapping/CRUD, and workspace boundaries.
- [x] 4.4 Preserve and rerun `AuthenticatedPrincipalContextWebFilterTest.kt` plus identity/tenancy context tests: exactly-once downstream invocation and selective cleanup.

## Phase 5: Measurement and Validation

- [x] 5.1 Run focused suites: `pnpm --filter app test:run`, `just backend-test-fast`, and PostgreSQL tests; fix only RED-test regressions.
- [x] 5.2 Run `just frontend-test-cov` and `just backend-coverage`; inspect `apps/web/{marketing,app}/coverage/lcov.info` and configured `build/reports/kover/report.xml`, compare missing changed lines with `main`, and repeat tests until both unchanged 80% gates pass with margin.
- [ ] 5.3 Run `just ci`, then verify all 14 Sonar findings are closed and `codecov.yml`, `sonar-project.properties`, and `.github/workflows/quality-gate.yml` have no threshold, exclusion, or semantic changes. Detekt and the targeted Ideas E2E blocker are resolved; full CI/final quality-gate validation remains pending because app LCOV is 69.49% project coverage, below 80%, and credentialed Sonar/Codecov evidence is unavailable.
