---
name: playwright
description: >
  Playwright E2E guidance for the Profile Tailors monorepo. Anchored to the
  real surfaces (apps/web/app, apps/web/admin, apps/web/marketing) and to
  the HAR-based mocking doctrine used by apps/web/app. Trigger when
  planning, generating, debugging, or running Playwright specs or fixtures
  in this monorepo.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  category: testing
  family: playwright
  source: local
  version: 2026-09-28
---

# Playwright E2E Testing Skill

Playwright guidance specific to the Profile Tailors monorepo. This skill
describes the real E2E surfaces, the canonical commands, the HAR-based
mocking doctrine used by `apps/web/app`, and the tag convention that
separates frontend-only runs from integration-style runs. Generic
Playwright patterns (POM, locator priority, accessibility helpers) live
in `playwright-best-practices/SKILL.md`; this file concentrates on what
is specific to this monorepo.

## When to use

- Planning, writing, or debugging a Playwright spec in this monorepo.
- Adding or refreshing a HAR capture for an API-backed flow in
  `apps/web/app`.
- Running an E2E lane locally or in CI.
- Distinguishing between mocked lanes (no backend) and real-CAS lanes (live SMP backend) on the
  dashboard; the admin lane is mocked today.
- Reviewing or updating Playwright config, fixtures, or selectors.

## Surfaces in scope

The monorepo has three E2E surfaces today. Each one ships its own
Playwright configuration, its own fixtures, and its own specs. Specs and
helpers live next to each surface. Shared consent contracts and validation
fixtures live under `shared/web/`; there is no shared E2E package.

| Surface            | Path                  | Config files                                                                                   | Specs / fixtures                                                                           |
|--------------------|-----------------------|------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Dashboard SPA      | `apps/web/app/`       | `e2e/playwright.config.ts`, `e2e/playwright.{media-mocked,media-real,scheduler,pwa}.config.ts` | `e2e/specs/`, `e2e/fixtures/` (incl. HAR-mocked `base-test.ts`), `e2e/pages/`, `e2e/hars/` |
| Admin SPA (mocked) | `apps/web/admin/`     | `e2e/playwright.mocked.config.ts`                                                              | `e2e/specs/`, `e2e/fixtures/` (stateful API fake), `e2e/pages/`                            |
| Marketing (Astro)  | `apps/web/marketing/` | `playwright.config.ts`                                                                         | `tests/e2e/*.spec.ts` (accessibility, consent, landing-page, seo, waitlist-form)           |

Shared code that several consumers touch (consent contract, validation
fixtures) lives under `shared/web/`. The local Playwright runner entry
point is `scripts/run-playwright.mjs` (it leases an ephemeral port,
forwards a small set of `PLAYWRIGHT_*` env vars, and shells out to
`pnpm exec playwright test`).

## Canonical commands

Prefer the `just` recipes listed below. Each recipe wraps the
underlying `pnpm` / `playwright` invocation, plumbs port leases, and
sets the right `PLAYWRIGHT_*` env vars. Run `just -l` against the repo
root to confirm the recipe set; if a recipe is missing, fall back to
the package-level command documented in `apps/web/<surface>/package.json`.

```bash
just playwright-install                                    # install Chromium/Firefox/WebKit binaries
just frontend-test-e2e                                     # marketing + apps/web/app Media-mocked lane
just app-test-e2e-media-mocked                             # apps/web/app Media Library mocked lane (HAR replay)
just app-test-e2e-media-real                               # apps/web/app Media Library real-CAS smoke lane
just app-test-e2e-media                                    # both app Media Library lanes (mocked + real)
just frontend-test-e2e-headed                              # visible-browser run, marketing + app mocked
just frontend-test-e2e-ui                                  # Playwright UI mode, marketing + app mocked
just frontend-test-e2e-report                              # open the last Playwright HTML report
```

Direct equivalents when `just` is not available:

```bash
node scripts/run-playwright.mjs -- --project=chromium       # any surface, port leased automatically
pnpm --filter app test:e2e:media:mocked                    # apps/web/app mocked lane
pnpm --filter app test:e2e:media:real                      # apps/web/app real-CAS lane
pnpm --filter marketing test:e2e                           # apps/web/marketing surface
pnpm --filter admin test:e2e                               # apps/web/admin mocked lane
```

If a spec belongs to a single tag group, narrow the run with `pnpm exec
playwright test --grep @<tag>` against the surface's working directory.

## HAR-based mocking doctrine (apps/web/app)

The dashboard SPA does not require a running SMP backend for the
common E2E paths. API responses are replayed from a HAR capture, with
explicit support for re-recording when the contract changes. The
mechanism is configured in `apps/web/app/e2e/playwright.config.ts`
(the HAR section) and wired into every spec through
`apps/web/app/e2e/fixtures/base-test.ts`.

### How it works

- `routeFromHAR` (registered in the base fixture) intercepts every
  `/api/*` request and serves responses from
  `apps/web/app/e2e/hars/auth-flow.har`.
- Requests that do not match a HAR entry fall through to the real
  network (static assets, fonts, etc. still come from the dev
  server).
- Per-spec overrides via `page.route()` take priority over HAR replay
  because they are registered after the base fixture. Use
  `route.fallback()` to keep an overridden request resolvable by
  replay when the spec wants that.

### Replay vs record

- **Replay (default)**: HAR serves API responses. The `webServer`
  block in the config starts only `pnpm run dev:app`; no SMP backend
  is required.
- **Record (`UPDATE_HAR=true`)**: `page.route()` calls hit the real
  SMP backend and the responses are captured into the HAR file.
  Requires the API to be reachable on the expected port.

```bash
pnpm exec playwright test --grep @frontend                                # HAR replay only
UPDATE_HAR=true pnpm exec playwright test --grep @integration              # re-record HAR from real backend
./apps/web/app/e2e/scripts/record-har.sh                                  # helper for the second form
```

When the API contract changes, commit the refreshed
`auth-flow.har` alongside the fixture change; never hand-edit the
HAR file.

## Tag convention

The dashboard SPA defines the canonical tag groups. Other surfaces
should align with this convention when they add tags.

| Tag            | Meaning                                                                                     | Backend needed                                     |
|----------------|---------------------------------------------------------------------------------------------|----------------------------------------------------|
| `@frontend`    | Specs that only need the Vite dev server (rendering, validation, responsive, i18n text)     | No                                                 |
| `@integration` | Specs that exercise API-backed flows via HAR replay and targeted Playwright route overrides | No (HAR replay by default; record mode flips this) |
| `@smoke`       | Fast subset kept green by every lane                                                        | Depends                                            |
| `@fast`        | Run during `just ci-local`; excluded from the longest lanes                                 | Depends                                            |

Examples already used in the codebase:

- `pnpm exec playwright test --grep @frontend`
- `UPDATE_HAR=true pnpm exec playwright test --grep @integration`

Do not invent new tag groups without updating `playwright-best-practices/SKILL.md`
and announcing the change. Reviewers should flag unknown tags as drift during
PR review.

## Locator and fixture guidance

- Prefer role/label/text locators before CSS selectors. Tables and
  examples are in `playwright-best-practices/SKILL.md` (the locator
  priority table there is canonical; do not duplicate it here).
- For the dashboard, import the HAR-mocked test from
  `apps/web/app/e2e/fixtures/base-test.ts` rather than from
  `@playwright/test` directly. Specs that bypass the base fixture lose
  HAR replay and will flake or hit the real backend.
- For the admin, use `apps/web/admin/e2e/fixtures/admin-mocks.ts`
  (a stateful fake that mirrors the SMP contract for the waitlist and
  bulk-invitation endpoints).
- For marketing, select via role-based locators and rely on the
  consent fixtures exported from `shared/web`.

## Running a single surface or browser project

```bash
pnpm exec playwright test --project=chromium                              # apps/web/app default
pnpm exec playwright test --project=firefox
pnpm exec playwright test --project=webkit
pnpm exec playwright test --project=mobile-chrome
pnpm exec playwright test --project=mobile-safari
pnpm exec playwright test --grep @smoke                                    # run only smoke slice
pnpm exec playwright test specs/login.spec.ts                             # single spec file
```

WebKit is intentionally excluded from the dashboard SPA test matrix;
the rationale is documented in `apps/web/app/e2e/playwright.config.ts`
(cookies set via `routeFromHAR` do not persist under WebKit/Safari).

## Anti-patterns

- Importing `test`/`expect` from `@playwright/test` directly inside
  `apps/web/app/e2e/specs/`; always import from `e2e/fixtures/base-test.ts`.
- Waiting for `networkidle`; use specific element waits instead.
- Hard-coded `page.waitForTimeout(...)`; prefer `expect(locator).toBeVisible()`.
- `click({ force: true })`; hides real accessibility gaps.
- Recording HARs in CI; recording requires the real backend and is a
  developer-machine action.
- Specs that depend on the order of other specs; each spec owns its
  fixtures and must be runnable in isolation.
- Tests that screenshot for visual validation; prefer ARIA snapshots
  and structural assertions.

## Accessibility checks

Both `apps/web/app` and `apps/web/marketing` use `@axe-core/playwright`
for automated WCAG 2.x scans. The pattern stays generic across
surfaces:

```ts
import AxeBuilder from '@axe-core/playwright'

test('page has no a11y violations', async ({ page }) => {
  await page.goto('/')
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze()
  expect(results.violations).toEqual([])
})
```

The marketing surface already runs accessibility as part of `just
frontend-test-e2e`; the dashboard opts in per spec.

## Determinism and CI expectations

- HAR replay lanes must be reproducible from a clean checkout with no
  SMP backend running; if a spec requires a live backend, it must
  live in the `@integration` group behind the `UPDATE_HAR` flip.
- Retries in CI default to 2 (`retries: process.env.CI ? 2 : 0`)
  across the three surfaces; do not lower this in spec-local overrides.
- HTML reports open only when `PLAYWRIGHT_HTML_OPEN !== 'never'`.
  The shared runner sets this to `'never'` to keep CI logs clean.
- All Playwright binaries live in the dev-dependency tree; refresh
  with `just playwright-install` after a Playwright version bump.
- The exact Playwright version is the value declared in
  `apps/web/{app,marketing,admin}/package.json`; do not hardcode it in
  specs, fixtures, or this skill.

## Resources

- `apps/web/app/e2e/README.md` — dashboard-side lane guide and the
  HAR capture playbook.
- `apps/web/admin/e2e/README.md` — admin mocked lane, including the
  stateful API fake contract.
- `apps/web/marketing/tests/e2e/README.md` — marketing Astro runs.
- `scripts/run-playwright.mjs` — port-leasing runner used by `just`.
- `playwright-best-practices/SKILL.md` — generic Playwright patterns (POM, locator priority table,
  accessibility, retries, fixtures).
