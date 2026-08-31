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


## Change Record: marketing-seo-quality-90-plus (2026-08-30)

# Delta for marketing-a11y-seo

Source, build, and preview checks are code-controlled acceptance. Cloudflare transforms, hostname
redirects/HSTS/cache, and live recrawls are deployment-only evidence and MUST be reported separately.

## MODIFIED Requirements

### Requirement: SEO Invariants — 12 URLs

The six English and six Spanish routes MUST expose unique truthful metadata, exactly one non-empty `h1`,
HTTPS trailing-slash canonicals, and reciprocal `en`/`es`/`x-default` alternates. Legal indexability
MUST follow publication approval; the home `h1` MAY differ from its title. (Previously: every `h1` had to
equal the document title.)

#### Scenario: Metadata is unique and bounded
- GIVEN all 12 routes are rendered
- WHEN titles and descriptions are parsed
- THEN titles MUST be unique, at least 30 characters, and end ` — Profile Tailors`
- AND descriptions MUST be unique and 120–160 characters

#### Scenario: Heading invariant holds
- GIVEN any route
- WHEN headings are counted
- THEN exactly one non-empty `h1` MUST be present

#### Scenario: Canonical and hreflang are reciprocal
- GIVEN any route
- WHEN its head is inspected
- THEN canonical and `en`/`es`/`x-default` links MUST use corresponding HTTPS trailing-slash URLs
- AND EN/ES MUST reference each other

#### Scenario: Indexability follows publication
- GIVEN legal publication is approved or not approved
- WHEN legal pages are rendered
- THEN approved pages MUST be `index,follow` and unapproved pages MUST be `noindex,nofollow`

### Requirement: Robots and Sitemap Routes (SEO)

Root `robots.txt` and `sitemap.xml` MUST be valid. Robots MUST allow `/` for the wildcard and approved
search/AI crawlers, contain no `Disallow: /`, and identify the canonical sitemap. Sitemap MUST contain
exactly the 12 approved canonical URLs. (Previously: robots and sitemap were checked without complete
inventory parity.)

#### Scenario: Robots and sitemap are served
- GIVEN a production build
- WHEN both root resources are requested
- THEN successful valid documents MUST be returned and the sitemap MUST contain exactly 12 canonical URLs

#### Scenario: Approved crawlers are not blocked
- GIVEN robots and route meta directives
- WHEN approved crawler policy is evaluated
- THEN each approved bot MUST have `Allow: /`, no `Disallow: /` MUST apply, and indexable routes MUST expose `index,follow`

### Requirement: Link Hygiene — No Broken/Orphan

Repository-controlled internal links MUST resolve below 400, use no unintended `http://` or
`cdn-cgi/l/email-protection` destinations, and connect the approved route graph. Every sitemap route MUST
have an inbound followable link. (Previously: deployment-generated transformations were not distinguished
from repository links.)

#### Scenario: Crawlable links resolve
- GIVEN internal links from all 12 preview routes
- WHEN normalized destinations are requested
- THEN every response MUST be below 400 and no destination MUST contain a prohibited scheme or path

#### Scenario: Sitemap and graph have parity
- GIVEN the sitemap and rendered link graph
- WHEN paths are compared
- THEN every sitemap route MUST have inbound coverage without `nofollow`, and every discovered approved route MUST be listed

### Requirement: Performance Budget — Measure Only

The site SHOULD track approved Lighthouse budgets for all 12 routes in consent-visible and seeded-consent
states on mobile and desktop. Repository-controlled resource changes MUST be measured; managed resources
and field results remain deployment evidence. (Previously: only a single preview LCP/CLS/INP measurement
was required.)

#### Scenario: Controlled performance evidence is recorded
- GIVEN a documented run for every route, device, and consent state
- WHEN Lighthouse is executed
- THEN LCP/CLS/INP MUST be compared with budgets, and FCP, TTFB, transfer, and render-blocking resources MUST be recorded

#### Scenario: Performance changes preserve contracts
- GIVEN first visit, seeded consent, DNT/GPC, and reduced-motion contexts
- WHEN the page and waitlist flow are exercised
- THEN analytics MUST remain consent-gated, the banner non-modal, the form usable, motion compliant, and keyboard access intact

## ADDED Requirements

### Requirement: Structured Data Matches Page Identity

Every indexable route MUST emit valid JSON-LD with page type, URL, language, name, and description matching
its canonical and visible identity. It MUST NOT add unsupported claims.

#### Scenario: Structured data validates
- GIVEN each rendered route
- WHEN JSON-LD is parsed
- THEN the expected `WebSite` or `WebPage` identity MUST match the route and locale

### Requirement: Accessibility Has No Regression

All 12 routes MUST preserve one main landmark, reduced-motion axe coverage, keyboard skip-link focus, and an accessible locale switch without new violations.

#### Scenario: Accessibility and controls pass
- GIVEN reduced-motion axe scans and keyboard navigation on every locale
- WHEN results and controls are evaluated
- THEN no new axe violation MUST appear, focus MUST reach `tabindex="-1"` main content, and the locale switch MUST name and open its paired route

### Requirement: Bilingual Route Parity

Each of the six route identities MUST have one English and one Spanish page with equivalent purpose,
heading shape, navigation, structured-data identity, publication state, and reciprocal metadata; copy length
MAY differ.

#### Scenario: Route inventory is paired
- GIVEN the approved route inventory
- WHEN build output and sitemap are inspected
- THEN every English route MUST have exactly one Spanish counterpart, both MUST return successfully, and both MUST be listed

#### Scenario: Locale identity is preserved
- GIVEN an English route and its Spanish counterpart
- WHEN links, headings, canonicals, alternates, and JSON-LD are compared
- THEN each MUST retain its route identity and language navigation MUST point to the counterpart
