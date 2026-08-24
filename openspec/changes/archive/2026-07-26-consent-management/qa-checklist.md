# QA Checklist: Consent Management

Manual verification steps for the consent management feature across both surfaces (marketing site +
app).

## 1. Marketing Site (Astro) — Consent Banner

### 1.1 First Visit (No Consent)

- [ ] Navigate to marketing site (`/`) with clean localStorage
- [ ] Consent banner is visible at the bottom of the viewport
- [ ] Banner has `position: fixed` and does not shift page content (CLS = 0)
- [ ] "Accept all", "Reject all", and "Save preferences" buttons have **equal visual prominence** (
  same size, padding, font-weight, color saturation)
- [ ] Necessary cookies toggle is disabled and marked as "Always on"
- [ ] Analytics toggle is ON by default (no DNT/GPC)

### 1.2 Accept All Flow

- [ ] Click "Accept all"
- [ ] Banner disappears
- [ ] Reload the page — banner does NOT reappear
- [ ] `localStorage` has `pt-consent` with `analytics: true`, `source: "banner"`,
  `consentVersion: 1`
- [ ] Ahrefs analytics script loads (check Network tab for `analytics.ahrefs.com`)

### 1.3 Reject All Flow

- [ ] Clear `localStorage`, reload
- [ ] Click "Reject all"
- [ ] Banner disappears
- [ ] Reload — banner NOT shown
- [ ] `localStorage` receipt has `analytics: false`, `source: "banner"`
- [ ] Ahrefs script does NOT load

### 1.4 Granular Save

- [ ] Clear `localStorage`, reload
- [ ] Toggle analytics OFF, click "Save preferences"
- [ ] Banner disappears
- [ ] `localStorage` receipt has `analytics: false`, `source: "banner"` (marketing saves always use
  `banner` source)

### 1.5 DNT Signal

- [ ] Enable "Do Not Track" in browser settings
- [ ] Navigate to marketing site
- [ ] Analytics toggle is OFF by default
- [ ] Banner still shows for transparency
- [ ] Accept all — `localStorage` receipt has `dnt: true`

### 1.6 GPC Signal

- [ ] Enable Global Privacy Control in browser
- [ ] Navigate to marketing site
- [ ] Analytics toggle is OFF by default

### 1.7 Version Upgrade

- [ ] Seed `localStorage` with `consentVersion: 0` and reload
- [ ] Banner reappears (version mismatch)
- [ ] Re-accept — `localStorage` receipt has `consentVersion: 1`

### 1.8 Cookie Settings Link (Footer)

- [ ] Navigate to any marketing page
- [ ] Scroll to footer — "Cookie settings" link is visible in the legal links section
- [ ] Click "Cookie settings" — consent banner reappears

### 1.9 i18n

- [ ] Switch to Spanish (`/es/`)
- [ ] All consent strings appear in Spanish: "Usamos cookies", "Aceptar todas", etc.
- [ ] Footer "Cookie settings" shows "Configuración de cookies"
- [ ] The analytics toggle label reads "Cookies de análisis"
- [ ] Switch back to English — strings revert to English

---

## 2. App (Vue + shadcn-vue) — Consent Banner & Settings

### 2.1 First Visit (Logged In)

- [ ] Log into the app with a clean browser
- [ ] Consent banner appears as a Dialog/modal
- [ ] Three action buttons visible: Accept All, Reject All, Save Preferences
- [ ] Necessary category is disabled
- [ ] Analytics toggle is configurable

### 2.2 Accept All

- [ ] Click "Accept All"
- [ ] Dialog closes
- [ ] `localStorage`: `analytics: true`, `source: "banner"`, `consentVersion: 1`
- [ ] Reload — dialog does NOT reappear

### 2.3 Reject All

- [ ] Clear storage, reload
- [ ] Click "Reject All"
- [ ] Dialog closes
- [ ] `localStorage`: `analytics: false`, `source: "banner"`

### 2.4 Cookie Settings Link (Footer + Sidebar)

- [ ] After accepting or rejecting, locate "Cookie settings" link in:
    - **Footer bar** at the bottom of the app shell
    - **Sidebar user menu** (click user avatar/name)
- [ ] Click either link — CookieSettings dialog opens
- [ ] Dialog shows the same category layout (necessary disabled, analytics toggleable)

### 2.5 Withdrawal via Cookie Settings

- [ ] Open CookieSettings dialog from footer/sidebar
- [ ] Toggle analytics OFF
- [ ] Click "Save Preferences"
- [ ] Dialog closes
- [ ] `localStorage`: `analytics: false`, `source: "settings-panel"`
- [ ] Reload — banner does NOT reappear (valid consent still exists)

### 2.6 Version Upgrade

- [ ] Seed `localStorage` with `consentVersion: 0`
- [ ] Reload the app
- [ ] Banner appears (outdated version)
- [ ] Accept — receipt has `consentVersion: 1`

### 2.7 Backend Sync Error Handling (Authenticated Users)

- [ ] Accept/reject consent while authenticated
- [ ] If backend sync fails, a toast appears: "Consent saved locally, sync failed."
- [ ] `localStorage` is still updated correctly regardless of sync outcome

### 2.8 Equal Prominence (Visual Check)

- [ ] All three buttons in the banner dialog have the same visual weight
    - Same padding (`py-3`) and min-width
    - Same font-size and weight
    - Same hover/active states
- [ ] No button is visually diminished or "dark patterned"

---

## 3. Cross-Surface

### 3.1 Shared localStorage Key

- [ ] Consent given on marketing site persists and is valid in the app
- [ ] Consent given in the app persists and is valid on the marketing site
- [ ] Both surfaces use the same `pt-consent` key and validation schema

### 3.2 Consent Version Alignment

- [ ] Marketing inline script validates `consentVersion` identically to app's Zod schema
- [ ] Both surfaces reject `consentVersion: 0`
- [ ] Bumping `CURRENT_CONSENT_VERSION` requires re-consent on both surfaces

---

## 4. Performance

- [ ] Marketing inline script (`ConsentScript.astro`) is < 2KB minified
- [ ] No layout shift (CLS = 0) when banner appears
- [ ] Lighthouse performance score is maintained (or regression < 1 point)
- [ ] Ahrefs script is blocked until explicit consent (verify via Network tab)
