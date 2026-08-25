# Verification Report: Login Redesign

## Verdict

**FAIL**

The implementation is broadly functional and the focused frontend, build, lint, backend unit, BDD, and secret-scan commands completed successfully. However, formal verification found critical spec and task gaps: backend capability/disabled-registration BDD scenarios claimed by task 1.5 were not added, the shared SVG follows OS color scheme rather than the app-selected theme, registration does not focus the first invalid field, mutable links remain activatable while submission is pending, and WCAG AA contrast is not covered by a passing runtime test.

## Change and Mode

| Field | Value |
|---|---|
| Change | `login-redesign` |
| Persistence | OpenSpec |
| Verification mode | Strict TDD requested by `openspec/config.yaml` (`apply.tdd: true`); strict verifier reference was unavailable at the configured skill path, so verification used source/diff inspection plus runtime evidence |
| State before/after | Remains `current_phase: apply`, `next: verify` because CRITICAL issues prevent advancing to archive |

## Completeness

| Artifact / task set | Result |
|---|---|
| Proposal, design, 5 delta specs, tasks | Present and reviewed |
| Tasks checked | 13/13 marked complete |
| Tasks substantiated | 12/13; task 1.5 is not substantiated as written |
| Full worktree diff | Reviewed, including tracked modifications, deletions, and all untracked files |
| Experimental `.fig` files | None present |
| Secrets | No leaks detected by full-worktree `gitleaks protect` |

## Build, Tests, and Coverage Evidence

| Command | Runtime result | Notes |
|---|---|---|
| `pnpm --filter app test:run -- ...focused files...` | PASS | Vitest ran the complete app suite due CLI forwarding behavior: 107 files, 1,213 passed, 1 todo |
| `just app-build` | PASS | `vue-tsc --build` and Vite production build passed; existing large-chunk warning only |
| `pnpm --filter app lint` | PASS | Biome checked 740 files |
| Focused Playwright Chromium auth/recovery suite | PASS | 32 passed, 2 device-project-only skips |
| `just backend-test-fast` | PASS / UP-TO-DATE | Gradle task successful; relevant handler/controller tests are present |
| `just backend-bdd-fast` | PASS / UP-TO-DATE | Initial task result successful but cached |
| Forced BDD rerun with `.env` password exported | PASS | Fresh `--rerun-tasks`; 123-scenario suite completed successfully in 2m21s |
| Forced BDD rerun without exported DB password | ENV FAILURE | Diagnostic attempt failed because `SMP_DB_TEST_PASSWORD` was absent; corrected rerun passed and this is not an implementation defect |
| `gitleaks protect --no-banner --redact --exit-code 1 --config .gitleaks.toml` | PASS | No leaks found |
| Coverage | NOT MEASURED | Threshold is 0. Focused Playwright reported `Unknown% (0/0)` after base fixture changed away from coverage instrumentation |

`just ci` was not repeated because tasks record a prior pass and the focused runtime commands above exercised the changed surfaces. No evidence artifact beyond task annotations existed before this verification.

## Spec Compliance Matrix

| Requirement / scenario group | Implementation evidence | Passing runtime evidence | Status |
|---|---|---|---|
| Standalone login/register/forgot/reset/verify routes | Named routes use `meta.standalone`; reset is not `guestOnly` | Vitest route/App tests; Playwright authenticated reset | COMPLIANT |
| Public response contains exactly two booleans and no SSO | Kotlin response/domain reduced to two fields | Controller/integration tests in backend test task | COMPLIANT, but missing mandated BDD contract scenario |
| Defensive normalization, dedupe, retry, fail closed | Pinia exact-boolean normalization and shared promise | Store Vitest tests | COMPLIANT |
| Login usable while capabilities load/fail | Login renders immediately; dependent links hidden | Vitest and Playwright loading test | COMPLIANT |
| Register/recovery unavailable in requested route, no request | In-place components; synthetic routes removed | Playwright register/forgot tests; router Vitest | COMPLIANT |
| Reset session-agnostic and token privacy | Reset route is standalone/non-guest-only; no storage/logging path found | Playwright authenticated reset and privacy tests | COMPLIANT |
| Existing generic recovery behavior and safe reset errors | Existing API mapping retained | Playwright recovery/error/privacy tests; fresh BDD recovery suite | COMPLIANT |
| Responsive centered login/register at 320px | `AuthShell` single max-width column | Playwright 320px login/register tests | COMPLIANT |
| Light/dark themed shared adaptive symbol | Correct shared alias and SVG imported | Overflow tests only; SVG uses `prefers-color-scheme`, not app theme state | FAILING |
| WCAG AA contrast in light/dark | Theme classes exist | No runtime contrast assertion | UNTESTED |
| Accessible login invalid focus/error behavior | Login focuses email/password and generic alert | Vitest + Playwright | COMPLIANT |
| Accessible registration first-invalid behavior | Registration always focuses email on any validation failure | No covering test for valid email + invalid password/consent | FAILING |
| Pending state disables mutable controls/navigation | Fields readonly; controls/buttons disabled | Login/register tests cover part of behavior | FAILING: RouterLinks remain navigable |
| Email preserved; passwords/consent cleared on mode switch | Email parent-owned; secrets form-local | Playwright mode-switch test | COMPLIANT |
| Legal links and named auth navigation | Named auth links; EN `/terms`/`privacy`, ES `/es/...`; marketing pages exist | Primitive and Playwright checks | COMPLIANT |
| Backend disabled registration before mutation | Existing authoritative gate in `RegisterUserHandler` | Backend unit test present/passed task | COMPLIANT at unit level; required BDD scenario absent |
| Backend disabled recovery request/reset before mutation | Existing shared `passwordRecoveryEnabled` supplier gates both handlers | Fresh BDD suite includes disabled request/reset and unchanged token/password scenarios | COMPLIANT |
| Recovery merge not regressed | Existing recovery feature files/glue retained; focused view tests were rewritten but E2E privacy/error coverage remains | 32 focused Playwright passes + fresh 123 BDD passes | COMPLIANT WITH WARNING |

## Correctness Findings

| Finding | Judge A (spec/source) | Judge B (runtime/tests) | Severity | Status |
|---|---|---|---|---|
| Task 1.5 claims `local-auth.feature` capability/disabled-registration scenarios, but that file is unchanged and contains none | ✅ | ✅ | CRITICAL | Confirmed |
| SVG color follows OS `prefers-color-scheme`, not the app-selected light/dark theme | ✅ | ✅ | CRITICAL | Confirmed |
| Registration focuses email even when password/consent is the first invalid field | ✅ | ✅ | CRITICAL | Confirmed |
| Pending RouterLinks use only `aria-disabled`/tabindex and remain activatable | ✅ | ✅ | CRITICAL | Confirmed |
| WCAG AA contrast scenario has no passing covering runtime test | ✅ | ✅ | CRITICAL | Confirmed (`UNTESTED`) |
| Base E2E fixture changed from coverage-aware test to plain Playwright, yielding 0/0 coverage | ✅ | ✅ | WARNING | Confirmed |
| `register-flow.spec.ts` removed ~1,100 lines of broader registration regression coverage | ✅ | ✅ | WARNING | Confirmed; focused replacement passes but coverage breadth decreased |
| No SSO/scaffolding, `.fig` experiment, or leaked secret was introduced | ✅ | ✅ | INFO | Confirmed |

## Design Coherence

| Design decision | Result |
|---|---|
| Focused `AuthShell`, `LoginForm`, `RegisterForm`, `PasswordField`, legal links | Coherent |
| Form-local sensitive state and email-only orchestration | Coherent |
| In-route capability gates and session-agnostic reset | Coherent |
| Strict capability normalization and shared authoritative backend configuration | Coherent |
| No SSO and no duplicate recovery flag/port | Coherent in current diff |
| Shared adaptive branding across app theme | Deviates: asset is shared, but adaptation is tied to OS media query |
| TDD/task evidence | Deviates: task 1.5 completion statement does not match the diff |

## Issues

### CRITICAL

1. **Missing mandatory BDD scenarios / inaccurate task completion** — `tasks.md` 1.5 says `local-auth.feature` and glue were extended for exact public capabilities and disabled registration/request/reset. `local-auth.feature` is unchanged, has no capability or disabled-registration scenarios, and no corresponding glue change exists. Recovery disabled scenarios exist in the previously merged recovery feature files, but that does not satisfy the advertised capability and registration BDD work.
2. **App theme does not control SVG adaptation** — `profiletailors-logotype.svg` uses `@media (prefers-color-scheme: dark)`. The SPA stores an explicit app theme; when app theme differs from OS theme the symbol can render black on dark or white on light. Existing E2E tests check overflow/button visibility, not symbol contrast under both app themes.
3. **Registration violates first-invalid focus** — `RegisterForm.submit()` always calls `emailInput.focus()` for every validation failure. With a valid email and invalid password/consent, focus is wrong.
4. **Pending navigation is not actually disabled** — `aria-disabled` is descriptive only. Login forgot-password and register-to-login RouterLinks do not prevent click/navigation while pending; the login link only removes tab focus. This violates the loading-state contract.
5. **WCAG AA contrast scenario is untested** — no passing runtime test calculates or audits contrast for text, controls, errors, and focus indicators in both themes. Per verification policy, an uncovered spec scenario is CRITICAL `UNTESTED`.

### WARNING

1. E2E base fixture now imports plain `@playwright/test`, while the config still emits a coverage report; the focused run produced `Unknown% (0/0)`. This is an unintended coverage regression unless explicitly approved.
2. `register-flow.spec.ts` was reduced from 1,172 to 72 lines, removing many existing registration/session/email-verification regression scenarios. Current focused and app unit suites pass, but the removed behavioral breadth should be reviewed against the recovery-merge preservation goal.
3. The strict-TDD verification reference files named by the phase protocol were absent from the installed skill directory, so historical RED-before-GREEN chronology could not be independently audited.

### SUGGESTION

1. Add a dedicated app recipe for auth-focused Playwright tests; the current project command is generic and direct Playwright invocation was required.

## Final Decision

Do not advance `state.yaml`. Fix the CRITICAL findings, add the missing runtime/BDD coverage, and rerun verification. Archive is not currently allowed.
