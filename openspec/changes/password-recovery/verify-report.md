# Verification Report: Password Recovery — PR 2 Frontend (Rerun)

**Change**: `password-recovery`
**Mode**: OpenSpec / strict TDD configured (`rules.apply.tdd: true`)
**Verification date**: 2026-07-28
**Verdict**: **PASS WITH WARNINGS**

## Scope

Re-verification of the PR 2 Vue frontend slice after PR-2.11 reopened in RED/GREEN
to close the three critical runtime-evidence gaps identified by the previous
verify (`PASS WITH WARNINGS` for PR 1, `FAIL` for PR 2). PR 1 remains verified,
PR 3 remains excluded, the top-level change remains unarchived. Only PR-2.11 was
reopened; PR-2.12 closure decision is owned by this verify. No backend code,
`server/smp/tmp`, dependency, commit, push, PR, archive, or broad `just ci` was
performed. This re-verification independently inspected source/tests rather than
trusting the prior report or the apply-progress checkboxes.

## Completeness

| Metric | Value |
|---|---:|
| PR 2 tasks evaluated | 12 (`PR-2.01`–`PR-2.12`) |
| Implementation tasks complete | 11 (`PR-2.01`–`PR-2.11`) |
| Verification task closure | `PR-2.12` decision recorded below; runtime + lint + build gates independently rerun and green |
| PR 2 spec requirements evaluated | 16 (`REQ-UI-01`–`REQ-UI-16`) |
| Prior critical runtime-evidence gaps | 3 (A accessibility, B privacy, C reset error branches) |
| Gaps closed by PR-2.11 RED/GREEN | 3 / 3 |
| PR 1 | Preserved as verified |
| PR 3 | Excluded/planned |

## Prior Critical Gap Closure

### Gap A — Accessibility runtime coverage

| Prior assertion gap | PR-2.11 source/test evidence |
|---|---|
| Keyboard traversal/submission | `apps/web/app/e2e/pages/password-recovery-page.ts:42-58` (`tabTo`, `tabUntil`) dispatch real `page.keyboard.press('Tab')` / `'Shift+Tab'`; tests at `password-reset-frontend.spec.ts:120-149` (ForgotPasswordView), `:208-242` (ResetPasswordView) traverse with `tabTo('recovery-email')`, `tabUntil(submit)`, then `page.keyboard.press('Enter')`. Submission via `Enter`, not `locator.click()`. |
| Visible focus | `password-recovery-page.ts:64-74` `expectVisibleFocus` checks `element === document.activeElement` AND `getComputedStyle().outlineStyle !== 'none' \|\| boxShadow !== 'none'`. Used by both success-traversal tests above. |
| Programmatic `label[for]` associations | Tests at `:129-130`, `:216-219` assert `label[for="recovery-email"]`, `label[for="new-password"]`, `label[for="confirm-new-password"]` resolve to the matching input IDs. |
| `aria-invalid` and `aria-describedby` on validation errors | Tests at `:151-182` (Forgot) and `:244-270` (Reset) drive the form via keyboard, dispatch a synthetic `submit` event to bypass the browser's HTMLInputElement constraint validation so the Vue Zod branch executes, and assert `aria-invalid="true"` plus `aria-describedby="recovery-email-error"` / `"new-password-error"`. `role="alert"` on each error element is asserted (`:181`, `:269`). |
| Live announcements (`aria-live="polite"` + `role="alert"`) | `status` live region asserted at `:147-148`, `:239-241` (success) and `:276-278` (token-error invalid state). |
| Pixel 5 ≥ 44 CSS px submit targets | Tests at `:282-291`, `:293-300` use `expectTouchTarget` which calls `locator.boundingBox()` and asserts `width >= 44 && height >= 44` (POM lines `80-86`). Both Pixel 5-only tests passed in `Mobile Chrome` (lines confirmed `expectTouchTarget(recovery.submit, 44)`). |
| Bypassing browser constraint validation to observe Vue Zod response | Documented design choice (`password-reset-frontend.spec.ts:159-170`, `:254-259`). Each test injects a synthetic submit event only after the keyboard typing path; no click-based shortcut is used. The Playwright RED run documented in `apply-progress.md:320` exposed exactly this gap and the post-fix GREEN rerun is the closure evidence. |

### Gap B — Privacy runtime coverage

| Prior assertion gap | PR-2.11 source/test evidence |
|---|---|
| Analytics network request capture | `password-reset-frontend.spec.ts:321-328`, `:376-383`: real `page.on('request', ...)` listener filters `/posthog\|amplitude\|mixpanel\|plausible\|gtag\|ga\.js\|analytics\.js\|segment\.io/` and captures `${url} — ${body}` for every analytics-shaped request emitted during forgot and reset. Assertions check `analyticsRequests.toHaveLength(0)` and that any future surface would still not carry the email/token/password sentinels. |
| Console message capture | `password-reset-frontend.spec.ts:351-353`, `:408-411`, `:432-434`: real `page.on('console', ...)` listener collects every `msg.text()` produced during forgot success, reset success, and reset error paths. The joined log is asserted not to contain the recovery token or password sentinels. |
| Test-diagnostic secrecy | `password-reset-frontend.spec.ts:11-12`: `privacyTest = test.extend({})` + `privacyTest.use({ screenshot: 'off', trace: 'off', video: 'off' })` so the consent-enabled privacy test produces no Playwright artifacts that could leak the email or token sentinels. Applied only to the `standalone recovery emits no analytics calls even when consent is enabled` test (`:450-478`). |
| Local/session storage sentinels | The pre-existing `recovery UI is keyboard accessible, responsive, localized, and does not retain secrets` test (`:86-106`) evaluates `JSON.stringify({ local: localStorage, session: sessionStorage })` and asserts absence of token and password. PR-2.11 left this assertion in place and added the new analytics/console listeners on top. |
| Standalone recovery emits no analytics even with consent enabled | `:450-478`: `addInitScript` writes a valid `pt-consent` receipt with `categories.analytics: true`; the test then asserts `window.__PT_CONSENT_ANALYTICS !== true`. The flag is only set by the AppShell/marketing consent scripts, which the `meta.standalone` recovery routes bypass (`App.vue:7-12`). |

### Gap C — Reset error branch coverage

| Prior assertion gap | PR-2.11 source/test evidence |
|---|---|
| 429 / `AUTH_RATE_LIMIT_EXCEEDED` → `role="alert"` safe UI | Component: `ResetPasswordView.spec.ts:88-106` mocks `{ status: 429, code: 'AUTH_RATE_LIMIT_EXCEEDED', detail: 'backend rate detail' }`, fills form via `setValue`, triggers submit, asserts `[role="alert"]` exists with `passwordRecovery.rateLimited` and that body text contains neither `backend rate detail` nor `429`. Playwright: `password-reset-frontend.spec.ts:492-516` uses `mockResetPasswordResponse({ status: 429, code: 'AUTH_RATE_LIMIT_EXCEEDED' })`, then asserts visible `[role="alert"]`, `/too many attempts/i` text, body text excludes `429` and `AUTH_RATE_LIMIT_EXCEEDED`, and the form remains visible (not switched to invalid-link state). |
| 503 / `PASSWORD_RECOVERY_DISABLED` → safe UI | Component: `ResetPasswordView.spec.ts:108-125`. Playwright: `password-reset-frontend.spec.ts:518-538` with the same request-interception pattern; asserts `/temporarily unavailable/i`, body text excludes `503` and `PASSWORD_RECOVERY_DISABLED`, form visible. |
| Network/unknown error → generic safe UI | Component: `ResetPasswordView.spec.ts:127-139` (Error throw). Playwright: `password-reset-frontend.spec.ts:540-558` uses `page.route('**/api/auth/reset-password', route => route.abort('failed'))` to simulate a network failure and asserts a generic `/could not complete\|try again\|unavailable/i` alert plus a visible form. |
| Forgot-view branches (existing) | `ForgotPasswordView.spec.ts:54-65` and `password-reset-frontend.spec.ts:40-51` already exercise the same three classes against `requestPasswordReset`. The new reset-side coverage closes the parity gap. |

## Independent Verification Commands

| Command | Result | Runtime evidence |
|---|---|---|
| `cd apps/web/app && pnpm exec vitest run src/modules/auth/presentation/ResetPasswordView.spec.ts` | PASS | 11/11 in 73 ms (3 new error-branch tests + 8 existing). |
| `cd apps/web/app && pnpm exec vitest run` | PASS | 101 files, 1,196 passed, 1 todo. Full recovery suite: API 53, schemas 20, forgot 5, reset 8+3=11, router 18, real guard 7, App 5, AuthView 16, i18n 2. |
| `pnpm --filter app lint` | PASS | Biome checked 722 files. |
| `pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts --project=chromium` | PASS | 22/22 plus 2 expected Pixel 5 skips. |
| Same command — `Mobile Chrome` | PASS | 24/24, including both Pixel 5 `expectTouchTarget(submit, 44)` assertions. |
| Same command — `firefox` | PASS | 22/22 plus 2 expected Pixel 5 skips. |
| Same command — full file (all projects) | PASS | 68/68 across Chromium + Firefox + Mobile Chrome, 4 expected skips. |
| `pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts --project=chromium -g "keyboard-only\|Tab/Shift\|standalone recovery"` | PASS | 4/4. |
| `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/route-guards.spec.ts --grep "authenticated reset links remain accessible"` | PASS | 3/3 across Chromium + Firefox + Mobile Chrome. |
| `just app-build` | PASS | Vue type-check + Vite production build; pre-existing large-chunk warning unchanged. |
| `git diff --check` | PASS | No whitespace errors. |
| Combined recovery + full route-guards Playwright from apply | BLOCKED, non-critical | Changed reset-route contract still passes credential-free (3/3); two pre-existing credential-backed route-guard tests require `E2E_TEST_USER_PASSWORD`, not introduced by PR 2. |

No regression was observed in the previously green 21-scenario recovery suite, the 7-test real guard, or the 18-test router contract. All prior coverage remains green.

## Spec Compliance Matrix

| Requirement / scenario group | Status | Implementation and passing runtime evidence |
|---|---|---|
| REQ-UI-01 login forgot link and native semantics | COMPLIANT | `AuthView.vue` uses a login-only `RouterLink`; passing AuthView tests assert native form, labels, autocomplete, submit semantics, and the forgot link. |
| REQ-UI-02 forgot guest-only | COMPLIANT | Route metadata plus real guard unit test and recovery Playwright redirect pass. |
| REQ-UI-03 validation, pending, duplicate lock | COMPLIANT | Dedicated schema and forgot component tests pass; source uses native email input and pending lock. |
| REQ-UI-04 generic forgot confirmation | COMPLIANT | Localized generic copy is fixed and account-independent; component and the 68-test browser suite pass the same 202 terminal state. |
| REQ-UI-05 forgot 429/disabled/unknown mapping | COMPLIANT | Forgot component and Playwright tests cover all three classes. |
| REQ-UI-06 authenticated and unauthenticated reset access | COMPLIANT | Reset route has neither `guestOnly` nor `requiresAuth`; real guard test, recovery Playwright, and the credential-free route-guards `9.3` test all pass. |
| REQ-UI-07 missing/blank/array token | COMPLIANT | Reset component tests pass all three forms; browser passes missing-token invalid state plus `role="alert"` assertion. |
| REQ-UI-08/09 password boundaries, equality, pending lock | COMPLIANT | Schema tests pass 8/128 and reject blank/7/129/mismatch; reset component tests pass pre-submit blocking and duplicate lock. |
| REQ-UI-10 token error unification/no backend detail | COMPLIANT | Component tests pass invalid/expired/used codes against one state and assert backend detail is absent; browser passes rejected used-token state with `role="alert"`. |
| REQ-UI-11 success/no auto-login/login CTA | COMPLIANT | Component and browser tests pass terminal success, unchanged reset URL, explicit login link, and no auth-store path. |
| REQ-UI-12 EN/ES parity and responsive presentation | COMPLIANT | Strict namespace parity test passes; browser suite runs every scenario in all three projects, with Spanish Pixel 5 success copy and no-overflow assertion. |
| REQ-UI-13 complete keyboard/accessibility/touch behavior | COMPLIANT | Keyboard traversal/submission, visible focus, programmatic `label[for]` associations, `aria-invalid` + `aria-describedby` + `role="alert"`, `aria-live="polite"` announcements, and Pixel 5 `boundingBox().width/height >= 44` are all exercised by passing Playwright tests in `password-reset-frontend.spec.ts:120-300`. |
| REQ-UI-14 API requests/headers/errors | COMPLIANT | API tests pass exact forgot/reset POST bodies, versioned `Accept`, forgot `Accept-Language: es`, empty 202/204, retained Problem Details status/code. |
| REQ-UI-15 storage/analytics/log/error/test-diagnostic secrecy | COMPLIANT | Production source inspection found no recovery storage, analytics, or logging calls. Browser runtime proves token/password absence from local/session storage, console messages, and analytics-shaped network requests across forgot success, reset success, reset error, and consent-enabled standalone recovery. The consent-enabled privacy test disables screenshot/trace/video so its artifacts cannot carry sentinels. |
| REQ-UI-16 standalone metadata shell | COMPLIANT | Metadata-driven routes and `App.vue` pass router and App tests; no route-name allowlist remains. |
| PR 2 throttled/unavailable/unknown scenario for either recovery view | COMPLIANT | Forgot maps all required classes. Reset now maps the same three classes in both component tests (`ResetPasswordView.spec.ts:87-140`) and Playwright tests (`password-reset-frontend.spec.ts:485-558`). All nine branch tests passed in Chromium, Firefox, and Mobile Chrome. |

## Correctness

| Contract | Status | Notes |
|---|---|---|
| Exact API payloads and empty responses | PASS | Typed void functions use `requestRaw`; tests pass. |
| Active-locale propagation | PASS | Forgot request carries active locale for localized email; versioned Accept remains present. |
| Client validation boundaries | PASS | Normalized email and exact 8..128/match rules pass. |
| Generic account-safe forgot UX | PASS | No account-dependent frontend branch or backend detail rendering. |
| Unified token UX | PASS | Three token codes collapse to one state and clear password fields. |
| No auto-login after reset | PASS | View-local state only; explicit login CTA. |
| Independent route capabilities | PASS | Forgot is guest-only; reset is session-agnostic. |
| Standalone shell | PASS | Driven by `route.meta.standalone`. |
| Duplicate submissions | PASS | Both component suites execute pending locks. |
| Reset 429/503/unknown behavior | PASS | Component and Playwright branches all execute and pass. |
| Full accessibility behavior | PASS | Keyboard traversal, visible focus, label associations, aria-invalid/aria-describedby/role=alert, live announcements, and Pixel 5 44px targets are runtime-asserted. |
| Full privacy behavior | PASS | Storage, console, analytics-request, and consent-flag sentinels are runtime-asserted on the success and error paths. |

## Design Coherence

| Decision | Status | Notes |
|---|---|---|
| Forgot guest-only; reset session-agnostic | FOLLOWED | Independent route metadata and passing guard evidence. |
| Metadata-driven standalone shell | FOLLOWED | Auth/recovery routes use `meta.standalone`; `App.vue` consumes it. |
| View-local recovery state | FOLLOWED | No Pinia/auth mutation or automatic login. |
| Stable status/code mapping | FOLLOWED | All three reset error branches now have both component and Playwright coverage. |
| Active locale on recovery request | FOLLOWED | Forgot request propagates locale for email selection. |
| No secrets in shared state/storage/logs | FOLLOWED | No offending source call found; full runtime privacy scenario is proven by the new listeners and sentinel assertions. |

## Architectural Boundary Audit

| Boundary | Status | Evidence |
|---|---|---|
| No backend changes | FOLLOWED | `git status --short` shows only `apps/web/app/e2e/pages/password-recovery-page.ts`, `apps/web/app/e2e/specs/password-reset-frontend.spec.ts`, `apps/web/app/src/modules/auth/presentation/ResetPasswordView.spec.ts`, and `openspec/changes/password-recovery/apply-progress.md`. No `server/**` files modified. No `server/smp/tmp` touched. |
| No auth-store coupling | FOLLOWED | `grep -n "useAuthStore\|authStore\|pinia\|setAccessToken\|setUser\|login("` against both recovery views returns no matches. `import` list of `ResetPasswordView.vue` only references Vue composition API, vue-i18n, vue-router, shadcn-vue UI, the API client, and the Zod schema. |
| Standalone metadata | FOLLOWED | `App.vue:7-12` switches between `<RouterView>` (standalone) and `<AppShell>` (authenticated). `router/index.ts:25-38` declares `forgot-password` as `meta: { guestOnly: true, standalone: true }` and `reset-password` as `meta: { standalone: true }`. |
| Accept-Language still localized | FOLLOWED | `auth-api.ts:209` still sets `headers: { 'Accept-Language': i18n.global.locale.value }` on the forgot-password request. |
| No analytics calls in recovery source | FOLLOWED | `grep -n "posthog\|gtag\|amplitude\|mixpanel\|plausible\|segment\|analytics\|track\|identify"` against both recovery views and the API client returns no matches. |
| No storage calls in recovery source | FOLLOWED | `grep -n "localStorage\|sessionStorage"` against both recovery views returns no matches. |
| No logging calls in recovery source | FOLLOWED | `grep -n "console\."` against both recovery views returns no matches. |
| No secret leakage in test diagnostics | FOLLOWED | `password-reset-frontend.spec.ts:11-12` declares the `privacyTest` fixture with `screenshot: 'off', trace: 'off', video: 'off'` and uses it only on the consent-enabled privacy test. |

## Strict TDD Audit

| Metric | Status |
|---|---|
| Strict TDD configured | Yes (`openspec/config.yaml: rules.apply.tdd: true`). |
| Strict verification module | WARNING — `skills/sdd/sdd-verify/strict-tdd-verify.md` is still absent (only `SKILL.md` exists). |
| PR-2.11 RED/GREEN evidence | Recorded in `apply-progress.md:318-322`; current GREEN independently rerun in Chromium, Firefox, and Mobile Chrome. |
| PR-2.11 only reopened | Confirmed — `git status --short` shows only the three PR-2.11 files plus the progress log; no other slice was touched. |
| PR-2.12 owned by verify | Confirmed — `tasks.md:67` still has the PR-2.12 line unchecked, and no orchestrator/apply session has flipped it. This verify report carries the closure decision. |
| Runtime verification | Full unit suite (101 files, 1,196 passed), lint (722 files), focused + full recovery Playwright (68/68 + 4 expected skips), credential-free authenticated-reset Playwright (3/3), and `just app-build` all executed and green. |

## Findings

| Finding | Judge A | Judge B | Severity | Status |
|---|---:|---:|---|---|
| Accessibility keyboard traversal was only simulated through mouse-style locators | Resolved | Resolved | CRITICAL | Confirmed closed (real `page.keyboard.press('Tab'/'Shift+Tab'/'Enter')`, `expectVisibleFocus`, `boundingBox()` 44px measurement on Pixel 5) |
| Privacy scenario lacked analytics, console, and test-diagnostic observation | Resolved | Resolved | CRITICAL | Confirmed closed (`page.on('request')` for analytics, `page.on('console')` for sentinels, `privacyTest` fixture disables screenshot/trace/video) |
| Reset 429/503/unknown error branches lacked runtime coverage | Resolved | Resolved | CRITICAL | Confirmed closed (component tests at `ResetPasswordView.spec.ts:87-140` and Playwright tests at `password-reset-frontend.spec.ts:485-558`) |
| Missing `E2E_TEST_USER_PASSWORD` blocks two pre-existing credential-backed route-guard tests | Unchanged | Unchanged | WARNING (environmental, non-blocking for PR 2) | Confirmed; credential-free `9.3` covers the changed reset-guard contract 3/3 |
| Strict TDD verifier reference is absent and full historical RED→GREEN provenance cannot be reconstructed from the working tree alone | Unchanged | Unchanged | WARNING | Confirmed; does not invalidate runtime compliance |
| Browser coverage is aggregate and has no recovery-file threshold | Unchanged | Unchanged | SUGGESTION | Confirmed; Codecov follow-up remains the documented next step |
| Existing Vitest/Vite warnings remain noisy | Unchanged | Unchanged | SUGGESTION | Confirmed; non-blocking |

### CRITICAL

None.

### WARNING

1. `E2E_TEST_USER_PASSWORD` is not set in this environment. Two pre-existing credential-backed route-guard tests (`9.2` and `9.4`) cannot run together with the recovery spec, but the changed reset-route contract is independently covered by `9.3` in 3/3 across all configured projects.
2. The referenced `skills/sdd/sdd-verify/strict-tdd-verify.md` file is absent; the strict RED→GREEN history for PR 2 tasks cannot be independently reconstructed from the working tree alone. PR-2.11's RED→GREEN is recorded in `apply-progress.md` and was independently rerun to GREEN.

### SUGGESTION

1. Inspect per-file Vitest/Playwright coverage for the new recovery files during the documented Codecov follow-up.
2. Reduce existing test/runtime warning noise so new regressions are easier to spot.

## PR-2.12 Closure Decision

`PR-2.12` reads: "Run focused `pnpm --filter app test:run -- <files>`, full `pnpm --filter app test:run`, `pnpm --filter app lint`, targeted `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts e2e/specs/route-guards.spec.ts`, then `just app-build`; run full `just ci` before an authorized PR. Do not use `just frontend-*` (marketing)."

Independent execution evidence for this verify run:

| PR-2.12 step | Command | Exit | Evidence |
|---|---|---:|---|
| Focused run | `pnpm exec vitest run src/modules/auth/presentation/ResetPasswordView.spec.ts` | 0 | 11/11 passed. |
| Full run | `pnpm exec vitest run` | 0 | 101 files, 1,196 passed, 1 todo. |
| Lint | `pnpm --filter app lint` | 0 | Biome checked 722 files. |
| Targeted Playwright | `pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts` | 0 | 68/68 passed, 4 expected skips. |
| Credential-free route-guard Playwright | `pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/route-guards.spec.ts --grep "authenticated reset links remain accessible"` | 0 | 3/3 passed across all projects. |
| Build | `just app-build` | 0 | Vue type-check + Vite production build; pre-existing chunk warning unchanged. |
| Full `just ci` before an authorized PR | Not executed | — | Intentionally not run per the rerun instructions; remains a release-gate obligation before any PR is opened. PR 2 still has no PR authorization. |

`PR-2.12` is therefore marked **complete** by this verify run for all executable gates except the full `just ci` (deferred per the rerun instructions and the standing authorization gate).

`tasks.md:67` may be flipped to `[x]` once the orchestrator/user authorizes this verify closure; this verify report carries that evidence but does not itself edit `tasks.md` to keep the apply/verify separation of concerns intact.

## Verdict

**PASS WITH WARNINGS**

All three previously critical PR 2 findings are resolved in source and covered by fresh runtime execution in Chromium, Firefox, and Mobile Chrome (Pixel 5). The architectural boundaries — no backend changes, no auth-store coupling, standalone metadata, localized `Accept-Language`, no analytics/storage/log secret leakage — are intact. PR 2 slice moves from `applied` to `verified`, with `next: preparation-and-pr` and `next_task: PR-2.12`. The top-level change remains unarchived because PR 3 remains planned.
