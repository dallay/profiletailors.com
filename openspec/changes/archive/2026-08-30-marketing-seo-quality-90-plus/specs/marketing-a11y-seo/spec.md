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
