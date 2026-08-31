# Tasks: Raise Marketing Site Quality and SEO

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 280–390; resource work is conditional |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR: RED contracts → source fixes → evidence |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr; base `trunk` |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Route contracts and failing tests | PR 1 | Base `trunk`; no production edits first |
| 2 | Head, crawl, and semantics | PR 1 | Depends on Unit 1; reversible source delta |
| 3 | Verification and operator evidence | PR 1 | Depends on Units 1–2; separate live/platform results |

## Phase 1: RED / Regression Coverage

- [ ] 1.1 Add failing Vitest cases in `apps/web/marketing/src/i18n/utils.test.ts` for the typed six-route inventory, locale pairing, canonical URL helpers, 12 metadata bounds, and uniqueness.
- [ ] 1.2 Extend `src/__tests__/robots.txt.test.ts` and add `apps/web/marketing/src/__tests__/sitemap.xml.test.ts` for valid robots/XML, approved bot `Allow: /`, HTTPS trailing slashes, and exactly 12 URLs.
- [ ] 1.3 Extend `apps/web/marketing/tests/e2e/seo.spec.ts` with failing all-12 checks for JSON-LD identity, one `main`/non-empty `h1`, reciprocal hreflang, prohibited links, and sitemap graph parity.
- [ ] 1.4 Extend `tests/e2e/accessibility.spec.ts` and `consent.spec.ts` for EN/ES legal axe scans, reduced motion, skip-link/locale focus, first visit, seeded consent, DNT/GPC, and analytics gating.

## Phase 2: GREEN / Production Implementation

- [ ] 2.1 Implement the named inventory and `counterpartPath`/`canonicalUrl` helpers in `src/i18n/utils.ts`, preserving `useTranslations` and legal publication state.
- [ ] 2.2 Update `src/layouts/Layout.astro` for helper-driven HTTPS canonicals, reciprocal alternates, legal robots, and truthful `WebSite`/`WebPage` JSON-LD.
- [ ] 2.3 Update `src/pages/sitemap.xml.ts` and `robots.txt.ts` to consume the inventory and emit valid escaped output without changing approved crawler policy.
- [ ] 2.4 Update legal page templates, `Nav.astro`, `Footer.astro`, locale wrappers, and only necessary `i18n/{en,es}.ts` copy for landmarks, headings, paired labels, links, and truthful parity.
- [ ] 2.5 Only after measured evidence, optimize `global.css`, scripts, consent/analytics, or waitlist resources; preserve consent, DNT/GPC, motion, theme, waitlist, and keyboard behavior.

## Phase 3: Focused Verification

- [ ] 3.1 Run focused Vitest for `utils.test.ts`, robots, sitemap, and changed consent/script tests.
- [ ] 3.2 Run `just frontend-check` and `just frontend-build`; parse `dist` for 12 pages, head, JSON-LD, robots, sitemap, and link invariants.
- [ ] 3.3 Run focused Playwright `seo.spec.ts` and `accessibility.spec.ts` against the preview for EN and ES.
- [ ] 3.4 Run `consent.spec.ts`, `landing-page.spec.ts`, and `waitlist-form.spec.ts` in first-visit, seeded, DNT/GPC, and reduced-motion states.

## Phase 4: Full Affected-Surface Checks

- [ ] 4.1 Run `just frontend-lint`, `just frontend-test`, `just frontend-check`, `just frontend-build`, and `just frontend-test-e2e`; record each result.
- [ ] 4.2 Run controlled Lighthouse for all 12 routes, mobile/desktop, and consent-visible/seeded states; record LCP, CLS, INP, FCP, TTFB, transfer, and render-blocking data.
- [ ] 4.3 Update `docs/marketing/seo.md` or `docs/marketing/lighthouse/baseline.json` only with reproducible evidence; review `git diff --check` and rollback scope.

## Phase 5: Deployment-Only Evidence Checklist

- [ ] 5.1 Operator verifies HTTP/HTTPS and `www` redirect chains, HSTS, cache headers, and canonical-host ownership with `curl -I`.
- [ ] 5.2 Operator verifies deployment provenance/cache purge and confirms live legal pages emit no `/cdn-cgi/l/email-protection` links.
- [ ] 5.3 Operator recrawls all 12 live URLs and records Lighthouse/Unlighthouse configuration plus an available Ahrefs crawl/export and timestamp.
- [ ] 5.4 Report Cloudflare, redirect, HSTS, cache, and Ahrefs results separately; do not claim a guaranteed 90+ score.
