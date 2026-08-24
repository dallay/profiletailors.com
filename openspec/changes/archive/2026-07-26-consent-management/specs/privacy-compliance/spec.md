# Delta for Privacy Compliance

## ADDED Requirements

### Requirement: Consent Receipt Storage

The system MUST store user consent choices in a structured, versioned receipt in browser
localStorage for anonymous marketing visitors.

The receipt MUST include:

- Consent model version (`consentVersion`) as integer
- Privacy policy document version (`policyVersion`) as YYYY-MM-DD date string
- ISO 8601 timestamp of consent grant
- Region code (hardcoded `"EU"` for MVP over-compliance)
- Category choices (necessary, analytics)
- DNT/GPC signal presence at consent time
- Consent source (banner or settings-panel)

**localStorage key**: `pt-consent`

**Schema validation**:
| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `consentVersion` | integer | Yes | Must equal current system version (1) |
| `policyVersion` | string | Yes | Format YYYY-MM-DD, must be valid date |
| `timestamp` | string | Yes | ISO 8601 format |
| `region` | string | Yes | Two-letter code (EU for MVP) |
| `categories.necessary` | boolean | Yes | Must always be true |
| `categories.analytics` | boolean | Yes | true or false |
| `dnt` | boolean | Yes | true if DNT/GPC active at consent time |
| `source` | string | Yes | "banner" or "settings-panel" |

**Invalid receipt handling**: If any required field missing, type wrong, or `consentVersion`
outdated → treat as no consent.

#### Scenario: First-time visitor accepts analytics

- GIVEN a user visits the marketing site for the first time
- AND no `pt-consent` key exists in localStorage
- WHEN the user clicks "Accept all" in the consent banner
- THEN a receipt is written to localStorage with:
    - `consentVersion: 1`
    - `policyVersion: "2026-07-23"`
    - `timestamp: "<ISO 8601 now>"`
    - `region: "EU"`
    - `categories.necessary: true`
    - `categories.analytics: true`
    - `dnt: false`
    - `source: "banner"`

#### Scenario: User rejects analytics

- GIVEN a user visits the marketing site
- AND no consent receipt exists
- WHEN the user clicks "Reject all"
- THEN a receipt is written with `categories.analytics: false`
- AND Ahrefs script MUST NOT load

#### Scenario: Corrupted receipt triggers re-consent

- GIVEN a receipt exists but JSON is malformed
- WHEN the page loads
- THEN the inline script treats it as no consent
- AND the banner appears
- AND Ahrefs is blocked

#### Scenario: Outdated consentVersion triggers re-consent

- GIVEN a receipt exists with `consentVersion: 0`
- AND the system current version is `1`
- WHEN the page loads
- THEN the banner appears
- AND the user must consent again

---

### Requirement: Consent Banner Display Logic

The system MUST show a consent banner on first visit, when consent is outdated, or when explicitly
requested via "Cookie settings" link.

The system MUST NOT show the banner when a valid receipt exists for the current `consentVersion`.

**Show banner when**:

- No `pt-consent` in localStorage
- Receipt exists but `consentVersion` < system current version
- User clicks "Cookie settings" footer link

**Hide banner when**:

- Valid receipt exists with current `consentVersion`

#### Scenario: First visit shows banner

- GIVEN a user visits the marketing site
- AND no localStorage receipt exists
- WHEN the page loads
- THEN the consent banner appears

#### Scenario: Returning user with valid consent sees no banner

- GIVEN a user has a receipt with `consentVersion: 1`
- AND the system version is `1`
- WHEN the user visits the site
- THEN the banner does NOT appear
- AND analytics loads if `categories.analytics: true`

#### Scenario: Cookie settings link re-opens banner

- GIVEN a user has consented
- AND the banner is hidden
- WHEN the user clicks "Cookie settings" in the footer
- THEN the banner appears
- AND current consent choices are pre-selected in toggles

---

### Requirement: Consent Banner UI Structure

The consent banner MUST present two categories with equal-prominence action buttons and i18n
support.

**Categories displayed**:

1. **Necessary** (always on, no toggle): Label + description, checkbox disabled/checked
2. **Analytics** (opt-in toggle): Label + description, toggle switch

Marketing category MUST NOT be shown (not used in MVP).

**Actions**:

- "Accept all" button (primary styling)
- "Reject all" button (primary styling, equal visual weight)
- "Save preferences" button (primary styling)

**Equal prominence rule**: All three buttons MUST have identical size, color saturation, and
position hierarchy. No dark patterns (e.g., green accept + gray reject).

**Content**:

- Heading: i18n key `consent.banner.heading`
- Description: i18n key `consent.banner.description` with embedded privacy policy link
- Necessary category: `consent.category.necessary.label`, `consent.category.necessary.description`
- Analytics category: `consent.category.analytics.label`, `consent.category.analytics.description`

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

#### Scenario: Save preferences respects toggle state

- GIVEN the banner is displayed
- AND the analytics toggle is OFF
- WHEN the user clicks "Save preferences"
- THEN `categories.analytics` is set to `false`
- AND the receipt is saved

---

### Requirement: DNT and GPC Signal Handling

The system MUST detect browser Do Not Track (DNT) and Global Privacy Control (GPC) signals and set
restrictive defaults while maintaining transparency.

**Detection**:

```javascript
function isDNTEnabled() {
  return navigator.doNotTrack === "1" || 
         navigator.doNotTrack === "yes" ||
         window.doNotTrack === "1";
}

function isGPCEnabled() {
  return navigator.globalPrivacyControl === true;
}
```

**Behavior when detected**:

- Analytics toggle defaults to OFF
- Banner is STILL shown (transparency over silent blocking)
- State remains `UNDECIDED` until user clicks a button
- If user clicks "Accept all", they override the signal
- Receipt stores `"dnt": true` to record signal presence

**Legal interpretation**: DNT and GPC are privacy *preferences*, not stored *consent*. Showing the
banner with restrictive defaults respects the signal while allowing informed override.

#### Scenario: DNT signal blocks analytics by default

- GIVEN `navigator.doNotTrack === "1"`
- WHEN the user visits the site
- THEN the banner appears
- AND the analytics toggle is OFF
- AND Ahrefs does NOT load yet

#### Scenario: User with DNT explicitly accepts analytics

- GIVEN `navigator.doNotTrack === "1"`
- AND the banner is displayed with analytics toggle OFF
- WHEN the user clicks "Accept all"
- THEN `categories.analytics` is set to `true`
- AND `dnt: true` is stored in the receipt
- AND Ahrefs loads

#### Scenario: GPC signal treated identically to DNT

- GIVEN `navigator.globalPrivacyControl === true`
- WHEN the user visits the site
- THEN behavior is identical to DNT scenario
- AND analytics toggle defaults OFF

---

### Requirement: Script Blocking Mechanism

The system MUST conditionally load Ahrefs Analytics based on the consent receipt, using an inline
synchronous script that runs before analytics initialization.

**Execution order**:

1. Inline `<script>` in `<head>` (synchronous, no `async`/`defer`)
2. Read `localStorage.getItem('pt-consent')`
3. Parse JSON and validate receipt
4. Set `window.__PT_CONSENT_ANALYTICS = true` only if valid receipt with
   `categories.analytics === true`
5. Later in page: `Analytics.astro` checks `window.__PT_CONSENT_ANALYTICS` before injecting Ahrefs

**Error handling**:

- JSON parse failure → treat as no consent, block scripts
- Missing fields → treat as no consent
- `categories.analytics === false` → block scripts
- No receipt → block scripts

**Fallback**: If uncertain, block. Over-compliance is safer than tracking without consent.

#### Scenario: Valid consent allows analytics load

- GIVEN a receipt exists with `categories.analytics: true`
- WHEN the page loads
- THEN `window.__PT_CONSENT_ANALYTICS` is set to `true`
- AND `Analytics.astro` injects the Ahrefs script

#### Scenario: No consent blocks analytics

- GIVEN no receipt exists
- WHEN the page loads
- THEN `window.__PT_CONSENT_ANALYTICS` is `undefined` or `false`
- AND `Analytics.astro` does NOT inject Ahrefs

#### Scenario: Rejected analytics blocks script

- GIVEN a receipt exists with `categories.analytics: false`
- WHEN the page loads
- THEN Ahrefs is blocked
- AND `window.__PT_CONSENT_ANALYTICS` remains falsy

---

### Requirement: Consent Withdrawal Flow

The system MUST allow users to change consent choices at any time via a "Cookie settings" link.

**Entry points**:

- Marketing site: Footer link "Cookie settings"
- App: Settings > Privacy > Cookie preferences

**Behavior**:

- Clicking the link re-opens the banner
- Current receipt values pre-populate the toggles
- User changes toggle states
- Clicks "Save preferences"
- Receipt is updated in localStorage
- Page reloads (or dynamically updates script state)

**Backend sync (app only)**:

- When user is authenticated AND in app context
- After saving to localStorage, call `POST /api/governance/consent`
- SubjectReference: `user(userId)` from auth context
- Purpose: `"web.analytics"`
- If backend fails, localStorage is still saved (degraded mode)

#### Scenario: User withdraws analytics consent

- GIVEN a user has consented with `categories.analytics: true`
- AND analytics is currently running
- WHEN the user clicks "Cookie settings"
- AND toggles analytics OFF
- AND clicks "Save preferences"
- THEN the receipt is updated with `categories.analytics: false`
- AND `source: "settings-panel"` is recorded
- AND the page reloads (or scripts stop)

#### Scenario: Backend sync failure does not block withdrawal

- GIVEN an authenticated user in the app
- WHEN they withdraw analytics consent
- AND the backend API call fails (500 error)
- THEN the localStorage receipt is STILL updated
- AND the user sees a warning "Consent saved locally, sync failed"

---

### Requirement: Consent Version Upgrades

The system MUST require new consent when `consentVersion` increments, but MUST NOT require new
consent for non-material `policyVersion` changes.

**Version semantics**:

- `consentVersion` (integer): Increments when consent purposes/categories change materially (e.g.,
  adding "Advertising Personalization" category)
- `policyVersion` (YYYY-MM-DD string): Tracks privacy policy document version

**Upgrade scenarios**:
| Stored Version | Current Version | Action |
|----------------|-----------------|--------|
| `consentVersion: 1` | `consentVersion: 1` | Valid, no action |
| `consentVersion: 0` | `consentVersion: 1` | Invalid, show banner |
| `policyVersion: "2026-07-01"` | `policyVersion: "2026-07-23"` | Valid if `consentVersion`
matches |

#### Scenario: Material change requires re-consent

- GIVEN a user has a receipt with `consentVersion: 1`
- AND the system upgrades to `consentVersion: 2` (added Marketing category)
- WHEN the user visits the site
- THEN the banner appears
- AND the user must consent again

#### Scenario: Policy clarification does not invalidate consent

- GIVEN a user has `policyVersion: "2026-07-01"`
- AND the policy document updates to `"2026-07-23"` (typo fix)
- AND `consentVersion` remains `1`
- WHEN the user visits the site
- THEN their consent remains valid
- AND the banner does NOT appear

---

### Requirement: Internationalization

The system MUST provide consent banner copy in English and Spanish with legally accurate
translations.

**Required i18n keys**:

| Key                                      | EN                                                                                                          | ES                                                                                                                          |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `consent.banner.heading`                 | "We use cookies"                                                                                            | "Usamos cookies"                                                                                                            |
| `consent.banner.description`             | "We use cookies to improve your experience. You can read our [privacy policy](/privacy/) for more details." | "Usamos cookies para mejorar tu experiencia. Puedes leer nuestra [política de privacidad](/es/privacy/) para más detalles." |
| `consent.category.necessary.label`       | "Necessary cookies"                                                                                         | "Cookies necesarias"                                                                                                        |
| `consent.category.necessary.description` | "Required for authentication, security, and basic site functionality."                                      | "Requeridas para autenticación, seguridad y funcionalidad básica del sitio."                                                |
| `consent.category.analytics.label`       | "Analytics cookies"                                                                                         | "Cookies de análisis"                                                                                                       |
| `consent.category.analytics.description` | "Help us understand how you use the site to improve your experience."                                       | "Nos ayudan a entender cómo usas el sitio para mejorar tu experiencia."                                                     |
| `consent.action.acceptAll`               | "Accept all"                                                                                                | "Aceptar todas"                                                                                                             |
| `consent.action.rejectAll`               | "Reject all"                                                                                                | "Rechazar todas"                                                                                                            |
| `consent.action.savePreferences`         | "Save preferences"                                                                                          | "Guardar preferencias"                                                                                                      |
| `consent.footer.cookieSettings`          | "Cookie settings"                                                                                           | "Configuración de cookies"                                                                                                  |

**Translation quality**: Spanish copy MUST be legally accurate, not machine-translated. Terms like "
cookies", "privacy policy", "analytics" have specific legal meanings in LOPD-GDD context.

#### Scenario: Spanish user sees localized banner

- GIVEN a user's browser locale is `es-ES`
- WHEN the marketing site loads
- THEN the banner heading displays "Usamos cookies"
- AND all category labels are in Spanish

---

### Requirement: E2E Test Coverage

The system MUST verify consent flow behavior with automated end-to-end tests covering accept,
reject, withdrawal, version upgrades, and DNT/GPC scenarios.

**Test scenarios** (Playwright):

1. **Accept all**: Visit → banner appears → click "Accept all" → verify receipt `analytics: true` →
   verify Ahrefs loaded → refresh → banner hidden
2. **Reject all**: Visit → banner appears → click "Reject all" → verify receipt `analytics: false` →
   verify Ahrefs NOT loaded → refresh → banner hidden
3. **Granular accept**: Visit → toggle analytics ON → click "Save preferences" → verify receipt
   `analytics: true`
4. **DNT enabled**: Mock `navigator.doNotTrack = "1"` → visit → banner appears → analytics toggle
   OFF → click "Accept all" → verify `dnt: true` in receipt → verify Ahrefs loaded (user override)
5. **Withdrawal**: Accept all → click "Cookie settings" → toggle analytics OFF → save → verify
   receipt updated → verify Ahrefs blocked
6. **Version upgrade**: Create receipt `consentVersion: 0` → visit → banner appears (outdated)
7. **GPC signal**: Mock `navigator.globalPrivacyControl = true` → visit → analytics toggle OFF →
   behavior identical to DNT

#### Scenario: E2E test verifies accept flow

- GIVEN a Playwright test navigates to the marketing site
- AND no localStorage receipt exists
- WHEN the test clicks "Accept all"
- THEN `localStorage.getItem('pt-consent')` contains valid JSON
- AND `categories.analytics === true`
- AND the Ahrefs script tag exists in the DOM

#### Scenario: E2E test verifies DNT handling

- GIVEN a test mocks `navigator.doNotTrack = "1"`
- WHEN the page loads
- THEN the analytics toggle is pre-checked OFF
- AND Ahrefs is not loaded until user explicitly accepts

---

## Success Metrics

| Criterion                                   | Acceptance Test                                                  |
|---------------------------------------------|------------------------------------------------------------------|
| Non-essential scripts blocked until consent | Playwright test verifies no Ahrefs network request before accept |
| Equal prominence buttons                    | Visual regression test confirms identical button styling         |
| Withdrawal flow works                       | E2E test changes consent, verifies receipt updated               |
| Receipt schema complete                     | Unit test validates all required fields present                  |
| DNT/GPC documented and tested               | E2E test with mocked signals passes                              |
| Version upgrade requires re-consent         | E2E test with outdated `consentVersion` shows banner             |
| i18n coverage                               | Spanish locale test verifies all keys translated                 |

---

## Notes

- **Marketing category omitted**: Proposal included Marketing toggle, but user decision
  post-proposal removed it. No marketing scripts exist; showing "Marketing: OFF" would be misleading
  consent (GDPR Article 4(11) requires informed consent about specific purposes).
- **Backend sync deferred**: Anonymous visitors stay localStorage-only. Authenticated app users sync
  to `/api/governance/consent` for audit trail.
- **policyVersion format finalized**: User chose `YYYY-MM-DD` over semantic versioning for legal
  audit clarity.
