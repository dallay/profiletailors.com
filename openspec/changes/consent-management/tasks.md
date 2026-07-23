# Tasks: Frontend Consent Management

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 1,800-2,200 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1: Shared + Marketing → PR 2: App + Integration |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

**PR 1 Status**: ✅ COMPLETE - All 16 tasks implemented (TASK-001 to TASK-016)

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Shared foundation + Marketing site consent | PR 1 | Standalone deliverable: types, validation, inline script, marketing banner, E2E tests (4 scenarios) |
| 2 | App consent + backend sync + final integration | PR 2 | Depends on PR 1; adds Vue components, Pinia store, backend sync, app E2E tests (3 scenarios) |

---

## Phase 1: Shared Foundation

- [x] **TASK-001**: Create ConsentReceipt TypeScript types
  - **File(s)**: `shared/types/consent.ts`
  - **Done when**: 
    - `ConsentReceipt` interface matches design spec (section 3.1)
    - `CURRENT_CONSENT_VERSION`, `CURRENT_POLICY_VERSION`, `CONSENT_STORAGE_KEY`, `ANALYTICS_FLAG` constants exported
    - Types compile without errors (`pnpm typecheck`)
  - **Dependencies**: None
  - **Verification**: `pnpm typecheck` passes ✅

- [x] **TASK-002**: Write ConsentReceipt validation schema with Zod
  - **File(s)**: `shared/validation/consent.schema.ts`
  - **Done when**: 
    - `consentReceiptSchema` validates all fields per design section 3.2
    - `validateReceipt()` helper returns null for invalid receipts
    - policyVersion regex enforces YYYY-MM-DD format
  - **Dependencies**: TASK-001
  - **Verification**: Types compile, schema exports correctly ✅

- [x] **TASK-003**: Write unit tests for Zod validation schema
  - **File(s)**: `shared/validation/consent.schema.test.ts`
  - **Done when**: 
    - Tests cover: valid receipt, wrong consentVersion, invalid policyVersion format, missing fields
    - All tests pass (`just frontend-test`)
  - **Dependencies**: TASK-002
  - **Verification**: `just frontend-test` passes with 100% schema coverage ✅

- [x] **TASK-004**: Create DNT/GPC detection utility
  - **File(s)**: `shared/utils/detect-privacy-signals.ts`
  - **Done when**: 
    - `detectPrivacySignals()` returns `{ dnt, gpc, hasSignal }` object per design section 5.1
    - `getDefaultAnalyticsState()` returns false when signals detected
    - Handles undefined navigator (SSR-safe)
  - **Dependencies**: None
  - **Verification**: Types compile, utility exports correctly ✅

- [x] **TASK-005**: Write unit tests for privacy signals detection
  - **File(s)**: `shared/utils/detect-privacy-signals.test.ts`
  - **Done when**: 
    - Tests cover: no signals, DNT=1, DNT=yes, GPC=true, both signals, SSR scenario
    - All tests pass
  - **Dependencies**: TASK-004
  - **Verification**: `just frontend-test` passes with 100% utility coverage ✅

---

## Phase 2: Marketing Site (Astro)

- [x] **TASK-006**: Add consent constants
  - **File(s)**: `apps/web/marketing/src/constants/consent.ts`
  - **Done when**: 
    - `CURRENT_CONSENT_VERSION = 1`, `CURRENT_POLICY_VERSION = "2026-07-23"`
    - `PT_CONSENT_KEY = "pt-consent"` exported
  - **Dependencies**: None
  - **Verification**: Constants compile and export correctly ✅

- [x] **TASK-007**: Create inline consent detection script
  - **File(s)**: `apps/web/marketing/src/components/consent/ConsentScript.astro`
  - **Done when**: 
    - Inline script matches design section 2.1 (226 lines example)
    - Sets `window.__PT_CONSENT_ANALYTICS` flag synchronously
    - Uses `is:inline` attribute (no async/defer)
    - Validates receipt with inline schema check matching Zod rules
    - Dispatches `consentReady` custom event
  - **Dependencies**: TASK-001, TASK-002 (for schema reference)
  - **Verification**: Script compiles in Astro build ✅

- [x] **TASK-008**: Create ConsentBanner.astro component
  - **File(s)**: `apps/web/marketing/src/components/consent/ConsentBanner.astro`
  - **Done when**: 
    - UI matches design section 2.1 (lines 106-223)
    - Shows Necessary (disabled) + Analytics (toggle) categories
    - Three buttons: Accept All, Reject All, Save Preferences (equal prominence)
    - Uses `data-consent-*` attributes for test selectors
    - Hidden by default with `hidden` attribute
  - **Dependencies**: TASK-001
  - **Verification**: Component renders in Astro dev server ✅

- [x] **TASK-009**: Add ConsentBanner equal prominence styles
  - **File(s)**: `apps/web/marketing/src/components/consent/ConsentBanner.astro` (inline)
  - **Done when**: 
    - All three buttons have identical flex basis, padding, font-weight per design section 7.3
    - Uses CSS variables for theme integration (Nothing design)
    - Toggle switch styled with accessible focus states
    - Banner positioned fixed at bottom with z-index below modals
  - **Dependencies**: TASK-007
  - **Verification**: Visual inspection in browser shows equal prominence ✅

- [x] **TASK-010**: Create consent banner client-side bridge script
  - **File(s)**: `apps/web/marketing/src/components/consent/ConsentBanner.astro` (inline script)
  - **Done when**: 
    - `initConsentBanner()` function attaches event listeners to buttons
    - Saves consent to localStorage using shared types
    - Updates toggle state on click
    - Hides banner after save
    - Reloads page after consent change (to re-run inline script)
  - **Dependencies**: TASK-001, TASK-002
  - **Verification**: Manual test: click buttons → localStorage updates → page reloads ✅

- [x] **TASK-011**: Add CookieSettingsLink component
  - **File(s)**: `apps/web/marketing/src/components/consent/CookieSettingsLink.astro`
  - **Done when**: 
    - Footer link to re-open banner
    - Client-side script to show banner
  - **Dependencies**: TASK-009
  - **Verification**: Component renders, clicking re-opens banner ✅

- [x] **TASK-012**: Add i18n translations for consent (marketing EN)
  - **File(s)**: `apps/web/marketing/src/i18n/en.ts`
  - **Done when**: 
    - EN keys match design section 7.2
    - Keys include: banner heading/description, category labels/descriptions, action buttons, footer link
    - Integrated with existing i18n system
  - **Dependencies**: None
  - **Verification**: Translations load in EN locale ✅

- [x] **TASK-013**: Add i18n translations for consent (marketing ES)
  - **File(s)**: `apps/web/marketing/src/i18n/es.ts`
  - **Done when**: 
    - ES keys match design section 7.2
    - Keys include: banner heading/description, category labels/descriptions, action buttons, footer link
    - Integrated with existing i18n system
  - **Dependencies**: TASK-012
  - **Verification**: Translations load in ES locale ✅

- [x] **TASK-014**: Modify Analytics.astro for conditional loading
  - **File(s)**: `apps/web/marketing/src/components/Analytics.astro`
  - **Done when**: 
    - Checks `window.__PT_CONSENT_ANALYTICS` flag before rendering Ahrefs script
    - Only loads Ahrefs when flag === true
    - Maintains existing Partytown integration
    - Matches design section 4.2
  - **Dependencies**: TASK-006
  - **Verification**: Manual test with/without consent → Ahrefs loads conditionally ✅

- [x] **TASK-015**: Integrate ConsentScript into Layout.astro
  - **File(s)**: `apps/web/marketing/src/layouts/Layout.astro`
  - **Done when**: 
    - `<ConsentScript />` added in `<head>` before any other scripts
    - `<ConsentBanner />` added at end of `<body>`
    - Both components imported
  - **Dependencies**: TASK-006, TASK-007
  - **Verification**: View page source → inline script appears first in head ✅

- [x] **TASK-016**: Write E2E tests for consent scenarios
  - **File(s)**: `apps/web/marketing/e2e/consent.spec.ts`
  - **Done when**: 
    - TASK-014: Test navigates to `/`, clicks Accept All, verifies localStorage receipt has `analytics: true`, reloads page and confirms Ahrefs script loads, banner hidden after reload
    - TASK-015: Test clicks Reject All, verifies localStorage receipt has `analytics: false`, reloads and confirms Ahrefs never loads
    - TASK-016: Test sets `navigator.doNotTrack = '1'` via `addInitScript`, verifies analytics toggle defaults to OFF, clicks Accept All and verifies user can override
  - **Dependencies**: TASK-007, TASK-009, TASK-011, TASK-014, TASK-015
  - **Verification**: `just frontend-test-e2e` passes for consent scenarios ✅

---

## Phase 3: App (Vue + shadcn-vue)

- [ ] **TASK-017**: Create Pinia consent store
  - **File(s)**: `apps/web/app/src/modules/settings/infrastructure/consent.store.ts`
  - **Done when**: 
    - Store matches design section 3.3
    - Actions: `loadFromStorage()`, `saveConsent()`, `syncToBackend()`, `openSettings()`, `closeSettings()`
    - Getters: `hasValidConsent`, `analyticsEnabled`
    - Uses shared types and validation from Phase 1
    - Backend sync only for authenticated users (`useAuthStore().isAuthenticated`)
  - **Dependencies**: TASK-001, TASK-002
  - **Verification**: Store compiles, exports correctly

- [ ] **TASK-018**: Write unit tests for consent store
  - **File(s)**: `apps/web/app/src/modules/settings/infrastructure/consent.store.test.ts`
  - **Done when**: 
    - Tests cover: loadFromStorage (valid/invalid), saveConsent, version mismatch, DNT detection
    - Mock localStorage and auth store
    - All tests pass
  - **Dependencies**: TASK-017
  - **Verification**: `just frontend-test` passes with store coverage

- [ ] **TASK-019**: Create ConsentBanner.vue dialog component
  - **File(s)**: `apps/web/app/src/components/consent/ConsentBanner.vue`
  - **Done when**: 
    - Uses shadcn-vue Dialog, Switch, Button components
    - Matches design section 2.2 (lines 350-496)
    - Shows when `!hasValidConsent || forceOpen`
    - Three actions: Accept All, Reject All, Save Preferences
    - Calls `consentStore.saveConsent()` with correct parameters
  - **Dependencies**: TASK-017
  - **Verification**: Component renders in app dev server

- [ ] **TASK-020**: Create CookieSettings.vue panel component
  - **File(s)**: `apps/web/app/src/components/consent/CookieSettings.vue`
  - **Done when**: 
    - Standalone settings panel view (not dialog)
    - Shows current consent state from store
    - Allows toggling analytics and saving
    - Accessible via footer link
  - **Dependencies**: TASK-017
  - **Verification**: Component renders, can toggle and save

- [ ] **TASK-021**: Create useConsent composable hook
  - **File(s)**: `apps/web/app/src/components/consent/useConsent.ts`
  - **Done when**: 
    - Wraps common consent store operations
    - Returns reactive state and action methods
    - Simplifies component usage
  - **Dependencies**: TASK-017
  - **Verification**: Composable exports correctly, usable in components

- [ ] **TASK-022**: Add i18n translations for consent (app)
  - **File(s)**: 
    - `apps/web/app/src/shared/i18n/locales/en/consent.ts`
    - `apps/web/app/src/shared/i18n/locales/es/consent.ts`
  - **Done when**: 
    - EN + ES keys match design section 7.2 (identical to marketing)
    - Integrated with app i18n system
  - **Dependencies**: None
  - **Verification**: Translations load in app EN and ES locales

- [ ] **TASK-023**: Integrate ConsentBanner.vue into App.vue
  - **File(s)**: `apps/web/app/src/App.vue`
  - **Done when**: 
    - `<ConsentBanner />` component added to root
    - Renders on initial load if no valid consent
    - Does not block UI (positioned as overlay)
  - **Dependencies**: TASK-019, TASK-022
  - **Verification**: App loads, banner shows when localStorage empty

- [ ] **TASK-024**: Add cookie settings link to app footer
  - **File(s)**: `apps/web/app/src/components/layout/Footer.vue` (or equivalent)
  - **Done when**: 
    - Footer has "Cookie Settings" link
    - Clicking link calls `consentStore.openSettings()` to re-open banner
    - Link styled consistently with other footer links
  - **Dependencies**: TASK-017, TASK-023
  - **Verification**: Click footer link → banner reopens

- [ ] **TASK-025**: Implement backend sync error handling
  - **File(s)**: `apps/web/app/src/modules/settings/infrastructure/consent.store.ts` (modify)
  - **Done when**: 
    - `syncToBackend()` catches errors per design section 6.3
    - Sets `syncError` state on failure
    - Shows non-blocking toast/warning to user
    - localStorage save always succeeds (sync is optional)
  - **Dependencies**: TASK-017
  - **Verification**: Simulate network failure → localStorage saves, warning shown

- [ ] **TASK-026**: Write E2E test — App accept flow (scenario 5)
  - **File(s)**: `apps/web/app/e2e/consent.spec.ts`
  - **Done when**: 
    - Test navigates to app, clicks Accept All in dialog
    - Verifies localStorage and store state updated
    - Banner hidden after save
  - **Dependencies**: TASK-019, TASK-023
  - **Verification**: `just frontend-test-e2e` passes for this scenario

- [ ] **TASK-027**: Write E2E test — Withdrawal via settings (scenario 6)
  - **File(s)**: `apps/web/app/e2e/consent.spec.ts` (same file)
  - **Done when**: 
    - Test accepts consent first
    - Opens cookie settings via footer link
    - Toggles analytics off, saves
    - Verifies localStorage updated with `source: 'settings'`
  - **Dependencies**: TASK-024, TASK-026
  - **Verification**: `just frontend-test-e2e` passes for this scenario

- [ ] **TASK-028**: Write E2E test — Version upgrade re-consent (scenario 7)
  - **File(s)**: `apps/web/app/e2e/consent.spec.ts` (same file)
  - **Done when**: 
    - Test seeds localStorage with `consentVersion: 0` (outdated)
    - Navigates to app
    - Verifies banner shown despite existing receipt
  - **Dependencies**: TASK-026
  - **Verification**: `just frontend-test-e2e` passes for this scenario

---

## Phase 4: Integration & Cross-Surface Testing

- [ ] **TASK-029**: Add cookie settings link to marketing footer
  - **File(s)**: `apps/web/marketing/src/components/Footer.astro` (or equivalent)
  - **Done when**: 
    - Footer has "Cookie Settings" link
    - Clicking link re-shows banner via client script
    - Link uses i18n key `consent.footer.cookieSettings`
  - **Dependencies**: TASK-009, TASK-010
  - **Verification**: Click footer link → banner reopens

- [ ] **TASK-030**: Create E2E test utilities
  - **File(s)**: `apps/web/marketing/e2e/utils/consent.ts` or `shared/test-utils/consent.ts`
  - **Done when**: 
    - Helpers: `mockPrivacySignals()`, `setConsentReceipt()`, `clearConsent()` per design section 8.3
    - Reusable across marketing and app E2E tests
  - **Dependencies**: TASK-001
  - **Verification**: Utilities importable in E2E tests

- [ ] **TASK-031**: Verify equal prominence styling (manual QA)
  - **File(s)**: N/A (visual verification)
  - **Done when**: 
    - All three buttons (Accept/Reject/Save) have identical size, color saturation, font weight
    - No dark patterns (primary button doesn't dominate)
    - Verified in both marketing and app surfaces
    - Screenshots captured for documentation
  - **Dependencies**: TASK-008, TASK-019
  - **Verification**: Manual inspection in Chrome + Safari, screenshots attached

- [ ] **TASK-032**: Performance check — Inline script size and CLS
  - **File(s)**: N/A (performance measurement)
  - **Done when**: 
    - Inline script minified size < 2KB per design section 9.1
    - CLS score remains 0 (banner doesn't cause layout shift)
    - Lighthouse performance score maintained
    - Use `just frontend-test-e2e` with performance audit
  - **Dependencies**: TASK-006, TASK-012
  - **Verification**: Lighthouse audit shows CLS=0, script size confirmed

---

## Phase 5: Documentation & Polish

- [ ] **TASK-033**: Update AGENTS.md with consent management context
  - **File(s)**: `AGENTS.md`
  - **Done when**: 
    - Documents consent flow architecture
    - Explains dual-surface approach (marketing vs app)
    - Notes backend sync only for authenticated users
    - Lists localStorage schema and key (`pt-consent`)
    - Adds to "Key Gotchas" section if applicable
  - **Dependencies**: All implementation tasks complete
  - **Verification**: Documentation reads clearly, covers key decisions

- [ ] **TASK-034**: Create CONSENT.md architectural documentation
  - **File(s)**: `docs/CONSENT.md` or `openspec/changes/consent-management/IMPLEMENTATION.md`
  - **Done when**: 
    - Documents: architecture, consent flow, version upgrade strategy, DNT/GPC handling
    - Includes diagrams from design (sequence diagrams)
    - Lists all E2E test scenarios
    - Provides troubleshooting guide (e.g., "banner not showing", "Ahrefs still loads")
  - **Dependencies**: TASK-033
  - **Verification**: Documentation renders correctly, team can follow it

- [ ] **TASK-035**: Verify BDD scenarios mapping (if applicable)
  - **File(s)**: `server/smp/src/test/resources/features/consent-*.feature` (optional)
  - **Done when**: 
    - If backend consent sync has BDD coverage, ensure feature files exist
    - Map E2E scenarios to BDD scenarios
    - Run `just backend-bdd-fast` to confirm no regressions
  - **Dependencies**: Backend consent endpoint exists
  - **Verification**: BDD scenarios pass or are documented as not applicable

---

## Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| Phase 1 | 5 | Shared foundation: types, validation, DNT detection |
| Phase 2 | 11 | Marketing site: inline script, banner, E2E tests |
| Phase 3 | 12 | App: Pinia store, Vue components, E2E tests |
| Phase 4 | 4 | Integration: footer links, test utilities, QA |
| Phase 5 | 3 | Documentation and polish |
| **Total** | **35** | |

### Implementation Order

1. **Phase 1 first** — Shared types and validation are foundation for both surfaces
2. **Phase 2 and 3 can partially overlap** — After Phase 1, marketing and app can be developed independently
3. **Phase 4** — Requires both surfaces complete for cross-testing
4. **Phase 5** — Final polish after all code complete

### Chained PR Strategy (if chosen)

**PR 1: Shared + Marketing** (~900-1,100 lines)
- Tasks: TASK-001 through TASK-016
- Deliverable: Marketing site consent fully functional with 4 E2E tests passing
- Base: main

**PR 2: App + Integration** (~900-1,100 lines)  
- Tasks: TASK-017 through TASK-035
- Deliverable: App consent, backend sync, cross-surface integration, 3 additional E2E tests
- Base: PR 1 branch (if stacked-to-main) or feature/consent-management tracker branch (if feature-branch-chain)

### Next Step

**User decision required**: Choose chain strategy before proceeding to `sdd-apply`:
- **stacked-to-main**: PR 1 merges to main, PR 2 targets main (fast iteration)
- **feature-branch-chain**: Both PRs accumulate in feature/consent-management, only tracker merges to main (rollback control)
- **size:exception**: Keep as single PR with maintainer approval (generated code exception)

After decision, ready for `sdd-apply` to implement first work unit.
