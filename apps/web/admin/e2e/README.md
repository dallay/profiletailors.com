# Admin E2E (mocked lane)

[![Playwright](https://img.shields.io/badge/Playwright-1.x-2d3748?style=flat-square&logo=playwright&logoColor=2ead33)](https://playwright.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-6.x-2d3748?style=flat-square&logo=typescript&logoColor=3178c6)](https://www.typescriptlang.org)

Browser tests for the Back Office admin SPA. The mocked lane runs **without a
backend**: auth, session, waitlist, and bulk invitation endpoints are
intercepted by a stateful fake in `fixtures/admin-mocks.ts` that mirrors the
SMP contract (per-entry outcomes, summary counts, validation errors).

Test plan: `openspec/specs/e2e/bulk-waitlist-invitation-test-plan.md`

## Run

```bash
pnpm --filter admin test:e2e          # headless, managed vite server
pnpm --filter admin test:e2e:headed   # headed browser
pnpm --filter admin test:e2e:debug    # Playwright inspector
```

## Layout

- `e2e/playwright.mocked.config.ts` — lane config (chromium, `specs/*.spec.ts`)
- `e2e/fixtures/test-data.ts` — factories, principals, login helper
- `e2e/fixtures/admin-mocks.ts` — stateful API fake (`registerAdminMocks`)
- `e2e/fixtures/base-test.ts` — session setup helpers
- `e2e/pages/waitlist-page.ts` — page object for `/waitlist`
- `e2e/specs/` — one file per flow (`waitlist-bulk-invite.spec.ts`)

## Conventions

- One `registerAdminMocks(page)` call per test (fresh state, isolated tests).
- Later-registered routes win: override defaults inside a test for error paths
  (e.g. `route.abort('failed')` for transport failures).
- Prefer role/label/testid locators; no `waitForTimeout`; no screenshots for
  assertions (trace + screenshot on failure only, per config).
