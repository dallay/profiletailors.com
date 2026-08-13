# Delta for Consent Banner Presentation

New capability `consent-banner-presentation`; `privacy-compliance` receipt/source/version contract unchanged.

## ADDED Requirements

### Requirement: Non-modal Fixed Banner Presentation

First-level prompt MUST render as a fixed, non-modal surface near the viewport bottom. It MUST NOT mount `DialogOverlay`, MUST NOT add a full-screen dark backdrop, MUST NOT trap focus, and MUST NOT block app interaction. MUST use a scoped z-index without a portal. Desktop: max-width 560–680px, centered or bottom-right, above footer. Mobile: bottom sheet, no horizontal overflow. MUST respect `env(safe-area-inset-*)`; light/dark themes.

#### Scenario: First visit shows non-modal prompt

- GIVEN no valid consent receipt
- WHEN app loads
- THEN `consent-banner` prompt visible
- AND no dark overlay present
- AND app content remains visible and interactable

#### Scenario: Prompt does not block interaction

- GIVEN prompt visible
- WHEN user clicks app navigation outside it
- THEN control responds
- AND focus is not trapped

### Requirement: First-Level Actions

Prompt MUST make Accept all, Reject optional, and Customize immediately available with equal practical accessibility and no dark patterns.

#### Scenario: All actions available up front

- GIVEN prompt visible
- WHEN user inspects it
- THEN Accept all, Reject optional, and Customize are each actionable without further interaction

### Requirement: Customize Surface

Customize MUST expose the analytics preference and a Save action, inline in the banner or as a compact sheet. Necessary MUST remain always enabled and MUST NOT be user-disabled in any surface. MUST NOT mount a full-screen backdrop and MUST never leave an orphaned overlay — if overlay primitives are used, overlay and content MUST share one lifecycle.

#### Scenario: Customize saves granular preference

- GIVEN prompt visible
- WHEN user opens Customize, toggles analytics, clicks Save
- THEN receipt saved with `categories.analytics` = toggle, `source: "banner"`
- AND Necessary remains enabled and immutable
- AND prompt closes

### Requirement: Re-open Detailed Preferences

Footer "Cookie settings" MUST reopen the detailed preferences modal (`CookieSettings`, the only consent modal, opened solely by `showCookieSettings`) after a decision is saved. This flow MUST NOT re-show the banner.

#### Scenario: Cookie settings reopens preferences

- GIVEN valid receipt exists
- WHEN user clicks footer "Cookie settings"
- THEN preferences modal opens with current choices pre-selected
- AND banner stays hidden

### Requirement: Visibility State Machine

Banner MUST show when no valid receipt exists; MUST be suppressed when a valid current-version receipt exists. Malformed, missing-field, or outdated receipts MUST count as no consent and re-show the banner.

| State | Receipt | Banner |
|-------|---------|--------|
| A | none / invalid | shows |
| B | stale version | re-shows |
| C | valid current | hidden |
| D | DNT/GPC | shows, analytics OFF default |

While undecided, banner MUST NOT be dismissible: no close control MUST render; Escape MUST be ignored. Only a consent decision (accept/reject/customize-save) hides it. The store MUST NOT expose a force-open/settings-open API: `forceOpen`, `openSettings`, and `closeSettings` MUST be removed (no production caller exists; footer re-open goes through `showCookieSettings` only).

#### Scenario: Valid consent suppresses prompt

- GIVEN valid current receipt
- WHEN app loads
- THEN first-level prompt not displayed

#### Scenario: Stale receipt re-prompts

- GIVEN outdated or invalid receipt in localStorage
- WHEN app loads
- THEN non-modal prompt displayed
- AND rest of app remains visually available

#### Scenario: Undecided state is not dismissible

- GIVEN prompt visible, no decision made
- WHEN user presses Escape or seeks a close control
- THEN prompt remains visible
- AND no receipt is written

### Requirement: Persistence Contract

| Action | `analytics` | `source` |
|--------|-------------|----------|
| Accept all | true | banner |
| Reject optional | false | banner |
| Customize + Save | toggle value | banner |

Receipts MUST stay versioned (`consentVersion` 1), policy-version validated (`policyVersion` "2026-07-23"), region `EU`, ISO timestamped, `dnt` captured, Necessary `true`. DNT/GPC MUST default analytics OFF while still showing the prompt; explicit Accept overrides. Backend sync for authenticated users MUST stay best-effort and MUST NOT block UI dismissal; sync failure MUST NOT revert the local receipt.

#### Scenario: Reject persists false and blocks analytics

- GIVEN prompt visible
- WHEN user selects Reject optional
- THEN receipt with `analytics: false`, source `banner` saved
- AND prompt closes
- AND non-essential analytics remain disabled

#### Scenario: Sync failure does not block dismissal

- GIVEN authenticated user and failing governance API
- WHEN user accepts
- THEN local receipt saved and prompt closes
- AND local-only warning shown

### Requirement: Browser Resilience

Prompt MUST remain usable with privacy protections enabled and MUST NEVER leave the app behind an orphaned blocking overlay, even if prompt content fails to render. Chrome/Chromium, Safari/WebKit, Brave (Shields ON and OFF) MUST be verified across states A–D. Investigation MUST document the Brave root cause even if the redesign eliminates the symptom.

#### Scenario: Privacy browser with no valid consent

- GIVEN privacy-oriented browser, protections enabled, no valid receipt
- WHEN consent experience initializes
- THEN choice surface remains usable
- AND app not left behind an orphaned full-screen overlay

#### Scenario: Stale consent in authenticated session

- GIVEN existing authenticated session and missing/invalid receipt
- WHEN app loads
- THEN consent requested again
- AND auth state does not cause a blank consent overlay

### Requirement: i18n, Accessibility, and Test Coverage

Prompt MUST provide EN/ES copy, keyboard navigation with visible focus states, and MUST NOT regress analytics gating. Component tests MUST assert no global dialog overlay is mounted; E2E MUST cover the non-modal prompt and stale-consent state, keeping `consent-banner` testid.

#### Scenario: Localized and keyboard-usable prompt

- GIVEN locale `es-ES`
- WHEN prompt renders
- THEN copy is Spanish
- AND all actions keyboard reachable with visible focus

#### Scenario: No global overlay mounted

- GIVEN prompt renders in component tests
- THEN no `DialogOverlay`/full-screen backdrop node exists in the DOM
