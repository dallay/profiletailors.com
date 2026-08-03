## Exploration: PR #577 quality-gate remediation

### Current State

PR #577 is open at `4aff6746` on `feat/app-product-tour`, based on `main`. The remote PR diff is **243 files, 12,712 additions, and 807 deletions**. It combines product features (product tour, Ideas, Analytics, hashtags, AI composer), admin work, security/a11y work, legal/compliance documentation, tooling, and dependency changes. CodeRabbit explicitly skipped review because the PR contains 242 reviewable files, above its 100-file limit.

The failing quality signals are real and are not caused by a missing test command:

- Codecov patch coverage is `47.84946%`. The user-reported snapshot says **1,932 missed patch lines**; the latest Codecov bot comment on the checked head reports **1,940 missed lines**. This eight-line discrepancy is a report revision/line-accounting difference, not a material change in scope. The report lists approximately 1,780 covered patch lines, so reaching 80% requires covering roughly **1,190–1,196 additional changed lines** if the denominator stays stable.
- Sonar new-code coverage is `46.02792489167068%` against the configured 80% gate.
- Sonar new-code reliability is `C` / numeric rating `3.0`, with 10 new bugs and 4 new code smells. The Sonar quality gate requires rating `A` and therefore requires closing the findings, not only adding tests.
- All other observed PR checks pass: frontend/backend unit tests, builds, BDD, E2E, lint, CodeQL, Semgrep, Trivy, GitGuardian, Socket, and deployments. Only Quality Gate, SonarCloud analysis, and `codecov/patch` fail.

The repository configuration confirms that the thresholds must remain unchanged:

- `codecov.yml` sets project and patch targets to 80% with a 2% threshold.
- `sonar-project.properties` imports app LCOV and backend Kover XML reports and excludes only tests/generated/UI wrapper/build output; the affected feature files are in scope.
- `.github/workflows/quality-gate.yml` runs app coverage with `pnpm test:coverage`, backend Kover coverage, and Sonar/Codecov uploads. The BDD tasks are explicitly excluded from the backend coverage commands, so BDD scenarios are contract protection but cannot be treated as the primary patch-coverage solution.
- `apps/web/app/vite.config.ts` instruments all `src/**/*.ts` and `src/**/*.vue` files except tests/specs/declarations. There is no local coverage exclusion for the new feature code.

Sonar reports these 14 open issues for PR 577:

| # | Type/rule | Location | Required remediation |
|---|---|---|---|
| 1–3 | BUG / `Web:InputWithoutLabelCheck` | `AnalyticsView.vue:91`, `:107`, `:114` | Give each date/preset control a stable `id` and associated accessible label. |
| 4–9 | BUG / `Web:InputWithoutLabelCheck` | `IdeasView.vue:619`, `:669`, `:745`, `:746`, `:759`, `:760` | Add stable ids and associated labels for the custom select/input controls. |
| 10 | BUG / `Web:InputWithoutLabelCheck` | `HashtagSuggestionPanel.vue:162` | Add an id and associated label to the saved-set name input. |
| 11 | CODE_SMELL / `typescript:S3358` | `CreatePostModal.vue:932` | Replace the nested ternary with an independent statement/branch. |
| 12 | CODE_SMELL / `kotlin:S6517` | `HashtagAnalysisPort.kt:3` | Make the single-method interface functional or use an equivalent function type. |
| 13 | CODE_SMELL / `kotlin:S6508` | `HashtagsController.kt:54` | Replace `Void` with `Unit`. |
| 14 | CODE_SMELL / `kotlin:S6619` | `IdeasCommandHandlers.kt:32` | Remove the Elvis operation that Sonar identifies as always succeeding. |

The important coverage seams are:

- **Ideas frontend:** `ideas.store.ts` has 462 new lines but its only existing test covers three pure helpers. `loadBoard`, workspace guards, all mutations, optimistic move/rollback, response-shape readers, errors, column fallback, and `clearState` are untested. `IdeasView.vue` has 779 new lines and no view test; its quick capture, detail save/delete/convert, column editing, drag/drop branches, lifecycle cleanup, and error/loading paths are consequently almost entirely uncovered. Codecov reports approximately 627 missing lines in the view and 336 in the store.
- **Analytics frontend:** `apps/web/app/src/modules/analytics/infrastructure/analytics.store.ts` is a new 164-line API-backed store with no test. The existing `dashboard/infrastructure/analytics.store.test.ts` tests a different legacy mock store and does not cover this store. The new `AnalyticsView.vue` has no dedicated view test; date controls, loading/empty/error branches, metric formatting, chart rendering, best-times rendering, post preview, and export interaction are not covered. Codecov reports approximately 130 missing store lines.
- **AI composer:** `CreatePostModal.test.ts` now covers generate, regenerate, comparison, accept, and rate-limit display, but not optimize, validation early returns, empty response, generic failure, request option normalization, or the no-content acceptance branch. `ai-content-api.ts` has no dedicated service test and Codecov reports approximately 48 missing lines there. The modal still has approximately 105 missing changed lines.
- **Hashtag frontend:** `useHashtagSuggestions.ts` has no test and approximately 55 missing lines. Its API success/failure paths, minimum-content guard, 30-tag limit, set application, save/delete state transitions, and block formatting are all direct unit-test seams. `HashtagSuggestionPanel.vue` has no test; its computed fallback/limit branches and add/remove/apply/save/delete emits should be covered independently rather than through the 1,700-line composer. `hashtag-api.ts` also has no dedicated request-shape test.
- **Ideas backend:** `IdeasControllerTest` covers only a subset of dispatches. The six handlers and `R2dbcIdeaRepositories.kt` have no focused handler/repository tests; current BDD scenarios cover HTTP flows but are excluded from the Kover command. High-yield additions are handler tests with in-memory fakes for default columns, normalization, not-found/invalid-column paths, move normalization, delete, and conversion, plus PostgreSQL repository tests for JSON mapping, board upsert, update/delete, and workspace isolation.
- **Analytics backend:** There are no focused controller, handler, or repository tests for the new analytics context. `AnalyticsHandlers.kt`, `AnalyticsController.kt`, and especially `R2dbcAnalyticsRepository.kt` have substantial uncovered branches: default/custom dates, pagination, empty/non-empty aggregates, zero-impression rates, best-time row mapping, CSV quoting, and CSV response headers. Add fast handler/controller tests and a focused PostgreSQL repository integration test.
- **Hashtag backend:** Existing tests cover only one empty-content service case and two controller command responses. Query/command handlers, validation/error paths, `LocalHashtagAnalysisService` topic/inline-hashtag branches, saved-set repository mapping/CRUD, and problem-detail responses remain seams. Add focused service/handler/controller tests and a repository integration test; retain the BDD feature as contract coverage.
- **Request-context regression:** `AuthenticatedPrincipalContextWebFilterTest` already asserts the regression invariant that an authenticated request invokes the downstream chain exactly once and clears only principal context. Existing workspace/request-path tests cover context propagation and cleanup. This is an adequate focused seam; it should be preserved and rerun, not expanded as a major coverage workstream.

### Affected Areas

- `codecov.yml` — establishes the non-negotiable 80% patch/project gate.
- `sonar-project.properties` — establishes the Sonar source, test, LCOV, Kover, and exclusion scope.
- `.github/workflows/quality-gate.yml` — determines which tests and reports feed Sonar and Codecov; BDD is excluded from the backend coverage command.
- `apps/web/app/src/modules/ideas/presentation/views/IdeasView.vue` — largest single missing patch surface, with no component test.
- `apps/web/app/src/modules/ideas/infrastructure/ideas.store.ts` and `ideas.store.test.ts` — new store with only helper-level coverage today.
- `apps/web/app/src/modules/analytics/infrastructure/analytics.store.ts` — new API-backed store with no matching test; the existing dashboard analytics test targets another store.
- `apps/web/app/src/modules/dashboard/presentation/views/AnalyticsView.vue` — new view behavior and three Sonar label findings.
- `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` and `CreatePostModal.test.ts` — AI/hashtag behavior is partially tested; nested-ternary finding remains.
- `apps/web/app/src/modules/publishing/presentation/components/composer/HashtagSuggestionPanel.vue` — untested component and one label finding.
- `apps/web/app/src/modules/publishing/presentation/composables/useHashtagSuggestions.ts` — untested composable with multiple deterministic branches.
- `apps/web/app/src/modules/publishing/services/ai-content-api.ts` and `hashtag-api.ts` — new request adapters without direct tests.
- `server/smp/src/main/kotlin/com/profiletailors/smp/{ideas,analytics,hashtags}/**` — new handlers, controllers, ports, services, and R2DBC adapters with limited unit coverage.
- `server/smp/src/test/kotlin/com/profiletailors/smp/{ideas,hashtags}/**` — existing controller/service tests are useful foundations but do not cover the application and persistence layers.
- `server/smp/src/test/resources/features/{ideas-canvas,analytics-overview,hashtags}.feature` and matching BDD glue — valuable behavior coverage, but not sufficient for the quality-gate Kover run because BDD tasks are excluded there.
- `server/smp/src/{main,test}/kotlin/com/profiletailors/smp/{identity,tenancy,platform}` — request-context regression fix and existing cleanup tests.

### Approaches

1. **Keep PR 577 unified and add targeted real coverage** — close the 14 Sonar findings and add focused tests around the highest-missing executable files.
   - Pros: honors the existing decision to keep one PR; preserves the feature integration context; no threshold or exclusion changes; directly improves regression confidence.
   - Cons: still a large review unit; requires a substantial but bounded test tranche; component tests for the monolithic Ideas view and composer may be brittle.
   - Effort: High.

2. **Split the PR into feature/review slices, then remediate each gate** — separate Ideas, Analytics, composer/hashtags, security/a11y, and documentation/tooling before adding coverage.
   - Pros: smaller patch denominators, clearer ownership, better Sonar/Codecov attribution, and a realistic review surface; would also restore CodeRabbit reviewability.
   - Cons: conflicts with the explicit keep-one-PR decision; requires rebasing/stack management and may change delivery order; does not eliminate the need for tests or the 14 finding fixes.
   - Effort: High.

3. **Change thresholds/exclusions or rely on BDD/E2E as coverage** — weaken the gate or assume broad integration tests will supply the missing line coverage.
   - Pros: little implementation effort.
   - Cons: violates the stated constraint, does not resolve Sonar reliability findings, and the workflow excludes BDD from the backend Kover run. This is not a valid remediation approach.
   - Effort: Low technically, unacceptable procedurally.

### Recommendation

Keep the PR unified for now, but treat remediation as a strictly scoped test-and-static-fix batch. The current diff is objectively too broad for reliable review and gate attribution, but splitting is a product/process decision already rejected for this change. Do not add more features, refactor the product surfaces, lower thresholds, add coverage exclusions, or fabricate broad snapshot/“renders without error” tests.

The smallest credible plan is:

1. **First close all 14 Sonar findings.** Fix the ten real label associations and four small code smells. This is mandatory for the reliability gate; tests alone cannot turn `C/3` into `A`.
2. **Add high-yield frontend unit/component coverage.**
   - Extend `ideas.store.test.ts` with store-level tests for load/no-auth/error, response normalization, create/update, move success and rollback, delete, convert, columns, workspace guards, and reset state.
   - Add a focused `IdeasView` component suite for initial loading/error, quick capture, detail update/delete/convert, column add/reorder/remove/save, and representative drag/drop target branches. Cover behavior, not snapshots.
   - Add a new test file beside the API-backed analytics store for all fetch/error/finally paths, date presets/custom ranges, export blob/download handling, and `refresh` fan-out. Add a focused `AnalyticsView` suite for loading/empty/error/data branches and date/export interactions.
   - Add `useHashtagSuggestions` and `HashtagSuggestionPanel` unit/component tests for all deterministic branches and emitted actions. Add direct `ai-content-api`/`hashtag-api` request-shape tests and extend `CreatePostModal.test.ts` only for the missing optimize/validation/empty/generic-error paths.
3. **Add high-yield backend tests.**
   - Unit-test Ideas handlers/policies with fakes, then add repository integration coverage for JSON mapping, CRUD, board upsert, ordering, and workspace boundaries.
   - Unit-test Analytics handlers/controller/CSV construction, then add repository integration coverage for empty/non-empty ranges, pagination, date aggregation, zero-rate calculations, best-time mapping, and export rows.
   - Expand hashtag service/handler/controller tests and add saved-set repository integration coverage for CRUD, normalization, validation, not-found, and mapping.
4. **Preserve the request-context regression test** and rerun the focused identity/tenancy tests; no new broad test family is needed there.
5. **Re-run the exact quality-gate coverage commands and inspect the generated LCOV/Kover missing-line reports.** The first target is to cover approximately 1,190–1,196 additional patch lines, prioritizing the current Codecov leaders: `IdeasView`, `ideas.store`, analytics store/view, `CreatePostModal`, Ideas/Analytics repositories and handlers, `useHashtagSuggestions`, AI API, and hashtag repository. Stop adding tests once the real reports show the 80% gates with margin; do not chase arbitrary test counts.

This plan is large enough to be realistic about the gap but smaller than attempting to test every 243-file change uniformly. It should be proposed as a focused remediation slice even if it remains physically inside the unified PR.

### Risks

- The Codecov snapshot differs by eight missed lines between the user report and the latest PR comment; final planning must use a fresh post-change report rather than hard-coded line arithmetic.
- `IdeasView.vue` and `CreatePostModal.vue` are large monolithic components, so broad interaction tests can become slow and sensitive to shadcn/Teleport implementation details. Prefer stable test ids and behavior assertions.
- BDD and E2E passing does not imply Kover/LCOV patch coverage because the quality workflow uses separate coverage commands and excludes BDD tasks from backend coverage.
- R2DBC repository coverage needs a database-capable test path for SQL/mapping branches; fake-only tests will leave the largest backend files uncovered. Docker/Testcontainers availability and CI database credentials are prerequisites.
- Sonar’s ten accessibility findings count as reliability bugs; omitting even one can leave the `C` rating gate failing after coverage reaches 80%.
- Covering only the new frontend tests may improve Codecov substantially but still leave Sonar new-code coverage below 80% because the backend additions are also in Sonar scope.
- The unified PR remains difficult to review and CodeRabbit remains unavailable at the current size; splitting is the cleaner long-term option but conflicts with the current delivery decision.

### Ready for Proposal

Yes. The proposal should state the immutable 80% thresholds, the mandatory closure of all 14 Sonar issues, the unified-PR constraint, the focused frontend/backend test scope above, and the exact report-driven verification sequence. No application code or quality-gate configuration should be changed during exploration.
