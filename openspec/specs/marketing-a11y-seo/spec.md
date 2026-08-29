# Marketing Accessibility and SEO Specification

## Requirements

### Requirement: Focus Management via tabindex="-1" (a11y)

Legal, accessibility, cookie-policy, terms, privacy, and home pages MUST set `tabindex="-1"` on
their main-content target so skip-link and programmatic focus land on content without adding an
extra tab stop. Page list: `_AcceptableUsePage.astro`, `_AccessibilityPage.astro`,
`_CookiePolicyPage.astro`, `_HomePage.astro`, `_PrivacyPolicy.astro`, `_TermsPage.astro`.

#### Scenario: Main content is focusable via skip link

- GIVEN a user activates the skip link on a legal page
- WHEN focus moves to the main content
- THEN the target element MUST be the main-content region with `tabindex="-1"`
- AND the element MUST NOT appear as a Tab key stop after activation

#### Scenario: Programmatic focus lands on content

- GIVEN a keyboard user navigates to a page with a hash anchor
- WHEN the page loads
- THEN focus MUST land on the `tabindex="-1"` main-content element

### Requirement: Reduced-Motion Axe Context

The marketing E2E accessibility suite MUST run axe with a reduced-motion browser context so
motion-only violations are not false positives. The `accessibility.spec.ts` MUST use the
reduced-motion context for axe scans.

#### Scenario: Axe scan runs under reduced motion

- GIVEN the E2E accessibility spec sets a reduced-motion context
- WHEN axe scans the page
- THEN animations are disabled
- AND no motion-only violation is reported

### Requirement: Robots and Sitemap Routes (SEO)

Site MUST expose `robots.txt`/`sitemap.xml` at root. `robots.txt` MUST have `User-agent: *` `Allow: /` plus explicit `Allow: /` for `OAI-SearchBot`,`GPTBot`,`PerplexityBot`,`ClaudeBot`,`Google-Extended`,`GoogleOther`,`Bingbot`, and `Sitemap: https://profiletailors.com/sitemap.xml`. Sitemap MUST list 12 URLs. Allow-all deliberate; blocking AI prohibited. (Previously: minimal allow-all without per-bot)

#### Scenario: robots.txt is served

- GIVEN request for `/robots.txt`
- WHEN site responds
- THEN valid `robots.txt` is returned

#### Scenario: sitemap.xml is served

- GIVEN request for `/sitemap.xml`
- WHEN site responds
- THEN valid XML sitemap is returned

#### Scenario: robots.txt per-bot Allow and sitemap line

- GIVEN `GET /robots.txt`
- WHEN reading body
- THEN MUST contain `Allow: /` for `*` and each bot and `Sitemap:` line

#### Scenario: Does not block AI

- GIVEN `robots.txt` and `<meta name="robots">` on 12 URLs
- WHEN checking
- THEN no `Disallow: /` for AI and meta MUST be `index,follow` on all 12

### Requirement: SEO Invariants — 12 URLs

For 12 URLs (6 routes x2 locales), title MUST be >=30 ending ` — Profile Tailors`, meta 120-160, exactly one `h1` equals title, canonical https + trailing-slash, hreflang `en`/`es`/`x-default`, all unique. (Traces: ACCEPT #10-12)

#### Scenario: Titles branded and unique

- GIVEN any of 12 URLs
- WHEN reading `<title>`
- THEN MUST be >=30, suffix ` — Profile Tailors`, unique

#### Scenario: Meta band and unique

- GIVEN any of 12 URLs
- WHEN reading `<meta name="description">`
- THEN MUST be 120-160 and unique

#### Scenario: Single H1 matches title

- GIVEN any of 12 URLs
- WHEN counting `h1`
- THEN MUST be 1 and text equals route title

#### Scenario: Canonical and hreflang present

- GIVEN any of 12 URLs
- WHEN reading `<head>`
- THEN canonical MUST be https + trailing-slash and hreflang MUST include `en`,`es`,`x-default`

### Requirement: Link Hygiene — No Broken/Orphan

Site MUST have 0 broken links, 0 `cdn-cgi` hrefs, 0 `http://` hrefs, sitemap 12 URLs, each URL >=1 inbound dofollow. (Traces: FIX #1-3, #15)

#### Scenario: No broken or obfuscated href

- GIVEN crawl of 12 URLs collecting `a[href]` excl. `mailto:`/`#`/external
- WHEN fetching each internal href on preview
- THEN status MUST be <400 and href MUST NOT contain `cdn-cgi`

#### Scenario: No http href

- GIVEN rendered HTML of 12 URLs
- WHEN scanning `href`/`src`
- THEN none MUST start with `http://`

#### Scenario: Sitemap parity and no orphan

- GIVEN `sitemap.xml` and crawled link graph
- WHEN comparing
- THEN sitemap MUST have 12 URLs and each MUST have >=1 inbound dofollow

### Requirement: IndexNow Intentionally Absent

Site MUST NOT implement IndexNow; sitemap + Search Console is discovery. (Traces: ACCEPT #9)

#### Scenario: No IndexNow artifacts

- GIVEN built site and source
- WHEN searching for IndexNow
- THEN no `<key>.txt` and no POST to `api.indexnow.org` MUST exist

#### Scenario: Sitemap is discovery source

- GIVEN `sitemap.xml`
- WHEN submitted to Search Console
- THEN 12-URL sitemap MUST be submitted source

### Requirement: Performance Budget — Measure Only

Site SHOULD track Lighthouse budget for 12 URLs; no perf fix without baseline. (Traces: FIX #4)

#### Scenario: Lighthouse budget recorded

- GIVEN preview build of 12 URLs
- WHEN running Lighthouse
- THEN LCP/CLS/INP per URL MUST be recorded vs budget

#### Scenario: Reject unmeasured perf fix

- GIVEN perf code change
- WHEN no Lighthouse artifact exists
- THEN change MUST be rejected

### Requirement: Redirect/HTTPS — Platform Decision

3XX/302/HTTP/chain is PLATFORM (bulk 301 http->https, www->apex, 302->301, HSTS) in runbook; repo MUST enforce no `http://` hrefs. (Traces: PLATFORM #5-6, #13-14)

#### Scenario: Runbook documents redirect

- GIVEN `docs/marketing/seo.md`
- WHEN reading redirect section
- THEN MUST state bulk 301 + HSTS owned by operator

#### Scenario: Repo rejects http href

- GIVEN CI on 12 URLs
- WHEN scanning links
- THEN any `http://` MUST fail check

## TDD Requirement

Every scenario MUST have a failing-first test. In-tree: `tests/e2e/accessibility.spec.ts` (reduced-
motion axe context fix). Tabindex and robots/sitemap routes are covered by `just frontend-test-e2e`
/ `just frontend-check`; a regression assertion MAY lock the reduced-motion context flag.
