# Tasks: Frontend Consent Management

## Review Workload Forecast

| Field                   | Value                                              |
|-------------------------|----------------------------------------------------|
| Estimated changed lines | 1,800-2,200                                        |
| 400-line budget risk    | High                                               |
| Chained PRs recommended | Yes                                                |
| Suggested split         | PR 1: Shared + Marketing → PR 2: App + Integration |
| Delivery strategy       | ask-on-risk                                        |
| Chain strategy          | pending                                            |

**PR 1 Status**: ✅ COMPLETE — TASK-001 to TASK-016 implemented
**PR 2 Status**: ✅ COMPLETE — TASK-017 to TASK-032 implemented (16/19), TASK-033–035 pending (Phase
5)

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                           | Likely PR | Notes                                                                                                               |
|------|------------------------------------------------|-----------|---------------------------------------------------------------------------------------------------------------------|
| 1    | Shared foundation + Marketing site consent     | PR 1 ✅    | Standalone deliverable: types, validation, inline script, marketing banner, E2E tests (4 scenarios). **Completed.** |
| 2    | App consent + backend sync + final integration | PR 2      | Depends on PR 1; adds Vue components, Pinia store, backend sync, app E2E tests (3 scenarios).                       |

---

## Phase 1: Shared Foundation ✅

- [x] **TASK-001**: Create `shared/types/consent.ts` — ConsentReceipt interface, version/policy
  constants, storage key, analytics flag
- [x] **TASK-002**: Create `shared/validation/consent.schema.ts` — Zod schema + `validateReceipt()`
  helper
- [x] **TASK-003**: Create `shared/validation/consent.schema.test.ts` — Unit tests: valid receipt,
  wrong consentVersion, invalid policyVersion, missing fields
- [x] **TASK-004**: Create `shared/utils/detect-privacy-signals.ts` — `detectPrivacySignals()`,
  `getDefaultAnalyticsState()`, SSR-safe
- [x] **TASK-005**: Create `shared/utils/detect-privacy-signals.test.ts` — Tests: no signals, DNT=1,
  DNT=yes, GPC=true, both, SSR

---

## Phase 2: Marketing Site (Astro) ✅

- [x] **TASK-006**: Create `apps/web/marketing/src/constants/consent.ts` — consent constants
- [x] **TASK-007**: Create `apps/web/marketing/src/components/consent/ConsentScript.astro` — inline
  synchronous `<head>` script, localStorage read + validation + flag setting + consentReady event
- [x] **TASK-008**: Create `apps/web/marketing/src/components/consent/ConsentBanner.astro` — banner
  UI with 2 categories (necessary disabled, analytics toggle), 3 equal-prominence buttons
- [x] **TASK-009**: Style ConsentBanner — equal prominence CSS, fixed bottom positioning, Nothing
  theme integration
- [x] **TASK-010**: Add client-side bridge script in ConsentBanner — `initConsentBanner()`, event
  listeners, localStorage save, page reload on change
- [x] **TASK-011**: Create `apps/web/marketing/src/components/consent/CookieSettingsLink.astro` —
  footer link to re-open banner
- [x] **TASK-012**: Add EN consent i18n keys to `apps/web/marketing/src/i18n/en.ts`
- [x] **TASK-013**: Add ES consent i18n keys to `apps/web/marketing/src/i18n/es.ts`
- [x] **TASK-014**: Modify `apps/web/marketing/src/components/Analytics.astro` — check
  `window.__PT_CONSENT_ANALYTICS` before conditional Ahrefs load
- [x] **TASK-015**: Integrate ConsentScript into `apps/web/marketing/src/layouts/Layout.astro` — add
  to `<head>`, add ConsentBanner to `<body>`
- [x] **TASK-016**: Create `apps/web/marketing/e2e/consent.spec.ts` — E2E: accept all, reject all,
  DNT enabled, GPC signal

---

## Phase 3: App (Vue + shadcn-vue)

- [x] **TASK-017**: Create `apps/web/app/src/modules/settings/infrastructure/consent.store.ts` —
  Pinia store: `loadFromStorage()`, `saveConsent()`, `syncToBackend()`, `openSettings()`,
  `closeSettings()`, getters `hasValidConsent`, `analyticsEnabled`
- [x] **TASK-018**: Create
  `apps/web/app/src/modules/settings/infrastructure/consent.store.test.ts` — Store unit tests: load
  valid/invalid, saveConsent, version mismatch, DNT detection (14 tests all pass)
- [x] **TASK-019**: Create `apps/web/app/src/components/consent/ConsentBanner.vue` — shadcn-vue
  Dialog with Switch/Button, accept/reject/save actions
- [x] **TASK-020**: Create `apps/web/app/src/components/consent/CookieSettings.vue` — standalone
  settings panel for granular withdrawal
- [x] **TASK-021**: Create `apps/web/app/src/components/consent/useConsent.ts` — composable wrapping
  consent store operations
- [x] **TASK-022**: Add `footer.cookieSettings` i18n key to EN + ES translation files
- [x] **TASK-023**: Integrate ConsentBanner.vue into app root (fix alias `@components` →
  `@/components`)
- [x] **TASK-024**: Add cookie settings link to app footer — `consentStore.openSettings()` on click
- [x] **TASK-025**: Implement backend sync error handling — `toast.error()` on sync failure,
  localStorage always saved
- [x] **TASK-026**: Create `apps/web/app/e2e/specs/consent.spec.ts` — E2E: accept all in app dialog
- [x] **TASK-027**: E2E: withdrawal via cookie settings — toggle off + save, verify
  `source: 'settings-panel'`
- [x] **TASK-028**: E2E: version upgrade re-consent — seed `consentVersion: 0`, verify banner shows

---

## Phase 4: Integration & Cross-Surface Testing

- [x] **TASK-029**: Add cookie settings link to marketing site footer — re-shows banner, uses i18n
  `consent.footer.cookieSettings`
- [x] **TASK-030**: Create shared E2E test utilities — `mockPrivacySignals()`,
  `setConsentReceipt()`, `clearConsent()`
- [x] **TASK-031**: Manual QA — verify equal prominence styling in marketing + app (see
  `qa-checklist.md`)
- [x] **TASK-032**: Performance check — inline script < 2KB, CLS = 0, Lighthouse score maintained (
  see `performance-check.md`)

---

## Phase 5: Documentation & Polish

- [x] **TASK-033**: Update `AGENTS.md` with consent management context and architecture notes
- [x] **TASK-034**: Create architectural documentation (localStorage schema, consent flow, version
  upgrade, DNT/GPC)
- [x] **TASK-035**: Verify BDD mapping (if backend consent endpoint has BDD coverage)

### BDD Gap — Consent API

**Finding:** No Cucumber BDD feature files exist for the consent governance API.

The backend consent API (`ConsentController` at `/api/governance/consent`) has
good unit/infrastructure test coverage:

- `ConsentControllerWebTest.kt` — WebFlux controller tests (POST record, POST withdraw, GET list,
  GET history, validation errors)
- `RecordWorkspaceConsentHandlerTest.kt` — Application handler tests
- `WithdrawWorkspaceConsentHandlerTest.kt` — Withdrawal logic tests
- `ConsentRecordModelsTest.kt` — Domain model tests
- `R2dbcConsentRepositoryTest.kt` — Repository integration tests

However, per the project's **BDD (Cucumber) — MANDATORY** policy (AGENTS.md),
every new backend feature/endpoint MUST include BDD scenarios. The consent API
is a governance feature that ships without BDD coverage.

**Suggested new task:** Create `governance-consent.feature` with:

```gherkin
@governance @smoke @fast
Feature: Governance consent
  Governance consumers should be able to record and withdraw consent.

  Scenario: Record workspace consent
    Given an authenticated user exists
    When the client records consent for the workspace
    Then the consent response should indicate CREATED status
    And the consent record should be persisted

  Scenario: Withdraw workspace consent
    Given the workspace has an active consent record
    When the client withdraws the consent
    Then the consent record should be WITHDRAWN

  Scenario: List workspace consent
    Given the workspace has consent records
    When the client lists workspace consent
    Then the response should contain the consent records

  Scenario: Get consent history
    Given a subject has consent records
    When the client retrieves the consent history
    Then the response should contain lifecycle events
```

**Step definitions** should follow the `*BddSteps.kt` pattern and leverage the
existing `BddDatabaseSupport` and `WebTestClient` infrastructure.

---

## Summary

| Phase     | Tasks  | Focus                                                     |
|-----------|--------|-----------------------------------------------------------|
| Phase 1   | 5 ✅    | Shared foundation: types, validation, DNT detection       |
| Phase 2   | 11 ✅   | Marketing site: inline script, banner, E2E tests          |
| Phase 3   | 12 ✅   | App: Pinia store, Vue components, E2E tests               |
| Phase 4   | 4 ✅    | Integration: footer links, test utilities, QA, perf check |
| Phase 5   | 3 ✅    | Documentation and polish                                  |
| **Total** | **35** | **35 complete / 0 pending**                               |

### Implementation Order

1. **Phase 1** ✅ — Shared types and validation (foundation for both surfaces)
2. **Phase 2** ✅ — Marketing site (standalone deliverable)
3. **Phase 3** — App components (depends on Phase 1 shared types)
4. **Phase 4** — Cross-surface integration (depends on Phase 2 + 3)
5. **Phase 5** — Final polish after all code complete

### Chained PR Strategy

**PR 1** (✅ COMPLETE): TASK-001–016 — Marketing site consent fully functional with 4 E2E tests —
base: main
**PR 2** (⏳ PENDING): TASK-017–035 — App consent, backend sync, cross-surface integration, 3
additional E2E tests — base: PR 1 branch or feature tracker

Chain strategy is still **pending** — user must choose between:

- **stacked-to-main**: PR 1 merged, PR 2 targets main (fast iteration)
- **feature-branch-chain**: Both PRs accumulate in feature branch, only tracker merges (rollback
  control)
- **size:exception**: Single PR with maintainer approval

### Next Step

**User decision required** before `sdd-apply` on PR 2: Choose chain strategy (stacked-to-main |
feature-branch-chain | size:exception). Delivery strategy is `ask-on-risk` — risk is High (
1,800–2,200 lines).
