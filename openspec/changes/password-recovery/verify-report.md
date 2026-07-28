# Verification Report: Password Recovery — PR 1 Core Backend

**Change**: `password-recovery`

**Mode**: OpenSpec / strict TDD configured (`rules.apply.tdd: true`)

**Verification date**: 2026-07-28

**Verdict**: **PASS WITH WARNINGS**

## Scope

This final verification covers PR 1 core backend only. PR 2 frontend and PR 3 audit, cleanup, notification retry/terminal-failure, telemetry, metrics, and runbook behavior remain excluded. `PR-1.30` remains incomplete because opening a PR was not authorized. No `just ci`, frontend, or marketing command was run.

## Completeness

| Metric | Value |
|---|---:|
| PR 1 implementation and verification tasks evaluated | 29 (`PR-1.01`–`PR-1.29`) |
| Truthfully complete | 29 |
| Incomplete core tasks | 0 |
| Publication task | `PR-1.30` excluded/not authorized |
| PR 2 / PR 3 | Excluded |

The revised wording for `PR-1.21`–`PR-1.26` now truthfully assigns evidence across executable Cucumber, unit, WebFlux, and PostgreSQL tests instead of claiming that every invariant is Cucumber-owned. Source inspection found no empty/no-op password-recovery step definitions, and both Cucumber lanes executed all discovered scenarios without undefined, pending, failed, or skipped steps.

## Fresh Build and Test Evidence

Commands were run sequentially. The first broad recipe pass was green but Gradle reported tasks as up-to-date; the focused suites and all runtime lanes were then rerun with `--rerun-tasks` to obtain fresh execution evidence.

| Command | Result | Runtime evidence |
|---|---|---|
| `just backend-check` | PASS | Exit 0; compilation, formatting, Detekt, tests, PostgreSQL integration task, Kover verification, and check completed. |
| `just backend-bdd-fast` | PASS | Exit 0. |
| `just backend-test-postgres` | PASS | Exit 0. |
| `just backend-bdd-postgres` | PASS | Exit 0. |
| Focused timing/handler/hasher/profile tests with `--rerun-tasks` | PASS | 25 tests, 0 failed, 0 skipped: request handler 12, timing equalizer 3, token hasher 9, profile isolation 1. |
| `:server:smp:bddFastTest --rerun-tasks` | PASS | 118 scenarios, 0 failed, 0 skipped. |
| `:server:smp:postgresIntegrationTest --rerun-tasks` | PASS | Fresh PostgreSQL lane; password-recovery evidence includes repository 12, request rollback 1, reset transactions 5, endpoint integration 20, and Actuator 8; all passed. |
| `:server:smp:bddPostgresTest --rerun-tasks` | PASS | 118 scenarios, 0 failed, 0 skipped. |
| `git diff --check` | PASS | Exit 0. |
| Coverage | PASS but non-meaningful | `koverVerify` passed; configured threshold remains `0`. |

Test compilation emitted non-blocking warnings in test sources, including always-true checks, Java boxed-type use, unnecessary null assertions, parameter-name mismatches, and one deprecated JSON assertion.

## Spec Compliance Matrix

| Requirement / scenario group | Status | Implementation and passing runtime evidence |
|---|---|---|
| REQ-PR-01..06 | COMPLIANT | DTO validation, shared normalization, local/OAuth/unknown behavior, and generic empty 202 responses pass Cucumber, handler, and WebFlux/PostgreSQL endpoint tests. |
| REQ-PR-07..10 | COMPLIANT | 32-byte CSPRNG token, URL-safe unpadded Base64, SHA-256 hash-only storage, 30-minute TTL, active-token replacement, and secrecy assertions pass focused and PostgreSQL tests. |
| REQ-PR-11 | COMPLIANT | Persistence transaction completes before event publication; managed bounded asynchronous email delivery does not await provider latency. Transaction-failure and consumer tests pass. |
| REQ-PR-12..16 | COMPLIANT | RFC 9457 validation, IP/email limits, equivalent account behavior, mutable-clock window boundaries, and disabled flag pass Cucumber/unit/WebFlux evidence. |
| Timing-enumeration scenario | COMPLIANT | Start is captured before normalization/account work using `System.nanoTime`; existing-local token generation, transaction commit, and event publication finish before equalization; unknown and OAuth-only paths reach the same post-work boundary. Default production minimum is configurable and non-no-op at 250 ms. Deterministic tests prove exact remaining-duration calculation, over-budget behavior, cancellation propagation, and call order. |
| REQ-RP-01..14 | COMPLIANT | Token hashing/errors, exact expiration predicate, password policy/hash, atomic consume/update/revocation, 204/no cookie/session, rate limit, flag, rollback, replay, and concurrent consumption pass Cucumber, unit, WebFlux, and PostgreSQL execution. |
| REQ-TOK-01..05 | COMPLIANT | Dedicated migration, unique hash, `VARCHAR(64)` FK to `user_identities(principal_id)` with cascade, partial active index, hash-only rows, exact predicates, and concurrency pass schema/repository/PostgreSQL tests. |
| REQ-NOT-01..05 | COMPLIANT | Event contract, configured reset URL, EN/ES content, expiry/ignore text, escaping, post-commit async dispatch, and secret-free provider failure logging pass Cucumber and focused tests. |
| REQ-NOT-06..08 | EXCLUDED | PR 3 hardening. |
| REQ-UI-01..16 | EXCLUDED | PR 2 frontend. |
| REQ-HARD-01..04 | EXCLUDED | PR 3 hardening. |

**PR 1 behavioral result**: all approved PR 1 requirements and scenario contracts have passing runtime coverage through their documented acceptance owners. No PR 1 scenario remains failing or untested.

## Key Traceability

| Contract | Primary evidence |
|---|---|
| Operational minimum-duration equalization | `PasswordRecoveryTimingEqualizer.kt`; `RequestPasswordResetHandler.kt`; `PasswordRecoveryConfigurationProperties.kt`; `IdentityBootstrapConfiguration.kt`; `MinimumDurationPasswordRecoveryTimingEqualizerTest`; request handler call-order test. |
| Mixed acceptance ownership | Revised `tasks.md` `PR-1.21`–`PR-1.26`; five password-recovery feature files; focused unit/WebFlux/PostgreSQL suites; both 118-scenario Cucumber lanes. |
| 10,000-token uniqueness | `PasswordResetTokenHasherTest.10000 generated tokens are unique`; fresh focused test execution passed. |
| Test profile isolation | `TestProfileIsolationConfigurationTest`; `@ActiveProfiles("test")` on fast BDD, PostgreSQL BDD, and Actuator contexts; fresh profile test and Actuator PostgreSQL tests passed. |
| Exact transaction and rollback behavior | `PasswordResetRequestTransactionPostgresIntegrationTest`; `PasswordResetTransactionPostgresIntegrationTest`; `R2dbcPasswordResetTokenRepositoryTest`. |
| Async dispatch and logging secrecy | `SendPasswordResetEmailConsumerTest`; notification Cucumber scenarios; post-commit request transaction test. |
| HTTP/security contracts | Request/reset/security Cucumber features; `LocalAuthEndpointIntegrationTest`; `IdentityProblemDetailsHandlerTest`; `AuthRateLimitWebFilterTest`. |

## Correctness

| Contract | Status | Notes |
|---|---|---|
| Configurable operational equalizer | PASS | Default 250 ms production bean; no production no-op binding. |
| Monotonic and cancellable timing | PASS | `System.nanoTime` and coroutine `delay`; cancellation is propagated. |
| Post-work equalization placement | PASS | Existing path: invalidate/create/commit/publish before equalize; unknown/OAuth paths equalize after lookup. |
| Deterministic timing tests | PASS | Synthetic monotonic readings and injected delay; no wall-clock threshold assertions. |
| Managed post-commit async dispatch | PASS | Bounded Spring executor; provider latency is not awaited by HTTP flow. |
| Provider error/log secrecy | PASS | Raw token, password, recipient, reset URL, and arbitrary provider error are excluded. |
| Schema and migration | PASS | Dedicated table, correct FK/cascade, unique hash, partial active index, rollback. |
| Consume/invalidate semantics | PASS | Exact expiration/used predicates and zero-row behavior; persisted-state tests pass. |
| Concurrent issuance/reset | PASS | Real concurrent HTTP/PostgreSQL evidence leaves one active/consumed token and one winning password. |
| Locale propagation | PASS | Request locale propagates through event and EN/ES rendering. |
| Rate limits/CORS/public errors | PASS | Required buckets, deterministic windows, reset-specific CORS, generic public behavior, and exact codes pass. |
| Test profile isolation | PASS | Three affected integration contexts explicitly pin `test`; production/dev behavior was not altered. |

## Design Coherence

| Decision | Status | Notes |
|---|---|---|
| Independent hash-only lifecycle | FOLLOWED | Dedicated model/table/repository; raw token remains transient. |
| Atomic reset boundary | FOLLOWED | Consume, password update, and refresh-session revocation share one R2DBC transaction. |
| Post-commit asynchronous notification | FOLLOWED | Event follows commit; managed executor decouples provider delivery. |
| Timing enumeration control | FOLLOWED | Configurable bounded minimum, monotonic timing, cancellable delay, and post-work boundary match the revised design. |
| Abuse controls | FOLLOWED | IP, normalized-email, and reset-attempt limits remain in place. |
| Hexagonal dependency direction | FOLLOWED | Application owns timing abstraction; Spring configuration supplies infrastructure wiring. |
| PR 3 stable seams/exclusions | FOLLOWED | Audit, cleanup, retry/failure, telemetry, metrics, and runbook remain excluded without weakening PR 1. |

## Strict TDD Audit

| Metric | Status |
|---|---|
| Strict TDD configured | Yes |
| Strict verification module | WARNING — documented `strict-tdd-verify.md` path is absent, so the artifact/source/runtime audit was applied directly. |
| Latest timing/profile/token closure | RED/GREEN evidence recorded in `apply-progress.md` and current GREEN independently rerun. |
| Full historical RED→GREEN proof for every PR-1.01..PR-1.29 task | Not independently reconstructable from the current working tree/history. |
| Runtime verification | PASS — focused and broad test lanes executed successfully. |

## Findings

| Finding | Judge A | Judge B | Severity | Status |
|---|---:|---:|---|---|
| Prior timing blocker: production no-op and pre-work placement | No longer present | No longer present | CRITICAL | Resolved |
| Prior acceptance wording/no-op glue blocker | No longer present | No longer present | CRITICAL | Resolved |
| Prior 1,000-vs-10,000 token sample mismatch | No longer present | No longer present | WARNING | Resolved |
| Ambient `dev` profile could alter BDD/Actuator contexts | No longer present | No longer present | CRITICAL | Resolved by minimal test-only profile pinning |
| Full historical strict RED→GREEN proof is unavailable and the documented strict verification module is absent | Yes | Yes | WARNING | Confirmed; does not invalidate current runtime compliance |
| Kover threshold is configured as zero | Yes | Yes | SUGGESTION | Confirmed |
| Kotlin test compilation emits non-blocking warnings | Yes | Yes | SUGGESTION | Confirmed |
| Production delay converts remaining duration to whole milliseconds, so a sub-millisecond remainder can truncate to zero | Yes | No | SUGGESTION | Theoretical; no observable spec failure and 250 ms default remains operational |

### CRITICAL

None.

### WARNING

1. Strict TDD is configured, but complete historical RED→GREEN provenance for every PR 1 task cannot be independently proven from the current working tree, and the documented strict verification module is missing.

### SUGGESTION

1. Set a meaningful non-zero Kover threshold if coverage verification is intended to be a release gate.
2. Clean the existing test-source compiler warnings.
3. If sub-millisecond exactness matters operationally, avoid truncating `Duration` to whole milliseconds in the injected production delay.

## Verdict

**PASS WITH WARNINGS**

All previously critical PR 1 findings are resolved in source and covered by fresh runtime execution. `PR-1.01`–`PR-1.29` are truthfully complete; `PR-1.30` remains intentionally excluded and unauthorized. The change may advance to the archive phase when orchestration is ready, but it must not be archived yet because PR 2 and PR 3 remain outstanding.
