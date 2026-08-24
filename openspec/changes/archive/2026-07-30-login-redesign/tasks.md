# Tasks: Login Redesign

## Review Workload Forecast

| Field                   | Value                                          |
|-------------------------|------------------------------------------------|
| Estimated changed lines | 900–1,300                                      |
| 400-line budget risk    | High                                           |
| Chained PRs recommended | Yes                                            |
| Suggested split         | PR 1 contracts → PR 2 components → PR 3 UI/E2E |
| Delivery strategy       | size:exception                                 |
| Chain strategy          | single-pr                                      |

Decision needed before apply: No — resolved by explicit maintainer approval for `size:exception`
Chained PRs recommended: Yes, but declined under the approved size exception
Chain strategy: single-pr
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                              | Likely PR | Notes                      |
|------|---------------------------------------------------|-----------|----------------------------|
| 1    | Reconcile recovery, capabilities, guards, backend | PR 1      | Base main; unit + BDD      |
| 2    | Component architecture and behavior               | PR 2      | Base PR 1; focused Vitest  |
| 3    | Visual, a11y, browser verification                | PR 3      | Base PR 2; Playwright + CI |

## Phase 1: Reconciliation and Contracts

- [x] 1.1 RED: inventory merged recovery versus untracked auth/capability files and encode retained
  behavior in existing `ForgotPasswordView.spec.ts`, `ResetPasswordView.spec.ts`, and backend
  recovery tests; GREEN: remove conflicting partial code only; REFACTOR: delete `login*.fig`. Done:
  focused tests pass.
- [x] 1.2 RED: update capability tests for exactly two booleans and no SSO; GREEN: remove SSO and
  divergent `PasswordRecoveryAvailabilityPort.kt`, adapter, and test across frontend/backend
  capability files; REFACTOR: map existing registration/recovery properties directly. Done:
  `just backend-test-fast` passes.
- [x] 1.3 RED: extend `public-capabilities.store.spec.ts` for strict false normalization, shared
  concurrent request, resolved/error/retry; GREEN: minimally update domain, `auth-api.ts`, and
  store; REFACTOR: consolidate duplicate suites. Done:
  `pnpm --filter app test:run -- public-capabilities.store` passes.
- [x] 1.4 RED: extend router/App tests for standalone named routes, preserved URL/query, fail-closed
  register/recovery, and authenticated reset; GREEN: remove redirect/synthetic routes and wire
  in-place gates; REFACTOR: centralize route metadata. Done: focused Vitest passes.
- [x] 1.5 RED: add missing `local-auth.feature` scenarios and BDD glue for exact capability output
  plus disabled registration/request/reset without mutation or token consumption; GREEN: add only
  absent backend enforcement; REFACTOR: reuse authoritative properties. Done:
  `just backend-bdd-fast` passes.

## Phase 2: Component Architecture

- [x] 2.1 RED: add component tests for shell branding/legal links and password pressed-state; GREEN:
  create `AuthShell.vue`, `AuthLegalLinks.vue`, `PasswordField.vue`; REFACTOR: keep public
  props/events narrow. Done: focused Vitest passes.
- [x] 2.2 RED: add `LoginForm` tests for validation focus, busy/read-only, deduplication, generic
  focused alert, retry; GREEN: create `LoginForm.vue`; REFACTOR: localize secrets/pending state.
  Done: focused Vitest passes.
- [x] 2.3 RED: add `RegisterForm` tests for consent/password clearing and disabled navigation;
  GREEN: create `RegisterForm.vue`; REFACTOR: share only password primitive. Done: focused Vitest
  passes.
- [x] 2.4 RED: rewrite `AuthView.spec.ts` for email-only orchestration and non-blocking
  capabilities; GREEN: compose forms and unavailable states; REFACTOR: use named routes. Done:
  focused Vitest passes.

## Phase 3: Visual, Accessibility, Verification

- [x] 3.1 RED: add EN/ES rendering assertions; GREEN: update auth/recovery locale files and centered
  320px light/dark styling with shared adaptive SVG; REFACTOR: remove hero/SSO styles. Done:
  `just app-build` passes.
- [x] 3.2 RED: update auth Playwright specs/page objects for keyboard focus, ARIA, loading/failure
  gates, authenticated reset, privacy, legal links, themes, and 320px overflow; GREEN: fix only
  observed gaps; REFACTOR: deduplicate helpers. Done: focused Chromium suite passes 42 tests with 2
  project-specific skips; Mobile Chrome recovery/touch suite passes 3 tests.
- [x] 3.3 Run `just backend-check`, `just backend-bdd-fast`, `just app-build`, and `just ci`;
  reconcile failures without weakening assertions. Done: focused app suite (1,216 passed, 1 todo),
  `just app-build`, `just backend-bdd-fast` (128 scenarios), `just backend-check`, focused Biome for
  the two login-redesign E2E specs, focused Chromium Playwright (14 passed with coverage), and full
  `just ci` pass sequentially. The approved delivery strategy is a single PR with explicit
  `size:exception`.
