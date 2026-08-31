# QA Report: marketing-seo-quality-90-plus

## 1. Identity

| Field | Value |
|---|---|
| Change | `marketing-seo-quality-90-plus` |
| Mode | capability-driven acceptance QA |
| Phase | qa (apply → verify → qa → archive) |
| Date | 2026-08-31 |
| Worktree | `/Users/acosta/Dev/dallay/worktrees/site-audit` |
| Base | trunk (`main`) |
| Capability scope (in) | Durable code-controlled product-visible behaviour: metadata, structured data, canonical/hreflang, sitemap, robots, accessibility landmarks, accessible names, mailto rendering |
| Capability scope (out) | Cloudflare platform rules, redirects/HSTS/cache, backlinks, Ahrefs ranking, Lighthouse performance budgets |

## 2. Source artifacts and technical handoff

| Artifact | Path | Status |
|---|---|---|
| Proposal | `openspec/changes/marketing-seo-quality-90-plus/proposal.md` | read |
| Design | `openspec/changes/marketing-seo-quality-90-plus/design.md` | read |
| Tasks | `openspec/changes/marketing-seo-quality-90-plus/tasks.md` | read |
| Exploration | `openspec/changes/marketing-seo-quality-90-plus/exploration.md` | read |
| Apply progress | `openspec/changes/marketing-seo-quality-90-plus/apply-progress.md` | read |
| Delta spec | `openspec/changes/marketing-seo-quality-90-plus/specs/marketing-a11y-seo/spec.md` | read |
| Main spec | `openspec/specs/marketing-a11y-seo/spec.md` | read (delta is a MODIFIED/ADDED superset) |
| `verify-report.md` | present | 263 lines, verdict `PASS` (operator-owned warnings deferred) — written by orchestrator before QA; see §8 verdict rationale for archive gate |
| `state.yaml` | not produced | n/a |

Worktree status: `13` files modified, `1` new test file, `351` insertions / `18` deletions per `git diff --stat`. Diff matches the file table in `apply-progress.md`.

## 3. Target, environment, permissions, limitations

| Item | Value |
|---|---|
| Target | `apps/web/marketing/` (Astro 7.2.6 static-first site, 6 EN + 6 ES routes) |
| Environment | Local macOS, Node `>=24.19.0`, pnpm 11, Vitest 3.2.7, Playwright chromium |
| Permissions | Repository write limited to `openspec/changes/marketing-seo-quality-90-plus/qa-report.md`. No source code edited by QA. |
| External evidence | None — Live `curl`/`curl -I` of `profiletailors.com`, Ahrefs recrawl, and Cloudflare dashboard are explicitly out of scope per `proposal.md` (operator handoff) and per the QA prompt. |
| Limitation | Multi-browser Playwright (firefox, webkit, Mobile Chrome, Mobile Safari) and Lighthouse performance budgets were not executed; the user prompt places Lighthouse performance budgets out of scope. |
| Limitation | The seeded-consent and DNT/GPC E2E states from `consent.spec.ts` were exercised by the test runner; consent banner behaviour is part of the marketing E2E lane and was not separately run for all 12 routes. |
| Limitation | No external Ahrefs or live Cloudflare evidence was fabricated. |

## 4. Capability inventory

| Capability | Selected | Status | Rationale |
|---|---|---|---|
| Static build output (12 HTML + robots.txt + sitemap.xml) | yes | available | `just frontend-build` exercised; `dist/` parsed |
| Vitest unit suite (utils, robots, sitemap, scripts, components) | yes | available | `just frontend-test` exercised |
| Biome lint | yes | available | `just frontend-lint` exercised |
| Astro type check | yes | available | `just frontend-check` exercised |
| Playwright e2e — SEO crawl, link graph, accessible names, main landmarks, JSON-LD, mailto rendering, canonical/hreflang parity | yes | available | `seo.spec.ts` chromium run |
| Playwright e2e — axe WCAG 2.2 AA under reduced-motion, skip link, keyboard reach | yes | available | `accessibility.spec.ts` chromium run |
| Playwright e2e — landing hero, bilingual switch, responsive, perf smoke | yes | available | `landing-page.spec.ts` chromium run |
| Playwright e2e — consent: accept-all, reject-all, DNT, GPC, save preferences, outdated version | yes | available | `consent.spec.ts` chromium run |
| Playwright e2e — waitlist form happy path, validation, 429, 202 | yes | available | `waitlist-form.spec.ts` chromium run |
| Lighthouse performance budgets (mobile/desktop, consent-visible/seeded) | rejected | out of scope | User prompt explicitly excludes Lighthouse performance budgets |
| Live Cloudflare transforms / email-protection rewrite | rejected | out of scope | User prompt explicitly excludes Cloudflare platform rules |
| Live `curl -I` redirect chain / HSTS / `www` | rejected | out of scope | User prompt explicitly excludes redirects/HSTS/cache |
| Backlinks / Ahrefs ranking | rejected | out of scope | User prompt explicitly excludes backlinks and Ahrefs ranking |
| Multi-browser Playwright (firefox/webkit/Mobile Chrome/Mobile Safari) | rejected | not exercised | Out of QA scope per prompt; smoke is sufficient for capability acceptance |
| Deployment / live evidence | rejected | out of scope | Deployment is an operator-owned track per `proposal.md` |

## 5. Scenario matrix

Each scenario references the delta spec `openspec/changes/marketing-seo-quality-90-plus/specs/marketing-a11y-seo/spec.md` unless noted. Status values: `PASS`, `FAIL`, `BLOCKED`, `NOT TESTED`. Static inspection does NOT yield `PASS`; all `PASS` rows have an executable check or built-output observable.

### 5.1 MODIFIED Requirement: SEO Invariants — 12 URLs

| Scenario | Result | Evidence |
|---|---|---|
| Metadata is unique and bounded (titles ≥30 chars, suffix ` — Profile Tailors`, unique; descriptions 120–160, unique) | PASS | `apps/web/marketing/src/i18n/utils.test.ts` (54 tests): "all 12 titles are >=30 characters", "all 12 titles end with branded suffix", "all 12 titles are unique", "all 12 descriptions are 120-160 characters", "all 12 descriptions are unique" — all green. Build `dist/` parse confirmed: 12 distinct titles, length min 34 / max 68, all descriptions in 128–159 range. |
| Heading invariant holds (exactly one non-empty h1) | PASS | `seo.spec.ts:188 "Single H1 and Canonical and hreflang and og on 12 URLs"` asserts `expect(h1).toHaveCount(1)` and `h1Text.length > 0` on every URL. All 12 URLs green. `dist/` parse: every HTML has exactly one `<h1>`. |
| Canonical and hreflang are reciprocal (HTTPS, trailing slash, en/es/x-default, EN↔ES) | PASS | `seo.spec.ts:188` asserts canonical matches `canonicalMap[url]` and en/es/x-default match the documented map for each URL. `seo.spec.ts:398..429` "canonical and hreflang parity between EN/ES counterparts" runs 12 parity assertions (en↔es, x-default). All green. Build `dist/` grep: canonical and three hreflang present per page; canonical points to its own URL, en/es point to their counterpart, x-default matches en. |
| Indexability follows publication (legal approved → `index,follow`; unapproved → `noindex,nofollow`) | PASS | `seo.spec.ts:168 "Layout robots meta is index,follow on 12 URLs"` asserts `directives` contains `index`, `follow`, and not `noindex`, `nofollow` on every URL. `legal-publication.ts` exports `legalPublicationStatus = APPROVED` and `utils.test.ts:81` asserts it. Build `dist/` grep: every page emits `index,follow,max-image-preview:large,max-snippet:-1,max-video-preview:-1`. |

### 5.2 MODIFIED Requirement: Robots and Sitemap Routes

| Scenario | Result | Evidence |
|---|---|---|
| Robots and sitemap are served (valid documents, sitemap = 12 URLs) | PASS | `seo.spec.ts:100 "Sitemap parity — 12 loc and inbound >=1"` GETs `/sitemap.xml`, asserts status 200 and 12 loc elements with `^https://profiletailors.com/` and `/$`. `seo.spec.ts:151 "per-bot Allow and Does not block AI"` GETs `/robots.txt`, asserts 200 and per-bot stanzas. `__tests__/robots.txt.test.ts` (2 tests) and `__tests__/sitemap.xml.test.ts` (9 tests) all green. `just frontend-test` reports `135/135` passed. |
| Approved crawlers are not blocked (`Allow: /` for wildcard and 7 bots, no `Disallow: /`, `index,follow` on all 12) | PASS | `seo.spec.ts:151` asserts `User-agent: *` + `Allow: /` + `Sitemap:` line + each of `OAI-SearchBot`, `GPTBot`, `PerplexityBot`, `ClaudeBot`, `Google-Extended`, `GoogleOther`, `Bingbot` has an `Allow: /` stanza; no `Disallow: /`. Build `dist/robots.txt` shows the same structure. `seo.spec.ts:168` confirms `index,follow` on all 12 routes (see 5.1). |

### 5.3 MODIFIED Requirement: Link Hygiene — No Broken/Orphan

| Scenario | Result | Evidence |
|---|---|---|
| Crawlable links resolve (status < 400, no `http://`, no `cdn-cgi`) | PASS | `seo.spec.ts:47 "No broken or obfuscated href (12 URLs)"` crawls every page, asserts no `cdn-cgi` and no `http://` in any internal href, then `request.get(target)` asserts status `< 400` for every normalised destination. All 12 URLs green. `seo.spec.ts:81 "No http href on any of 12 URLs"` (whitelists `xmlns:serif` namespace only) all green. Build `dist/` grep: `cdn-cgi` count = 0, `href="http://"` count = 0. |
| Sitemap and graph have parity (every sitemap route has inbound coverage, no `nofollow`) | PASS | `seo.spec.ts:100` builds a `Map<pathname, count>`, counts only `nofollow`-free inbound anchors across all 12 URLs, asserts `count >= 1` for every sitemap loc. All 12 paths have ≥1 inbound followable link. |

### 5.4 MODIFIED Requirement: Performance Budget — Measure Only

| Scenario | Result | Evidence |
|---|---|---|
| Controlled performance evidence is recorded | NOT TESTED | User prompt explicitly excludes Lighthouse performance budgets. Repository baseline is `docs/marketing/lighthouse/baseline.json` (12-URL measure-only). No new Lighthouse runs were executed in this QA. |
| Performance changes preserve contracts (consent, banner, form, motion, keyboard) | PASS (non-lighthouse) | `consent.spec.ts` (8 tests) covers accept-all/reject-all/DNT/GPC/seeded preferences/outdated version. `waitlist-form.spec.ts` (6 tests) covers validation, 202/429, key submission. `accessibility.spec.ts` (11 tests, reduced-motion axe) covers no WCAG 2.2 AA violation on landing + legal pages. No source code in `global.css`, `ConsentScript`, `ConsentBanner`, `Analytics`, `WaitlistForm`, or motion scripts was modified by the change (`apply-progress.md` confirms). |

### 5.5 ADDED Requirement: Structured Data Matches Page Identity

| Scenario | Result | Evidence |
|---|---|---|
| Structured data validates (`@type`, URL, language match route + locale) | PASS | `seo.spec.ts:331 "JSON-LD structured data identity"` (12 tests) parses `script[type="application/ld+json"]`, asserts `@context == https://schema.org`, `@type ∈ {WebSite, WebPage}` matches `expectedJsonLd[url]` (home → `WebSite`, legal → `WebPage`), `inLanguage` matches locale, `url` matches canonical, `description.length >= 50`, `name.length > 0`. Build `dist/` grep confirms the same payload: `name = "Profile Tailors"` for the two `WebSite` pages; `name = "<Page Title>"` for the ten `WebPage` pages; matching `url`, `inLanguage`, and `description`. |

### 5.6 ADDED Requirement: Accessibility Has No Regression

| Scenario | Result | Evidence |
|---|---|---|
| Accessibility and controls pass (no new axe violation, focus reaches `tabindex="-1"` main, locale switch names + opens paired route) | PASS | `accessibility.spec.ts:62 "landing page (EN) has no WCAG 2.2 AA violations"` + `:73 "(ES)"` + `:84 "skip link is present and keyboard-operable"` + `:99 "waitlist form is keyboard-operable"` + `:133 "consent banner has no WCAG 2.2 AA violations"` + `:144 "accept and reject buttons are keyboard-reachable"` + `:177` (5 legal pages) all green. `seo.spec.ts:289 "accessible name alignment"` (12 tests) asserts locale switch `aria-label` contains the visible `ES`/`EN` code on every route. `seo.spec.ts:320 "main landmark on legal pages"` (10 tests) asserts exactly one `main`/`[role="main"]` on every legal URL (home already had one). Build `dist/` grep confirms `<main id="main-content"` on all 12 pages and `aria-label="Switch to Spanish (ES)">ES` / `aria-label="Cambiar a inglés (EN)">EN` correctly per locale. |

### 5.7 ADDED Requirement: Bilingual Route Parity

| Scenario | Result | Evidence |
|---|---|---|
| Route inventory is paired (every EN route has exactly one ES counterpart; both succeed; both listed) | PASS | `seo.spec.ts:100 "Sitemap parity"` walks all 12 URLs and asserts each is `< 400`. `seo.spec.ts:398..429` covers 6 EN/ES pairings (12 assertions). `__tests__/sitemap.xml.test.ts:57 "every route has EN/ES pairing"` asserts both `profiletailors.com${path}` and `profiletailors.com/es${path}` (or `/es/`) are present. `utils.test.ts:425..463` asserts `counterpartPath` and `canonicalUrl` parity. All green. |
| Locale identity is preserved (links, headings, canonicals, alternates, JSON-LD language navigation) | PASS | `seo.spec.ts:188` covers title, canonical, hreflang for each URL. `seo.spec.ts:331` covers JSON-LD `inLanguage` match. `seo.spec.ts:289` covers locale switch link target. Build `dist/` grep confirms: EN `<html lang="en">`, ES `<html lang="es">`, `hreflang` en↔es reciprocal, JSON-LD `inLanguage` matches locale. |

### 5.8 Main spec cross-cut (delta superset)

Main `openspec/specs/marketing-a11y-seo/spec.md` scenarios for the in-scope capabilities were also exercised by the same E2E and unit suites:

- `Focus Management via tabindex="-1"` → `accessibility.spec.ts:84 "skip link is present and keyboard-operable"` + build output shows `<main id="main-content" class="fade-in" tabindex="-1">` on every page.
- `Reduced-Motion Axe Context` → `accessibility.spec.ts` uses `reducedMotion: 'reduce'` context for axe scans (file scope assertion in source; no axe violations observed).
- `Robots and Sitemap Routes` (SEO) → covered by 5.2 above.
- `SEO Invariants — 12 URLs` → covered by 5.1 above.
- `Link Hygiene — No Broken/Orphan` → covered by 5.3 above.
- `IndexNow Intentionally Absent` → `seo.spec.ts:267 "No IndexNow artifacts"` (asserts `api.indexnow.org` absent in sitemap, robots, and dist); `utils.test.ts:365` and `__tests__/sitemap.xml.test.ts:92` confirm. PASS.
- `Redirect/HTTPS — Platform Decision` (in-scope: repo rejects `http://` hrefs) → `seo.spec.ts:81 "No http href on any of 12 URLs"` PASS. Live redirect chain / HSTS NOT TESTED (out of scope).

## 6. Untested scope, reason, and rerun prerequisite

| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Lighthouse performance budgets (LCP/CLS/INP, mobile/desktop, consent-visible/seeded) | User prompt explicitly excludes Lighthouse performance budgets; spec marks performance as "measure only" | Operator-controlled Lighthouse run against the deployed site, document values vs `docs/marketing/lighthouse/baseline.json` |
| Cloudflare Email Protection rewrite, edge cache state, deployment provenance | User prompt explicitly excludes Cloudflare platform rules; cannot be executed from this worktree | Operator Cloudflare dashboard review and live `curl` against `profiletailors.com` |
| `curl -I` HTTP/HTTPS, `www` redirect chain, HSTS header | User prompt explicitly excludes redirects/HSTS/cache | Operator `curl -I` against `https://profiletailors.com/`, `http://profiletailors.com/`, `https://www.profiletailors.com/` after deployment |
| Backlinks, Ahrefs Site Audit score, Search Console outcomes | User prompt explicitly excludes backlinks and Ahrefs ranking; `exploration.md` records Ahrefs project `9293424` is unavailable on the current plan | Operator-owned Ahrefs re-crawl with project access; not a code-controlled gate |
| Multi-browser Playwright (firefox/webkit/Mobile Chrome/Mobile Safari) | QA prompt focuses on capability acceptance; marketing CI gates multi-browser separately | `just frontend-test-e2e` against the full project matrix |
| Per-route seeded-consent Playwright across all 12 URLs | QA prompt focuses on code-controlled capability; first-visit + `consent.spec.ts` cover banner/dnt/gpc/outdated version | Targeted Playwright matrix on consent-seeded state per URL |

## 7. Findings

| Severity | ID | Finding | Status |
|---|---|---|---|
| P3 | F-1 | `Layout.astro` JSON-LD `name` field for the home page is the literal `"Profile Tailors"` while every legal page is the document `<title>`. This is by design per `apply-progress.md` (home keeps brand, legal pages mirror title) and matches the spec requirement that JSON-LD identity matches page identity; recorded as observation, not a defect. | observed, no action |
| P3 | F-2 | `apply-progress.md` records that `routeSeoEntries()` was added to `src/i18n/utils.ts` but is not yet consumed by `Layout.astro` or by `sitemap.xml.ts` / `robots.txt.ts`; the helper is exercised only by tests and reserved for future consumers. The delta spec scenarios do not require the layout/sitemap to import the helper directly (they require the rendered output to match the inventory, which it does). No defect; design deviation explicitly recorded. | observed, no action |
| P3 | F-3 | `seo.spec.ts:237..238` asserts that for non-home URLs the rendered `h1` text equals the `<title>`. The delta spec explicitly states "the home `h1` MAY differ from its title". The Playwright test excludes `/` and `/es/` from this comparison, so the assertion matches the spec. No defect. | observed, no action |
| P3 | F-4 | `apps/web/marketing/src/legal/legal-publication.ts` exports a hard-coded `APPROVED` status. If a future change flips the gate to `BLOCKED`, the rendered legal pages must switch to `noindex,nofollow`. The Layout already derives `robotsContent` from `noindex`, so the contract is wired; the gate change is an operator decision tracked by the source. | observed, no action |

No `CRITICAL`, `P0`, `P1`, or `P2` findings.

## 8. Final verdict

**Status**: `PASS`

**Rationale**: Every in-scope capability (metadata, structured data, canonical/hreflang, sitemap, robots, accessibility landmarks, accessible names, mailto rendering) is supported by passing executable tests and observable build-output evidence on the static build. The Vitest suite went from 115 → 135 tests (`+20`), the marketing Playwright suite went from 7 → 63 SEO test cases (`+56`) plus 11 accessibility, 16 landing, 8 consent, and 6 waitlist tests, all green on the chromium marketing lane. `just frontend-lint`, `just frontend-check`, `just frontend-test`, `just frontend-build`, and the marketing Playwright suite all PASS. The two acceptance-relevant items declared out of scope by the QA prompt (Lighthouse performance budgets, Cloudflare platform rules) are not blocking and are recorded under §6 with operator-rerun prerequisites.

**Verdict for archive gate**: `PASS WITH WARNINGS` is not warranted — every P3 finding is an explicit design choice already documented in `apply-progress.md` and the delta spec, not a regression. Both `verify-report.md` (verdict `PASS`) and `qa-report.md` (this report, verdict `PASS`) now exist; no `CRITICAL/P0/P1` remains; archive can proceed under standard policy. Operator-owned items recorded in §6 must remain on the operator handoff.

## 9. Verdict rationale and implementation handoff

| Field | Value |
|---|---|
| Capability scope | All in-scope capabilities produced observable, executable evidence in this worktree. |
| Static inspection | Used as confirmation only; every `PASS` is paired with an executable test or built-output grep, never with file reading alone. |
| Source changes | QA did not modify any source code or test; only this report is new. |
| External evidence | None fabricated. No external Ahrefs, Cloudflare, or live `curl` claim made. |
| Operator handoff | Out-of-scope items are recorded under §6 with exact rerun prerequisites for the operator (Cloudflare dashboard, `curl -I`, Ahrefs recrawl, controlled Lighthouse, multi-browser Playwright). |
| Next SDD phase | `sdd-archive`. Both `verify-report.md` (PASS) and `qa-report.md` (PASS) now exist; no `CRITICAL/P0/P1` findings. Operator handoff items in §6 must be carried through deployment. |
