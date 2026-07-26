# Consent Management — Architecture

## Overview

Profile Tailors implements a **privacy-first, cross-surface consent system**
that covers both the marketing landing page (Astro) and the dashboard SPA (Vue 3).
Consent decisions are stored in the browser's `localStorage` with a validated
receipt schema. The system detects privacy signals (DNT / GPC) and respects
them as opt-out defaults.

The architecture is **frontend-first**: consent is recorded locally before any
backend sync attempt. The backend governance API provides durable audit storage.

---

## Architecture Diagram

```
                          ┌─────────────────────────────────┐
                          │         Browser Storage          │
                          │      localStorage['pt-consent'] │
                          │  ┌───────────────────────────┐  │
                          │  │  ConsentReceipt (JSON)    │  │
                          │  │  - consentVersion: 1      │  │
                          │  │  - categories.analytics   │  │
                          │  │  - dnt: bool              │  │
                          │  │  - source: 'banner' |     │  │
                          │  │    'settings-panel'        │  │
                          │  └───────────────────────────┘  │
                          └──────────┬──────────────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              │                      │                      │
              ▼                      ▼                      ▼
   ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
   │  Marketing Site  │   │   App (Vue 3)   │   │    Backend API   │
   │   (Astro 6)      │   │   (shadcn-vue)  │   │  Spring Boot 4   │
   │                  │   │                  │   │                  │
   │ ConsentScript    │   │ Pinia Store      │   │ POST /consent    │
   │ (inline <head>)  │   │ (loadFromStorage │   │ POST /withdraw   │
   │                  │   │  → saveConsent   │   │ GET  /consent    │
   │ ConsentBanner    │   │  → syncToBackend)│   │ GET  /history    │
   │ (fixed bottom)   │   │                  │   └──────────────────┘
   │                  │   │ ConsentBanner    │
   │ Analytics.astro  │   │ (Dialog)         │
   └──────────────────┘   │                  │
                          │ CookieSettings   │
                          │ (settings panel) │
                          └──────────────────┘
```

---

## Cross-Surface Coverage

| Surface | Tech | Component(s) | Trigger |
|---------|------|-------------|---------|
| **Marketing** | Astro 6 | `ConsentScript.astro` (inline `<head>`), `ConsentBanner.astro` (fixed bottom), `CookieSettingsLink.astro` (footer) | On load if no valid receipt |
| **App** | Vue 3 + shadcn-vue | `ConsentBanner.vue` (Dialog), `CookieSettings.vue` (standalone panel) | On load if no valid receipt; footer link opens settings |
| **Backend** | Spring Boot 4 / WebFlux | `ConsentController` at `/api/governance/consent` | On explicit sync from Pinia store |

---

## localStorage Schema

**Key:** `pt-consent`

**Type:** `ConsentReceipt` (defined in `shared/web/types/consent.ts`)

```typescript
interface ConsentReceipt {
  consentVersion: number    // Material version — increment when categories change
  policyVersion: string     // Privacy policy date (YYYY-MM-DD)
  timestamp: string         // ISO 8601 of consent action
  region: string            // 'EU' for MVP (over-compliance)
  categories: {
    necessary: true         // Always true — needed for basic functionality
    analytics: boolean      // User opt-in for analytics/tracking
  }
  dnt: boolean              // DNT or GPC was active at consent time
  source: 'banner' | 'settings-panel'  // How consent was given
}
```

**Runtime validation** uses a Zod schema (`shared/web/validation/consent.ts`):
- `consentVersion` must be a literal `1` (exact match)
- `policyVersion` must be a valid ISO date
- `timestamp` must be a valid ISO datetime
- `region` must be `'EU'`
- `necessary` is locked to `true` (immutable)
- `source` must be `'banner'` or `'settings-panel'`

`validateConsentReceipt()` returns `null` for any invalid input, which is
treated identically to "no consent given" — the banner is shown.

---

## Consent Flow

### 1. Page Load → Banner Decision

```
Page Load
    │
    ▼
Read localStorage['pt-consent']
    │
    ├── null / parse error ──────────► Show banner (no consent)
    │
    ▼ valid JSON
Validate with Zod schema
    │
    ├── invalid ─────────────────────► Show banner (invalid receipt)
    │
    ▼ valid
Check consentVersion
    │
    ├── < CURRENT_CONSENT_VERSION ───► Show banner (outdated version)
    │
    ▼ version matches
Check policyVersion
    │
    ├── < CURRENT_POLICY_VERSION ────► Show banner (policy changed)
    │
    ▼ all checks pass
Set analytics flag → Hide banner → Ready
```

### 2. Banner → Storage

```
Banner shown
    │
    ▼
User interacts with banner:
  ├── "Accept all"  → analytics = true
  ├── "Reject all"  → analytics = false
  └── Save preferences → analytics = from toggle state
    │
    ▼
Detect DNT/GPC signals → populate dnt field
    │
    ▼
Build ConsentReceipt → JSON.stringify
    │
    ▼
Write to localStorage['pt-consent']
    │
    ▼ (Vue app only)
Pinia store → syncToBackend() → POST /api/governance/consent
    │
    ▼
Set window.__PT_CONSENT_ANALYTICS flag
    │
    ▼
Hide banner
```

### 3. Storage → Analytics Flag

The **marketing site** uses an inline `<script>` in `<head>` (`ConsentScript.astro`):
1. Reads `localStorage['pt-consent']` synchronously
2. Validates against the version/policy constants
3. Sets `window.__PT_CONSENT_ANALYTICS = true/false`
4. Dispatches a `consentReady` custom event
5. `Analytics.astro` checks `__PT_CONSENT_ANALYTICS` before loading Ahrefs

The **app** uses the Pinia store:
1. Store reads from localStorage on init
2. Computed getters expose `analyticsEnabled`
3. Analytics initialisation reads the getter

---

## Version Upgrade Mechanism

The system uses **two version axes** to trigger re-consent:

### consentVersion (Material Version)

```typescript
export const CURRENT_CONSENT_VERSION = 1
```

- Incremented when consent purposes or tracking categories change
- Stored receipts with `consentVersion < CURRENT_CONSENT_VERSION` are discarded
- Forces the banner to re-show so the user re-accepts under new terms

### policyVersion (Policy Date)

```typescript
export const CURRENT_POLICY_VERSION = '2026-07-23'
```

- Updated when the privacy policy is revised
- Uses ISO calendar date format (`YYYY-MM-DD`)
- Zod validates that the stored `policyVersion` matches the current constant
- A mismatch triggers re-consent regardless of `consentVersion`

**Upgrade flow:**
1. Both are compiled as constants in the shared library
2. The inline script (marketing) and Pinia store (app) both reference them
3. On mismatch, the existing receipt is deleted and banner is shown
4. After re-consent, the new receipt carries the current versions

---

## DNT / GPC Privacy Signal Detection

Defined in `shared/web/utils/privacy-signals.ts`:

| Signal | Detection | Effect |
|--------|-----------|--------|
| **DNT** | `navigator.doNotTrack === '1'` or `'yes'` | Analytics defaults OFF |
| **GPC** | `navigator.globalPrivacyControl === true` | Analytics defaults OFF |
| Both | Either signal active | Analytics defaults OFF |

All functions are **SSR-safe** — they return `false` when `navigator` is
undefined.

The `dnt` boolean is persisted in the consent receipt so the audit trail
records whether a privacy signal was present at consent time.

**UX rule:** When a privacy signal is active, the analytics toggle in the
banner defaults to OFF, but the user can still explicitly override it.

---

## Backend Sync (Pinia Store → API)

The Vue app's Pinia store (`consent.store.ts`) adds optional backend sync:

```
consentStore.saveConsent(receipt)
    │
    ├── localStorage.setItem('pt-consent', JSON.stringify(receipt))
    │       (always saved locally first)
    │
    └── syncToBackend(receipt)  ──►  POST /api/governance/consent
            │                              │
            ├── on success ────► done       ├── 201 Created (new)
            └── on error ──────► toast      └── 200 OK (idempotent)
                                  error
```

- LocalStorage write is **always** performed first — the app works offline
- Backend sync is a best-effort optimisation for audit persistence
- On sync failure, a `toast.error()` notifies the user but the local consent
  remains valid
- The store does NOT block on backend response

### Backend Governance API

Base path: `/api/governance/consent`

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/governance/consent` | Record a consent decision |
| POST | `/api/governance/consent/withdraw` | Withdraw an active consent |
| GET | `/api/governance/consent` | List workspace consent records |
| GET | `/api/governance/consent/history` | Get consent lifecycle history |

Source values accepted by the API: `'banner'`, `'settings-panel'`

---

## Shared Layer Structure

```
shared/web/
├── types/
│   ├── consent.ts             # ConsentReceipt interface, constants
│   └── consent.test.ts        # Constant validation tests
├── validation/
│   ├── consent.ts             # Zod schema + validateConsentReceipt()
│   └── consent.test.ts        # Schema validation tests (valid/invalid)
└── utils/
    └── privacy-signals.ts     # isDNTEnabled(), isGPCEnabled(), hasPrivacySignal()
```

---

## E2E Test Coverage

### Marketing (Astro)

`apps/web/marketing/e2e/consent.spec.ts` — 6 tests:

1. **accept-all** — analytics=true, source='banner', banner hides on reload
2. **reject-all** — analytics=false, Ahrefs blocked
3. **dnt-enabled** — toggle default OFF, user can override, dnt=true in receipt
4. **save preferences** — granular toggle OFF, analytics=false
5. **version upgrade** — consentVersion=0 expired → banner shows
6. **gpc-signal** — default analytics OFF, same behaviour as DNT

### App (Vue 3)

`apps/web/app/e2e/specs/consent.spec.ts` — 3 tests:

1. **TASK-026** — accept all via banner, source='banner', analytics=true
2. **TASK-027** — withdrawal via CookieSettings, source='settings-panel',
   analytics=false
3. **TASK-028** — version upgrade, re-consent upgrades to v1

### Shared Utilities

`apps/web/app/e2e/fixtures/consent-helpers.ts` — `setConsentReceipt()`,
`clearConsent()`, `mockConsentSync()` for consistent test setup.

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| localStorage as source of truth | Survives page reload, synchronous, no network dependency |
| Backend sync is best-effort | Consent must work offline; audit is secondary |
| Two-version upgrade (version + policy) | Policy changes and schema changes are independent events |
| Zod validation on every read | Defensive — localStorage data is mutable by users/extensions |
| DNT/GPC default OFF but overridable | Compliance without blocking user intent |
| Necessary category locked to `true` | Ensures minimum functionality consent is never disabled |
| Source tracking (banner vs settings) | Distinguishes first-time consent from preference changes |

---

## Related Files

- `shared/web/types/consent.ts` — Types and constants
- `shared/web/validation/consent.ts` — Zod schema
- `shared/web/utils/privacy-signals.ts` — DNT/GPC detection
- `apps/web/marketing/src/components/consent/ConsentScript.astro` — Inline head script
- `apps/web/marketing/src/components/consent/ConsentBanner.astro` — Marketing banner
- `apps/web/app/src/components/consent/ConsentBanner.vue` — App banner dialog
- `apps/web/app/src/components/consent/CookieSettings.vue` — App settings panel
- `apps/web/app/src/modules/settings/infrastructure/consent.store.ts` — Pinia store
- `server/smp/src/main/kotlin/.../governance/infrastructure/http/ConsentController.kt` — Backend API
