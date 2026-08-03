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

The marketing site MUST expose `robots.txt` and `sitemap.xml` routes via `robots.txt.ts` and
`sitemap.xml.ts` at the site root.

#### Scenario: robots.txt is served

- GIVEN a request for `/robots.txt`
- WHEN the marketing site responds
- THEN a `robots.txt` with valid directives is returned

#### Scenario: sitemap.xml is served

- GIVEN a request for `/sitemap.xml`
- WHEN the marketing site responds
- THEN a valid XML sitemap listing the site pages is returned

## TDD Requirement

Every scenario MUST have a failing-first test. In-tree: `tests/e2e/accessibility.spec.ts` (reduced-
motion axe context fix). Tabindex and robots/sitemap routes are covered by `just frontend-test-e2e`
/ `just frontend-check`; a regression assertion MAY lock the reduced-motion context flag.
