# App E2E Guide

## Overview

This folder contains the Playwright end-to-end suites for the Profile Tailors SPA. The suites are split by runtime dependency so local runs and CI can choose the cheapest lane that still validates the contract.

## Changes

### Composer media attachment lanes

The composer media coverage uses tags within the existing Media Library Playwright projects:

| Tag | Project | Purpose | Command |
| --- | --- | --- | --- |
| `@composer-ui-mocked` | `media-mocked-chromium` | Deterministic mocked composer flows: picker staging, limits, removal, failure paths, preview swaps | `pnpm test:e2e:media:mocked -- --grep "@composer-ui-mocked"` |
| `@real-unsplash` | `media-real-chromium` | Real-backend Unsplash composer smoke coverage | `pnpm test:e2e:media:real -- --grep "@real-unsplash"` |
| — | `media-real-chromium` | Additional real provider coverage | Deferred |

The existing media projects stay in place for both composer and non-composer scenarios:

- `media-mocked-chromium` runs the mocked Media Library and composer specs.
- `media-real-chromium` runs the real Media Library smoke and Unsplash composer smoke specs.

### Lane topology

- `apps/web/app/e2e/playwright.media-mocked.config.ts`
  - `media-mocked-chromium` matches the mocked Media Library and composer spec files
  - use `--grep "@composer-ui-mocked"` to run only the mocked composer scenarios
- `apps/web/app/e2e/playwright.media-real.config.ts`
  - `media-real-chromium` matches the real Media Library spec files
  - use `--grep "@real-unsplash"` to run only the Unsplash composer smoke

### CI integration

- The mocked composer scenarios run through the existing app Media Library mocked lane.
- Real Unsplash composer smoke remains credential- and backend-dependent.

### WebKit Support Level (Dropped for Dashboard E2E)

The Dashboard SPA (`apps/web/app`) E2E test matrix officially excludes WebKit (Desktop Safari / Mobile Safari).

**Rationale:**
- **Engine-Level Limitations:** WebKit has a known Playwright limitation (and underlying browser driver issue) where it fails to set or persist cookies from intercepted responses (e.g., via `context.routeFromHAR` or programmatically intercepted `Set-Cookie` headers). Since the Dashboard SPA uses HttpOnly cookies for session/refresh token handling, and all Dashboard E2E tests run in a backend-free (HAR-replayed) environment, WebKit is incapable of running these tests.
- **CI Strategy:** For the Dashboard SPA, our CI workflows (`.github/workflows/ci.yml`) install and run E2E suites exclusively on **Chromium**-based targets, which is highly stable, deterministic, and cost-effective.
- **Cross-Browser Scope:** Full cross-browser matrix coverage (including Firefox and WebKit) is maintained for the static/Astro-based marketing site (`apps/web/marketing`) where layout and accessibility are verified across engines.

## Usage

### Run mocked composer lane

From repo root:

```bash
just app-test-e2e-media-mocked
```

From `apps/web/app`:

```bash
pnpm test:e2e:media:mocked
```

Run only the composer-tagged scenarios with:

```bash
pnpm test:e2e:media:mocked -- --grep "@composer-ui-mocked"
```

Target one scenario:

```bash
pnpm test:e2e:media:mocked -- --grep "ML-COMPOSER-015"
```

### Run real composer smoke lane

Required environment:

- `E2E_MEDIA_EMAIL`
- `E2E_MEDIA_PASSWORD`
- backend + app runtime that satisfies the existing real media smoke setup

From repo root:

```bash
just app-test-e2e-media-real
```

From `apps/web/app`:

```bash
pnpm test:e2e:media:real
```

Run only the Unsplash composer smoke with:

```bash
pnpm test:e2e:media:real -- --grep "@real-unsplash"
```

### Aggregated media lane

```bash
just app-test-e2e-media
```

This runs:

1. `app-test-e2e-media-mocked`
2. `app-test-e2e-media-real`

## Troubleshooting

### Provider-panel scenarios do not need Pinia mutation

Composer E2E specs MUST NOT mutate `$pinia.state` directly. Use mocked routes and UI actions only:

- channel limits come from the mocked `/api/publishing/channels` route
- provider visibility comes from the mocked `/api/flags` route
- picker state is driven through clicks and seeded mock assets

### Real composer smoke requires credentials

Set `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD` before running the real lane. The real fixture
authenticates through the login flow and fails fast when either variable is missing.

## References

- `apps/web/app/e2e/playwright.media-mocked.config.ts`
- `apps/web/app/e2e/playwright.media-real.config.ts`
- `apps/web/app/e2e/specs/composer-media-attachments-mocked.spec.ts`
- `openspec/changes/composer-media-attachments-playwright-e2e/design.md`
- `openspec/changes/composer-media-attachments-playwright-e2e/verify-report.md`
