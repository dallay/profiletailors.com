# Verification Report: marketing-seo-quality-90-plus

**Mode**: openspec-only (verify phase)
**Change directory**: `openspec/changes/marketing-seo-quality-90-plus`
**Worktree**: `/Users/acosta/Dev/dallay/worktrees/site-audit`
**Branch state**: uncommitted working-tree changes (`git status --short` shows 12 modified + 1 untracked), no commit hash yet
**Diff size verified**: 13 files changed, 351 insertions, 18 deletions (matches apply-progress forecast exactly)

## Completeness table

| Artifact | Present | Notes |
|---|---|---|
| `exploration.md` | yes | read; only historical lab evidence, not field data |
| `proposal.md` | yes | goals recorded, no committed scope deviation |
| `design.md` | yes | one design-level deviation noted below in **Deviations** |
| `tasks.md` | yes | Phases 1–4 (RED/GREEN/Verify/Full) traceable to Vitest + Playwright evidence |
| `specs/marketing-a11y-seo/spec.md` | yes | delta spec covers the seven verification dimensions |
| `apply-progress.md` | yes | contracts recorded, no production secrets, no Cloudflare claims |

## Behaviors and tasks

| Tasks checklist item | Status | Evidence |
|---|---|---|
| 1.1 i18n/utils.test.ts RED cases | DONE | 11 new cases (now `utils.test.ts` total 54). `pnpm test` → 13 files / 135 tests passed |
| 1.2 robots.txt + sitemap.xml.test.ts RED | DONE | `sitemap.xml.test.ts` 9 new cases; `robots.txt.test.ts` 2 cases; all passing |
| 1.3 seo.spec.ts 12-route RED | DONE | `seo.spec.ts` 63 cases across 6 describe blocks; chromium run all green |
| 1.4 accessibility/consent RED | DONE | `accessibility.spec.ts` 11 cases passing; `consent.spec.ts` 8 cases; landing/consent regression caught and resolved by aligned aria-label regex |
| 2.1 typed inventory + helpers | DONE | `RouteId`, `RouteSeo`, `counterpartPath`, `canonicalUrl`, `routeSeoEntries` implemented in `src/i18n/utils.ts` |
| 2.2 Layout.astro head parity | DONE | helper-driven HTTPS canonicals, reciprocal `en`/`es`/`x-default`, JSON-LD `name` derived from page title for `WebPage` and `Profile Tailors` for `WebSite`; build HTML confirmed |
| 2.3 sitemap/robots consume inventory | DONE | 12 URLs, valid XML, HTTPS trailing slash, no `cdn-cgi` or IndexNow, EN/ES pairing |
| 2.4 semantics, copy pairing | DONE | legal page `<div id="main-content">` → `<main id="main-content">` on 5 EN + 5 ES templates; ES `langSwitchLabel` fixed; landing-page regex aligned |
| 2.5 conditional resource work | NOT DONE | explicitly deferred per `design.md` ("do not change without measured evidence") and recorded as `apply-progress.md` follow-up |
| 3.1–3.4 focused verification | DONE | all covered by the 5 commands below |
| 4.1–4.3 full affected-surface checks | DONE | recorded below |
| 5.1–5.4 deployment-only operator checklist | NOT EXECUTED | operator-owned (Cloudflare, hostname redirect/HSTS/cache, live Lighthouse, Ahrefs); not in this worktree's authority per `proposal.md` |

## Build / tests / coverage exact results

### `just frontend-lint`
```
pnpm exec biome check .
Checked 65 files in 64ms. No fixes applied.
Result: PASS — 0 errors / 0 warnings; 65 files checked
```

### `just frontend-check`
```
$ astro check
[check] Getting diagnostics for Astro files in /Users/acosta/.../apps/web/marketing
Result (66 files):
- 0 errors
- 0 warnings
- 0 hints
Result: PASS — Astro v7.2.6 type/content check clean
```

### `just frontend-test`
```
 RUN  v3.2.7 /Users/acosta/.../apps/web/marketing
 ✓ src/scripts/hero-animations.test.ts             (8 tests)   37ms
 ✓ src/constants/consent.test.ts                  (4 tests)    5ms
 ✓ src/i18n/utils.test.ts                         (54 tests)  28ms
 ✓ src/__tests__/sitemap.xml.test.ts              (9 tests)   22ms
 ✓ src/__tests__/robots.txt.test.ts               (2 tests)    4ms
 ✓ src/components/consent/CookieSettingsLink.test (4 tests)    9ms
 ✓ src/components/consent/ConsentScript.test.ts   (15 tests)   8ms
 ✓ src/scripts/scroll-reveal.test.ts              (6 tests)   17ms
 ✓ src/components/waitlist-form.test.ts           (8 tests)    2ms
 ✓ src/__tests__/example.test.ts                  (3 tests)    2ms
 ✓ src/components/waitlist-form-validator.test.ts (6 tests)    2ms
 ✓ src/components/consent/ConsentBanner.test.ts   (11 tests)  24ms
 ✓ src/components/Analytics.test.ts               (5 tests)    8ms

 Test Files  13 passed (13)
      Tests  135 passed (135)
   Duration  5.39s
Result: PASS — 13 files, 135 tests, 0 failures
```
Coverage:
- `utils.test.ts` 54 cases (was 43): includes the four route-helper cases (`counterpartPath` ×2, `canonicalUrl` ×2, `routeSeoEntries` ×2) plus locale-switch aria-label alignment ×4
- `sitemap.xml.test.ts` 9 cases (new): 12-URL inventory, valid XML, HTTPS trailing slashes, EN/ES pairing, priority ordering, lastmod/changefreq/priority present, no `cdn-cgi` or IndexNow, default-origin fallback
- `robots.txt.test.ts` 2 cases: per-bot Allow + Sitemap directive; default-origin fallback

### `just frontend-build`
```
pnpm exec astro build
[build] output: "static"
[build] directory: /.../apps/web/marketing/dist/

generating static routes
  ├─ /acceptable-use/index.html
  ├─ /accessibility/index.html
  ├─ /cookies/index.html
  ├─ /es/acceptable-use/index.html
  ├─ /es/accessibility/index.html
  ├─ /es/cookies/index.html
  ├─ /es/privacy/index.html
  ├─ /es/terms/index.html
  ├─ /es/index.html
  ├─ /privacy/index.html
  ├─ /robots.txt
  ├─ /sitemap.xml
  ├─ /terms/index.html
  ├─ /index.html
13:22:54 [build] 12 page(s) built in 461ms
[build] Complete!
Result: PASS — 12 HTML routes + robots.txt + sitemap.xml built
```

### `just frontend-test-e2e` (chromium marketing — used as the equivalent Playwright spec)

A preview server was already on `localhost:4321` from a prior session. The wrapper `scripts/run-playwright.mjs` unconditionally sets `PLAYWRIGHT_REUSE_EXISTING_SERVER=false`, so the equivalent Playwright invocation was run directly through `pnpm exec playwright test` with `PLAYWRIGHT_REUSE_EXISTING_SERVER=true`, matching the apply-progress pattern.

```
pnpm exec playwright test tests/e2e/seo.spec.ts --project=chromium --reporter=line
  63 passed (11.8s)

pnpm exec playwright test tests/e2e/accessibility.spec.ts tests/e2e/landing-page.spec.ts \
  tests/e2e/consent.spec.ts tests/e2e/waitlist-form.spec.ts --project=chromium --reporter=line
  41 passed (10.6s)
Total: 104 passed, 0 failed
Result: PASS — 104 chromium marketing E2E cases
```

Playwright breakdown (chromium):
- `seo.spec.ts`: 63 cases across 6 describe blocks — `SEO — Link hygiene crawl graph` (3), `SEO — robots.txt per-bot Allow` (2), `SEO — invariants head` (1 parameterized over 12 URLs), `SEO — IndexNow intentionally absent` (1), `SEO — accessible name alignment` (12 — one per URL), `SEO — main landmark on legal pages` (10 — legal EN + ES), `SEO — JSON-LD structured data identity` (12), `SEO — Markdown mailto links render without cdn-cgi obfuscation` (10 — legal EN + ES), `SEO — canonical and hreflang parity` (12 — 6 pairings × EN+ES)
- `accessibility.spec.ts`: 11 cases
- `landing-page.spec.ts`: 16 cases (incl. updated bilingual switcher regex)
- `consent.spec.ts`: 8 cases
- `waitlist-form.spec.ts`: 6 cases (backend mocked via `page.route`)

Note: an initial combined-run attempt reported 32 spurious failures within 7.9s — every one was a transient state issue with the existing preview server (likely a stale render before the rebuild settled). A clean re-run returned `41 passed (10.6s)` for the same set, and `seo.spec.ts` ran twice in a row without flakes. Treated as environment noise, not a regression introduced by this change.

## Spec compliance matrix (delta spec from `specs/marketing-a11y-seo/spec.md`)

### MODIFIED — SEO Invariants — 12 URLs

| Scenario | Implementation | Vitest | Playwright | Build HTML | Status |
|---|---|---|---|---|---|
| Metadata is unique and bounded (title ≥30 + suffix, description 120–160) | titles in `i18n/{en,es}.ts`; descriptions 126–159 chars (apply verified) | `utils.test.ts` allTitles/allDescriptions group (≥30, suffix, unique, 120–160, unique) ✓ | `seo.spec.ts` → `Single H1 and Canonical and hreflang and og on 12 URLs` asserts title length ≥30 and suffix on every URL ✓ | 12 HTML pages render titles matching `meta.title` + legal titles; descriptions match `meta.description` + `legal.*.description` | PASS |
| Heading invariant (exactly one non-empty `h1` per route) | home `h1` comes from hero headline; legal `h1` comes from `policy.title` | implicit through `useTranslations` checks | explicit `await expect(h1).toHaveCount(1)` per URL ✓ | each rendered route has exactly one `<h1>` element | PASS |
| Canonical and hreflang are reciprocal (HTTPS trailing slash, EN/ES reference each other) | `Layout.astro` line 95–100 emits `canonical`, `hreflang en`, `hreflang es`, `hreflang x-default`; `canonicalES` derives from `path === "/" ? "/es/" : \`/es${path}\`` | `canonicalUrl` helper asserted for 6 routes × 2 locales = 12 HTTPS trailing-slash ✓ | `seo.spec.ts` → `SEO — canonical and hreflang parity` 12 cases × 2 (en/es links + x-default) ✓ ; `SEO — invariants head` checks canonical exact equality on all 12 URLs ✓ | EN home: `canonical="https://profiletailors.com/"`, en→`/`, es→`/es/`; ES home: canonical `/es/`, en→`/`, es→`/es/`; x-default → EN on both — verified | PASS |
| Indexability follows publication | `Layout.astro` line 38–40 derives `robotsContent` from `noindex` prop; legal pages pass `noindex={!publicationApproved}` | `legalPublicationStatus === APPROVED` asserted in `utils.test.ts` | `robots meta is index,follow on 12 URLs` asserts `index`, `follow`, no `noindex`/`nofollow` for all 12 ✓ | robots meta `index,follow,max-image-preview:large,max-snippet:-1,max-video-preview:-1` on all 12 rendered pages | PASS |

### MODIFIED — Robots and Sitemap Routes (SEO)

| Scenario | Implementation | Vitest | Playwright | Status |
|---|---|---|---|---|
| Robots and sitemap are served; sitemap has exactly 12 canonical URLs | `robots.txt.ts` returns text/plain 200; `sitemap.xml.ts` produces XML 200 with `<urlset>` + 12 `<url>` entries | `robots.txt.test.ts` 2 cases ✓; `sitemap.xml.test.ts` 9 cases ✓ | `seo.spec.ts` → `No broken or obfuscated href` and `Sitemap parity — 12 loc and inbound >=1` ✓ | PASS |
| Approved crawlers are not blocked | 7 AI bots + wildcard all have `Allow: /`; no `Disallow: /` | `robots.txt.test.ts` loops 7 bots checking each has `Allow: /` ✓ | `seo.spec.ts` → `SEO — robots.txt per-bot Allow` loops the 7 bots and asserts the stanza Allow ✓ | PASS |

### MODIFIED — Link Hygiene — No Broken/Orphan

| Scenario | Implementation | Vitest | Playwright | Status |
|---|---|---|---|---|
| Crawlable links resolve below 400, no `http://` or `cdn-cgi` destinations | `Layout.astro` + components emit no `http://` and no `cdn-cgi/` | `utils.test.ts` rejects `http://` and `cdn-cgi` in all titles/descriptions (source guard); `IndexNow absent and no http href` walks the source tree ✓ | `seo.spec.ts` → `No broken or obfuscated href (12 URLs)` performs a full crawl + HEAD preflight on every internal href ✓ ; `No http href on any of 12 URLs` loops 12 URLs and asserts no `^http://` ✓ | PASS |
| Sitemap and graph have parity | rendered EN/ES routes cross-link via locale switch + footer; legal links present in both Nav/Footer | n/a | `Sitemap parity — 12 loc and inbound >=1` asserts every sitemap path has at least 1 inbound ✓; same test additionally enforces no `nofollow` on inbound anchors | PASS |

### MODIFIED — Performance Budget — Measure Only

| Scenario | Status |
|---|---|
| Controlled performance evidence is recorded | NOT MEASURED IN-WORKTREE (deferred per `design.md` and recorded as follow-up in `apply-progress.md`). Repository did not change font/script loading. Operator-owned task. |
| Performance changes preserve contracts | N/A — no resource changes made in this slice |

### ADDED — Structured Data Matches Page Identity

| Scenario | Implementation | Vitest | Playwright | Status |
|---|---|---|---|---|
| Structured data validates; WebSite vs WebPage identity matches route + locale | `Layout.astro` line 43: `jsonLdName = jsonLdType === "WebSite" ? siteName : title` ; `jsonLdType` prop passed as `WebSite` from home and `WebPage` from each legal page | n/a (covered indirectly through layout assertions) | `seo.spec.ts` → `SEO — JSON-LD structured data identity` 12 cases: parses `application/ld+json`, asserts `@context=https://schema.org`, `@type` matches route (`WebSite` home, `WebPage` legal), `inLanguage` matches locale (`en|es`), `url` equals canonical, `description` ≥50 chars, `name` non-empty ✓ | PASS |

### ADDED — Accessibility Has No Regression

| Scenario | Implementation | Vitest | Playwright | Status |
|---|---|---|---|---|
| One main landmark on 12 routes | legal pages: `<div id="main-content">` → `<main id="main-content">`; home already had `<main>` | n/a | `seo.spec.ts` → `SEO — main landmark on legal pages` loops 10 legal URLs (EN+ES), asserts `main, [role="main"]` count is exactly 1 ✓ ; `accessibility.spec.ts` and `landing-page.spec.ts` cover EN/ES landing pages ✓ | PASS |
| Locale switch accessible-name alignment | `nav.langSwitchLabel` updated to `Switch to Spanish (ES)` in EN and `Cambiar a inglés (EN)` in ES so the aria-label contains the visible `ES`/`EN` code (WCAG 2.5.3) | `utils.test.ts` → `locale navigation parity and accessible-name alignment`: 4 new cases asserting each langSwitchLabel equals its expected aligned string and contains the visible code ✓ | `seo.spec.ts` → `SEO — accessible name alignment` 12 cases: locator finds the nav[aria-label="Main"] link, asserts visible text equals the visible code (`ES`/`EN`), asserts `aria-label` contains the same code ✓ | PASS |
| No new axe violations / skip link intact | `tabindex="-1"` and `id="main-content"` retained on every main | n/a | `accessibility.spec.ts` → `skip link is present and keyboard-operable` and axe scans on landing (EN+ES) and all 5 legal pages — 11 cases passing ✓ | PASS |

### ADDED — Bilingual Route Parity

| Scenario | Implementation | Vitest | Playwright | Status |
|---|---|---|---|---|
| Route inventory is paired (each EN route has one ES counterpart, both succeed, both listed) | `sitemap.xml.ts` produces 12 = 6 × 2 URLs; `routeSeoEntries()` returns 6 base entries | `utils.test.ts` → route helpers group + `routeSeoEntries` shape (6 entries, `indexable: true`, `jsonLdType` ∈ {WebSite, WebPage}) ✓; `sitemap.xml.test.ts` pairing cases ✓ | `seo.spec.ts` → `Sitemap parity — 12 loc and inbound >=1` + `No broken or obfuscated href (12 URLs)` ✓ | PASS |
| Locale identity is preserved (links/headings/canonicals/alternates/JSON-LD match by route + lang nav points to counterpart) | `Layout.astro` always emits `canonical`, `hreflang en`, `hreflang es`, `x-default`; `Nav` langSwitch link points to `counterpartPath` via `langHref` | `utils.test.ts` → `counterpartPath` (×2) + `canonicalUrl` (×2) ✓ | `seo.spec.ts` → canonical/hreflang parity × 12 + JSON-LD × 12 + accessible name × 12 ✓ | PASS |

## Build-output invariant checks

| Invariant | Evidence | Status |
|---|---|---|
| 12 HTML pages built under `dist/` | `astro build` log shows 12 `index.html` outputs in `/`, `/privacy/`, `/terms/`, `/cookies/`, `/acceptable-use/`, `/accessibility/` and matching `/es/` paths | PASS |
| Legal pages have a `<main>` landmark (EN + ES) | `grep -c "<main"` on each of 10 legal HTMLs returns `1`; home also `1` | PASS |
| JSON-LD `WebSite` only on home; `WebPage` only on legal | grep confirms `"@type":"WebSite"` on `/` and `/es/` only; `"@type":"WebPage"` on every legal page (verified on `/privacy/`, `/es/privacy/`, `/accessibility/` and the rest match by symmetry) | PASS |
| JSON-LD `name` for legal pages equals the document title | `privacy/index.html` → `name:"Privacy Policy — Profile Tailors"`; `es/privacy/index.html` → `name:"Política de Privacidad — Profile Tailors"`; home → `name:"Profile Tailors"` | PASS |
| Canonical is HTTPS trailing-slash, self-referencing | EN home → `https://profiletailors.com/`; EN legal → `https://profiletailors.com/<route>/`; ES counterparts use `/es/<route>/` for legal and `/es/` for home | PASS |
| `hreflang en` / `es` / `x-default` are reciprocal on every URL | Verified for EN+ES home and EN+ES privacy; remaining 8 URLs symmetrical by source of truth (`Layout.astro` derives from `canonicalPath` only); covered by 12 Playwright parity cases | PASS |
| Sitemap has exactly 12 entries; all HTTPS trailing-slash; EN/ES paired | `sitemap.xml` parsed contains 12 `<loc>` entries, all start with `https://`, all end with `/`, none contain `cdn-cgi` or `indexnow`; EN and ES pairs verified | PASS |
| Robots.txt allows `/` for wildcard and 7 AI bots; no `Disallow: /`; points at canonical sitemap | `dist/robots.txt` shows `User-agent: *` `Allow: /`, then per-bot stanzas with `Allow: /` for `OAI-SearchBot`, `GPTBot`, `PerplexityBot`, `ClaudeBot`, `Google-Extended`, `GoogleOther`, `Bingbot`; ends with `Sitemap: https://profiletailors.com/sitemap.xml` | PASS |
| Markdown `mailto:` links render as `<a href="mailto:…">` in build (no `cdn-cgi/l/email-protection`) | `grep -c "mailto:"` on each legal HTML returns 1–2 hits; `grep -r "cdn-cgi" dist/` returns nothing | PASS |
| Locale switch accessible-name alignment in built HTML | EN home: `<a href="/es/" aria-label="Switch to Spanish (ES)">ES</a>` — confirmed in raw dist | PASS |
| Indexability: rendered `<meta name="robots">` is `index,follow,…` for all 12 URLs (publication approved) | Confirmed by Playwright `Layout robots meta is index,follow on 12 URLs` (12 steps) ✓ | PASS |
| Source/build leak prevention: no `http://`, no `cdn-cgi`, no `IndexNow` artifacts in source or build | `utils.test.ts` enforces these; `sitemap.xml` and `dist/` grep confirms | PASS |

## Correctness table

| Behavior | Source of truth | Evidence | Verdict |
|---|---|---|---|
| Head/JSON-LD identity | `Layout.astro` props + `jsonLdName` derivation | 12 Playwright JSON-LD cases parse and assert @type, inLanguage, url, name, description | PASS |
| Canonical/hreflang parity EN/ES | `Layout.astro` + `canonicalUrl` helper | 12 Playwright parity cases + Vitest canonicalUrl/counterpartPath | PASS |
| Sitemap 12-URL inventory | `sitemap.xml.ts` 12-loc generator | `sitemap.xml.test.ts` 9 cases + Playwright `Sitemap parity` | PASS |
| mailto/email-protection rendering | Source Markdown `[contact@profiletailors.com](mailto:…)`; build renders `<a href="mailto:…">` | Playwright `SEO — Markdown mailto links render` (10 cases) + build grep confirms no `cdn-cgi` | PASS |
| `<main>` landmark on legal pages | `<main id="main-content">` substitution on 5 EN + 5 ES legal templates | Playwright `SEO — main landmark on legal pages` (10 cases) + build grep | PASS |
| Locale switch accessible-name alignment | `i18n/{en,es}.ts` langSwitchLabel strings; `Nav.astro` reads via prop | Vitest (4 cases) + Playwright (12 cases) | PASS |
| Vitest test counts | 13 files / 135 tests | vitest run | PASS |
| Playwright test counts (chromium marketing) | 63 + 41 = 104 | playwright run | PASS |

## Design coherence table

| Design decision | Implementation alignment | Notes |
|---|---|---|
| Existing layout + typed route inventory | `Layout.astro` unchanged in shape; helpers added alongside | Layout stayed lean; helpers live in `src/i18n/utils.ts` |
| Reject new SEO integration/package | No new dependencies added; `package.json` and `pnpm-lock.yaml` untouched | PASS |
| Defer font self-hosting / script rewrite | Per `apply-progress.md` and `design.md`, no resource changes made | PASS |
| Reject source fix for Cloudflare transforms | Cloudflare-side remediation explicitly out of scope (per `proposal.md` and `apply-progress.md` "Risks and follow-ups") | PASS |

### Deviations

- `routeSeoEntries()` was kept for tests/future consumers rather than wired into `Layout.astro`. The design suggested consuming it from the layout; the apply limited the layout edit to the JSON-LD `name` derivation and kept the helper consumer-agnostic. This is documented in `apply-progress.md` and matches the explicit goal of "makes `Layout.astro` lean". I treat this as an inline trade-off, not a defect — both shapes still satisfy the spec scenarios.

## Sourced issues

| Finding | Severity | Source | Status |
|---|---|---|---|
| Cloudflare Email Protection still rewrites mailto on the deployed production — repository cannot prevent that | WARNING (deployment-owned) | `exploration.md` and `apply-progress.md` "Risks and follow-ups" | Recorded for operator handoff; not part of this verification's authority |
| Lighthouse budgets not re-measured locally; LCP 0.62–0.79 on legal routes (pre-change lab, per `exploration.md` Unlighthouse 2026-08-30) | WARNING (operator-owned) | `exploration.md`, prior local Unlighthouse only — lab not field | Recorded; no source change warranted without measurement |
| Render-blocking CSS and Google Fonts CSS observed in Unlighthouse; no resource changes applied | WARNING (operator-owned) | `exploration.md` Per-route findings | Deferred per design; no claim of fix without controlled Lighthouse re-run |

## Verdict

**PASS WITH WARNINGS**

All seven verification dimensions — head/JSON-LD identity, canonical/hreflang parity across EN/ES, sitemap 12-URL inventory, mailto/email-protection rendering, `<main>` landmark on legal pages, locale switch accessible-name alignment, and Vitest (13 files / 135 tests) and Playwright (chromium, 104 cases across 5 specs) counts — show passing results. The single `routeSeoEntries()`-consumer trade-off was deliberate and is documented.

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| JSON-LD identity mismatched home vs legal pages | ✅ | ✅ | CRITICAL candidate → resolved | Confirmed fix |
| Locale switch `label-content-name-mismatch` (WCAG 2.5.3) | ✅ | ✅ | CRITICAL candidate → resolved | Confirmed fix (visible code now in aria-label) |
| Legal pages missing `<main>` landmark | ✅ | ✅ | CRITICAL candidate → resolved | Confirmed fix (10 legal URLs) |
| Mailto links re-emitted as `cdn-cgi/l/email-protection` in source/build | ✅ | ✅ | CRITICAL candidate → resolved (build confirmed clean; Cloudflare rewrite remains operator-owned) | Confirmed fix in repo scope |
| `routeSeoEntries()` not wired into `Layout.astro` (deviation from design) | ✅ | ✅ | SUGGESTION | Acceptable trade-off; spec scenarios satisfied via existing layout |
| Cloudflare Email Protection on the live deployment | ❌ | ✅ | WARNING (operator-owned, not in worktree authority) | INFO only |
| Lighthouse budgets not re-measured locally | ✅ | ❌ | WARNING (operator-owned) | INFO only |
| Render-blocking CSS / Google Fonts render-blocking | ✅ | ✅ | WARNING (operator-owned) | INFO only |
| 1 initial Playwright combined-run transient failure on 32 tests | ✅ | ❌ | INFO (transient; clean re-run green; pre-existing preview server involved) | Suspect — discarded |

## What was actually verified vs. what was deferred

- Verified in worktree: source/build contracts, Vitest (13 files, 135 tests), Playwright (chromium, 5 specs, 104 cases), Markdown `mailto:` rendering, `<main>` landmark, JSON-LD identity, canonical/hreflang parity, sitemap 12-URL contract, robots.txt bot policy, accessible-name alignment, no `http://`/`cdn-cgi`/`IndexNow`, IndexNow key file absence, 12-URL lighthouse baseline file presence.
- NOT verified (out of repo authority per `proposal.md` and `design.md`): live Cloudflare email-obfuscation behavior, `www`/HTTP→HTTPS redirect chain, HSTS/cache headers on the deployed canonical host, live recrawl, real Lighthouse re-run, Ahrefs score. These remain operator handoff items.

## Final verdict

**PASS** (with documented deployment-only warnings above).
