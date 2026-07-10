# App E2E Guide

## Overview

This folder contains the Playwright end-to-end suites for the Profile Tailors SPA. The suites are split by runtime dependency so local runs and CI can choose the cheapest lane that still validates the contract.

## Changes

### Composer media attachment lanes

The composer media coverage now uses dedicated tags and Playwright projects:

| Tag | Project | Purpose | Command |
|---|---|---|---|
| `@composer-ui-mocked` | `media-mocked-composer` | Deterministic mocked composer flows: picker staging, limits, removal, failure paths, preview swaps | `pnpm test:e2e:media:mocked:composer` |
| `@composer-smoke-real` | `media-real-composer` | Real-backend happy-path smoke coverage for composer attachments | `pnpm test:e2e:media:real:composer` |
| `@composer-provider-real` | `media-real-composer` | Reserved for real provider coverage | Deferred |

The existing media projects stay in place for non-composer scenarios:

- `media-mocked-chromium` runs mocked Media Library coverage except `@composer-ui-mocked`
- `media-real-chromium` runs real Media Library smoke except `@composer-smoke-real`

### Lane topology

- `apps/web/app/e2e/playwright.media-mocked.config.ts`
  - `media-mocked-chromium` excludes `@composer-ui-mocked`
  - `media-mocked-composer` runs only `@composer-ui-mocked`
- `apps/web/app/e2e/playwright.media-real.config.ts`
  - `media-real-chromium` excludes `@composer-smoke-real`
  - `media-real-composer` runs only `@composer-smoke-real`
  - when `E2E_MEDIA_EMAIL` or `E2E_MEDIA_PASSWORD` is missing, the real composer project resolves to an empty grep and skips discovery cleanly

### CI integration

- PR CI now runs `app-e2e-mocked-composer` when composer E2E paths change
- Real composer smoke remains for scheduled/manual execution because it needs live credentials and a real backend environment
- HTML reports for composer lanes annotate the generated `index.html` with:
  - `data-tag="@composer-ui-mocked"`
  - `data-tag="@composer-smoke-real"`

## Usage

### Run mocked composer lane

From repo root:

```bash
just app-test-e2e-media-mocked-composer
```

From `apps/web/app`:

```bash
pnpm test:e2e:media:mocked:composer
```

Target one scenario:

```bash
pnpm test:e2e:media:mocked:composer -- --grep "ML-COMPOSER-015"
```

### Run real composer smoke lane

Required environment:

- `E2E_MEDIA_EMAIL`
- `E2E_MEDIA_PASSWORD`
- backend + app runtime that satisfies the existing real media smoke setup

From repo root:

```bash
just app-test-e2e-media-real-composer
```

From `apps/web/app`:

```bash
pnpm test:e2e:media:real:composer
```

### Aggregated media lane

```bash
just app-test-e2e-media
```

This runs:

1. `app-test-e2e-media-mocked`
2. `app-test-e2e-media-mocked-composer`
3. `app-test-e2e-media-real`

## Troubleshooting

### Provider-panel scenarios do not need Pinia mutation

Composer E2E specs MUST NOT mutate `$pinia.state` directly. Use mocked routes and UI actions only:

- channel limits come from the mocked `/api/publishing/channels` route
- provider visibility comes from the mocked `/api/flags` route
- picker state is driven through clicks and seeded mock assets

### Real composer project discovers zero tests

That is expected when `E2E_MEDIA_EMAIL` or `E2E_MEDIA_PASSWORD` is missing. The config intentionally avoids failing discovery in environments without real credentials.

### Report headers

If you need to confirm lane attribution, open the generated HTML report and inspect the `<body>` tag for the lane `data-tag` attribute.

## References

- `apps/web/app/e2e/playwright.media-mocked.config.ts`
- `apps/web/app/e2e/playwright.media-real.config.ts`
- `apps/web/app/e2e/specs/composer-media-attachments-mocked.spec.ts`
- `openspec/changes/composer-media-attachments-playwright-e2e/design.md`
- `openspec/changes/composer-media-attachments-playwright-e2e/verify-report.md`
