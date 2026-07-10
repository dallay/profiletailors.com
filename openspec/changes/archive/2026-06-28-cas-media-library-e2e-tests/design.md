# Design: CAS Media Library E2E Tests

## Technical Approach

Add media-specific Playwright infrastructure under `apps/web/app/e2e/` that separates real CAS smoke
from mocked UI coverage. Real CAS uses a config/base fixture with no HAR or `/api/media/**`
interception; mocked coverage uses stateful route handlers. This implements
`openspec/specs/e2e/cas-media-library-test-plan.md` and the proposal without changing product code.

## Architecture Decisions

| Decision             | Choice                                                                                                                   | Alternatives considered                     | Rationale                                                                                                                                                                               |
|----------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Real CAS config      | Create `e2e/playwright.media-real.config.ts` importing a `media-real-test.ts` fixture with direct backend calls          | Reuse `playwright.config.ts`/`base-test.ts` | `base-test.ts` globally calls `context.routeFromHAR(... '**/api/**')`, which would mask CAS. Real config must never install HAR and should run Chromium serially for shared real state. |
| Mocked UI config     | Create `e2e/playwright.media-mocked.config.ts` + `media-mocked-test.ts` layering auth mocks and media state machine      | Add media routes to scheduler config        | Scheduler mocks are order-dependent and publication-focused. Media needs fresh per-context state and parallel-safe tests.                                                               |
| Page objects/helpers | Add `pages/media-library-page.ts`, extend `pages/compose-modal-page.ts`, add `fixtures/media-*` helpers                  | Inline selectors in specs                   | Existing E2E uses page objects. Media locators must capture current UI quirks: unnamed icon buttons, `data-testid` filters, hidden file inputs, status/counter text.                    |
| Fixture generation   | Generate deterministic files in `e2e/fixtures/media-files.ts` and write optional artifacts under `e2e/.generated/media/` | Commit binary fixtures                      | Keeps repo light while preserving manifest assertions for size/hash/relationships. Large 500 MB files stay backend-only/manual.                                                         |
| Request ledger       | Add `fixtures/media-request-ledger.ts` using `context.on('request/response/requestfailed')`                              | Global request counts                       | Parallel-safe assertions require correlation by URL, method, assetId, workspace header/query, fixture hash, and phase.                                                                  |

## Data Flow

Real CAS:

```text
Media spec -> media-real-test (auth/session) -> real SPA/backend
       -> request ledger observes PUT/POST/GET/DELETE -> cleanup by runId
```

Mocked UI:

```text
Spec -> media-mocked-test -> MediaRouteState per context
       -> route handlers mutate assets/uploads/publications -> page assertions
```

## File Changes

| File                                                 | Action            | Description                                                                                                                                                  |
|------------------------------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `apps/web/app/e2e/playwright.media-real.config.ts`   | Create            | Chromium-only real backend config, `testMatch: media-real*.spec.ts`, no HAR, `workers: 1`, trace/screenshot/video on failure.                                |
| `apps/web/app/e2e/playwright.media-mocked.config.ts` | Create            | Parallel mocked config, `testMatch: media-mocked*.spec.ts`, auth/workspace/media routes.                                                                     |
| `apps/web/app/e2e/fixtures/media-real-test.ts`       | Create            | Auth/session bootstrap/reuse for real CAS, runId/workspace markers, cleanup fixture.                                                                         |
| `apps/web/app/e2e/fixtures/media-mocked-test.ts`     | Create            | Extends coverage base or Playwright base with auth mocks plus `registerMediaMocks(context, state)`.                                                          |
| `apps/web/app/e2e/fixtures/media-mocks.ts`           | Create            | Route state machine for list, PUT states (`201/200/202/409/429/5xx`), POST upload, GET asset, DELETE, signed preview/content, composer publication payloads. |
| `apps/web/app/e2e/fixtures/media-files.ts`           | Create            | Deterministic fixture generator and manifest (`name`, `type`, `size`, `sha256`, expected relation).                                                          |
| `apps/web/app/e2e/fixtures/media-request-ledger.ts`  | Create            | Ordered request ledger with filters/assertions for CAS sequences and zero POST checks.                                                                       |
| `apps/web/app/e2e/pages/media-library-page.ts`       | Create            | Navigation, upload, filters, counters, cards, selection/delete dialogs, responsive assertions.                                                               |
| `apps/web/app/e2e/pages/compose-modal-page.ts`       | Modify            | Add media attach/remove/preview and submit payload helpers.                                                                                                  |
| `apps/web/app/e2e/specs/media-real-smoke.spec.ts`    | Create            | `@real-cas` P0 smoke slice.                                                                                                                                  |
| `apps/web/app/e2e/specs/media-mocked-ui.spec.ts`     | Create            | Loading, errors, polling, accessibility known defects, responsive, partial failures.                                                                         |
| `apps/web/app/e2e/specs/media-composer.spec.ts`      | Create            | Attachment readiness, one-file/10 MiB limits, failed upload blocking, assetId publication.                                                                   |
| `apps/web/app/package.json`                          | Modify            | Add `test:e2e:media:real`, `test:e2e:media:mocked`, headed/debug variants.                                                                                   |
| `justfile`                                           | Modify            | Add `app-test-e2e-media-mocked`, `app-test-e2e-media-real`, `app-test-e2e-media`.                                                                            |
| `.github/workflows/ci.yml`                           | Modify if present | Add mocked PR lane and optional/scheduled real CAS lane; repo currently has no workflow file.                                                                |

## Interfaces / Contracts

```ts
type CasEvent = { method: 'PUT'|'POST'|'GET'|'DELETE'; url: string; status?: number; assetId?: string; workspaceId?: string; fixture?: string; body?: unknown }
type MediaRouteState = { assets: MediaAssetSummary[]; uploads: Record<string, 'new'|'ready'|'waiting'|'failed'>; publications: unknown[] }
```

Auth/session reuse: mocked tests use existing `mockAuthenticatedSession`; real tests should prefer
Playwright `storageState` generated by setup only when seeded CAS accounts exist, otherwise log in
through UI and save per-run state outside git.

Cleanup: real fixture records assetIds/postIds immediately, deletes posts first then media, then
final query by `e2e-cas-{runId}-`; cleanup is idempotent and failure-visible.

## Testing Strategy

| Layer       | What to Test                                                                                                                           | Approach                                                                                                   |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Unit        | Fixture manifest and ledger filters                                                                                                    | Vitest or focused Playwright helper specs if logic grows.                                                  |
| Integration | Backend-only CAS invariants already owned by `server/smp/src/test/kotlin/com/profiletailors/smp/media/**`; add only missing contracts. | `just backend-test-fast` / `just backend-test-postgres` when backend slices are added.                     |
| E2E         | Browser-observable real CAS and mocked UI states                                                                                       | `just app-test-e2e-media-mocked`; `just infra-up && just backend-run` then `just app-test-e2e-media-real`. |

## Migration / Rollout

No migration required. Slice rollout: 1) infrastructure/helpers, 2) mocked UI PR lane, 3) real smoke
manual/pre-merge lane, 4) composer, 5) extended/workspace/concurrency/nightly. Tradeoff: real tests
give protocol confidence but are slower/flakier; mocked tests give deterministic UI coverage but
cannot prove storage invariants.

## Open Questions

- [ ] Exact seeded CAS account/workspace creation API is not identified; real fixture may need setup
  support before automation can run unattended.
- [ ] GitHub workflow path is absent; CI integration may be deferred until workflows exist.
