# Verification Report: consent-ux-replace-blocking-consent-modal-with-a-non-modal-banner-and-harden-privacy-browser-behavior

## Status

**FAIL** — the implementation is structurally aligned and focused checks pass, but required
browser-matrix evidence and the dedicated DNT/GPC UI scenario are absent. This report is technical
verification only; it does not grant operator or product acceptance.

## Target and provenance

- **Issue**: GitHub #677
- **Change**: `consent-ux-replace-blocking-consent-modal-with-a-non-modal-banner-and-harden-privacy-browser-behavior`
- **Artifact mode**: OpenSpec
- **Local worktree tested**: `860c37444adcfdebc50c66ca868889ee472681fc` (`HEAD`), clean apart from generated test/build output not tracked by git
- **Actual PR #775 remote head**: `6088384a688e97a725f9c7a5213ab9e71d386dca`
- **Important scope limit**: `860c3744` is one unpushed commit ahead of PR #775. All runtime commands below executed against local `HEAD`, not the remote PR head. The local commit is **not** currently in PR #775.

The local-only commit changes `ConsentBanner.vue`, its component test, `useConsent.ts`, the E2E
overlay helper, `proposal.md`, and `state.yaml`. It does not add the missing DNT/GPC scenario or
the Brave/Safari matrix.

## Completeness

| Metric | Value |
|---|---:|
| Tasks in `tasks.md` | 21 |
| Tasks marked complete | 12 |
| Tasks marked incomplete | 9 |

Incomplete tasks: **4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.1, 5.2, 5.3**. The core gaps are 4.2–4.6:
the consent E2E expansion, DNT/GPC E2E, full app E2E, and manual browser matrices remain
unchecked. The existing `state.yaml`, `qa-report.md`, and prior `verify-report.md` also explicitly
record those gaps.

## Build, test, and coverage evidence

| Command | Result | Evidence |
|---|---|---|
| `pnpm --filter app exec vitest run src/components/consent/ConsentBanner.spec.ts src/components/consent/CookieSettings.spec.ts src/components/consent/useConsent.spec.ts src/modules/settings/infrastructure/consent.store.test.ts src/layouts/AppShell.test.ts` | **PASS** | 5 files, 50 tests passed |
| `pnpm --filter app test:run` | **PASS on serial rerun** | 117 files, 1,353 tests passed |
| `pnpm exec playwright test -c e2e/playwright.config.ts specs/consent.spec.ts` | **PASS** | 9 tests passed across Chromium, Firefox, and Mobile Chrome |
| `pnpm --filter app type-check` | **PASS** | `vue-tsc --build` exited 0 |
| `pnpm --filter app build` | **PASS** | Vite production build exited 0; emitted only existing chunk-size warning |
| `pnpm --filter app lint` | **PASS with warnings** | Biome exited 0; six warnings are in unrelated privacy-store code |
| `just frontend-lint` | **PASS** | Marketing Biome check, 62 files; this recipe does not lint the app |
| Shared consent tests | **PASS** | 49 tests: validation, storage, DNT/GPC signals |
| Marketing consent/analytics tests | **PASS** | 29 tests: marketing banner, ConsentScript, analytics gating |
| Focused app coverage | **PASS against configured threshold** | 12.88% aggregate for the focused selection; configured threshold is 0%; consent store lines reported at 95.65% |

One initial parallel invocation of `pnpm --filter app test:run` exited non-zero because
`CookieSettings.spec.ts > shows the dialog when open prop is true` timed out at 5 seconds. The
single-worker/serial rerun passed all 1,353 tests, but the observed timeout is retained as a
suite-stability finding rather than silently discarded.

The repository-level `./gradlew build` was not run: this change is frontend-only, no backend files
changed, and the focused app production build/type-check are the relevant build evidence.

## Spec compliance matrix

Statuses mean: **PASS** = runtime test evidence covers the stated behavior; **PARTIAL** = only a
subset of the behavior is runtime-proven; **NOT EVIDENCED** = no passing runtime evidence found.

### OpenSpec scenarios

| Requirement / scenario | Status | Evidence |
|---|---|---|
| R1 — First visit shows a non-modal prompt | **PASS** | `ConsentBanner.spec.ts > shows a non-modal aside when consent is missing`; focused E2E TASK-026 shows `consent-banner` and `expectNoOverlay` passes |
| R1 — Prompt does not block interaction | **PARTIAL** | Local `860c3744` adds `ConsentBanner.spec.ts > keeps the app interactable while the banner is visible`; no E2E click on a real app navigation control while the banner is visible |
| R2 — All first-level actions available | **PASS** | Component test asserts Reject, Customize, and Accept controls exist; component action tests pass |
| R3 — Customize saves granular preference and keeps Necessary immutable | **PASS** | Component test expands inline panel, toggles analytics, saves, and asserts `source: 'banner'`; Necessary switch is disabled |
| R4 — Cookie settings reopens preferences without re-showing banner | **PARTIAL** | TASK-027 E2E opens the footer flow, exercises the real switches, saves `source: 'settings-panel'`; no explicit assertion of current-choice preselection or post-close banner state |
| R5 — Valid current receipt suppresses prompt | **PASS** | Component visibility test plus TASK-028 reload after saving v1 receipt |
| R5 — Stale/invalid receipt re-prompts | **PASS** | Store tests cover malformed JSON/outdated version; shared validation tests cover invalid shapes; TASK-028 covers outdated receipt E2E |
| R5 — Undecided state is not dismissible | **PASS** | Component Escape test passes with no receipt written; source has no close control or Escape handler |
| R6 — Reject persists false and keeps analytics disabled | **PARTIAL** | Component payload and store persistence tests pass; no app E2E assertion that the analytics runtime remains disabled after the first-level Reject action |
| R6 — Backend sync failure does not block dismissal | **PARTIAL** | Store tests prove local receipt, `syncError`, and toast survive a rejected API call; no integrated component/E2E test proves the banner closes and warning renders in the same flow |
| R7 — Privacy browser with no valid consent remains usable | **NOT EVIDENCED** | No Brave Shields or Safari/WebKit run; Playwright app config explicitly excludes WebKit and has no Brave project |
| R7 — Authenticated stale consent does not create blank overlay | **PARTIAL** | Authenticated Chromium/Firefox/Mobile E2E covers stale v0 re-consent; it does not run in Brave and does not assert the full stale flow has no blank overlay |
| R8 — Localized and keyboard-usable prompt | **PARTIAL** | EN/ES locale files and i18n key tests pass; no Spanish browser run, keyboard-tab/focus-visible E2E, or component focus assertion |
| R8 — No global overlay mounted | **PASS** | Component test asserts no `[data-slot="dialog-overlay"]`; `ConsentBanner.vue` has no Dialog, portal, overlay, backdrop, or focus-trap path |

### Issue #677 acceptance criteria

| # | Criterion | Status | Evidence / limitation |
|---:|---|---|---|
| 1 | First-time consent has no full-screen dark backdrop | **PASS** | Non-modal component test and TASK-026 E2E overlay assertion pass |
| 2 | Invalid/outdated re-consent uses the same non-modal presentation | **PASS** | Store invalidation + TASK-028 stale banner + non-modal component evidence |
| 3 | Accept, Reject, and Customize are immediately available | **PASS** | Component action-availability test passes |
| 4 | Necessary remains immutable/always enabled | **PASS** | Disabled Necessary switch component test and `necessary: true` store tests pass |
| 5 | Customize exposes analytics and Save | **PASS** | Inline Customize component test passes |
| 6 | Cookie settings reopens detailed preferences | **PARTIAL** | Real TASK-027 flow passes; lifecycle/preselection assertions are incomplete |
| 7 | Underlying app remains visible while prompt is open | **PARTIAL** | No overlay and sibling-control click are proven; no visual assertion or real navigation click |
| 8 | Missing/invisible consent content cannot disable the entire app | **PASS** structurally | Initial prompt has no global backdrop/portal; browser execution still lacks the required privacy matrix |
| 9 | Brave Shields ON does not produce black/empty viewport | **NOT EVIDENCED** | Manual Brave Shields ON run absent |
| 10 | Authenticated stale/missing receipt is explicitly tested in Brave | **PARTIAL** | Authenticated stale E2E exists in Chromium-family runs; Brave evidence absent |
| 11 | DNT/GPC defaults work without hiding/breaking UI | **PARTIAL** | Store/shared privacy-signal tests pass; `mockPrivacySignals` is defined but unused in app consent E2E; no UI default-off/Accept override scenario |
| 12 | Valid consent suppresses prompt on subsequent loads | **PASS** | Component test and TASK-028 reload assertion pass |
| 13 | Invalid/malformed/outdated receipt re-shows prompt | **PASS** | Store + shared validation runtime tests and outdated E2E pass |
| 14 | Accept all persists `analytics=true` in current schema | **PASS** | TASK-026 localStorage assertion plus store schema tests pass |
| 15 | Reject optional persists `analytics=false` in current schema | **PASS** | Component/store persistence assertions pass; no first-level reject E2E |
| 16 | Customize + Save persists selected analytics value | **PASS** | Component save test and store receipt contract tests pass |
| 17 | Backend sync is best-effort and cannot block dismissal | **PARTIAL** | Store regression tests prove local-first behavior; integrated dismissal/UI warning evidence absent |
| 18 | EN/ES copy remains available | **PARTIAL** | Both locale files and key-parity tests pass; Spanish rendered prompt not exercised |
| 19 | Light/dark themes are verified | **NOT EVIDENCED** | Token-based classes are present, but no theme-matrix runtime test or screenshot evidence |
| 20 | Keyboard navigation and visible focus work | **PARTIAL** | Ordinary controls and Escape behavior are present; no tab-order/focus-ring runtime evidence |
| 21 | No horizontal overflow at mobile widths | **PARTIAL** | Mobile Chrome consent tests pass; no overflow assertion at 320px/explicit viewport matrix |
| 22 | No orphaned overlay if consent content fails to render | **PARTIAL** | Initial banner path makes the failure structurally impossible; actual `CookieSettings` overlay/content lifecycle is not directly asserted |
| 23 | Existing analytics gating remains green | **PASS** | Marketing `Analytics`, `ConsentScript`, and marketing banner suites: 29 tests passed; shared contracts unchanged |
| 24 | New browser/UI tests cover redesign and stale state | **PARTIAL** | Component and Chromium-family stale/UI tests exist; DNT/GPC, Safari/WebKit, and Brave coverage are missing |

### User-supplied Gherkin scenarios

| Feature / scenario | Status | Passing covering evidence |
|---|---|---|
| Non-blocking — First visit without consent | **PASS** | `ConsentBanner.spec.ts` action/overlay tests plus TASK-026 E2E visibility/localStorage flow |
| Non-blocking — Existing valid consent | **PASS** | `ConsentBanner.spec.ts > does not show the banner when valid consent exists`; TASK-028 post-reload assertion |
| Non-blocking — Stale consent receipt | **PARTIAL** | TASK-028 and store tests prove re-prompt; rest-of-app visual availability is not asserted in that stale E2E |
| Browser resilience — Privacy browser with no valid consent | **NOT EVIDENCED** | No Brave/Safari/WebKit runtime result |
| Browser resilience — Existing authenticated session with stale consent | **PARTIAL** | Authenticated stale E2E runs in Chromium/Firefox/Mobile; no privacy-browser/no-blank-overlay assertion |
| Consent choices — Reject optional analytics | **PARTIAL** | Component/store persistence tests pass; no integrated E2E analytics-gating assertion after first-level Reject |
| Consent choices — Customize analytics | **PASS** | Component test opens Customize, proves Necessary disabled, Analytics configurable, and Save payload |

## Correctness table

| Requirement area | Status | Static implementation evidence |
|---|---|---|
| Non-modal banner presentation | **Implemented** | `ConsentBanner.vue` is an inline fixed `<aside>` with `z-40`, safe-area padding, responsive width, no Dialog/Teleport/backdrop |
| First-level actions | **Implemented** | Reject, Customize, and Accept controls render together when undecided |
| Inline granular preferences | **Implemented** | Customize panel contains immutable Necessary and editable Analytics with Save/Back |
| Receipt state machine | **Implemented** | `loadFromStorage()` calls `validateConsentReceipt`; null invalid receipts drive `!hasValidConsent` |
| Receipt contract | **Implemented** | Version, policy, ISO timestamp, EU region, Necessary true, DNT capture, and source are built by the store |
| Best-effort backend sync | **Implemented with integration-test gap** | Local receipt is assigned before fire-and-forget sync; rejected sync sets error/toast without reverting receipt |
| CookieSettings ownership | **Implemented with lifecycle-test gap** | `CookieSettings.vue` remains the intentional Dialog; `AppShell.vue` owns `showCookieSettings`; store no longer exposes force/settings-open actions |
| DNT/GPC presentation behavior | **Partially evidenced** | Store captures signals and defaults analytics false without a valid receipt; UI/E2E proof is missing |
| Browser resilience | **Structurally mitigated, not browser-verified** | Removing the initial overlay/portal eliminates the identified orphaned-overlay path; Brave/Safari runtime proof is absent |

## Design coherence

| Design decision | Followed? | Evidence |
|---|---|---|
| Fixed non-modal banner instead of modal mitigation | **Yes** | Inline `<aside>`, no Dialog primitives in banner path |
| Inline Customize instead of compact dialog | **Yes** | Panel is conditionally rendered inside the banner |
| Visibility only from `hasValidConsent`; remove force/settings-open API | **Yes** | `v-if="!hasValidConsent"`; store return has no `forceOpen`, `openSettings`, or `closeSettings` |
| CookieSettings remains the only consent modal | **Yes** | `CookieSettings.vue` is the only consent component importing Dialog |
| Preserve component/testid contract | **Yes** | `ConsentBanner.vue` and `data-testid="consent-banner"` retained |
| Shared `useConsent(source)` action path | **Yes on local HEAD** | Banner uses `useConsent('banner')`; settings uses `useConsent('settings-panel')` |
| Browser matrix and root-cause documentation | **No / incomplete** | Design documents the portal-overlay failure class, but no durable ADR/manual Brave-Safari result is present; tasks 4.5, 4.6, and 5.1 remain unchecked |

## TDD compliance audit

| Metric | Status | Evidence |
|---|---|---|
| RED → GREEN → REFACTOR per task | **NOT EVIDENCED** | No apply-progress artifact for this change and no recorded failing-test runs |
| Tests committed before or with implementation | **PARTIAL** | Original implementation/test changes are paired in `311ccebb`; local review-fix test and implementation changes are paired in `860c3744` |
| RED phase explicitly observed | **NOT EVIDENCED** | Commit history alone cannot prove the tests failed before implementation |

Strict TDD is enabled in `openspec/config.yaml`, but the requested `strict-tdd-verify.md` module was
not present at the configured/project skill paths. This does not change the runtime results, but it
prevents a stronger RED-phase audit.

## Issues found

### CRITICAL

1. **Required DNT/GPC UI scenario is untested (`UNTESTED`)**: `mockPrivacySignals()` exists in
   `apps/web/app/e2e/fixtures/consent-helpers.ts` but has no caller in the app consent suite. No
   passing test proves prompt visibility, analytics toggle OFF by default, and explicit Accept All
   override under DNT/GPC.
2. **Required Brave/Safari browser evidence is missing (`NOT EVIDENCED`)**: no Brave Shields
   ON/OFF or Safari/WebKit run was performed. `apps/web/app/e2e/playwright.config.ts` explicitly
   excludes WebKit; the required manual matrix across states A–D is absent.
3. **Core verification tasks remain incomplete**: tasks 4.2–4.6 are unchecked, including the
   consent E2E expansion, DNT/GPC E2E, full app E2E, and browser matrices.
4. **A configured full app test invocation failed once**: the first parallel
   `pnpm --filter app test:run` run timed out in `CookieSettings.spec.ts`. A serial rerun passed
   117 files/1,353 tests, so this is currently a reproducibility/stability blocker rather than a
   confirmed functional regression.

### WARNING

1. Verification evidence is for local commit `860c3744`, not PR #775 remote head `6088384a`.
   The local review fixes are not in the actual PR and the remote artifact state differs.
2. CookieSettings real overlay/content lifecycle is only indirectly exercised. Unit tests stub
   Dialog primitives and E2E does not assert overlay removal, focus restoration, or banner state
   after closing the settings modal.
3. Theme, explicit 320px overflow, Spanish rendered prompt, keyboard tab order, and visible focus
   behavior lack runtime evidence. Mobile Chrome flows pass, but that is not an overflow/theme/a11y
   matrix.
4. Backend best-effort behavior is proven at store level, not as an integrated accept/reject UI
   flow with visible local-only warning.
5. Strict-TDD RED-phase evidence is unavailable; no apply-progress record exists for this change.
6. The durable Brave root-cause/ADR task remains unchecked even though `design.md` describes the
   portal-overlay failure class.

### SUGGESTION

1. Add the DNT/GPC Playwright scenario and assert the real banner toggle state plus Accept All
   override.
2. Run and record the requested Chrome/Chromium, Safari/WebKit, and Brave Shields ON/OFF × A–D ×
   EN/ES × light/dark × mobile/desktop matrix before archive.
3. Add web-first assertions for actual app navigation behind the banner, mobile overflow, focus
   visibility, and settings overlay lifecycle.
4. Update stale E2E comments such as “Verify the dialog closes” to describe banner visibility.

## Verdict

**FAIL** — focused local implementation checks are green and the structural non-modal redesign is
present, but the strict verification gate cannot pass with untested DNT/GPC UI behavior, absent
Brave/Safari matrix evidence, incomplete core tasks, and one observed full-suite timeout. Re-run
verification after those gaps are resolved, then hand off to `sdd-qa` for independent acceptance
validation.
