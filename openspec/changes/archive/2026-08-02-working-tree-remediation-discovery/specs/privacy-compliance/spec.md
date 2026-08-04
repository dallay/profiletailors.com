# Delta for Privacy Compliance

## MODIFIED Requirements

### Requirement: Consent Banner UI Structure

The consent banner MUST present two categories with equal-prominence action buttons and i18n support.

**Categories displayed**:
1. **Necessary** (always on, no toggle): Label + description, checkbox disabled/checked
2. **Analytics** (opt-in toggle): Label + description, toggle switch

Marketing category MUST NOT be shown (not used in MVP).

**Actions**:
- "Accept all" button (primary styling)
- "Reject all" button (primary styling, equal visual weight)
- "Save preferences" button (primary styling)

**Equal prominence rule**: All three buttons MUST have identical size, color saturation, and position hierarchy. No dark patterns (e.g., green accept + gray reject).

**Always-dark theme (new)**: The banner MUST be theme-independent and always render in a dark palette regardless of the page theme. Fixed WCAG-AA colors MUST be used:

| Role | Hex |
|------|-----|
| Link | `#0ea5e9` |
| Description text | `#a3a3a3` |
| Container background | `#1a1a1a` |
| Heading text | `#ffffff` |
| Border | `#333` |

Every color pair MUST meet WCAG-AA contrast (4.5:1 for normal text, 3:1 for large text/UI components).

**Content**:
- Heading: i18n key `consent.banner.heading`
- Description: i18n key `consent.banner.description` with embedded privacy policy link
- Necessary category: `consent.category.necessary.label`, `consent.category.necessary.description`
- Analytics category: `consent.category.analytics.label`, `consent.category.analytics.description`

(Previously: the banner followed the page theme without a fixed always-dark palette; contrast varied by theme.)

#### Scenario: Banner renders always-dark on a light page

- GIVEN the page theme is light
- WHEN the consent banner displays
- THEN the container background MUST be `#1a1a1a`
- AND heading text MUST be `#ffffff`
- AND the banner MUST NOT flip to a light palette

#### Scenario: Banner link meets WCAG-AA contrast

- GIVEN the banner is rendered with link color `#0ea5e9` on container `#1a1a1a`
- WHEN contrast is computed
- THEN the pair MUST meet WCAG-AA (≥4.5:1)

#### Scenario: Description text meets WCAG-AA contrast

- GIVEN the banner is rendered with description color `#a3a3a3` on container `#1a1a1a`
- WHEN contrast is computed
- THEN the pair MUST meet WCAG-AA (≥4.5:1)

#### Scenario: Accept all sets analytics true

- GIVEN the banner is displayed
- WHEN the user clicks "Accept all"
- THEN `categories.analytics` is set to `true`
- AND the receipt is saved
- AND the banner closes

#### Scenario: Reject all sets analytics false

- GIVEN the banner is displayed
- WHEN the user clicks "Reject all"
- THEN `categories.analytics` is set to `false`
- AND the receipt is saved
- AND the banner closes

## TDD Requirement

Every scenario MUST have a failing-first test. In-tree: `ConsentBanner.test.ts` (banner changes) and relocated `tests/e2e/consent.spec.ts`. New regression MAY assert the exact hex palette to lock WCAG-AA contrast.
