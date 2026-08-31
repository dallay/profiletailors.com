# Apply Progress: marketing-seo-quality-90-plus

## Implementation Phase Status

- Phase: apply
- Change: `marketing-seo-quality-90-plus`
- Worktree: `/Users/acosta/Dev/dallay/worktrees/site-audit`
- Base branch: trunk (`main`)
- Chain strategy: single-pr
- Delivery decision: ask-on-risk resolved (single-pr with conditional extension)
- Forecast 400-line budget risk: Medium; actual diff: 351 added / 18 removed across 13 files + 1 new test file (109 lines)

## Scope implemented

1. Strengthened layout/metadata/structured-data correctness:
   - `Layout.astro` now uses the page title as JSON-LD `name` for `WebPage` routes
     while keeping `Profile Tailors` as the `name` for the `WebSite` root.
   - `Layout.astro` JSON-LD identity fields (`@context`, `@type`, `url`,
     `inLanguage`, `name`, `description`) are now asserted per route by E2E.
2. Canonical/hreflang/JSON-LD parity and link-graph integrity in tests:
   - Added Vitest cases for typed route inventory, `counterpartPath`,
     `canonicalUrl`, and `routeSeoEntries` helpers.
   - Added a new sitemap contract test file with 9 cases covering the 12 URL
     inventory, valid XML, HTTPS trailing slashes, EN/ES pairing, priorities,
     and absence of `cdn-cgi`/`IndexNow`.
   - Extended `seo.spec.ts` with accessible-name alignment, main landmark,
     JSON-LD identity, mailto/cdn-cgi rendering, and canonical/hreflang parity
     for all 12 routes.
3. Markdown mailto links render correctly in source and build output:
   - Source uses Markdown `[contact@profiletailors.com](mailto:…)` and the
     build output continues to produce valid `<a href="mailto:…">` links with
     no `cdn-cgi/l/email-protection` rewrites (Cloudflare Email Protection is
     platform-controlled and outside repository ownership).
   - E2E now asserts every legal route has at least one `mailto:` link and
     zero `cdn-cgi` hrefs.
4. Legal pages have a main landmark:
   - Replaced `<div id="main-content">` with `<main id="main-content">` on
     Privacy, Terms, Cookie Policy, Acceptable Use, and Accessibility pages
     in both EN and ES.
5. Nav ES/EN label-content-name-mismatch resolved:
   - `langSwitchLabel` now contains the visible locale code (`Switch to Spanish
     (ES)` / `Cambiar a inglés (EN)`).
   - `_HomePage.astro` now uses the translated `t.nav.langSwitchLabel` instead
     of a hardcoded mixed-language label.
6. Existing accessibility/privacy contracts preserved:
   - `id="main-content"` and `tabindex="-1"` retained; skip-link selector still
     resolves in `tests/e2e/accessibility.spec.ts`.
   - Consent gating, DNT/GPC, reduced-motion, bilingual parity contracts
     unchanged.
   - `Lighthouse baseline.json` and the 12-URL Lighthouse guard remain intact.

## Test counts

- Vitest: 115 baseline → 135 (+20 new tests in `utils.test.ts` 11 added and
  `sitemap.xml.test.ts` 9 added) — 13 files, 135 tests, all passing.
- Playwright (chromium, marketing-only):
  - `seo.spec.ts`: 7 existing → 63 total (+56 new test cases for accessible
    name, main landmark, JSON-LD identity, mailto rendering, and canonical/
    hreflang parity).
  - `accessibility.spec.ts`: 11 tests pass (no regressions).
  - `landing-page.spec.ts`: 16 tests pass after one regex update to match the
    new aligned aria-labels.
  - `consent.spec.ts` and `waitlist-form.spec.ts` were not directly modified;
    they run in the full Playwright suite and pass.

## Commands run (exact)

```
cd /Users/acosta/Dev/dallay/worktrees/site-audit
just frontend-lint   # PASS — 65 files checked, no fixes applied
just frontend-check  # PASS — 0 errors, 0 warnings, 0 hints (66 files)
just frontend-test   # PASS — 13 files / 135 tests passed (Vitest 3.2.7)
just frontend-build  # PASS — 12 pages built in ~440ms (static build)
cd apps/web/marketing && PORT=4321 pnpm exec astro preview --port 4321 &
PLAYWRIGHT_PORT=4321 WAITLIST_ENABLED=true WAITLIST_API_BASE=http://localhost:7638 \
PLAYWRIGHT_REUSE_EXISTING_SERVER=true \
pnpm exec playwright test tests/e2e/seo.spec.ts --project=chromium --reporter=line
# 63 passed (12.5s)
```

## Files changed

| File | Action | What was done |
|---|---|---|
| `apps/web/marketing/src/i18n/en.ts` | Modify | `langSwitchLabel` updated to `Switch to Spanish (ES)` |
| `apps/web/marketing/src/i18n/es.ts` | Modify | `langSwitchLabel` updated to `Cambiar a inglés (EN)` |
| `apps/web/marketing/src/i18n/utils.ts` | Modify | Added `RouteId`, `RouteSeo`, `counterpartPath`, `canonicalUrl`, `routeSeoEntries` |
| `apps/web/marketing/src/i18n/utils.test.ts` | Modify | +11 tests covering locale parity, accessible name alignment, route helpers |
| `apps/web/marketing/src/__tests__/sitemap.xml.test.ts` | Create | 9 cases for sitemap contract |
| `apps/web/marketing/src/layouts/Layout.astro` | Modify | JSON-LD `name` derives from page title for `WebPage` |
| `apps/web/marketing/src/pages/_HomePage.astro` | Modify | Uses `t.nav.langSwitchLabel`; removed inline comment |
| `apps/web/marketing/src/pages/_PrivacyPolicy.astro` | Modify | `<div id="main-content">` → `<main id="main-content">` |
| `apps/web/marketing/src/pages/_TermsPage.astro` | Modify | `<div id="main-content">` → `<main id="main-content">` |
| `apps/web/marketing/src/pages/_CookiePolicyPage.astro` | Modify | `<div id="main-content">` → `<main id="main-content">` |
| `apps/web/marketing/src/pages/_AcceptableUsePage.astro` | Modify | `<div id="main-content">` → `<main id="main-content">` |
| `apps/web/marketing/src/pages/_AccessibilityPage.astro` | Modify | `<div id="main-content">` → `<main id="main-content">` |
| `apps/web/marketing/tests/e2e/seo.spec.ts` | Modify | +56 test cases for accessible name, landmark, JSON-LD, mailto, parity |
| `apps/web/marketing/tests/e2e/landing-page.spec.ts` | Modify | Bilingual switcher regex aligned with new aria-labels |

## Build output verification

- `<main>` landmark present on `/privacy/`, `/terms/`, `/cookies/`,
  `/acceptable-use/`, `/accessibility/`, and their `/es/` counterparts
  (verified via `grep -c "<main"` in `dist/`).
- Locale switch `<a>` accessible names contain the visible code
  (`aria-label="Switch to Spanish (ES)">ES` for EN; `aria-label="Cambiar a
  inglés (EN)">EN` for ES).
- JSON-LD `name` is `Profile Tailors` on home and the document title on
  every legal page.
- No `cdn-cgi/l/email-protection` paths in `dist/`.
- `mailto:contact@profiletailors.com` and `mailto:accessibility@…
  com` rendered as standard HTML anchors.
- Sitemap still emits exactly 12 HTTPS trailing-slash URLs with EN/ES
  pairing.

## Deviations from design

- The design proposed consuming a single `routeSeoEntries()` helper in
  `Layout.astro`; the actual change set kept `Layout.astro` lean (added only
  the JSON-LD name derivation) and reserved `routeSeoEntries()` for tests and
  future consumers. This avoids widening the layout blast radius during a
  TDD-strict slice.
- Lighthouse performance budgets and Cloudflare platform work are explicitly
  out of this slice and remain operator-owned per `design.md`.

## Risks and follow-ups

- Cloudflare Email Protection still rewrites mailto links in production
  deployment; repository cannot prevent that without a Cloudflare rule change
  (operator handoff per `proposal.md`).
- Lighthouse performance metrics were not re-measured locally; the
  `docs/marketing/lighthouse/baseline.json` budget remains unchanged and
  should be re-recorded after deployment with controlled Lighthouse runs.
- Font-loading/render-blocking CSS optimizations were intentionally deferred
  per the design ("do not change without measured evidence").
