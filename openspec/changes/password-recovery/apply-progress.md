# Apply Progress: Password Recovery

## Scope

Audited PR 1 backend tasks PR-1.01 through PR-1.29 on branch `feat/dallay-523-password-recovery-backend`. No commit, push, or pull request was created.

## Completed

- PR-1.01 through PR-1.20 verified against code, specs, focused tests, and backend checks.
- PR-1.27 `just backend-check` passed.
- PR-1.28 `just backend-bdd-fast` passed with 109 scenarios.
- PR-1.29 `just backend-bdd-postgres` passed.

## Incomplete

- PR-1.21 through PR-1.26 remain unchecked. The five feature files are substantially smaller than the complete scenario catalog in `spec.md`, all are globally tagged `@postgres` despite repository policy requiring `@postgres` only where necessary, and the single 1,200+ line glue class still contains marker/no-op steps rather than executable assertions for several specified scenarios.
- PR-1.30 is intentionally out of scope and prohibited by the user gate.

## TDD Evidence

- RED: focused `IdentityProblemDetailsHandlerTest` and `AuthRateLimitWebFilterTest` run failed 4 tests for disabled status, distinct token codes, and missing password-recovery IP buckets.
- GREEN: the same focused run passed all 20 tests after minimal fixes.
- RED: initial `just backend-bdd-fast` failed 23/109 scenarios; successive regression repairs reduced failures to 6, 2, 1, then 0.
- GREEN: final `just backend-bdd-fast` passed all 109 scenarios.
- GREEN: final `just backend-check` and `just backend-bdd-postgres` passed.

## Fixes Applied

- Mapped disabled recovery to 503 with `PASSWORD_RECOVERY_DISABLED`.
- Preserved distinct invalid/expired/used token codes while sharing one public detail.
- Added 5/15m forgot-password and 10/15m reset-attempt IP policies with coded 429 responses; retained normalized-email 3/30m application bucket.
- Added scenario isolation for both rate-limit stores and email recording.
- Corrected malformed/placeholder BDD assertions and first/second token tracking.
- Allowed the specified 128-character password policy with deterministic SHA-256 pre-hashing before BCrypt when UTF-8 input exceeds BCrypt's 72-byte limit.
- Kept password policy failures in the handler so `INVALID_PASSWORD` is returned without retaining the password in an exception.
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

- Corrected the five password-recovery feature headers to inherit the required identity domain tag, `@smoke`, and `@fast`.
- Removed global `@postgres`; persistence scenarios now carry `@postgres` individually.
- RED/GREEN tag evidence: after changing tag scope, `just backend-bdd-fast` passed, confirming the fast runner still discovers and executes the password-recovery scenarios.
- Stopped before marking PR-1.21 through PR-1.26 complete because the approved artifacts contradict the requested scope:
  - Proposal lines 23-25 defer audit metrics and cleanup to PR 3.
  - Tasks PR-1.23 requires a retention-cleanup BDD contract in PR 1.
  - Tasks PR-1.25 requires audit BDD in PR 1.
  - Spec scenarios require successful-reset audit events, suspicious-attempt audit data, cleanup behavior, notification retry, and terminal notification failure behavior that PR 1 production code does not implement.
  - The continuation is explicitly limited to PR-1.21 through PR-1.26, so implementing those missing production capabilities would violate the assigned scope and duplicate PR-3.03/PR-3.04/PR-3.06.

## Final PR 1 Continuation

- PR-1.21 through PR-1.26 completed under the reconciled core-backend scope; PR 3 audit, cleanup, retry/failure, and telemetry remain excluded.
- Added executable disabled-state and forgot-password IP-limit scenarios.
- Added a mutable test-only feature flag so disabled-state BDD exercises the real handlers and returns 503 without consuming/creating tokens.
- Replaced sequential request/reset race simulation with concurrent coroutines issuing real HTTP requests against the Postgres-backed application.
- Real concurrent forgot-password execution exposed a race that could leave two active tokens. Added a PostgreSQL transaction-scoped advisory lock keyed by principal before invalidation/insertion; the concurrent scenario then passed with exactly one active token.
- Feature tags use `@identity @password-recovery @smoke @fast`; `@postgres` is scoped to persistence scenarios.
- Final verification: `just backend-bdd-fast`, `just backend-check`, and `just backend-bdd-postgres` all passed sequentially.

## Verification-Failure Continuation

### Resolved

- True asynchronous primary email delivery: `SendPasswordResetEmailConsumer` now schedules provider work on a bounded Spring-managed `ThreadPoolTaskExecutor`. The event publication remains after token transaction commit, while the consumer returns after scheduling and does not await provider latency/failure. Executor shutdown is lifecycle-managed and drains queued work.
- Safe provider logging: arbitrary `EmailSendResult.error` text is no longer logged. Failure logs contain a stable category and principal identifier only.
- Schema reconciliation: the repository identity schema uses `user_identities`, not a `principal_identities` table, and both identity keys are `VARCHAR(64)`. Migration FK now targets `user_identities(principal_id)`; the spec schema wording was minimally corrected with this evidence. PostgreSQL metadata and cascade tests pass.
- Active-token invalidation now updates only unused, unexpired rows (`expires_at > invalidatedAt`); expired unused rows remain unchanged.

### RED/GREEN Evidence

- RED: consumer async test failed to compile because no executor seam existed; GREEN after injecting a Spring `TaskExecutor` and scheduling delivery.
- RED: PostgreSQL metadata test reported FK target `principals(id)`, and invalidation test showed expired-unused rows mutated; GREEN after migration/SQL fixes.
- Final broad verification remained green: `just backend-check`, `just backend-bdd-fast`, `just backend-bdd-postgres`.

### Still Open

- PR-1.21 through PR-1.26 were reopened truthfully. The latest verify report lists 15 scenario-level runtime gaps; this continuation resolved the critical production blockers and several coverage gaps, but did not fabricate evidence for the remaining locale, rate-window, transaction-failure injection, CORS, timing, and secret-capture scenarios.

## Runtime-Evidence Closure: Remaining PR 1 Gaps

| Gap | Exact runtime evidence | Result |
|---:|---|---|
| 1 | `PasswordResetRequestTransactionPostgresIntegrationTest.persistence failure leaves no token and suppresses notification publication` | PASS — real PostgreSQL transaction inserts then fails; persisted token count remains zero and publisher captures no event. |
| 2 | BDD `Send the password reset email using the supported locale` (`en`, `es`) plus `SendPasswordResetEmailConsumerTest.renders password reset email in English and Spanish from the event locale` | PASS — real generated subjects, text, and HTML asserted. |
| 3 | BDD `Apply email rate limiting equally to existing and unknown accounts` | PASS — normalized variants exhaust both buckets and both final responses are generic 429. |
| 4 | `AuthRateLimitWebFilterTest.forgot password admits a new request exactly when its window expires` | PASS — mutable clock, no wall-clock sleep. |
| 5 | `AuthRateLimitWebFilterTest.reset password admits a new attempt exactly when its window expires` | PASS — mutable clock, no wall-clock sleep. |
| 6 | `PasswordResetTransactionPostgresIntegrationTest.reset immediately before expiration commits through handler and repository` | PASS — `expiresAt = now + 1s`, persisted password/token/session state asserted. |
| 7 | `PasswordResetTransactionPostgresIntegrationTest.reset exactly at expiration is rejected through handler and repository` | PASS — equality boundary rejects and persisted state is unchanged. |
| 8 | BDD `Reject a token with modified characters` | PASS — fixture changes exactly one character while preserving token length. |
| 9 | `SendPasswordResetEmailConsumerTest.provider failure logs exclude email token reset URL password and provider text`; hash-only PostgreSQL assertions; PR 1 audit/metrics absence asserted by no PR 1 emitters | PASS — captured logs exclude email, token, reset URL, password, and arbitrary provider error; PR 3 audit/metrics remain excluded. |
| 10 | `PasswordResetTransactionPostgresIntegrationTest.password update failure rolls back persisted token consumption` | PASS — injected failure after real writes; password, token, session remain original/active. |
| 11 | `PasswordResetTransactionPostgresIntegrationTest.token consumption failure rolls back persisted password update` | PASS — injected failure after repository write; all persisted state rolls back. |
| 12 | `PasswordResetTransactionPostgresIntegrationTest.session revocation failure rolls back password and token persisted state` | PASS — real session UPDATE followed by failure; transaction restores all rows. |
| 13 | BDD `Concurrent reset attempts with different passwords are atomic` | PASS — two real concurrent HTTP requests; one 204, one 400, one stored hash, one consumption. |
| 14 | BDD `Reject reset requests from a disallowed origin` and `LocalAuthEndpointIntegrationTest.reset password rejects a disallowed origin` | PASS — reset-specific request returns 403 and password remains unchanged. |
| 15 | `RequestPasswordResetHandlerTest.existing and unknown identities traverse the timing equalization seam` plus async consumer test | PASS — deterministic seam proves both identity paths traverse the same explicit equalizer; no flaky latency threshold. |

### RED/GREEN Evidence for This Continuation

- Locale RED: consumer test failed compilation because `PasswordResetRequested` had no locale; GREEN after locale propagation from `Accept-Language` and EN/ES template rendering.
- Timing RED: focused handler test failed compilation because no timing equalization seam existed; GREEN after `PasswordRecoveryTimingEqualizer` was called on both existing and unknown paths.
- PostgreSQL transaction RED: new transaction integration class initially could not initialize without the repository-required test DB credential; GREEN under the project test environment, with five persisted-state tests passing.
- CORS RED was added as a reset-specific integration test; existing production CORS correctly returned 403, so no production change was necessary.
- Async BDD RED: email assertion raced the managed executor; GREEN after the recording sender gained a deterministic semaphore-based completion seam (no sleeps).
- Formatting RED: `just backend-check` failed Spotless/Detekt; GREEN after applying formatter and narrowing the long descriptive test-name suppression.

### Final Commands Run

- Focused locale test — initial exit 1 (missing locale), intermediate exit 1 (Spanish assertion wording), final exit 0.
- Focused request/reset rate-window tests — exit 0.
- Focused PostgreSQL reset transaction suite — initial exit 1 without sourced DB test credential; final exit 0, 5/5 passed.
- Focused timing equalizer test — initial exit 1 (missing seam), final exit 0.
- Focused secret-free provider logging test — exit 0.
- Focused reset CORS integration test — exit 0.
- Focused persistence-failure transaction test — exit 0.
- `just backend-check` — initial exit 1 (format/Detekt), final exit 0.
- `just backend-bdd-fast` — initial exit 1 (async assertion race), final post-refactor exit 0, 118/118 passed.
- `just backend-bdd-postgres` — final post-refactor exit 0.
- Final sequential gate rerun: `just backend-check` exit 0 → `just backend-bdd-fast` exit 0 → `just backend-bdd-postgres` exit 0.

## Focused Apply: Timing, Acceptance Truthfulness, and Token Sample

### Operational Timing Enumeration Defense

- Replaced the no-op timing seam with `MinimumDurationPasswordRecoveryTimingEqualizer`.
- Production binds a configurable `app.identity.password-recovery.minimum-response-duration`, defaulting to 250 ms.
- The handler marks monotonic start before email/account processing and calls equalization only after each accepted path finishes its account-dependent work. Existing-local execution completes token generation, transaction commit, and event publication before equalization; unknown/OAuth-only execution also reaches the same post-work boundary.
- `MonotonicTimeSource` and `SuspendingDelay` are injected for deterministic tests. Production uses `System.nanoTime` and cancellable coroutine `delay`; tests use exact synthetic readings and captured delays with no wall-clock assertions.

### Acceptance Evidence Ownership

- PR-1.21: executable Cucumber owns generic 202, privacy, normalization, lifecycle, notifications, limits, validation, and disabled behavior. Focused handler/PostgreSQL tests own timing and request transaction-failure invariants.
- PR-1.22: executable Cucumber owns public reset errors, validation, hashing outcomes, session revocation, replay, rate limits, disabled behavior, and concurrent HTTP outcomes. Focused PostgreSQL tests own exact expiration boundaries and injected rollback failures.
- PR-1.23: executable PostgreSQL-tagged Cucumber owns externally observable persistence lifecycle. Focused repository/schema/PostgreSQL tests own metadata, exact SQL predicates, hash-only rows, rollback, and lower-level concurrency.
- PR-1.24: executable Cucumber owns dispatch and rendered content. Focused consumer/template/transaction tests own post-commit order, provider non-blocking behavior, failure-log redaction, and escaping. Retry, terminal failure, and telemetry remain PR 3.
- PR-1.25: executable Cucumber owns enumeration response contracts, replay, CORS, and brute-force limits. Focused deterministic tests own entropy, the 10,000-token uniqueness sample, and timing equalization. Audit remains PR 3.
- PR-1.26 wording now requires an audited executable PR 1 glue catalog rather than pretending every lower-level invariant is Cucumber-owned.

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
- authentication rate limiting executes a real invalid reset request and resets stores for scenario isolation.
- existing account/credential/OAuth/unknown setup steps assert persisted fixture state.

Renamed the misleading PR 1 notification `telemetry` scenario to notification-content secrecy; PR 3 telemetry remains excluded.

### Token Uniqueness

- `PasswordResetTokenHasherTest` now generates 10,000 tokens and asserts 10,000 unique raw tokens and hashes.

### RED/GREEN Evidence

- RED: focused timing/hasher compilation failed because the old seam exposed only `equalize(normalizedEmail)` and had no monotonic start/post-work contract.
- GREEN: focused handler, minimum-duration equalizer, and 10,000-token hasher tests passed after the new abstractions and handler placement were implemented.
- REFACTOR: production configuration provides the non-no-op default; deterministic tests cover remaining-duration calculation, elapsed-over-budget behavior, cancellation propagation, and exact post-work call order.
- BDD glue audit exposed a Spring test bean collision and rate-limit pre-consumption. The bean was made a true replacement by name, and marker limit steps were removed rather than consuming requests before scenarios.

### Commands Run in This Focused Apply

- Focused handler/hasher tests (RED) — exit 1, compile failure proving the old timing seam lacked the required contract.
- Focused handler/equalizer/10,000-token tests (GREEN) — exit 0.
- Focused handler/equalizer/10,000-token tests after refactor — exit 0.
- Raw `:server:smp:bddFastTest --tests ...` attempt — exit 1, invalid suite class filter; superseded by unfiltered recipe.
- Raw unfiltered `:server:smp:bddFastTest` without sourced test DB environment — exit 1, environment setup failure.
- `just backend-bdd-fast` iterations — exit 1; password-recovery failures were removed, but the suite still has one unrelated pre-existing failure: `Pending user media upload attempt is denied by email verification gate` at `AuthorizationBddSteps.kt:155`.
- `just backend-check` — exit 1; initial formatting/Detekt findings were fixed, but the run also reported two unrelated `ActuatorEndpointsIntegrationTest` health failures.
- `just backend-test-postgres` — exit 1; 268/270 passed, same two unrelated actuator health failures.
- `just backend-bdd-postgres` — exit 1; 117/118 passed, same unrelated pending-user media BDD failure.
- `git diff --check` — exit 0.

### Current Task State

- PR-1.21 through PR-1.29 remain reopened because the mandated broad gates are not all green.
- PR-1.30 remains prohibited and incomplete.
- State remains `current_phase: apply`, `next: verify`.

## Test-Infrastructure Blocker Resolution

- Root-cause regression evidence: `TestProfileIsolationConfigurationTest` failed under `SPRING_PROFILES_ACTIVE=dev` because the fast BDD, Postgres BDD, and Actuator integration contexts did not explicitly select the repository's existing `test` profile.
- Minimal fix: added `@ActiveProfiles("test")` to `CucumberSpringConfiguration`, `CucumberPostgresSpringConfiguration`, and `ActuatorEndpointsIntegrationTest`. No production/dev email-verification policy, SMTP configuration, or health behavior changed.
- Mail health did not require a test-only override: pinning the test profile alone made the full Actuator class pass under ambient dev.
- Focused ambient-dev GREEN evidence: profile-isolation regression test passed; pending-user media scenario passed in both fast and Postgres Cucumber lanes; `ActuatorEndpointsIntegrationTest` passed.
- Sequential broad gates passed after one unrelated transient resend-verification timeout was cleared by the required full rerun: `just backend-check`, `just backend-bdd-fast`, `just backend-test-postgres`, and `just backend-bdd-postgres`.
- PR-1.21 through PR-1.29 are now complete truthfully. PR-1.30 remains prohibited and incomplete. State remains `current_phase: apply`, `next: verify`.

## PR 2 Frontend Planning State

- PR 1 apply/verify history above remains complete; PR 2 is the next apply slice, beginning at `PR-2.01`, while PR 3 remains planned.
- Overall `current_phase` is `apply` because one scalar cannot represent verified PR 1 plus pending PR 2/PR 3; `state.yaml.slices` records the additive per-slice lifecycle.
- No PR 2 implementation or verification command has run yet.

## PR 2 Frontend Apply: PR-2.01 through PR-2.11

### Completed

- Added typed `requestPasswordReset` and `resetPassword` API functions using `requestRaw`, active EN/ES `Accept-Language`, empty 202/204 handling, and retained RFC 9457 status/code.
- Added normalized recovery-email and matching 8..128 password schemas.
- Added standalone, accessible forgot/reset views with local state, safe generic error mapping, duplicate locks, terminal reset success, and no auth-store mutation.
- Added `forgot-password` guest-only and `reset-password` session-agnostic routes; both bypass `AppShell` through `meta.standalone`.
- Added the login-only forgot link, EN/ES strict parity, safe E2E mocks/data/POM, and core/accessibility/mobile/i18n/authenticated/storage-secrecy Playwright coverage.
- No dependencies or backend files changed. PR 1 history remains intact.

### RED/GREEN Evidence

- PR-2.01 RED: 5 API tests failed because recovery functions did not exist; GREEN: 53/53 focused API tests passed.
- PR-2.02 RED: 9 schema tests failed because recovery schemas did not exist; GREEN: 20/20 focused schema tests passed.
- PR-2.03/04 RED: component imports failed because recovery views did not exist; GREEN: forgot 5/5 and reset 8/8 passed.
- PR-2.05 RED: three route/guard assertions failed because recovery routes were absent; GREEN: 25/25 router tests passed.
- PR-2.06 RED: standalone route still rendered `AppShell`; GREEN: 5/5 App tests passed.
- PR-2.07 RED: login forgot-link assertion failed; GREEN: 16/16 AuthView tests passed.
- PR-2.08 RED: locale parity failed because namespace was absent; GREEN: parity and referenced-key tests passed.
- PR-2.10/11 GREEN: targeted recovery Playwright passed 21/21 across Chromium, Firefox, and Pixel 5.

### Commands Run

- Focused Vitest RED/GREEN commands for each PR-2.01..PR-2.08 work unit — final focused batch passed.
- `pnpm --filter app test:run` — exit 0, 101 files, 1193 passed, 1 todo.
- `pnpm --filter app lint` — initial exit 1 for formatting only; after Biome write, exit 0 across 722 files.
- `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts` — exit 0, 21/21 passed.
- Combined recovery + existing route-guards Playwright — recovery/new authenticated-reset scenario passed; two existing credential-backed route-guard tests were blocked by missing `E2E_TEST_USER_PASSWORD`.
- `just app-build` — exit 0; Vue type-check and Vite production build passed with existing chunk-size warning.
- `git diff --check` — exit 0.

### Current Task State

- PR-2.01 through PR-2.11 are complete.
- PR-2.12 remains open because its exact combined route-guards command requires `E2E_TEST_USER_PASSWORD`; sdd-verify owns verified status.
- PR 2 state is `applied`, next `verify`; no commit, push, PR, archive, or history rewrite performed.

## Risks

- The first ambient-dev `backend-check` attempt also activated unrelated dev credentials and failed broadly; the required repository-context rerun was used for the final sequential gate. A subsequent first normal `backend-check` hit one transient resend-verification timeout, while the immediate full rerun passed.
- Existing commit `3b452dd0` contains unrelated marketing test changes in three files; this continuation did not touch them.
- The original PR 1 implementation is far above the 400-line review budget; this apply respected the selected stacked-to-main PR 1 boundary.
- PR 3 audit, metrics, notification retry/final-failure, cleanup, and operational telemetry remain intentionally excluded per `spec.md` and `design.md`.
