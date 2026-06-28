## Exploration: CAS Media Library E2E tests

### Current State

The repository already has a Vue app Playwright harness under `apps/web/app/e2e`, but it is
auth/scheduler-oriented rather than media-oriented. The default `playwright.config.ts` runs all app
E2E specs across Chromium, Firefox, WebKit, Mobile Chrome, and Mobile Safari, starts only Vite on
port 5173, and layers `auth-flow.har` over `**/api/**` via `fixtures/base-test.ts`. The
scheduler-specific config only matches `scheduler*.spec.ts`, runs serial Chromium with one worker,
starts Vite with `VITE_API_BASE_URL=""`, and layers programmatic scheduler mocks on top of HAR auth.

Auth setup is reusable: `fixtures/auth-helpers.ts` can mock refresh, user profile,
workspaces/current workspace, register/login, and can bootstrap an authenticated session without the
real backend. Existing page objects cover login, dashboard, scheduler, post detail, and compose
modal, but there is no Media Library page object, no media route state machine, no binary fixture
generator/manifest, no request-ledger helper for CAS, and no real-backend workspace/run isolation
helper.

The media frontend already implements the PUT-first CAS client in `src/lib/media-api.ts`: compute
SHA-256, `PUT /api/media/assets/{assetId}`, upload raw bytes only for `201 PENDING_UPLOAD`, poll on
`202 WAITING_FOR_BLOB` respecting `Retry-After`, skip upload on `200/READY`, and surface
`409/429/4xx/5xx`. Unit tests cover much of that protocol in `media-api.test.ts` and store behavior
in `stores/media.test.ts`. The UI view currently loads `READY,PROCESSING,FAILED`, shows `PROCESSING`
counters/filter values, supports multi-file `image/*,video/mp4,application/pdf`,
search/type/status/sort controls, card previews, single/bulk delete dialogs, and load-more
pagination. This conflicts with the canonical CAS lifecycle (`PENDING_UPLOAD`, `UPLOADING`, `READY`,
`FAILED`, `DELETED`) and should be treated as known product drift, not silently normalized in tests.

Backend tests are stronger than browser infrastructure today. Application-level CAS coverage exists
in `MediaCasHandlersTest.kt` for new upload, dedup, waiting, idempotency, hash mismatch, rate limit,
integrity failures, delete/reference counting, expiration, and GC. PostgreSQL tests exist for
locking/upsert, active-by-hash lookup, schema constraints, partial unique indexes, and
storage-key/nullability rules. CI already runs frontend unit/app unit/backend unit, marketing E2E,
backend BDD, and a full-stack scheduler E2E job with Postgres + WireMock + Spring Boot + Vite, but
no app media E2E command or CI job exists.

### Affected Areas

- `openspec/specs/e2e/cas-media-library-test-plan.md` — source plan to implement; contains 76
  scenario rows, despite the request mentioning 70.
- `apps/web/app/e2e/playwright.config.ts` — default app E2E config uses HAR-only auth mocking and
  all browsers; unsuitable as-is for real CAS smoke because it has no backend/storage lifecycle or
  media tags.
- `apps/web/app/e2e/playwright.scheduler.config.ts` — reference for a domain-specific E2E config,
  but currently scheduler-only and serial due shared state.
- `apps/web/app/e2e/fixtures/base-test.ts` — registers HAR replay for all `**/api/**`; media
  real-CAS tests must bypass or scope this, while mocked media tests can extend it.
- `apps/web/app/e2e/fixtures/auth-helpers.ts` — reusable authenticated session bootstrap; should be
  reused for mocked UI and adapted/augmented for real backend sessions.
- `apps/web/app/e2e/fixtures/scheduler-mocks.ts` — best local model for route-backed stateful mocks;
  media needs its own isolated per-context mock state instead of extending scheduler state.
- `apps/web/app/e2e/pages/compose-modal-page.ts` — composer assertions and file attachment hooks are
  incomplete for media upload/CAS publication assertions.
- `apps/web/app/src/lib/media-api.ts` — existing CAS browser protocol owner; Playwright should
  validate observable sequences, not reimplement internals.
- `apps/web/app/src/stores/media.ts` — store handles retries, upload tracking, selection,
  pagination, and deletion; UI E2E should assert user-visible state, while current retries could
  obscure deterministic failures if not controlled.
- `apps/web/app/src/views/MediaLibraryView.vue` — primary UI under test; has stale `PROCESSING`
  terminology and icon-only card action buttons without accessible names.
- `apps/web/app/src/views/MediaLibraryView.test.ts` — current component-level media UI coverage for
  empty/cards/filter/search/sort/delete selection; good candidate to keep lower-level assertions out
  of Playwright.
- `apps/web/app/package.json` — only scheduler E2E scripts exist; needs media-specific
  scripts/config names during implementation.
- `.github/workflows/ci.yml` — has scheduler full-stack pattern but no media smoke/mocked UI/real
  CAS jobs.
- `server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt` —
  already owns many CAS invariants that should not be duplicated in Playwright.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcMediaRepositoriesPostgresTest.kt` —
owns Postgres concurrency/locking/repository invariants.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/MediaPostgresSchemaConstraintsTest.kt` —
owns database constraints/partial indexes.
- `server/smp/src/test/resources/media-fixtures/*` — existing small backend media fixtures; browser
  fixture strategy should generate/manifest deterministic files separately and avoid committing
  large binaries.

### Approaches

1. **Media-specific Playwright configs with split modes** — Add separate mocked UI and real CAS
   configs/fixtures while preserving current auth/scheduler tests.
    - Pros: clear tags, predictable CI, no HAR pollution of real CAS endpoints, mocked suite stays
      parallel-safe.
    - Cons: adds new config/scripts and fixture plumbing; real backend mode needs environment and
      isolation helpers.
    - Effort: Medium

2. **Fold media tests into existing app/scheduler Playwright configs** — Reuse
   `playwright.config.ts` for mocked UI and `playwright.scheduler.config.ts` for full-stack.
    - Pros: fewer files initially; less command surface.
    - Cons: scheduler config only matches scheduler files, is serial/shared-state by design, and
      HAR-on-all-API conflicts with real CAS protocol assertions.
    - Effort: Low initially, High maintenance

3. **Push most scenarios to backend/Vitest and keep Playwright smoke-only** — Use browser tests only
   for navigation/upload/browse/delete happy paths and validate the rest below browser.
    - Pros: fastest and least flaky; aligns with backend invariant ownership.
    - Cons: misses observable UI failure modes around loading, dialogs, accessibility, responsive
      behavior, and request sequencing.
    - Effort: Low/Medium

### Recommendation

Use approach 1. Implement three lanes matching the plan: `media-ui-mocked` (parallel, route-state
machine, every PR), `media-smoke-real` (serial CAS subgroups against real backend/Postgres/test
storage with request ledger and run isolation), and `media-backend-contract` (existing/new Kotlin
tests). Do not expand the scheduler fixture; create media-specific fixtures/page objects so state
remains per context and tests stay maintainable.

Smallest maintainable vertical slices:

1. **Harness slice**: media Playwright config(s), `MediaLibraryPage` page object, authenticated
   mocked session reuse, deterministic small fixture generator/manifest, and media mock state
   machine for list/upload/delete/preview.
2. **Mocked UI slice**: loading/empty/error, browse/search/filter/sort/counters, single/bulk delete
   cancellation/partial failure, keyboard/dialog/a11y known defects, mobile/zoom responsiveness.
3. **Real CAS smoke slice**: auth navigation, one PNG upload, duplicate/dedup no-POST, one mutated
   upload, browse/filter, delete persistence, mobile smoke; serial and isolated by run prefix.
4. **Composer slice**: one image/MP4 attach, 10 MiB boundary, failure blocks publish, READY
   `assetId` in publication request; reuse/extend compose page object.
5. **Backend contract slice**: fill gaps not already covered in Kotlin tests: controller-level CAS
   PUT/POST statuses/headers, filename boundary cases, 500 MB boundary/streaming as nightly/manual,
   signed URL tamper/authorization if not already under preview tests.

Scenario ownership:

- **Playwright real CAS**: ML-AUTH-001/002, ML-LOAD-004, ML-UP-001/002/004/006,
  ML-CAS-001/002/003/004/005/006, ML-VAL-001/002/006, ML-BROWSE-001/002/003/005/007/008/009,
  ML-DEL-001/002/003/004/007/008, ML-COMP-001/002/004/005/006/007/008, ML-WS-001/002/003,
  ML-RWD-001/002/003.
- **Playwright mocked UI**: ML-LOAD-001/002/003/005/006, ML-UP-003/005, ML-CAS-009,
  ML-ERR-001/002/003/004, ML-BROWSE-004/006/010/011, ML-DEL-006, ML-COMP-003, ML-WS-004,
  ML-A11Y-001/002/003/004/005/006, ML-RWD-004.
- **Existing/new backend tests**: ML-CAS-007/008, ML-VAL-003/004/005/007/008, ML-DEL-005, plus all
  backend-only invariants in the plan. Many are partially covered by `MediaCasHandlersTest.kt`,
  `R2dbcMediaRepositoriesPostgresTest.kt`, and `MediaPostgresSchemaConstraintsTest.kt`; add focused
  WebFlux/integration tests only where HTTP contract/status/header or storage boundary evidence is
  missing.

Known-failing/known-limitation candidates to mark explicitly:

- `PROCESSING` terminology in `MediaLibraryView.vue` and UI requests conflicts with canonical
  `PENDING_UPLOAD/UPLOADING`; tests should expose this drift.
- Card hover icon buttons for download/delete have no accessible names; mark ML-A11Y-004 as known
  defect until fixed.
- Search/select controls rely on `aria-label` but lack stable `id`/`name` for some fields; if the
  plan requires `id`/`name`, mark ML-A11Y-005 as known defect rather than suppressing.
- The composer currently has only one-file local upload and no Browse Media Library selector;
  ML-COMP-007 should be recorded as current limitation, not failure.
- Real CAS browser tests are blocked on documented seeded users/workspaces, cleanup/isolation
  helper, test storage configuration, and a way to run Vite against real Spring Boot without HAR
  intercepting CAS endpoints.

### Risks

- The plan catalog has 76 scenario rows, not 70; downstream tasking should reconcile the count
  before tracking completion.
- Real CAS tests can become destructive/flaky without unique run workspaces, cleanup by run marker,
  and no shared developer workspace.
- Current app E2E HAR intercepts all `/api/**`; using it accidentally in real CAS mode would
  invalidate network-sequence assertions.
- CI currently has no media E2E job/scripts and the existing scheduler E2E is serial/shared-state,
  so copying it blindly would create slow brittle tests.
- Browser tests cannot prove physical blob uniqueness, storage GC, DB locks, or 500 MB memory
  behavior; over-claiming these from Playwright would be misleading.

### Ready for Proposal

Yes — propose a dedicated `cas-media-library-e2e-tests` change that first builds media test harness
and mocked UI coverage, then adds a small real-CAS smoke lane behind explicit environment/isolation
prerequisites, and keeps storage/database/lifecycle invariants in backend tests.