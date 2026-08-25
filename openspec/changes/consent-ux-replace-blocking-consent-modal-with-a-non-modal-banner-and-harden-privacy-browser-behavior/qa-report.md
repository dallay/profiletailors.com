# QA Report: DALLAY-579

## Status

**PASS WITH WARNINGS** — archive eligibility conditional on visible warnings below.

## Scope

Capability-driven acceptance QA for the change
`consent-ux-replace-blocking-consent-modal-with-a-non-modal-banner-and-harden-privacy-browser-behavior`.
Covered surfaces: app consent flow (`apps/web/app`). Marketing and backend untouched.

## Capabilities exercised

| Capability | Spec | Result |
|------------|------|--------|
| Non-modal first-level prompt (aside, no overlay, app interactable) | R1, R2, R8 | PASS |
| Inline Customize panel + Necessary always-on | R3 | PASS |
| Persistence contract (source/version/policy/region/timestamp/dnt/necessary) | R4, R6 | PASS |
| Backend sync failure does not block dismissal (local receipt kept, toast shown) | R6 | PASS |
| Browser-resilience structural mitigation (no Dialog/portal/overlay in banner path) | R7 | PASS (structural; manual matrix not run — see Warnings) |
| i18n EN/ES, keyboard semantics, visible focus | R5 | PASS at component layer |
| Stale receipt re-prompt path | R4 | PASS at component + e2e retain |
| DNT/GPC default analytics OFF while showing the prompt | R6 | PASS at store layer; no dedicated E2E scenario (warning) |

## Evidence — Commands

| Command | Result |
|---------|--------|
| `pnpm --filter app exec vitest run src/components/consent src/modules/settings/infrastructure/consent.store.test.ts src/layouts/AppShell.test.ts` | PASS (5 files, 49 tests) |
| `pnpm --filter app exec vitest run` (full app unit suite) | PASS (117 files, 1352 tests) |
| `pnpm --filter app type-check` | PASS |
| `just frontend-lint` | PASS (62 files) |

## Coverage details

### Non-modal presentation (R1, R2, R8)

- `ConsentBanner.vue` renders an inline `<aside data-testid="consent-banner">` with no Dialog
  imports, no portal, no overlay, no backdrop, no focus trap, no Escape handler, no close control.
- Visibility is `v-if="!store.hasValidConsent"`; no local `decided` ref.
- Component tests assert ASIDE semantics, no `dialog-overlay`, no `role=dialog`, no `aria-modal`.
- E2E `consent.spec.ts` `Accept All` scenario asserts `expectNoOverlay(page)` plus visible
  `cookie-settings-link` (TASK-026).

### Persistence (R4, R6)

- `ConsentBanner` and `CookieSettings` both call `saveConsent`; banner always uses
  `source: 'banner'`, settings always uses `'settings-panel'`.
- Store builds receipt with `consentVersion: 1`, `policyVersion: '2026-07-23'`, ISO timestamp,
  `region: 'EU'`, `necessary: true`, captured DNT/GPC signal.
- Sync-failure regression tests verify local receipt is preserved, `syncError` is set, and
  `toast.error('Consent saved locally, sync failed.')` fires.

### Browser resilience (R7)

- Structural removal: banner path contains zero dialog primitives, so the modal-overlay-paint
  failure class described in the proposal is structurally impossible.
- Manual Brave Shields ON/OFF × states A–D and Safari/WebKit matrix was **NOT run** in this
  environment (no browser harness available). Recommend a manual QA pass before merge.

### i18n (R5)

- `consent.banner.customize` / `consent.banner.back` added to both `en` and `es` locale files.
- Existing localized copy unchanged.

### Settings panel & store API (R3, design §6)

- `CookieSettings.vue` continues to render as a Dialog and uses `useConsent('settings-panel')`.
- Store no longer exposes `forceOpen`, `openSettings`, or `closeSettings`; no production caller.

## Defects found during QA and fixed in this phase

| Severity | Description | Resolution |
|----------|-------------|------------|
| P0 | `AppShell.test.ts` regressed: removing the `useConsentStore` mock (tied to removed `openSettings`) caused 10 tests to fail with `getActivePinia() was called but there was no active Pinia`, because `CookieSettings.vue` (always mounted by AppShell) now reads `useConsentStore` through `useConsent()`. | Re-added `vi.mock('@modules/settings/infrastructure/consent.store', …)` with the minimal new API (`receipt`, `hasValidConsent`, `analyticsEnabled`, `saveConsent`). AppShell tests pass. |

## Warnings / gaps (non-blocking but visible)

1. **DNT/GPC E2E scenario still missing.** The store covers `dnt` capture and the rejection path
   for DNT, but no Playwright scenario asserts that when `mockPrivacySignals({dnt:true})` is
   active, the prompt still shows AND analytics defaults OFF AND Accept All overrides. This is
   the only spec scenario explicitly listed as outstanding in the design (TASK 4.3) and was not
   implemented in this pass.
2. **Full app E2E suite not executed in this pass.** Only targeted E2E assertions were reviewed
   (`consent.spec.ts` Accept All + `expectNoOverlay`). A full `pnpm --filter app test:e2e` run
   against the live dev servers is recommended before archive.
3. **Manual browser matrix not run.** Brave Shields ON/OFF × states A–D and Safari/WebKit coverage
   were not feasible in this environment.
4. **DNT default UI value (analytics toggle) is only validated at store layer.** The new banner
   inlines `analyticsEnabled = store.analyticsEnabled` on mount; `store.analyticsEnabled` falls
   through to `receipt.categories.analytics ?? false` and stays `false` while there is no valid
   receipt — which is the desired pre-prompt default. However, no component test asserts the
   initial toggle OFF when DNT/GPC is detected. Store test covers `dnt: true` capture only.

## Archive recommendation

Conditional. PASS WITH WARNINGS is policy-allowed only when warnings are visible. Recommend:

1. Add the dedicated DNT/GPC E2E scenario (`mockPrivacySignals({dnt:true})` → banner visible,
   analytics defaults OFF, Accept All overrides) — short Playwright test in
   `apps/web/app/e2e/specs/consent.spec.ts`.
2. Execute full app E2E suite once.
3. Run manual Brave Shields ON/OFF and Safari/WebKit matrix and record results.

After (1)–(3), re-run sdd-qa to lift the warnings and proceed to archive.
