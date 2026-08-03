# Apply Progress: PR #577 Quality-Gate Remediation

## Delivery Context

- **Strategy:** `single-pr` with explicit `size-exception` approval.
- **Base:** `main`.
- **Branch:** `feat/app-product-tour`.
- **Scope:** unified PR #577; no threshold, exclusion, workflow, or delivery-order changes.

## Completed

### Sonar remediation

- Added stable labels and ids for the Analytics date preset/start/end controls.
- Added accessible associations for Ideas column selects/settings inputs and the saved-set name field.
- Extracted the CreatePostModal AI request branch from the nested ternary without changing request semantics.
- Changed `HashtagAnalysisPort` to a `fun interface`.
- Changed `HashtagsController.deleteSet` from `ResponseEntity<Void>` to `ResponseEntity<Unit>`.
- Replaced the Ideas handler's useless nullable fallback with an explicit non-empty-board branch and preserved the default-board fallback.

### Frontend coverage slice

- Added behavior tests for the Analytics store/view, Ideas store/view, AI content API, hashtag API/composable/panel, and CreatePostModal AI flows.
- Extended Ideas store tests for authentication/workspace guards, response normalization, create, optimistic rollback, update/delete/convert, and reset.
- Extended `IdeasView.test.ts` with real behavior coverage for workspace/loading/error/empty states, detail save/delete/convert success and failure, column edit/add/reorder/remove/save paths, drag/drop registration, movement normalization, no-op guards, and unmount cleanup.
- Added the IdeasView error branch so a failed board load is rendered instead of falling through to stale board content; no snapshot-only tests were added.
- Added Analytics store/view behavior tests for inclusive date presets and custom ranges, refresh fan-out, independent loading/error/fallback paths, empty/data rendering, number/rate/chart formatting, preview fallbacks, pagination, CSV success/failure/object-URL cleanup, and date-control effects.
- Added a minimal `try/finally` around CSV anchor clicks so a failed browser download still revokes its object URL.

### Backend coverage slice

- Added Ideas and Analytics handler/controller tests.
- Added Hashtag query/command handler tests and expanded local analysis/controller tests.
- Added PostgreSQL/Testcontainers repository coverage for Ideas, Analytics, and saved hashtag sets, including JSON mapping, pagination/aggregates, CRUD/upsert, rates, best-time rows, and workspace isolation.
- Added Ideas/hashtag tables to existing PostgreSQL cleanup support; no schema or production migration changed.
- Preserved and reran `AuthenticatedPrincipalContextWebFilterTest`.

### CreatePostModal coverage slice

- Extended `CreatePostModal.test.ts` with behavior coverage for focus-trap Escape/backdrop/cancel
  close paths, focus restoration, channel fallback when the selected channel changes, edit-mode
  updates without asset changes, initial-date initialization, custom-schedule validation (missing
  date, invalid time, and five-minute future boundary), deferred upload success/failure, media source
  controls, drag/paste guards, emoji insertion, and hashtag-panel toggling.
- Added test-wrapper cleanup tracking to avoid Teleport teardown races between modal instances.
- No production behavior change was required for this slice; the test suite exposed and then avoided
  a jsdom Teleport cleanup race by unmounting each modal before test cleanup.

## Validation Evidence

| Command | Result |
|---|---|
| `pnpm --filter app test:coverage` | PASS — fresh app LCOV regenerated; 117 files, 1,339 tests |
| `just frontend-test-cov` | PASS — fresh marketing LCOV regenerated; 11 files, 85 tests |
| `SMP_DB_TEST_PASSWORD=profiletailors ./gradlew :server:smp:test :server:smp:koverXmlReport -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest -x :server:smp:postgresIntegrationTest --no-daemon --rerun-tasks` | PASS — full backend test task and fresh Kover XML; build successful |
| `pnpm --filter app type-check` | PASS |
| Focused Biome check on `CreatePostModal.test.ts` | PASS |
| `git diff --exit-code origin/main -- codecov.yml sonar-project.properties .github/workflows/quality-gate.yml` | PASS — quality-gate configuration unchanged |
| `git diff --check` | PASS |

### Verify blocker continuation — 2026-08-03

- Detekt RED evidence: `just backend-lint` initially reproduced exactly six findings in the newly
  added tests: two `StringShouldBeRawString` findings and four `MaxLineLength` findings at the
  locations recorded by `verify-report.md`.
- Detekt GREEN evidence: converted the two escaped multiline test assertions to raw/multiline-safe
  constructions, wrapped the four long SQL seed statements, and reran `just backend-lint` — PASS,
  `BUILD SUCCESSFUL`, zero Detekt issues.
- Ideas E2E RED evidence: `pnpm --filter app exec playwright test -c e2e/playwright.config.ts
  e2e/specs/ideas-canvas.spec.ts` reproduced 4/9 failures: all three browsers timed out locating
  the `Save` button because the locale had unresolved `common.save`/`common.cancel` keys, and Mobile
  Chrome timed out locating the Ideas link while the responsive sidebar was closed.
- Ideas E2E diagnosis: the failures were a stale test/fixture contract plus a real mobile navigation
  behavior gap, not an Ideas board data regression. The column modal snapshot showed the literal
  `common.save` and `common.cancel`; the mobile snapshot showed the link present only inside the
  closed Sheet.
- Ideas E2E GREEN evidence: added `common.save`/`common.cancel` to both locales, made
  `SidebarNavSection` close the mobile Sheet after a route selection, added a focused regression test,
  and made the E2E navigation helper target the header trigger only when opening the mobile sidebar.
  Focused unit test passed (5/5), focused Ideas E2E passed 9/9 across Chromium, Firefox, and Mobile
  Chrome, and targeted Biome passed.
- Relevant follow-up validation: `pnpm --filter app type-check` PASS; `pnpm --filter app test:run`
  PASS with 117 files and 1,351 tests; `just backend-test-fast` PASS / BUILD SUCCESSFUL;
  `just backend-lint` PASS; quality-gate config diff against `origin/main` PASS; `git diff --check`
  PASS. Full frontend E2E was attempted but remains noisy/out of scope with pre-existing unrelated
  failures; no thresholds, exclusions, or workflow semantics were changed.

### Exact current-tree coverage metrics

- Marketing LCOV: **706/810 lines = 87.16%** (`apps/web/marketing/coverage/lcov.info`).
- App LCOV: **18,599/26,765 lines = 69.49%** (`apps/web/app/coverage/lcov.info`).
- App per-file metrics: `CreatePostModal.vue` **1,313/1,435 = 91.50%**; `ideas.store.ts` **364/377 = 96.55%**; `IdeasView.vue` **631/655 = 96.34%**; `AnalyticsView.vue` **327/327 = 100%**; `analytics.store.ts` **142/142 = 100%**; `HashtagSuggestionPanel.vue` **216/216 = 100%**; `useHashtagSuggestions.ts` **105/105 = 100%**; `ai-content-api.ts` **49/49 = 100%**; `hashtag-api.ts` **30/30 = 100%**.
- Backend Kover aggregate: LINE **13,920/15,524 = 89.67%**; INSTRUCTION **85,631/100,540 = 85.17%**; BRANCH **4,201/6,735 = 62.38%**; METHOD **2,300/2,578 = 89.22%**; CLASS **1,060/1,164 = 91.07%**.
- Relevant backend Kover packages: Ideas application **185/187 = 98.93%**, Ideas HTTP **52/52 = 100%**, Ideas persistence **102/102 = 100%**; Analytics application **43/43 = 100%**, Analytics HTTP **18/18 = 100%**, Analytics persistence **78/80 = 97.50%**; Hashtags application **83/83 = 100%**, Hashtags HTTP **31/31 = 100%**, Hashtags persistence **47/47 = 100%**, local analysis **110/110 = 100%**. Package marker/metadata entries remain non-executable `0/2` in each bounded-context package.
- Transparent changed-executable approximation from `origin/main` plus current worktree additions, intersected with fresh report line maps: frontend **2,811/3,013 = 93.30%**; backend/shared **1,265/1,311 = 96.49%**; combined **4,076/4,324 = 94.26%**. This excludes tests, generated/UI boilerplate, docs/OpenSpec, type-only/unmapped lines, and package metadata; it is a local approximation, not Codecov/Sonar parity.
- Main remaining mapped misses: CreatePostModal **45/481**, IdeasView **24/655**, Ideas store **13/377**, AppShell **3/14**; backend mapped misses are concentrated in Analytics persistence (**2**) and Ideas application (**2**), plus broader pre-existing security/publishing changed paths included by the origin/main comparison.

## Coverage Gap

- Exact current-tree LCOV: marketing **706/810 = 87.16%**; app **18,599/26,765 = 69.49%**. The app project report is **10.51 percentage points below** the unchanged 80% project target.
- Exact current-tree app per-file metrics: `CreatePostModal.vue` **1,313/1,435 = 91.50%**; `ideas.store.ts` **364/377 = 96.55%**; `IdeasView.vue` **631/655 = 96.34%**; `AnalyticsView.vue` **327/327 = 100%**; `analytics.store.ts` **142/142 = 100%**; `HashtagSuggestionPanel.vue` **216/216 = 100%**; `useHashtagSuggestions.ts` **105/105 = 100%**; `ai-content-api.ts` **49/49 = 100%**; `hashtag-api.ts` **30/30 = 100%**.
- Exact current-tree backend Kover: LINE **13,920/15,524 = 89.67%**; INSTRUCTION **85,631/100,540 = 85.17%**; BRANCH **4,201/6,735 = 62.38%**; METHOD **2,300/2,578 = 89.22%**; CLASS **1,060/1,164 = 91.07%**. Relevant package metrics: Ideas application **185/187 = 98.93%**, HTTP **52/52 = 100%**, persistence **102/102 = 100%**; Analytics application **43/43 = 100%**, HTTP **18/18 = 100%**, persistence **78/80 = 97.50%**; Hashtags application **83/83 = 100%**, HTTP **31/31 = 100%**, persistence **47/47 = 100%**, local analysis **110/110 = 100%**. Package marker entries are non-executable `0/2`.
- Transparent changed-executable approximation from `origin/main` plus current worktree additions: frontend **2,811/3,013 = 93.30%**; backend/shared **1,265/1,311 = 96.49%**; combined **4,076/4,324 = 94.26%** (**+14.26pp** over the 80% patch target). Tests, generated/UI boilerplate, docs/OpenSpec, type-only/unmapped lines, and package metadata are excluded; this is not Codecov/Sonar parity.
- Main remaining mapped misses: CreatePostModal **45/481**, IdeasView **24/655**, Ideas store **13/377**, AppShell **3/14**; backend misses include Analytics persistence (**2**) and Ideas application (**2**).

## Remaining Work

- `3.1`: Complete for this IdeasView slice; store/view coverage now includes the requested CRUD, column, drag/drop, loading/error/empty, and cleanup behavior.
- `3.2`: Complete for this AnalyticsView/store slice; all requested date, loading/error/empty/data, formatting, pagination, export, and effect behavior is covered.
- `3.3`: Complete. Added AI API response mapping coverage; CreatePostModal prompt validation, generation/optimization/regeneration, version selection/acceptance, rate-limit reset fallback, empty/generic errors, translated submit-label branches, create-another reset, nested-ternary remediation coverage, focus/channel/edit lifecycle behavior, scheduling validation, deferred media upload, and media/hashtag control behavior.
- `3.4`: Complete. Added hashtag API transport/204/error mapping tests, composable short/valid/error/loading/limit/saved-set flows, panel interactions and accessible-label coverage, and the trending-tag accessibility title.
- `4.1`: Complete. Added meaningful Ideas query/command handler, controller/problem-detail, and PostgreSQL repository edge coverage; preserved the principal-context regression.
- `5.2`: Complete. Generated fresh app LCOV and backend Kover, compared `origin/main...HEAD` plus working-tree additions, and recorded approximate patch coverage/remaining gaps. Overall app project coverage remains below 80%; no threshold/exclusion changes made.
- `5.3`: Full `just ci` and final credentialed Sonar/Codecov validation remain pending. The six Detekt
  blockers and four targeted Ideas E2E failures reported by verification are resolved with focused
  evidence. The exact current-tree app project report remains 69.49% versus the unchanged 80% gate;
  Sonar credentials remain unavailable. Current-source inspection confirms all 14 listed Sonar
  remediation locations are addressed, and quality-gate configuration is unchanged.

## Next Slice

Next action: SDD verify/full validation. The reported Detekt and Ideas E2E blockers are cleared; task
5.3 remains pending because raw app LCOV is below the unchanged 80% project gate and credentialed
Sonar/Codecov closure is unavailable.
