# Proposal: Frontend Consent Management

## Intent

Profile Tailors must comply with GDPR/LOPD-GDD requirements for cookie consent before loading
analytics and marketing scripts. Currently, Ahrefs analytics loads unconditionally on the marketing
site, violating consent requirements. This change implements a consent banner (marketing site +
app), localStorage-based consent storage for anonymous visitors, and script blocking that respects
user choices. The implementation prioritizes over-compliance (show banner to everyone, no
geolocation detection) and treats functional preferences (dark mode, locale) as necessary rather
than requiring consent.

## Scope

### In Scope

- **Consent banner UI**: Astro component for marketing site, shadcn-vue component for app
- **localStorage consent storage**: Versioned receipt with `consentVersion`, `policyVersion`,
  timestamp, region, and category choices
- **Script blocking**: Inline check prevents Ahrefs load until consent granted
- **Two categories**: Necessary (always on), Analytics (opt-in)
- **DNT/GPC handling**: Respect Do Not Track and Global Privacy Control signals as default
  rejections
- **Granular withdrawal**: Users can revoke consent per category from settings
- **i18n**: English and Spanish UX copy
- **E2E tests**: Accept, reject, withdraw, policy-change, DNT/GPC scenarios

### Out of Scope

- Backend `/api/public/consent` API — deferred until workspace-less users need server-side receipts
- Preferences category toggle — functional preferences (dark mode, locale, sidebar) treated as
  necessary cookies per AEPD guidance
- Policy update notification UI — future feature when `consentVersion` changes
- Geolocation detection — over-compliance strategy shows banner to all visitors regardless of
  location
- CSP strict-dynamic mode — future hardening after consent flow stabilizes
- Backend sync for anonymous users — marketing visitors stay localStorage-only

## Capabilities

> Contract between proposal and specs phases. Research of `openspec/specs/` shows existing
> capabilities:
> - `user-settings` (app settings management)
> - `privacy-compliance` (backend consent domain)
    > These will be modified. No new capabilities introduced — consent UI is an extension of
    existing privacy-compliance domain.

### New Capabilities

None — consent management extends existing `privacy-compliance` capability.

### Modified Capabilities

- `privacy-compliance`: Existing backend consent domain gets frontend UI counterpart. Requirements
  change from backend-only to full-stack consent flow with localStorage fallback for anonymous
  users.

## Approach

### localStorage Schema

```typescript
interface ConsentReceipt {
  consentVersion: string;    // "1" — changes only when purposes/categories materially change
  policyVersion: string;     // "2026-07-23" — tracks privacy policy document version
  timestamp: string;         // ISO 8601
  region: string;            // "EU" (hardcoded until geolocation added)
  categories: {
    necessary: true;         // Always true, not toggleable
    analytics: boolean;
    marketing: boolean;
  };
  dnt: boolean;              // Was DNT/GPC active at consent time?
}
```

### Component Structure

- **Marketing (Astro)**: `ConsentBanner.astro` + inline script in `<head>` checks localStorage
  before `Analytics.astro` executes
- **App (Vue + shadcn-vue)**: `ConsentBanner.vue` + `ConsentSettings.vue` (for withdrawal),
  integrated with existing `usePrivacy` store
- **Shared copy**: i18n keys in `shared/i18n/` for banner text, category descriptions, legal notices

### Script Blocking Mechanism

1. Inline synchronous script in `<head>` reads `localStorage.getItem('pt-consent')`
2. If no receipt OR analytics rejected OR DNT/GPC detected → set
   `window.__PT_CONSENT_ANALYTICS = false`
3. `Analytics.astro` checks `window.__PT_CONSENT_ANALYTICS` before injecting Ahrefs
4. Partytown compatibility: Ensure inline script runs before Partytown proxy initialization

### DNT/GPC Detection

- Check `navigator.doNotTrack === "1"` and `navigator.globalPrivacyControl === true`
- If either is true: default all optional categories to `false`, show banner with pre-rejected state
- Document behavior in privacy policy and banner footer text
- E2E test: Mock `navigator` properties, verify analytics blocked

### i18n Strategy

- Extend `shared/i18n/` with consent-specific keys
- Marketing uses Astro's locale detection
- App uses Pinia `locale` store (already implemented)
- Equal prominence for accept/reject buttons in both languages

### Versioning Strategy

- **`consentVersion`**: Increments only when consent purposes/categories change materially (e.g.,
  adding "Advertising Personalization" category)
- **`policyVersion`**: Tracks privacy policy document version (date-based: `"2026-07-23"`)
- Non-material policy changes (typos, clarifications) → update `policyVersion`, keep
  `consentVersion` → consent remains valid
- Material changes (new purpose, new category) → update both → require new consent
- Seam for future: Add `PolicyUpdateNotice.vue` that compares stored `consentVersion` with current,
  shows modal if mismatch

## Affected Areas

| Area                                                | Impact   | Description                                                      |
|-----------------------------------------------------|----------|------------------------------------------------------------------|
| `apps/web/marketing/src/components/`                | New      | `ConsentBanner.astro`, `ConsentModal.astro`                      |
| `apps/web/marketing/src/layouts/Layout.astro`       | Modified | Add inline consent-check script in `<head>`                      |
| `apps/web/marketing/src/components/Analytics.astro` | Modified | Conditional Ahrefs load based on `window.__PT_CONSENT_ANALYTICS` |
| `apps/web/app/src/components/privacy/`              | New      | `ConsentBanner.vue`, `ConsentSettings.vue`                       |
| `apps/web/app/src/stores/privacy.ts`                | Modified | Add consent state, localStorage sync, withdrawal methods         |
| `shared/i18n/`                                      | Modified | Add consent banner copy (EN/ES)                                  |
| `apps/web/marketing/e2e/`                           | New      | Playwright tests for consent flow                                |
| `apps/web/app/e2e/`                                 | New      | Playwright tests for app consent flow                            |

## Risks

| Risk                                                                          | Likelihood | Mitigation                                                              |
|-------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------|
| localStorage cleared by user → consent lost, scripts blocked until re-consent | Medium     | Acceptable UX tradeoff; backend sync out of scope for anonymous users   |
| DNT/GPC false positives → analytics blocked for users who would consent       | Low        | Document behavior clearly; users can manually override in banner        |
| Partytown race condition → Ahrefs loads before inline script runs             | Medium     | Test execution order; consider moving inline script earlier in `<head>` |
| Consent banner blocks critical marketing site content (CLS)                   | Medium     | Lazy-load banner after LCP, position at bottom with `position: fixed`   |
| Policy version confusion → users re-prompted unnecessarily                    | Low        | Document versioning strategy in CONTEXT.md; code review checklist       |
| Marketing category unused (no marketing scripts yet)                          | High       | Acceptable; future-proof design; no cost to include toggle              |

## Rollback Plan

1. **Immediate**: Remove `ConsentBanner` imports from `Layout.astro` and `App.vue` root
2. **Restore analytics**: Revert `Analytics.astro` to unconditional Ahrefs load
3. **Clean localStorage**: Add temporary script to `localStorage.removeItem('pt-consent')` on next
   deploy
4. **Tests**: Disable E2E consent tests via `@skip` annotation, not deletion
5. **Duration**: Rollback takes < 5 minutes; no database changes needed (backend API untouched)

## Dependencies

- Partytown (already integrated for marketing site)
- shadcn-vue Dialog/Switch components (already available in app)
- Playwright (already configured for E2E tests)
- No new external dependencies

## Success Criteria

- [ ] Non-essential scripts (Ahrefs) blocked until user grants consent
- [ ] Accept and reject buttons have equal visual prominence (same size, color weight)
- [ ] Users can withdraw consent per category from app settings
- [ ] Consent receipt stores `consentVersion`, `policyVersion`, timestamp, region (EU), and category
  choices
- [ ] DNT/GPC signals respected: analytics/marketing default to rejected, behavior documented
- [ ] E2E tests cover: accept all, reject all, granular accept, withdraw, `consentVersion` change (
  requires new consent), `policyVersion` change (does NOT require new consent), DNT/GPC override
- [ ] Marketing site and app both implement consent flow with identical localStorage schema
- [ ] i18n: Banner copy available in English and Spanish with equivalent clarity

## Open Questions

1. **Policy version format**: Use date-based `"2026-07-23"` (readable, maps to deploy) or semantic
   `"1.0.0"` (standard versioning)?
    - **Recommendation**: Date-based. Maps directly to legal doc dates, simpler for non-engineers to
      audit.

2. **DNT/GPC + banner interaction**: Should we show banner at all if DNT/GPC detected, or silently
   block and hide banner?
    - **Recommendation**: Show banner with pre-rejected state. Transparency > magic; users may have
      DNT on by mistake.

3. **Marketing category utility**: No marketing scripts planned near-term. Include toggle now (
   future-proof) or add later (YAGNI)?
    - **Recommendation**: Include now. Cost is negligible (one extra toggle), avoids
      `consentVersion` bump later.

## Next Steps

1. **Spec phase**: Write delta specs defining consent banner behavior, localStorage schema, script
   blocking contract
2. **Design phase**: Technical design for component architecture, state management, E2E test
   strategy
3. **Tasks phase**: Break down into implementation tasks (banner UI, inline script, E2E tests, i18n)
4. **Apply phase**: Implement with TDD (E2E tests first)
