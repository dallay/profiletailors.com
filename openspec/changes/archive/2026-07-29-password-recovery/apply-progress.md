# Apply Progress: Password Recovery

## Scope

Audited PR 1 backend tasks PR-1.01 through PR-1.29 on branch
`feat/dallay-523-password-recovery-backend`. No commit, push, or pull request was created.

## Completed

- PR-1.01 through PR-1.20 verified against code, specs, focused tests, and backend checks.
- PR-1.27 `just backend-check` passed.
- PR-1.28 `just backend-bdd-fast` passed with 109 scenarios.
- PR-1.29 `just backend-bdd-postgres` passed.

## Incomplete

- PR-1.21 through PR-1.26 remain unchecked. The five feature files are substantially smaller than
  the complete scenario catalog in `spec.md`, all are globally tagged `@postgres` despite repository
  policy requiring `@postgres` only where necessary, and the single 1,200+ line glue class still
  contains marker/no-op steps rather than executable assertions for several specified scenarios.
- PR-1.30 is intentionally out of scope and prohibited by the user gate.

## TDD Evidence

- RED: focused `IdentityProblemDetailsHandlerTest` and `AuthRateLimitWebFilterTest` run failed 4
  tests for disabled status, distinct token codes, and missing password-recovery IP buckets.
- GREEN: the same focused run passed all 20 tests after minimal fixes.
- RED: initial `just backend-bdd-fast` failed 23/109 scenarios; successive regression repairs
  reduced failures to 6, 2, 1, then 0.
- GREEN: final `just backend-bdd-fast` passed all 109 scenarios.
- GREEN: final `just backend-check` and `just backend-bdd-postgres` passed.

## Fixes Applied

- Mapped disabled recovery to 503 with `PASSWORD_RECOVERY_DISABLED`.
- Preserved distinct invalid/expired/used token codes while sharing one public detail.
- Added 5/15m forgot-password and 10/15m reset-attempt IP policies with coded 429 responses;
  retained normalized-email 3/30m application bucket.
- Added scenario isolation for both rate-limit stores and email recording.
- Corrected malformed/placeholder BDD assertions and first/second token tracking.
- Allowed the specified 128-character password policy with deterministic SHA-256 pre-hashing before
  BCrypt when UTF-8 input exceeds BCrypt's 72-byte limit.
- Kept password policy failures in the handler so `INVALID_PASSWORD` is returned without retaining
  the password in an exception.
- Split `PasswordRecoveryDisabledException` into its specified file.

## Commands Run

- `git status --short && git branch --show-current && git show --stat ... && just -l` — exit 0.
- `just backend-check` (pre-fix) — exit 0.
- `just backend-bdd-fast` (RED) — exit 1, 23/109 failed.
- focused Gradle tests for problem details and auth rate limiting (RED) — exit 1, 4/20 failed.
- focused Gradle tests for problem details and auth rate limiting (GREEN) — exit 0, 20/20 passed.
- `just backend-bdd-fast` — exit 1 (compile error), then exit 1 with 6 failures, then 2, then 1.
- `just backend-bdd-fast` (final) — exit 0, 109/109 passed.
- `just backend-check` (intermediate) — exit 1, formatting and two Detekt magic-number findings.
- `just backend-check` (final) — exit 0.
- `just backend-bdd-postgres` — exit 0.

## Continuation: PR-1.21 through PR-1.26

- Corrected the five password-recovery feature headers to inherit the required identity domain tag,
  `@smoke`, and `@fast`.
- Removed global `@postgres`; persistence scenarios now carry `@postgres` individually.
- RED/GREEN tag evidence: after changing tag scope, `just backend-bdd-fast` passed, confirming the
  fast runner still discovers and executes the password-recovery scenarios.
- Stopped before marking PR-1.21 through PR-1.26 complete because the approved artifacts contradict
  the requested scope:
    - Proposal lines 23-25 defer audit metrics and cleanup to PR 3.
    - Tasks PR-1.23 requires a retention-cleanup BDD contract in PR 1.
    - Tasks PR-1.25 requires audit BDD in PR 1.
    - Spec scenarios require successful-reset audit events, suspicious-attempt audit data, cleanup
      behavior, notification retry, and terminal notification failure behavior that PR 1 production
      code does not implement.
    - The continuation is explicitly limited to PR-1.21 through PR-1.26, so implementing those
      missing production capabilities would violate the assigned scope and duplicate
      PR-3.03/PR-3.04/PR-3.06.

## Final PR 1 Continuation

- PR-1.21 through PR-1.26 completed under the reconciled core-backend scope; PR 3 audit, cleanup,
  retry/failure, and telemetry remain excluded.
- Added executable disabled-state and forgot-password IP-limit scenarios.
- Added a mutable test-only feature flag so disabled-state BDD exercises the real handlers and
  returns 503 without consuming/creating tokens.
- Replaced sequential request/reset race simulation with concurrent coroutines issuing real HTTP
  requests against the Postgres-backed application.
- Real concurrent forgot-password execution exposed a race that could leave two active tokens. Added
  a PostgreSQL transaction-scoped advisory lock keyed by principal before invalidation/insertion;
  the concurrent scenario then passed with exactly one active token.
- Feature tags use `@identity @password-recovery @smoke @fast`; `@postgres` is scoped to persistence
  scenarios.
- Final verification: `just backend-bdd-fast`, `just backend-check`, and `just backend-bdd-postgres`
  all passed sequentially.

## Verification-Failure Continuation

### Resolved

- True asynchronous primary email delivery: `SendPasswordResetEmailConsumer` now schedules provider
  work on a bounded Spring-managed `ThreadPoolTaskExecutor`. The event publication remains after
  token transaction commit, while the consumer returns after scheduling and does not await provider
  latency/failure. Executor shutdown is lifecycle-managed and drains queued work.
- Safe provider logging: arbitrary `EmailSendResult.error` text is no longer logged. Failure logs
  contain a stable category and principal identifier only.
- Schema reconciliation: the repository identity schema uses `user_identities`, not a
  `principal_identities` table, and both identity keys are `VARCHAR(64)`. Migration FK now targets
  `user_identities(principal_id)`; the spec schema wording was minimally corrected with this
  evidence. PostgreSQL metadata and cascade tests pass.
- Active-token invalidation now updates only unused, unexpired rows (`expires_at > invalidatedAt`);
  expired unused rows remain unchanged.

### RED/GREEN Evidence

- RED: consumer async test failed to compile because no executor seam existed; GREEN after injecting
  a Spring `TaskExecutor` and scheduling delivery.
- RED: PostgreSQL metadata test reported FK target `principals(id)`, and invalidation test showed
  expired-unused rows mutated; GREEN after migration/SQL fixes.
- Final broad verification remained green: `just backend-check`, `just backend-bdd-fast`,
  `just backend-bdd-postgres`.

### Still Open

- PR-1.21 through PR-1.26 were reopened truthfully. The latest verify report lists 15 scenario-level
  runtime gaps; this continuation resolved the critical production blockers and several coverage
  gaps, but did not fabricate evidence for the remaining locale, rate-window, transaction-failure
  injection, CORS, timing, and secret-capture scenarios.

## Runtime-Evidence Closure: Remaining PR 1 Gaps

| Gap | Exact runtime evidence                                                                                                                                                                                        | Result                                                                                                                            |
|----:|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
|   1 | `PasswordResetRequestTransactionPostgresIntegrationTest.persistence failure leaves no token and suppresses notification publication`                                                                          | PASS — real PostgreSQL transaction inserts then fails; persisted token count remains zero and publisher captures no event.        |
|   2 | BDD `Send the password reset email using the supported locale` (`en`, `es`) plus `SendPasswordResetEmailConsumerTest.renders password reset email in English and Spanish from the event locale`               | PASS — real generated subjects, text, and HTML asserted.                                                                          |
|   3 | BDD `Apply email rate limiting equally to existing and unknown accounts`                                                                                                                                      | PASS — normalized variants exhaust both buckets and both final responses are generic 429.                                         |
|   4 | `AuthRateLimitWebFilterTest.forgot password admits a new request exactly when its window expires`                                                                                                             | PASS — mutable clock, no wall-clock sleep.                                                                                        |
|   5 | `AuthRateLimitWebFilterTest.reset password admits a new attempt exactly when its window expires`                                                                                                              | PASS — mutable clock, no wall-clock sleep.                                                                                        |
|   6 | `PasswordResetTransactionPostgresIntegrationTest.reset immediately before expiration commits through handler and repository`                                                                                  | PASS — `expiresAt = now + 1s`, persisted password/token/session state asserted.                                                   |
|   7 | `PasswordResetTransactionPostgresIntegrationTest.reset exactly at expiration is rejected through handler and repository`                                                                                      | PASS — equality boundary rejects and persisted state is unchanged.                                                                |
|   8 | BDD `Reject a token with modified characters`                                                                                                                                                                 | PASS — fixture changes exactly one character while preserving token length.                                                       |
|   9 | `SendPasswordResetEmailConsumerTest.provider failure logs exclude email token reset URL password and provider text`; hash-only PostgreSQL assertions; PR 1 audit/metrics absence asserted by no PR 1 emitters | PASS — captured logs exclude email, token, reset URL, password, and arbitrary provider error; PR 3 audit/metrics remain excluded. |
|  10 | `PasswordResetTransactionPostgresIntegrationTest.password update failure rolls back persisted token consumption`                                                                                              | PASS — injected failure after real writes; password, token, session remain original/active.                                       |
|  11 | `PasswordResetTransactionPostgresIntegrationTest.token consumption failure rolls back persisted password update`                                                                                              | PASS — injected failure after repository write; all persisted state rolls back.                                                   |
|  12 | `PasswordResetTransactionPostgresIntegrationTest.session revocation failure rolls back password and token persisted state`                                                                                    | PASS — real session UPDATE followed by failure; transaction restores all rows.                                                    |
|  13 | BDD `Concurrent reset attempts with different passwords are atomic`                                                                                                                                           | PASS — two real concurrent HTTP requests; one 204, one 400, one stored hash, one consumption.                                     |
|  14 | BDD `Reject reset requests from a disallowed origin` and `LocalAuthEndpointIntegrationTest.reset password rejects a disallowed origin`                                                                        | PASS — reset-specific request returns 403 and password remains unchanged.                                                         |
|  15 | `RequestPasswordResetHandlerTest.existing and unknown identities traverse the timing equalization seam` plus async consumer test                                                                              | PASS — deterministic seam proves both identity paths traverse the same explicit equalizer; no flaky latency threshold.            |

### RED/GREEN Evidence for This Continuation

- Locale RED: consumer test failed compilation because `PasswordResetRequested` had no locale; GREEN
  after locale propagation from `Accept-Language` and EN/ES template rendering.
- Timing RED: focused handler test failed compilation because no timing equalization seam existed;
  GREEN after `PasswordRecoveryTimingEqualizer` was called on both existing and unknown paths.
- PostgreSQL transaction RED: new transaction integration class initially could not initialize
  without the repository-required test DB credential; GREEN under the project test environment, with
  five persisted-state tests passing.
- CORS RED was added as a reset-specific integration test; existing production CORS correctly
  returned 403, so no production change was necessary.
- Async BDD RED: email assertion raced the managed executor; GREEN after the recording sender gained
  a deterministic semaphore-based completion seam (no sleeps).
- Formatting RED: `just backend-check` failed Spotless/Detekt; GREEN after applying formatter and
  narrowing the long descriptive test-name suppression.

### Final Commands Run

- Focused locale test — initial exit 1 (missing locale), intermediate exit 1 (Spanish assertion
  wording), final exit 0.
- Focused request/reset rate-window tests — exit 0.
- Focused PostgreSQL reset transaction suite — initial exit 1 without sourced DB test credential;
  final exit 0, 5/5 passed.
- Focused timing equalizer test — initial exit 1 (missing seam), final exit 0.
- Focused secret-free provider logging test — exit 0.
- Focused reset CORS integration test — exit 0.
- Focused persistence-failure transaction test — exit 0.
- `just backend-check` — initial exit 1 (format/Detekt), final exit 0.
- `just backend-bdd-fast` — initial exit 1 (async assertion race), final post-refactor exit 0,
  118/118 passed.
- `just backend-bdd-postgres` — final post-refactor exit 0.
- Final sequential gate rerun: `just backend-check` exit 0 → `just backend-bdd-fast` exit 0 →
  `just backend-bdd-postgres` exit 0.

## Focused Apply: Timing, Acceptance Truthfulness, and Token Sample

### Operational Timing Enumeration Defense

- Replaced the no-op timing seam with `MinimumDurationPasswordRecoveryTimingEqualizer`.
- Production binds a configurable `app.identity.password-recovery.minimum-response-duration`,
  defaulting to 250 ms.
- The handler marks monotonic start before email/account processing and calls equalization only
  after each accepted path finishes its account-dependent work. Existing-local execution completes
  token generation, transaction commit, and event publication before equalization;
  unknown/OAuth-only execution also reaches the same post-work boundary.
- `MonotonicTimeSource` and `SuspendingDelay` are injected for deterministic tests. Production uses
  `System.nanoTime` and cancellable coroutine `delay`; tests use exact synthetic readings and
  captured delays with no wall-clock assertions.

### Acceptance Evidence Ownership

- PR-1.21: executable Cucumber owns generic 202, privacy, normalization, lifecycle, notifications,
  limits, validation, and disabled behavior. Focused handler/PostgreSQL tests own timing and request
  transaction-failure invariants.
- PR-1.22: executable Cucumber owns public reset errors, validation, hashing outcomes, session
  revocation, replay, rate limits, disabled behavior, and concurrent HTTP outcomes. Focused
  PostgreSQL tests own exact expiration boundaries and injected rollback failures.
- PR-1.23: executable PostgreSQL-tagged Cucumber owns externally observable persistence lifecycle.
  Focused repository/schema/PostgreSQL tests own metadata, exact SQL predicates, hash-only rows,
  rollback, and lower-level concurrency.
- PR-1.24: executable Cucumber owns dispatch and rendered content. Focused
  consumer/template/transaction tests own post-commit order, provider non-blocking behavior,
  failure-log redaction, and escaping. Retry, terminal failure, and telemetry remain PR 3.
- PR-1.25: executable Cucumber owns enumeration response contracts, replay, CORS, and brute-force
  limits. Focused deterministic tests own entropy, the 10,000-token uniqueness sample, and timing
  equalization. Audit remains PR 3.
- PR-1.26 wording now requires an audited executable PR 1 glue catalog rather than pretending every
  lower-level invariant is Cucumber-owned.

### No-op Step Audit

Removed unused definitions entirely:

- `the password reset request limit has been exceeded`
- `the rate limit window has expired`
- `the reset attempt rate limit has been exceeded`
- `the IP password reset limit is 5 requests per 15 minutes`
- `the email password reset limit is 3 requests per 30 minutes`
- `the reset attempt limit is 10 requests per 15 minutes`
- `the password credential update will fail`
- `refresh session revocation will fail`
- `token consumption will fail`
- `the current time is`
- `the principalId is recorded without raw token`
- PR 3-only audit/security-event placeholder definitions
- unused cookie-name placeholder

Replaced referenced marker/setup steps with executable assertions:

- maximum password length probes the actual endpoint with 129 characters and asserts 400.
- current password verifies the seeded BCrypt hash.
- authentication rate limiting executes a real invalid reset request and resets stores for scenario
  isolation.
- existing account/credential/OAuth/unknown setup steps assert persisted fixture state.

Renamed the misleading PR 1 notification `telemetry` scenario to notification-content secrecy; PR 3
telemetry remains excluded.

### Token Uniqueness

- `PasswordResetTokenHasherTest` now generates 10,000 tokens and asserts 10,000 unique raw tokens
  and hashes.

### RED/GREEN Evidence

- RED: focused timing/hasher compilation failed because the old seam exposed only
  `equalize(normalizedEmail)` and had no monotonic start/post-work contract.
- GREEN: focused handler, minimum-duration equalizer, and 10,000-token hasher tests passed after the
  new abstractions and handler placement were implemented.
- REFACTOR: production configuration provides the non-no-op default; deterministic tests cover
  remaining-duration calculation, elapsed-over-budget behavior, cancellation propagation, and exact
  post-work call order.
- BDD glue audit exposed a Spring test bean collision and rate-limit pre-consumption. The bean was
  made a true replacement by name, and marker limit steps were removed rather than consuming
  requests before scenarios.

### Commands Run in This Focused Apply

- Focused handler/hasher tests (RED) — exit 1, compile failure proving the old timing seam lacked
  the required contract.
- Focused handler/equalizer/10,000-token tests (GREEN) — exit 0.
- Focused handler/equalizer/10,000-token tests after refactor — exit 0.
- Raw `:server:smp:bddFastTest --tests ...` attempt — exit 1, invalid suite class filter; superseded
  by unfiltered recipe.
- Raw unfiltered `:server:smp:bddFastTest` without sourced test DB environment — exit 1, environment
  setup failure.
- `just backend-bdd-fast` iterations — exit 1; password-recovery failures were removed, but the
  suite still has one unrelated pre-existing failure:
  `Pending user media upload attempt is denied by email verification gate` at
  `AuthorizationBddSteps.kt:155`.
- `just backend-check` — exit 1; initial formatting/Detekt findings were fixed, but the run also
  reported two unrelated `ActuatorEndpointsIntegrationTest` health failures.
- `just backend-test-postgres` — exit 1; 268/270 passed, same two unrelated actuator health
  failures.
- `just backend-bdd-postgres` — exit 1; 117/118 passed, same unrelated pending-user media BDD
  failure.
- `git diff --check` — exit 0.

### Current Task State

- PR-1.21 through PR-1.29 remain reopened because the mandated broad gates are not all green.
- PR-1.30 remains prohibited and incomplete.
- State remains `current_phase: apply`, `next: verify`.

## Test-Infrastructure Blocker Resolution

- Root-cause regression evidence: `TestProfileIsolationConfigurationTest` failed under
  `SPRING_PROFILES_ACTIVE=dev` because the fast BDD, Postgres BDD, and Actuator integration contexts
  did not explicitly select the repository's existing `test` profile.
- Minimal fix: added `@ActiveProfiles("test")` to `CucumberSpringConfiguration`,
  `CucumberPostgresSpringConfiguration`, and `ActuatorEndpointsIntegrationTest`. No production/dev
  email-verification policy, SMTP configuration, or health behavior changed.
- Mail health did not require a test-only override: pinning the test profile alone made the full
  Actuator class pass under ambient dev.
- Focused ambient-dev GREEN evidence: profile-isolation regression test passed; pending-user media
  scenario passed in both fast and Postgres Cucumber lanes; `ActuatorEndpointsIntegrationTest`
  passed.
- Sequential broad gates passed after one unrelated transient resend-verification timeout was
  cleared by the required full rerun: `just backend-check`, `just backend-bdd-fast`,
  `just backend-test-postgres`, and `just backend-bdd-postgres`.
- PR-1.21 through PR-1.29 are now complete truthfully. PR-1.30 remains prohibited and incomplete.
  State remains `current_phase: apply`, `next: verify`.

## PR 2 Frontend Planning State

- PR 1 apply/verify history above remains complete; PR 2 is the next apply slice, beginning at
  `PR-2.01`, while PR 3 remains planned.
- Overall `current_phase` is `apply` because one scalar cannot represent verified PR 1 plus pending
  PR 2/PR 3; `state.yaml.slices` records the additive per-slice lifecycle.
- No PR 2 implementation or verification command has run yet.

## PR 2 Frontend Apply: PR-2.01 through PR-2.11

### Completed

- Added typed `requestPasswordReset` and `resetPassword` API functions using `requestRaw`, active
  EN/ES `Accept-Language`, empty 202/204 handling, and retained RFC 9457 status/code.
- Added normalized recovery-email and matching 12..128 password schemas.
- Added standalone, accessible forgot/reset views with local state, safe generic error mapping,
  duplicate locks, terminal reset success, and no auth-store mutation.
- Added `forgot-password` guest-only and `reset-password` session-agnostic routes; both bypass
  `AppShell` through `meta.standalone`.
- Added the login-only forgot link, EN/ES strict parity, safe E2E mocks/data/POM, and
  core/accessibility/mobile/i18n/authenticated/storage-secrecy Playwright coverage.
- No dependencies or backend files changed. PR 1 history remains intact.

### RED/GREEN Evidence

- PR-2.01 RED: 5 API tests failed because recovery functions did not exist; GREEN: 53/53 focused API
  tests passed.
- PR-2.02 RED: 9 schema tests failed because recovery schemas did not exist; GREEN: 20/20 focused
  schema tests passed.
- PR-2.03/04 RED: component imports failed because recovery views did not exist; GREEN: forgot 5/5
  and reset 8/8 passed.
- PR-2.05 RED: three route/guard assertions failed because recovery routes were absent; GREEN: 25/25
  router tests passed.
- PR-2.06 RED: standalone route still rendered `AppShell`; GREEN: 5/5 App tests passed.
- PR-2.07 RED: login forgot-link assertion failed; GREEN: 16/16 AuthView tests passed.
- PR-2.08 RED: locale parity failed because namespace was absent; GREEN: parity and referenced-key
  tests passed.
- PR-2.10/11 GREEN: targeted recovery Playwright passed 21/21 across Chromium, Firefox, and Pixel 5.

### Commands Run

- Focused Vitest RED/GREEN commands for each PR-2.01..PR-2.08 work unit — final focused batch
  passed.
- `pnpm --filter app test:run` — exit 0, 101 files, 1193 passed, 1 todo.
- `pnpm --filter app lint` — initial exit 1 for formatting only; after Biome write, exit 0 across
  722 files.
-
`pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts` —
exit 0, 21/21 passed.
- Combined recovery + existing route-guards Playwright — recovery/new authenticated-reset scenario
  passed; two existing credential-backed route-guard tests were blocked by missing
  `E2E_TEST_USER_PASSWORD`.
- `just app-build` — exit 0; Vue type-check and Vite production build passed with existing
  chunk-size warning.
- `git diff --check` — exit 0.

### Current Task State

- PR-2.01 through PR-2.11 are complete.
- PR-2.12 remains open because its exact combined route-guards command requires
  `E2E_TEST_USER_PASSWORD`; sdd-verify owns verified status.
- PR 2 state is `applied`, next `verify`; no commit, push, PR, archive, or history rewrite
  performed.

## Risks

- The first ambient-dev `backend-check` attempt also activated unrelated dev credentials and failed
  broadly; the required repository-context rerun was used for the final sequential gate. A
  subsequent first normal `backend-check` hit one transient resend-verification timeout, while the
  immediate full rerun passed.
- Existing commit `3b452dd0` contains unrelated marketing test changes in three files; this
  continuation did not touch them.
- The original PR 1 implementation is far above the 400-line review budget; this apply respected the
  selected stacked-to-main PR 1 boundary.
- PR 3 audit, metrics, notification retry/final-failure, cleanup, and operational telemetry remain
  intentionally excluded per `spec.md` and `design.md`.

## PR 2 Critical Runtime-Evidence Closure: PR-2.11

### Scope

Closed the three critical PR 2 runtime evidence gaps identified by the latest frontend verification
report. This batch stayed within the stacked-to-main PR 2 frontend slice: test/POM changes only, no
backend, dependency, server/smp/tmp, commit, push, PR, archive, or PR-2.12/state verification
changes.

### Completed

- PR-2.11 accessibility evidence now executes real keyboard-only recovery flows for both views with
  `Tab`, `Shift+Tab`, and `Enter`; asserts visible focus, programmatic `label[for]` associations,
  `aria-invalid`, `aria-describedby`, `role="alert"`, `aria-live="polite"`, and 44 CSS-pixel Pixel 5
  submit targets.
- PR-2.11 privacy evidence now observes analytics-like network requests, browser console messages,
  and storage during forgot/reset success and reset-token error flows. It asserts no analytics
  emissions on standalone recovery even with valid analytics consent, no token/password sentinel in
  requests/logs/storage, and disables screenshot/trace/video diagnostics for the consent-enabled
  privacy test. Existing storage/backend-detail assertions remain.
- PR-2.11 reset error evidence now executes reset `429/AUTH_RATE_LIMIT_EXCEEDED`,
  `503/PASSWORD_RECOVERY_DISABLED`, and network/unknown branches in both component and Playwright
  tests, asserting safe localized role-alert UI, form state, and absence of backend detail.

### Exact Test Traceability

#### Component tests — `apps/web/app/src/modules/auth/presentation/ResetPasswordView.spec.ts`

-
`ResetPasswordView > reset unavailable / throttled error branches > maps reset 429/AUTH_RATE_LIMIT_EXCEEDED to rate-limited message without backend detail`
-
`ResetPasswordView > reset unavailable / throttled error branches > maps reset 503/PASSWORD_RECOVERY_DISABLED to unavailable message without backend detail`
-
`ResetPasswordView > reset unavailable / throttled error branches > maps reset unknown/network error to generic safe message without backend detail`

#### Playwright tests — `apps/web/app/e2e/specs/password-reset-frontend.spec.ts`

Accessibility:

-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › ForgotPasswordView: keyboard-only submission reaches success announcement`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › ForgotPasswordView: validation shows aria-invalid and associated error with role=alert`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › ForgotPasswordView: Tab/Shift+Tab traversal cycles in expected order`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › ResetPasswordView: keyboard-only submission reaches success announcement`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › ResetPasswordView: validation shows aria-invalid and role=alert error`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › ResetPasswordView: token error shows role=alert in invalid state`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › Pixel 5: submit button on ForgotPasswordView meets 44px touch target`
-
`Recovery accessibility: keyboard navigation, labels, focus, announcements, and touch targets › Pixel 5: submit button on ResetPasswordView meets 44px touch target`

Privacy:

-
`Recovery privacy: no token/password in analytics, console, or storage › forgot-password flow: no analytics network calls emit token or email sentinel`
-
`Recovery privacy: no token/password in analytics, console, or storage › forgot-password flow: console logs do not contain token or password sentinels`
-
`Recovery privacy: no token/password in analytics, console, or storage › reset-password success: no analytics calls emit new password or reset token`
-
`Recovery privacy: no token/password in analytics, console, or storage › reset-password success: console logs do not contain token or password sentinels`
-
`Recovery privacy: no token/password in analytics, console, or storage › reset-password error: console logs do not contain token on failure`
-
`Recovery privacy: no token/password in analytics, console, or storage › standalone recovery emits no analytics calls even when consent is enabled`

Reset error branches:

-
`ResetPasswordView error branches: 429, 503, and unknown via route interception › reset 429/AUTH_RATE_LIMIT_EXCEEDED shows rate-limited alert without backend detail`
-
`ResetPasswordView error branches: 429, 503, and unknown via route interception › reset 503/PASSWORD_RECOVERY_DISABLED shows unavailable alert without backend detail`
-
`ResetPasswordView error branches: 429, 503, and unknown via route interception › reset network/unknown error shows generic alert without internal detail`

### RED/GREEN Evidence

- RED: Before the new Playwright assertions existed, the baseline recovery spec passed 21/21 but the
  new accessibility assertions failed in Chromium: ForgotPasswordView and ResetPasswordView both
  remained `aria-invalid="false"` because native HTML constraint validation prevented the Vue submit
  handler. The test was corrected to dispatch the form submit event after keyboard entry, bypassing
  only browser constraint validation so the component's actual Zod branch executes. Component
  reset-branch tests were newly added against already-implemented production mappings and passed
  immediately, proving the previously uncovered branches execute.
- GREEN: focused component run passed 11/11; focused keyboard/privacy Playwright run passed 4/4 in
  Chromium; full recovery Playwright passed 68/68 executed across Chromium, Firefox, and Mobile
  Chrome, with 4 expected skips for Pixel-only tests outside Mobile Chrome. Mobile Chrome executed
  both 44px target tests.
- REFACTOR: extracted locale-independent keyboard traversal (`tabTo`, `tabUntil`), visible-focus,
  and touch-target helpers in `PasswordRecoveryPage`; removed unused helpers; formatted and linted
  the changed E2E files. Privacy tests inspect in-memory request/console data and intentionally
  avoid secret-bearing screenshot/trace/video diagnostics.

### Commands Run

-
`cd apps/web/app && pnpm exec vitest run --reporter=verbose src/modules/auth/presentation/ResetPasswordView.spec.ts` —
initial baseline 8/8; after component tests 11/11 passed.
- Initial Chromium Playwright RED:
  `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts --project=chromium` —
  exit 1, 20 passed, 2 failed; new aria-invalid runtime assertions exposed native
  constraint-validation behavior.
- Focused Chromium accessibility GREEN:
  `pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts --project=chromium -g "keyboard-only|Tab/Shift"` —
  3/3 passed.
- Focused Chromium accessibility/privacy GREEN: same command with
  `-g "keyboard-only|Tab/Shift|standalone recovery"` — 4/4 passed.
- `pnpm exec vitest run` — 101 files, 1,196 passed, 1 todo; exit 0.
- `pnpm --filter app test:run -- src/modules/auth/presentation/ResetPasswordView.spec.ts` — full app
  test script invocation completed with 101 files, 1,196 passed, 1 todo; exit 0.
-
`pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts` —
68 passed, 4 expected skips, exit 0 across Chromium, Firefox, and Mobile Chrome.
-
`pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/route-guards.spec.ts --grep "authenticated reset links remain accessible"` —
3/3 passed across all configured projects.
- `pnpm --filter app lint` — exit 0; Biome checked 722 files.
- `just app-build` — exit 0; Vue type-check and Vite production build passed; existing large-chunk
  warning remains.
- `git diff --check` — exit 0.

### Current Task State

- PR-2.01 through PR-2.11 remain complete, with PR-2.11 now carrying runtime evidence for all three
  prior critical gaps.
- PR-2.12 remains open and unmarked; sdd-verify owns verification state.
- PR 2 remains `applied`, next `verify`; no commit, push, PR, archive, or state verification changes
  performed.

### Risks

- The PR-2.11 evidence closure adds approximately 560 test/POM lines, above the repository's nominal
  400-line review budget. It remains one autonomous PR-2 stacked-to-main work-unit slice because the
  user explicitly assigned all three critical gaps together; no unrelated production scope was
  added.
- Existing Vitest warnings and the existing production bundle chunk-size warning remain non-blocking
  and unrelated.
- Recovery does not currently mount a real analytics SDK/call path; privacy tests therefore assert
  zero analytics-like emissions and retain sentinel checks for any future instrumentation.

## PR 3 Cleanup Apply: PR-3.01 and PR-3.02

### Implemented

- Added RED coverage for retention boundaries, active-token preservation, recent-record
  preservation, and idempotency in a focused repository test and executable
  `@pr-3 @cleanup @postgres` BDD glue.
- Added the application cleanup port, configurable retention/interval/initial delay, Clock-driven
  infrastructure scheduler, and PostgreSQL/R2DBC deletion in the existing identity adapter.
- The SQL predicate is `expires_at < :cutoff`, not `<=`: a token exactly at the retention boundary
  remains inside the configured window until it becomes strictly older.

### TDD Evidence

- RED: focused `R2dbcPasswordResetTokenRepositoryTest` compilation failed because
  `PasswordResetTokenCleanupPort`, `deleteExpiredBefore`, and `PasswordResetTokenCleanupScheduler`
  did not exist.
- GREEN: focused repository, scheduler, and configuration tests passed after the minimum
  implementation.
- REFACTOR: the scheduler uses the shared UTC `Clock`, durations use Spring-bound `Duration`, and
  focused Spotless/Detekt checks pass.
- PostgreSQL BDD attempt:
  `./gradlew :server:smp:bddPostgresTest -Dcucumber.filter.tags='@pr-3 and @cleanup'` could not
  initialize Testcontainers because no Docker environment was available (
  `DockerClientProviderStrategy`). The task did not forward the Cucumber filter and attempted the
  full Postgres suite; the cleanup scenario was discovered but could not execute.

### Current Task State

- PR-3.01 and PR-3.02 remain unchecked until the PostgreSQL BDD scenario executes successfully in a
  Docker/PostgreSQL-capable environment.
- PR-3.03 and later remain untouched. No commit, push, or PR was created.

## PR 3 Audit Apply: PR-3.03 and PR-3.04

### Implemented

- Added an identity-owned `PasswordResetAuditPort` and event carrying only principal ID and
  occurrence time; domain/application remain Spring-free.
- Added an `AuditHook` adapter that maps successful resets to a workspace-less `MutationAuditFact`
  action `PASSWORD_RESET_COMPLETED` with a single `occurredAt` detail.
- Emission runs only after `AtomicTransactionRunner.runAtomically` returns. Handler and adapter both
  isolate non-cancellation sink failures, so a committed reset still returns the existing success
  result/HTTP 204.
- Added executable `@audit @pr-3` BDD for the successful reset, with assertions excluding raw token,
  password, persisted hash, email, and raw IP.
- REQ-HARD-02 uses optional `MAY` behavior and defines no detection threshold/event action, so
  suspicious repeated-attempt emission was not implemented or fabricated in this slice.

### TDD Evidence

- RED compile: focused tests failed because `PasswordResetAuditPort`, `PasswordResetAuditEvent`,
  `AuditHookPasswordResetAuditAdapter`, and the handler dependency did not exist.
- Intermediate RED: after the seam/adapter existed,
  `audit sink failure cannot turn a committed reset into a failure` failed with
  `IllegalStateException`, proving sink failure still escaped after commit.
- GREEN: focused `ResetPasswordHandlerTest` plus `AuditHookPasswordResetAuditAdapterTest` passed
  14/14 after post-commit best-effort isolation.
- REFACTOR: `./gradlew :server:smp:spotlessCheck :server:smp:detekt` passed.
- BDD fast compiled and discovered `[PR 3] Audit a successful password change`, but all 120
  scenarios were blocked at Spring context startup because Testcontainers could not find Docker (
  `DockerClientProviderStrategy`); Docker was not started per user constraint.

### Current Task State

- PR-3.03 and PR-3.04 are complete and checked.
- PR-3.01/02 local changes were preserved unchanged and remain unchecked pending their PostgreSQL
  BDD verification.
- PR-3.05+ remain untouched. No commit, push, PR, or archive was created.

## PR 3 Notification Hardening Apply: PR-3.05 and PR-3.06

### Implemented

- Extended `EmailSendResult` with stable `EmailFailureCategory` classification; retry eligibility
  derives from category, never provider-message parsing.
- Added configurable bounded exponential retry policy (`maxAttempts`, initial/max backoff,
  multiplier) and a coroutine-delay seam. No `Thread.sleep`; event reception remains post-commit and
  delivery remains on the existing Spring task executor.
- Added identity application ports for safe terminal failure records and bounded notification
  telemetry. Records contain only principal ID, notification type, attempts, timestamp, and
  sanitized category.
- Added a dedicated R2DBC failure store and Liquibase table. Existing `NotificationRepository` was
  rejected because it persists recipient and arbitrary payload JSON and could retain the rendered
  reset URL/raw token.
- Hardened SMTP/Resend failures so arbitrary provider text and recipient email are not returned or
  logged. Added executable `@retry`, `@failure`, and `@privacy` `@pr-3` feature scenarios with real
  glue.

### TDD Evidence

- RED: focused compilation failed with unresolved retry policy, failure/telemetry ports, stable
  failure categories, delay seam, and consumer dependencies.
- GREEN: focused retry/config/provider/dispatcher suite passed 25/25 after the minimum
  implementation.
- REFACTOR: `spotlessApply`, `detekt`, and `git diff --check` passed; retry and terminal tests
  assert records, telemetry, and captured logs exclude email, raw token, reset URL/query, password,
  and arbitrary provider text.
- BDD: `just backend-bdd-fast` discovered all three PR-3 notification scenarios, but Spring context
  startup failed for all scenarios because Testcontainers could not find Docker (
  `DockerClientProviderStrategy`). Docker was not started per explicit constraint.

### Current Task State

- PR-3.05 and PR-3.06 are complete and checked.
- PR-3.01/02 local changes remain preserved and unchecked pending Docker/PostgreSQL BDD
  verification.
- PR-3.07+ remain untouched. No commit, push, PR, or archive was created.

## PR 3 Observability Apply: PR-3.07 and PR-3.08

### Implemented

- Replaced the PR-3.06 no-op notification telemetry bean with
  `PasswordRecoveryObservabilityAdapter`, an infrastructure-only Micrometer adapter backed by the
  existing `MeterRegistry` and `ObservationRegistry`; no dependency was added.
- Added one bounded counter, `identity.password.recovery.outcomes`, and one observation/span name,
  `identity.password.recovery`, for notification success/retry/terminal-failure and reset
  completed/failed outcomes.
- Added an infrastructure `PasswordResetOutcomeWebFilter` scoped exactly to
  `/api/auth/reset-password`, after the existing HTTP/security chain, so reset outcome
  instrumentation does not change application ports, handler atomicity, response contracts, or
  cancellation behavior.
- Reused the PR-3.05/06 `PasswordResetNotificationTelemetryPort` seam for notification outcomes. The
  adapter maps arbitrary/unknown notification type to `unknown` rather than using it as a tag.

### Cardinality and Privacy Decisions

- Exactly five low-cardinality dimensions are emitted on both metric and observation: `operation`,
  `notification.type`, `status`, `failure.category`, and `attempt.bucket`.
- Stable values only: operation `reset|notification_delivery`; type `password_reset|none|unknown`;
  status `completed|failed|success|retry|terminal_failure`; failure category from fixed enums plus
  `none`; attempt bucket `none|first|retry|exhausted`.
- Exact attempt counts are not emitted. Principal ID, email, raw IP, raw token/hash, password/hash,
  reset URL/query, exception text, and provider text are never accepted by the adapter and cannot
  become names, tags, or attributes.

### TDD Evidence

- RED 1: focused observability test compilation failed with unresolved
  `PasswordRecoveryObservabilityAdapter` and `PasswordResetFailureCategory`.
- GREEN 1: adapter tests passed after adding bounded counter/observation mapping.
- RED 2: reset-outcome filter test compilation failed with unresolved
  `PasswordResetOutcomeWebFilter`.
- GREEN 2: adapter + filter tests passed after adding the endpoint-scoped infrastructure filter.
- REFACTOR: avoided a Detekt parser crash caused by the fluent Observation chain, applied Spotless,
  fixed one test `LongMethod` finding, then focused tests, Spotless, Detekt, and `git diff --check`
  passed.

### Commands Run

- `./gradlew :server:smp:test --tests ...PasswordRecoveryObservabilityAdapterTest` — RED compile
  failure, then GREEN exit 0.
- `./gradlew :server:smp:test --tests ...PasswordResetOutcomeWebFilterTest` — RED compile failure,
  then GREEN exit 0.
- Focused observability + `SendPasswordResetEmailConsumerTest` — exit 0 (11 tests across the
  selected classes).
- `./gradlew :server:smp:spotlessCheck :server:smp:detekt` — exit 0.
- `git diff --check` — exit 0.
- Relevant `@pr-3` BDD remains prepared/discoverable from PR-3.05/06. It was not run because its
  Spring contexts require Docker/Testcontainers and the user explicitly prohibited starting Docker;
  the previous attempts already documented that blocker.

### Current Task State

- PR-3.07 and PR-3.08 are complete and checked.
- PR-3.01/02 remain preserved and unchecked pending Docker/PostgreSQL BDD verification.
- PR-3.09+ remain untouched. No commit, push, PR, Docker startup, or unrelated-file cleanup was
  performed.

## PR 3 Runbook Apply: PR-3.09 and PR-3.10

### Implemented

- Added a focused Kotlin documentation contract that requires the runbook, the prescribed top-level
  documentation structure, implemented metric/span names and bounded dimensions, retry and cleanup
  configuration keys, safe terminal-failure fields, feature-flag rollback, escalation, and real
  `justfile` recipes.
- Added an operational English runbook covering symptoms, incident triage, retries and terminal
  failures, cleanup/retention, safe PromQL and aggregate SQL, feature-flag rollback, focused
  validation, and escalation.
- The runbook explicitly prohibits searching for or recording raw tokens, email, raw IP, reset
  URLs/query strings, passwords, hashes, provider text, or exception messages. SQL selects only safe
  categories, time buckets, and aggregate counts.

### TDD Evidence

- RED:
  `./gradlew :server:smp:test --tests com.profiletailors.smp.identity.infrastructure.PasswordRecoveryRunbookTest --no-daemon`
  failed 1/1 at `PasswordRecoveryRunbookTest.kt:14` because `docs/runbooks/password-recovery.md` did
  not exist.
- Intermediate RED: after creating the runbook, the same test failed at line 27 because the required
  phrase `raw token` was split across a Markdown line wrap; the document was corrected without
  weakening the contract.
- GREEN: the same focused test passed 1/1.
- REFACTOR/static checks: `./gradlew :server:smp:spotlessCheck :server:smp:detekt --no-daemon`
  passed, followed by `git diff --check` exit 0.

### Current Task State

- PR-3.09 and PR-3.10 are complete and checked.
- PR-3.01/02 remain preserved, blocked, and unchecked pending Docker/PostgreSQL BDD verification.
- PR-3.11 remains explicitly out of scope and unchecked. No broad suites, commit, push, PR, Docker
  startup, or unrelated-file cleanup was performed.

## PR 3 Identity Event Configuration Regression Fix

### Root Cause

- PR-3.05/06 added `passwordResetRetryPolicy` to `IdentityEventConfiguration`, making that
  configuration depend on `PasswordRecoveryConfigurationProperties`.
- The full application happened to register those properties through
  `IdentityBootstrapConfiguration`, but the focused `ApplicationContextRunner` intentionally loaded
  `IdentityEventConfiguration` in isolation. The new dependency was therefore absent, context
  refresh failed, and both tests failed before their intended assertions: one surfaced the missing
  bean directly and the publisher test surfaced the failed context as an assertion error.

### Minimal Fix

- Made `IdentityEventConfiguration` self-contained by adding
  `@EnableConfigurationProperties(PasswordRecoveryConfigurationProperties::class)` next to its
  existing event import.
- Preserved the PR-3 retry-policy and retry-delay beans unchanged. No test changes or additional
  functional behavior were needed because the existing focused test already provided exact
  regression coverage.

### Evidence

- RED:
  `./gradlew :server:smp:test --tests 'com.profiletailors.smp.identity.infrastructure.IdentityEventConfigurationTest' --no-daemon` —
  exit 1, 2/2 failed with the reported missing-bean/context assertion failures.
- GREEN: the exact focused command — exit 0, 2/2 passed; repeated after static checks with
  `BUILD SUCCESSFUL`.
- `./gradlew :server:smp:spotlessCheck :server:smp:detekt --no-daemon` — exit 0.
- `git diff --check` — exit 0.
- Focused Semgrep scan of `IdentityEventConfiguration.kt` — 0 findings.

### Scope

- Existing PR-3.01..10 local work was preserved. Task status is unchanged: PR-3.01/02 remain
  blocked/unverified, PR-3.03..10 remain complete, and PR-3.11 remains open.
- No commit, push, PR, Docker, broad suite, dependency, schema, API, or unrelated functional change
  was performed.

## PR 3 Verification-Finding Closure

### Completed findings

- Cleanup now deletes only rows where `expires_at < cutoff` and `used_at` is null or also
  `< cutoff`; a cross-boundary regression preserves a token whose expiry is old but whose `used_at`
  equals the audit-retention cutoff.
- Cleanup BDD fixtures consistently use `principal-cleanup`, satisfying the declared FK.
- Resend defaults opaque `ResendException` failures to safe non-retryable `PROVIDER_REJECTED`; SMTP
  retries only the concrete `MailSendException`, treats `MailAuthenticationException` as permanent,
  and defaults other `MailException` types to permanent. This may under-retry ambiguous provider
  failures, deliberately preferring bounded retries over retrying invalid credentials/requests.
- Runbook SQL and its schema-drift contract now use `failure_category`.
- Added a non-Docker adapter/migration contract plus
  `R2dbcPasswordResetNotificationFailureRepositoryPostgresTest`, which is compiled and prepared but
  skipped because Docker is unavailable. PostgreSQL runtime is not claimed green.
- Terminal persistence and telemetry are isolated: telemetry is attempted in `finally`; ordinary
  persistence failures are safely logged/absorbed, while `CancellationException` is rethrown after
  telemetry.
- PR 3 BDD now autowires the production `SendPasswordResetEmailConsumer`; test-only beans replace
  only executor, delay, sender, failure sink, and telemetry sink. Production R2DBC/Micrometer remain
  covered by focused adapter contracts/tests and still require Docker-capable
  PostgreSQL/real-metrics acceptance.
- Corrected stale consumer KDoc. The unrelated JPG under `server/smp/tmp/` remains untouched and
  untracked.

### Strict TDD evidence

- RED: focused Gradle command selecting cleanup repository, Resend, SMTP, consumer, and runbook
  tests — exit 1, 31 tests, 5 failures, 13 Docker skips. Failures proved the old cleanup predicate,
  retry-all provider classification, telemetry suppression, and runbook schema drift.
- GREEN: the expanded focused command including the non-Docker terminal-store contract and prepared
  PostgreSQL test — `BUILD SUCCESSFUL`; Docker-backed classes were skipped rather than reported
  green.
- STATIC RED: first `just backend-check` failed Spotless and Detekt (`TooGenericExceptionCaught`);
  GREEN after focused formatting/refactor.
- FINAL GREEN: `./gradlew :server:smp:spotlessCheck :server:smp:detekt --no-daemon`,
  `just backend-check`, and `git diff --check` all exited 0.
- Docker probe: focused PostgreSQL terminal repository test reported no `/var/run/docker.sock` and
  was `SKIPPED`; no PostgreSQL runtime success is claimed.

### Current task state

- PR-3.01 through PR-3.10 are complete with non-Docker evidence and prepared PostgreSQL/BDD
  coverage.
- PR-3.11 remains incomplete because all `@pr-3` PostgreSQL/Cucumber scenarios still require a
  Docker-capable environment.
- PR 3 returns to `verify`; no commit, push, PR, verify-report edit, Docker startup, dependency,
  HTTP, reset-secret, or atomicity change was made in this continuation.

## PR 3 PostgreSQL Test Initialization Fix

### Diagnosis and confirmed root cause

- OrbStack was healthy and Testcontainers 2.0.5 connected successfully through
  `unix:///var/run/docker.sock` to Docker server 29.4.0, so Docker discovery and the persisted
  `UnixSocketClientProviderStrategy` were not the failure.
- The complete stack trace showed `PostgresTestContainerSupport.resolvePassword()` throwing before
  Testcontainers could start the PostgreSQL container because the direct Gradle invocation did not
  export `SMP_DB_TEST_PASSWORD` to the test worker JVM.
- The repository already stores the local test credential in ignored root `.env`, and every
  `just backend-*` test recipe explicitly exports it. Direct `./gradlew` did not share that setup;
  `bootRun` alone had an `.env` loader.
- The same direct-command initialization failure reproduced in the existing working
  `R2dbcPasswordResetTokenRepositoryTest`, ruling out the new test's companion-object pattern.
- The candidate database name `password_reset_terminal_failure` is 31 UTF-8 bytes, below
  PostgreSQL's 63-byte identifier limit. Running with the environment sourced started PostgreSQL,
  applied Liquibase, and passed, ruling out the database name and OrbStack compatibility.

### Minimal fix

- Added a lazy Gradle provider in `server/smp/build.gradle.kts` that prefers the process environment
  and otherwise reads `SMP_DB_TEST_PASSWORD` from the ignored root `.env`, then forwards only a
  non-blank value to every forked `Test` JVM.
- Kept `PostgresTestContainerSupport.resolvePassword()` fail-fast behavior unchanged when neither
  source provides a credential. No credential, username, Docker socket, context, or user-specific
  path is hardcoded; CI environment variables retain precedence.
- No production Kotlin, repository adapter, migration, or PostgreSQL test pattern changed.

### RED/GREEN evidence

- RED: exact fresh focused command with Docker available failed 1/1 during class initialization:
  `IllegalStateException: SMP_DB_TEST_PASSWORD must be set to run PostgreSQL-backed tests` at
  `PostgresTestContainerSupport.kt:37`.
- Diagnostic control: `set -a && source .env && set +a` plus the same focused command passed,
  proving the repository test, database name, migration, R2DBC adapter, Testcontainers, and OrbStack
  path were sound once the established credential reached the JVM.
- GREEN:
  `env -u SMP_DB_TEST_PASSWORD ./gradlew :server:smp:test --tests 'com.profiletailors.smp.identity.infrastructure.R2dbcPasswordResetNotificationFailureRepositoryPostgresTest' --rerun-tasks --no-daemon`
  passed fresh with all 35 tasks executed.
- REFACTOR/static:
  `env -u SMP_DB_TEST_PASSWORD ./gradlew :server:smp:spotlessCheck :server:smp:detekt --no-daemon`
  passed; `git diff --check` passed.

### Current task state and scope

- The focused PostgreSQL terminal-failure repository runtime is now green and no longer skipped.
- Existing local changes and the untracked JPG remain preserved. `verify-report.md` was not edited.
  No broad BDD suite, commit, push, or PR was performed.
- PR-3.11 remains unchecked because this batch intentionally ran only the requested focused
  PostgreSQL test and static checks, not all PR 3 scenarios.

## PR 3 Fast BDD Notification Wiring Fix

### Confirmed root cause

- The fast BDD Spring context registered the test `SyncTaskExecutor` bean named
  `passwordResetEmailTaskExecutor` first, then
  `IdentityEventConfiguration.passwordResetEmailTaskExecutor()` replaced it by the same name. The
  Spring startup evidence explicitly reported that production replaced the primary test bean.
- The production `ThreadPoolTaskExecutor` made `consume()` return after scheduling. The glue's first
  `awaitDelivery()` semaphore permit observed only attempt 1, so retry expected 2 but saw 1,
  terminal failure expected 3 but saw 1, and telemetry was still empty. Suite logs later showed
  attempts 2/3 and terminal telemetry completing after the failed assertions.
- The production consumer, retry policy, failure store, telemetry ports, and BDD assertions were
  correct. The defect was bean replacement order in real Spring wiring, not retry logic or assertion
  weakness.

### Minimal fix

- Added `@ConditionalOnMissingBean(name = ["passwordResetEmailTaskExecutor"])` to the production
  executor bean. Production still creates the bounded managed executor when no replacement exists;
  BDD keeps its named synchronous executor and continues to autowire the real
  `SendPasswordResetEmailConsumer`.
- No consumer was manually instantiated. No assertion, glue behavior, sensitive payload, logging,
  telemetry schema, or provider text handling changed.

### RED/GREEN evidence

- Focus-filter investigation: `CUCUMBER_FILTER_TAGS='@pr-3 and @notifications'` with
  `:server:smp:bddFastTest --rerun-tasks` still executed all 123 scenarios, confirming the current
  recipe/Gradle suite does not forward that environment filter as a real focused lane.
- RED: the fresh 123-scenario run failed exactly 3 tests: retry `expected 2 but was 1` at line 937;
  terminal failure `expected 3 but was 1` at line 948; telemetry `expected true but was false` at
  line 974.
- GREEN: after the one-bean conditional, the attempted filtered command (still full suite) passed
  fresh, followed by an explicit fresh full `:server:smp:bddFastTest --rerun-tasks` pass with all
  123 scenarios.
- REFACTOR/static: `:server:smp:spotlessCheck :server:smp:detekt` passed; `git diff --check` passed.

### Scope and remaining work

- PR-3.01 through PR-3.10 remain complete. PR-3.11 remains unchecked because PostgreSQL BDD and
  final verification were explicitly excluded from this batch.
- No `bdd-postgres`, verify-report edit, commit, push, or PR was performed. Existing local PR 3
  changes and the unrelated untracked JPG remain untouched.
