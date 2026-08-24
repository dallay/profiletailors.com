# Exploration: Frontend Consent Management and Analytics Blocking

## Current State

### Backend (Already Exists)

- **Complete consent domain** in `server/smp/governance/domain/ConsentRecordModels.kt`
    - `ConsentType`: CONSENT, CONTRACT_ACCEPTANCE, LEGITIMATE_INTEREST
    - `SubjectReference`: workspace, user, or anonymous (hashed identifier)
    - `ConsentRecord`: immutable append-only storage with withdrawal support
- **REST API** at `/api/governance/consent` (ConsentController)
    - POST `/api/governance/consent` — record consent
    - POST `/api/governance/consent/withdraw` — withdraw consent
    - GET `/api/governance/consent` — list workspace consent records
    - GET `/api/governance/consent/history` — history for subject+purpose
- **Validation**: enum validation, locale validation (ISO 639-1), idempotent storage

### Frontend State

**Marketing site (Astro 6):**

- `Analytics.astro` loads Ahrefs Analytics unconditionally via Partytown
    - Script: `https://analytics.ahrefs.com/analytics.js`
    - Only loads if `AHREFS_ANALYTICS_KEY` env var is set
    - Uses Partytown (`type="text/partytown"`) — analytics run in web worker
- Privacy policy at `/privacy/` and `/es/privacy/` — **APPROVED** for publication
    - Uses `isLegalPublicationApproved()` which returns `true` (legal-publication.ts)
    - Policy version: "22 July 2026" (matches today's date)
    - Cookie section exists, mentions Ahrefs as "cookieless" but conditional
- **No geolocation detection** exists
- **No consent banner** exists
- **No DNT/GPC handling** exists

**App (Vue 3 + shadcn-vue):**

- `privacy.store.ts` exists but handles **DSAR requests only** (ACCESS, EXPORT, CORRECTION,
  DELETION)
- No consent management code
- shadcn-vue components available: Dialog, Sheet, AlertDialog
- Theme: reka-nova preset (Nothing-inspired design already in use)

### Files That Need Consent Integration

1. `apps/web/marketing/src/components/Analytics.astro` — conditional loading
2. Marketing pages that load Analytics.astro (Layout.astro likely)
3. New consent banner component (Astro)
4. New consent storage (localStorage + optional backend sync)
5. App consent UI (if analytics added to app later)

---

## Affected Areas

- `apps/web/marketing/src/components/Analytics.astro` — conditional loading logic
- `apps/web/marketing/src/layouts/Layout.astro` — banner injection point
- `apps/web/marketing/src/i18n/en.ts` and `es.ts` — consent banner copy
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/ConsentRecordModels.kt` —
  purpose string conventions (documentation only, no code change)
- `apps/web/app/src/modules/settings/infrastructure/consent.store.ts` (new) — Pinia store for app
  consent
- E2E specs (new) — consent banner acceptance/rejection flows

---

## Approaches

### 1. **Geolocation Strategy**

#### Option A: Show banner to everyone (over-compliance)

- **Pros**: Simple, no false negatives, privacy-first default
- **Cons**: Banner fatigue for non-EU users, unnecessary friction
- **Effort**: Low

#### Option B: Edge geo headers (Vercel)

- **Pros**: No backend required, fast, Astro middleware can access
  `Astro.request.headers.get('x-vercel-ip-country')`
- **Cons**: Only works on Vercel, header not available in dev (always show banner in dev)
- **Effort**: Low

#### Option C: Backend IP geolocation

- **Pros**: Works in any deployment, accurate
- **Cons**: Adds latency, requires service (MaxMind, ipapi.co)
- **Effort**: Medium

#### Option D: Edge Config for region rules (Vercel)

- **Pros**: Dynamic region list, no redeploy to add countries
- **Cons**: Vercel-specific, requires Edge Config setup
- **Effort**: Medium

**Recommendation: Option A for MVP** (show banner to everyone), migrate to Option B after launch if
needed. Justification: over-compliance is safer than false negatives, avoids deployment-specific
logic, and MVP is for EU launch anyway.

---

### 2. **Cookie Categories and Purpose Strings**

Backend `purpose` field is a freeform string. Frontend needs consistent categories.

#### Proposed Categories:

| Category        | Purpose String      | GDPR Basis                               | Default   | User Control   | Examples                                                |
|-----------------|---------------------|------------------------------------------|-----------|----------------|---------------------------------------------------------|
| **Necessary**   | `cookies.necessary` | Legitimate interest / Contract necessity | Always ON | No toggle      | `pt_refresh` (session), CSRF token, language preference |
| **Analytics**   | `analytics.ahrefs`  | Consent (Art. 6.1.a)                     | OFF       | Opt-in toggle  | Ahrefs Web Analytics                                    |
| **Preferences** | `preferences.ui`    | Legitimate interest                      | ON        | Opt-out toggle | Theme, sidebar state, locale                            |
| **Marketing**   | `marketing.emails`  | Consent (Art. 6.1.a)                     | OFF       | Opt-in toggle  | Future: Google Ads, Meta Pixel                          |

**Purpose string convention**: `{category}.{specific-service}` (e.g. `analytics.ahrefs`,
`analytics.ga4`, `marketing.google-ads`)

**Cookie Inventory** (from privacy policy):

- `pt_refresh` — HttpOnly session cookie (7 days) → **necessary**
- `sidebar_state` — JS-accessible preference (7 days) → **preferences**
- localStorage: theme, locale, workspace, drafts → **preferences** (most), **necessary** (workspace,
  drafts)
- Ahrefs Analytics — cookieless but script execution still needs consent → **analytics**

**Learned**: Backend purpose field is freeform, so frontend owns the taxonomy. Tests use
`"marketing.emails"` — this convention already exists implicitly.

---

### 3. **Storage Strategy**

#### Option A: localStorage only

- **Pros**: Simple, no network, works offline
- **Cons**: Per-device, no cross-device sync, anonymous users lose consent if localStorage clears
- **Effort**: Low

#### Option B: Backend-first (always sync)

- **Pros**: Cross-device sync, audit trail, GDPR compliance built-in
- **Cons**: Requires auth or stable anonymous ID, adds latency
- **Effort**: Medium

#### Option C: Hybrid (localStorage + lazy backend sync)

- **Pros**: Fast initial load, eventual sync for authenticated users, audit trail for compliance
- **Cons**: Sync timing complexity, dual source of truth
- **Effort**: High

**Recommendation: Option C (hybrid)**

- **Anonymous users**: localStorage only, with
  `SubjectReference.anonymous(sha256(email || localStorage.id))` if they later register on waitlist
- **Authenticated users**: localStorage + backend sync on login, preference changes persist to
  backend via POST `/api/governance/consent`
- **Sync trigger**: on login (sync localStorage → backend), on logout (keep localStorage), on
  consent change (immediate backend write if authenticated)

**SubjectReference strategy**:

- Marketing site (pre-auth):
  `SubjectReference.anonymous(localStorage.getItem('consent-id') || generateUUID())`
- Waitlist join: sync consent via `SubjectReference.anonymous(sha256(email))` (backend already does
  this in GovernanceWaitlistConsentRecorder)
- App (authenticated): `SubjectReference.user(userId)` — backend records with workspace scope

---

### 4. **DNT/GPC Handling**

Browser signals:

- `navigator.doNotTrack` — deprecated but still used (`"1"` = do not track)
- `navigator.globalPrivacyControl` — modern signal (boolean, true = do not track)

#### Option A: Respect DNT/GPC (auto-block analytics)

- **Pros**: Privacy-first, honors user intent, no banner needed if DNT=1
- **Cons**: Low adoption (<5% of users), some false positives (VPN/Tor default settings)
- **Effort**: Low

#### Option B: Ignore DNT/GPC (banner always)

- **Pros**: No ambiguity, clear consent flow
- **Cons**: Ignores user preference signal
- **Effort**: None

#### Option C: Hybrid (DNT/GPC = default "reject", but show banner)

- **Pros**: Respects signal, allows override, clear UX
- **Cons**: Slightly more complex
- **Effort**: Low

**Recommendation: Option C (hybrid)**

- If `navigator.globalPrivacyControl === true` or `navigator.doNotTrack === "1"`: default analytics
  toggle to OFF, show banner with toggles pre-set
- User can still override (GPC is a preference signal, not a mandate)
- Log the signal in backend: `source = "gpc-default"` or `source = "dnt-default"`

---

### 5. **Policy Versioning**

Privacy policy is **APPROVED** (`legalPublicationStatus = "approved"`), dated "22 July 2026".

#### Option A: Fixed version string

- Version: `"1.0.0"` (SemVer)
- **Pros**: Clear, machine-readable
- **Cons**: Manual bump on every change
- **Effort**: Low

#### Option B: Date-based version

- Version: `"2026-07-22"` (ISO date from policy)
- **Pros**: Clear, matches lastUpdated field
- **Cons**: Requires date parsing for comparison
- **Effort**: Low

#### Option C: Git commit SHA

- Version: `"abc123"` (short SHA at deploy time)
- **Pros**: Automatic, traceable
- **Cons**: Opaque to users
- **Effort**: Medium

**Recommendation: Option B (date-based)**

- Use `"2026-07-22"` from `policy.lastUpdated`
- Easy for legal team to understand
- Backend already stores `policyVersion` as string, no format requirement
- On policy update: bump date in i18n, old consents remain valid (append-only)

**Version constant location**: `apps/web/marketing/src/legal/policy-version.ts`

```typescript
export const PRIVACY_POLICY_VERSION = "2026-07-22"
export const COOKIE_POLICY_VERSION = "2026-07-22" // when cookie page is approved
```

---

### 6. **Modal UX**

Marketing site uses Nothing-inspired dark theme (black background, subtle borders, monospace
accents). App uses shadcn-vue reka-nova preset (Nothing-inspired).

#### Option A: Astro component (marketing-only)

- **Pros**: No framework overhead, SSR-friendly
- **Cons**: Reimplements modal logic, no shared primitives with app
- **Effort**: Medium

#### Option B: Web Component (shared across marketing + app)

- **Pros**: Reusable, framework-agnostic
- **Cons**: Shadow DOM style isolation issues, overkill for one component
- **Effort**: High

#### Option C: Duplicate components (Astro for marketing, Vue for app)

- **Pros**: Native to each stack, leverage existing primitives
- **Cons**: Duplication, style drift risk
- **Effort**: Low

**Recommendation: Option C (duplicate components)**

- Marketing: custom Astro component with Nothing theme styles (consistent with
  `LegalPolicyUnavailable.astro`)
- App: shadcn-vue Dialog component (already available, consistent with app UI)
- Share i18n copy via shared JSON or duplicated i18n files (acceptable for banner copy)

**Marketing banner design** (Nothing-inspired):

- Fixed bottom banner (not modal overlay, less intrusive)
- Black background (`bg-black`), subtle top border
- Font: Geist Sans (body), Geist Mono (buttons)
- Buttons: "Accept All" (primary), "Reject Non-Essential" (secondary), "Manage Preferences" (
  tertiary)
- Preferences panel: slide-up overlay with category toggles

**App banner design** (shadcn-vue):

- Sheet component (drawer from bottom on mobile, dialog on desktop)
- Theme: reka-nova (matches app theme)
- Same button hierarchy as marketing

---

### 7. **Script Blocking Mechanism**

Ahrefs Analytics currently loads via Partytown (`type="text/partytown"`).

#### Option A: Conditional render (Astro)

```astro
{consentGiven && AHREFS_ANALYTICS_KEY && (
  <script type="text/partytown" src="..." />
)}
```

- **Pros**: Clean, no DOM manipulation
- **Cons**: Requires server state (can't read localStorage in Astro build)
- **Effort**: Low (if we accept always-render-banner approach)

#### Option B: Dynamic script injection (client-side JS)

```js
if (hasConsent('analytics.ahrefs')) {
  const script = document.createElement('script')
  script.src = 'https://analytics.ahrefs.com/analytics.js'
  script.dataset.key = AHREFS_ANALYTICS_KEY
  await import('partytown').then(/* party time inject */)
}
```

- **Pros**: Fully client-driven, reads localStorage
- **Cons**: Partytown integration is complex, loses `type="text/partytown"` SSR benefit
- **Effort**: High

#### Option C: Hybrid (inline script checks localStorage, conditionally injects)

```astro
<script>
  if (localStorage.getItem('consent')?.includes('analytics.ahrefs')) {
    const s = document.createElement('script')
    s.type = 'text/partytown'
    s.src = '...'
    document.head.appendChild(s)
  }
</script>
```

- **Pros**: Works in SSR, reads localStorage, simple
- **Cons**: Inline script (CSP nonce needed if strict CSP enabled)
- **Effort**: Low

**Recommendation: Option C (hybrid inline script)**

- Add inline `<script>` in `<head>` (or before `</body>`)
- Check `localStorage.getItem('pt-consent')` for `"analytics.ahrefs"`
- Dynamically append Partytown script if consent granted
- CSP nonce: Astro doesn't have built-in nonce support yet, but Profile Tailors doesn't use strict
  CSP currently (no `script-src 'nonce-...'` in codebase)

**Learned**: Partytown script injection is tricky — simpler to conditionally append the `<script>`
tag directly rather than importing Partytown runtime.

---

### 8. **E2E Testing**

No E2E specs exist currently (empty directory). Playwright is configured (`just frontend-test-e2e`).

#### Option A: No E2E (manual testing only)

- **Effort**: None
- **Risk**: High (consent flow is release blocker)

#### Option B: Basic happy path

- Test: accept all, reject all, verify analytics loaded/not loaded
- **Effort**: Low

#### Option C: Comprehensive (banner + localStorage + backend sync)

- Test: banner display, toggle interactions, localStorage persistence, backend API calls, DNT/GPC,
  cross-device sync
- **Effort**: High

**Recommendation: Option B (basic happy path) for MVP**

- `e2e/specs/consent-banner.spec.ts`
    - Test: banner shows on first visit
    - Test: "Accept All" → analytics script loads → localStorage saved
    - Test: "Reject Non-Essential" → analytics script does NOT load → localStorage saved
    - Test: banner does not show on second visit (localStorage persists)
    - Test: "Manage Preferences" → toggle analytics OFF → save → analytics does NOT load

**Future (post-MVP)**: add authenticated user backend sync tests, DNT/GPC tests, cross-device sync
tests

---

## Technical Decisions with Rationale

### 1. Geolocation: Show banner to everyone (Option A)

**Rationale**: MVP is for EU launch, over-compliance is safer than false negatives, avoids
deployment-specific logic.

### 2. Cookie Categories: 4 categories (necessary, analytics, preferences, marketing)

**Rationale**: Matches GDPR requirements, clear taxonomy, extensible for future services.
**Purpose strings**: `{category}.{service}` convention (e.g. `analytics.ahrefs`, `marketing.emails`)

### 3. Storage: Hybrid (localStorage + lazy backend sync) (Option C)

**Rationale**: Fast initial load, cross-device sync for authenticated users, GDPR audit trail, works
for anonymous users.
**SubjectReference**:

- Anonymous: `SubjectReference.anonymous(localStorage 'consent-id')`
- Waitlist: `SubjectReference.anonymous(sha256(email))`
- Authenticated: `SubjectReference.user(userId)`

### 4. DNT/GPC: Respect as default, allow override (Option C)

**Rationale**: Privacy-first, honors user intent, allows informed override.
**Implementation**: if `navigator.globalPrivacyControl === true` or `navigator.doNotTrack === "1"`,
default analytics toggle to OFF.

### 5. Policy Version: Date-based `"2026-07-22"` (Option B)

**Rationale**: Clear for legal team, matches `lastUpdated` field, no SemVer maintenance.
**Location**: `apps/web/marketing/src/legal/policy-version.ts`

### 6. Modal UX: Duplicate components (Astro for marketing, Vue for app) (Option C)

**Rationale**: Native to each stack, leverage existing primitives (shadcn-vue Dialog), acceptable
duplication.
**Marketing**: Fixed bottom banner (Nothing theme)
**App**: Sheet component (reka-nova preset)

### 7. Script Blocking: Inline script checks localStorage (Option C)

**Rationale**: Works in SSR, reads localStorage, simple implementation, no Partytown runtime
complexity.

### 8. E2E Testing: Basic happy path (Option B)

**Rationale**: MVP coverage, release blocker validation, defer comprehensive tests to post-MVP.

---

## Open Questions

### 1. **Policy version handling for updates**

**Question**: When privacy policy is updated (e.g. new data processor added), should we:

- A) Invalidate all existing consents (force re-consent on next visit)?
- B) Grandfather existing consents (only new users see new policy)?
- C) Show a "Policy Updated" banner to existing users, but don't block?

**Impact**: GDPR compliance, UX friction

**Recommendation needed from**: Legal/Product

---

### 2. **Waitlist consent sync**

**Question**: When a user joins the waitlist (`WaitlistConsent`), should we sync their marketing
site localStorage consent to backend immediately?

**Current behavior**: `GovernanceWaitlistConsentRecorder` records `purpose = "marketing.emails"`
with `SubjectReference.anonymous(sha256(email))` on waitlist join.

**New behavior needed**: Should we also record `analytics.ahrefs` consent at waitlist join time if
they accepted analytics?

**Recommendation**: YES — sync both `marketing.emails` (waitlist checkbox) and `analytics.ahrefs` (
banner consent) when email is submitted. This creates audit trail for anonymous users.

---

### 3. **ConsentType for analytics**

**Question**: Should analytics consent be recorded as `ConsentType.CONSENT` or
`ConsentType.LEGITIMATE_INTEREST`?

**Legal basis**:

- GDPR Art. 6.1.a: Consent — requires explicit opt-in, withdrawable
- GDPR Art. 6.1.f: Legitimate interest — requires balancing test, still withdrawable

Ahrefs is "cookieless" (no cookies, uses client-side storage only) and aggregates traffic data. This
could qualify for legitimate interest, but conservative approach is **CONSENT**.

**Recommendation**: `ConsentType.CONSENT` for analytics (safest, matches user expectation of "
Accept/Reject" banner).

**Implication**: Preferences (theme, sidebar) could use `LEGITIMATE_INTEREST` since they're
operationally necessary for UX.

---

### 4. **Backend API authentication for anonymous consent**

**Question**: `/api/governance/consent` currently requires workspace context (see `workspaceId` in
ConsentRecord). How do we record consent for anonymous marketing site visitors?

**Current endpoint**: POST `/api/governance/consent` expects `RecordConsentRequest` with
`subjectKind`, `subjectValue`, `consentType`, `purpose`, `policyVersion`, `source`, `locale`.

**Issue**: No workspace exists for anonymous users. Backend enforces `workspaceId.isNotBlank()`.

**Options**:

- A) Use a special "public" workspace ID (e.g. `"workspace-public"`) for anonymous consents
- B) Create a separate endpoint `/api/public/consent` that doesn't require workspace
- C) Don't sync anonymous consents to backend at all (localStorage only)

**Recommendation**: **Option B** — create `/api/public/consent` endpoint that accepts
`SubjectReference.anonymous(id)` and uses a synthetic workspace ID internally (e.g. `"public"`).
This maintains backend consistency without exposing internal workspace concept.

**Work needed**: New controller endpoint, or modify existing controller to accept optional workspace
context.

---

### 5. **Preferences category: opt-in or opt-out?**

**Question**: Should "Preferences" (theme, sidebar, locale) default to ON or OFF?

**Legal analysis**:

- These are not tracking cookies — they're operationally necessary for UX
- GDPR Recital 47: cookies "strictly necessary for the functionality requested by the user" don't
  need consent
- Theme/locale/sidebar are user-requested functionality

**Options**:

- A) Default ON, no toggle (treat as "strictly necessary")
- B) Default ON, opt-out toggle (legitimate interest)
- C) Default OFF, opt-in toggle (consent)

**Recommendation**: **Option A** — don't show preferences in banner at all, treat as strictly
necessary. Only show analytics and marketing toggles.

**Rationale**: Theme/sidebar preferences are not tracking, don't leave the device, and are necessary
for requested functionality. Over-compliance risks poor UX.

---

### 6. **CSP nonce for inline script**

**Question**: Should we add CSP `script-src` nonce support for the inline consent-checking script?

**Current state**: No strict CSP policy exists (no `Content-Security-Policy` header in codebase).

**Options**:

- A) No CSP (current state, accept inline scripts)
- B) Add CSP with nonce (requires middleware, nonce generation, HTML injection)

**Recommendation**: **Option A for MVP** — no CSP yet. Add CSP in post-MVP security hardening phase.

**Rationale**: CSP is not a release blocker, inline script is isolated and auditable, MVP focus is
consent functionality.

---

## Risks

### High Priority

1. **Anonymous consent backend sync** — Current `/api/governance/consent` requires workspace
   context, but anonymous marketing visitors don't have workspaces. Need new endpoint or workspace
   ID convention.
    - **Mitigation**: Create `/api/public/consent` endpoint with synthetic workspace ID.

2. **Policy version drift** — Frontend hardcodes `"2026-07-22"`, but backend has no validation
   against actual policy. If policy updates and version changes, old consents may be invalid.
    - **Mitigation**: Add policy version constant, reference from i18n, update in one place.

3. **localStorage clearing** — Users who clear localStorage lose consent preferences, see banner
   again. For anonymous users, no recovery path.
    - **Mitigation**: Acceptable for MVP (standard web behavior). Post-MVP: consider server-side
      fingerprinting (IP + User-Agent hash) as fallback.

### Medium Priority

4. **DNT/GPC adoption** — Only ~5% of users have DNT/GPC enabled, so most users will see banner
   regardless. Risk of false positives (Tor/VPN users).
    - **Mitigation**: Allow manual override, log `source = "gpc-default"` for audit.

5. **Script loading race condition** — If user accepts consent, script injection happens
   immediately. If page navigates before script loads, analytics miss first pageview.
    - **Mitigation**: Acceptable for MVP (analytics are best-effort). Post-MVP: preload hint or
      Service Worker caching.

6. **Cross-device consent sync** — Authenticated users may accept consent on desktop, but mobile
   localStorage is empty. Backend has record, but frontend doesn't sync on page load.
    - **Mitigation**: On app login, fetch
      `/api/governance/consent?subjectKind=USER&subjectValue={userId}` and sync to localStorage.

### Low Priority

7. **i18n copy duplication** — Consent banner copy exists in both `en.ts` and `es.ts`. If copy
   changes, must update both.
    - **Mitigation**: Acceptable for MVP (standard i18n pattern). Post-MVP: consider shared JSON.

8. **Partytown compatibility** — Dynamic script injection may not work perfectly with Partytown's
   web worker isolation. Risk of analytics failure.
    - **Mitigation**: Test thoroughly in E2E. Fallback: load script without Partytown if injection
      fails.

---

## Ready for Proposal

**YES** — with clarifications on open questions.

### Blockers (need user decision before proposal):

1. **Anonymous consent backend sync** (Question 4) — Need decision on endpoint strategy.
    - **Recommended path**: Create `/api/public/consent` endpoint with synthetic workspace ID
      `"public"`.

2. **Preferences category** (Question 5) — Need decision on toggle visibility.
    - **Recommended path**: Don't show preferences toggle, treat as strictly necessary.

3. **Policy update handling** (Question 1) — Need decision on re-consent UX.
    - **Recommended path**: Grandfather existing consents (Option B), show unobtrusive "Policy
      Updated" notice on next visit (not blocking).

### Clarifications needed (can proceed with assumptions):

4. **Waitlist consent sync** (Question 2) — Assume YES, sync both `marketing.emails` and
   `analytics.ahrefs`.
5. **ConsentType for analytics** (Question 3) — Assume `CONSENT` (safest).
6. **CSP nonce** (Question 6) — Assume NO CSP for MVP.

---

## Next Steps for Orchestrator

1. Present open questions to user, get decisions on blockers (Questions 1, 4, 5).
2. Once decisions made, proceed to **sdd-propose** phase.
3. Proposal should include:
    - Frontend components: `ConsentBanner.astro`, `ConsentPreferences.astro`, `consent-storage.ts`
    - Backend endpoint: `/api/public/consent` (if needed)
    - E2E specs: `consent-banner.spec.ts`
    - i18n updates: banner copy in `en.ts` and `es.ts`
    - Analytics conditional loading: update `Analytics.astro`
    - Policy version constants: `policy-version.ts`
