# Tasks: CAS Media Library E2E Tests

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1300 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (infra+mocked) → PR 2 (real smoke+composer) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Mocked config, fixtures, page object, media-mocked-ui spec | PR 1 | Base = main; self-contained, verifiable with `pnpm test:e2e:media:mocked` |
| 2 | Real smoke config, request ledger, real-smoke spec, composer spec, justfile/package.json commands | PR 2 | Base = PR 1 branch; requires infra-up + backend-run |

## Phase 1: Infrastructure / Foundation

- [x] 1.1 Create `e2e/fixtures/media-files.ts` — deterministic fixture generator: `base.png` (1×1 canvas), `base-copy.png` (identical bytes), `base-mutated.png` (different pixel/hash). Export manifest with `name`, `type`, `size`, `sha256`, `relation`. Verify: import in a throwaway spec, assert hash equality/inequality.
- [x] 1.2 Create `e2e/fixtures/media-mocks.ts` — `MediaRouteState` type + `registerMediaMocks(context, state)`. Stateful route handlers for `/api/media/**`: list (paginated), PUT initiate (201/200/409/429/5xx), POST upload, GET asset, DELETE, signed preview. Pattern follows `scheduler-mocks.ts`. Verify: unit-level assertions on state transitions.
- [x] 1.3 Create `e2e/fixtures/media-mocked-test.ts` — base fixture extending `@playwright/test` (NOT `base-test.ts` to avoid HAR). Provide `mediaPage`, `mockState`, auth mock via `mockAuthenticatedSession`. Verify: fixture compiles, `test.extend` exports `test`/`expect`.
- [x] 1.4 Create `e2e/pages/media-library-page.ts` — page object: `navigateTo()`, `uploadFile(path)`, `waitForUploadComplete()`, `getCards()`, `getCardByName(name)`, `deleteCard(name)`, `confirmDelete()`, `getCounter()`, `getEmptyState()`, `getErrorBanner()`. Verify: TS compiles, locators use `getByRole`/`getByTestId`.

## Phase 2: Mocked UI Coverage (PR 1 — first slice)

- [x] 2.1 Create `e2e/playwright.media-mocked.config.ts` — parallel workers, `testMatch: '**/media-mocked*.spec.ts'`, no HAR, trace/screenshot on failure. Follows `playwright.scheduler.config.ts` pattern. Verify: `npx playwright test -c e2e/playwright.media-mocked.config.ts --list` discovers zero specs (no specs yet).
- [x] 2.2 Create `e2e/specs/media-mocked-ui.spec.ts` — scenarios: empty library state, upload happy path (PUT 201 → POST → READY), upload error (PUT 429 blocks POST), loading/polling states, delete with confirmation, parallel context isolation (two tests share no state). Tags: `@media @mocked`. Verify: `pnpm test:e2e:media:mocked` passes all scenarios.
- [x] 2.3 Add `test:e2e:media:mocked` and `test:e2e:media:mocked:headed` scripts to `apps/web/app/package.json`. Verify: `pnpm test:e2e:media:mocked` runs.

## Phase 3: Real Smoke + Composer

- [x] 3.1 Create `e2e/fixtures/media-request-ledger.ts` — `CasEvent` type, `RequestLedger` class using `context.on('request'/'response'/'requestfailed')`. Methods: `forAsset(id)`, `forFixture(hash)`, `assertSequence(expected[])`, `assertZeroPosts()`. Verify: TS compiles, type exports resolve.
- [x] 3.2 Create `e2e/fixtures/media-real-test.ts` — auth via UI login or `storageState`, `runId` marker (`e2e-cas-{uuid}`), cleanup fixture (delete posts then assets by `runId` prefix, assert zero remaining). NO HAR, NO `/api/media/**` route interception. Verify: fixture compiles.
- [x] 3.3 Create `e2e/playwright.media-real.config.ts` — Chromium only, `workers: 1`, serial, `testMatch: '**/media-real*.spec.ts'`, trace/video on failure. Verify: `--list` discovers zero specs.
- [x] 3.4 Create `e2e/specs/media-real-smoke.spec.ts` — `@real-cas` tag. Scenarios: upload new content (ledger: PUT 201 → POST → READY), upload duplicate (ledger: READY/dedup, zero POST), delete asset. Record `200` vs `201` drift as known defect annotation. Verify: `pnpm test:e2e:media:real` with backend running.
- [x] 3.5 Create `e2e/specs/media-composer.spec.ts` — mocked config. Extend `compose-modal-page.ts` with `attachMedia()`, `removeAttachment()`, `getAttachmentPreview()`, `getPublishPayload()`. Scenarios: attachment readiness gates publish, 10 MiB limit rejects oversized, failed upload blocks submit, published payload includes `assetId`. Verify: `pnpm test:e2e:media:mocked` includes composer.

## Phase 4: Commands + Cleanup

- [x] 4.1 Add `test:e2e:media:real`, `test:e2e:media:real:headed` scripts to `apps/web/app/package.json`.
- [x] 4.2 Add justfile recipes: `app-test-e2e-media-mocked`, `app-test-e2e-media-real`, `app-test-e2e-media` (runs both). Verify: `just -l` shows new recipes, `just app-test-e2e-media-mocked` runs.
- [x] 4.3 Add known-defect annotations in mocked specs for: unnamed icon actions, missing field `id`/`name`, `PROCESSING` vs canonical lifecycle, absent composer library selector. Use `test.fixme()` or `test.skip()` with descriptive reason string + link to spec requirement. Verify: skipped tests appear in report with reason.
