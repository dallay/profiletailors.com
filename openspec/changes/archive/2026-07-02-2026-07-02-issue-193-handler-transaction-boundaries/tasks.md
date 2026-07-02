# Tasks: Issue #193 Handler Transaction Boundary Proof

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 180-320 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR: test-only rollback hardening |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Add focused Postgres rollback proof for issue #193 | PR 1 | Test-only; production changes only if regression is proven |

## Phase 1: Test Infrastructure Inspection

- [x] 1.1 Inspect `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` for container setup, cleanup, repository wiring, and `@Tag("postgres")` conventions.
- [x] 1.2 Inspect identity integration fixtures under `server/smp/src/test/kotlin/com/profiletailors/smp/identity/` and confirm seeding for `principals`, `user_identities`, and `email_verification_tokens`.
- [x] 1.3 Confirm focused Gradle filters for `postgres` tag before writing tests: `./gradlew :server:smp:test --tests "*PublishingHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres`.

## Phase 2: RED Rollback Tests

- [x] 2.1 Add a failing LinkedIn rollback scenario in `PublishingHandlersTransactionPostgresIntegrationTest.kt`: account upsert fails, connection/account rows absent, no channel event published.
- [x] 2.2 Create `server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt` with failing `VerifyEmailHandler` rollback proof: status update fails, token unused, identity remains unverified.
- [x] 2.3 Add resend rollback proof in the identity integration test if fixture setup is feasible: replacement-token creation fails, old token remains active, no email event published.

## Phase 3: GREEN / Minimal Fix Only If Needed

- [x] 3.1 Run the new focused tests and verify they fail for the intended rollback assertion before any production edit.
- [x] 3.2 If current production passes the rollback tests, leave production files unchanged.
- [x] 3.3 If a real regression appears, make the smallest handler transaction-boundary fix in `PublishingHandlers.kt` or `LocalAuthHandlers.kt` only.

## Phase 4: Verification and Evidence

- [x] 4.1 Run focused unit verification: `./gradlew :server:smp:test --tests "*PublishingHandlersTest" --tests "*LocalAuthHandlersTest"`.
- [x] 4.2 Run focused Postgres verification: `./gradlew :server:smp:test --tests "*PublishingHandlersTransactionPostgresIntegrationTest" --tests "*LocalAuthHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres`.
- [x] 4.3 Acceptance: LinkedIn social account failure rolls back social connection and suppresses event publication.
- [x] 4.4 Acceptance: email verification status failure leaves token unused and identity unverified.
- [x] 4.5 Acceptance: resend new-token failure preserves old token and suppresses email event, or document infeasibility with existing unit evidence.
- [x] 4.6 Update issue #193 evidence with test names, commands, and production-change status.

## Apply Evidence

- Focused pre-change Postgres filter confirmation: `./gradlew :server:smp:test --tests "*PublishingHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres` — PASS.
- LinkedIn rollback focused test after adding regression coverage: `./gradlew :server:smp:test --tests "*PublishingHandlersTransactionPostgresIntegrationTest.linkedin completion rolls back social connection when account upsert fails" -DincludeTags=postgres` — PASS; no production changes required.
- Identity rollback focused test first run: `./gradlew :server:smp:test --tests "*LocalAuthHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres` — FAIL at test compilation due nullable R2DBC row mapper type inference in the new test.
- Identity rollback focused test after test mapper fix: `./gradlew :server:smp:test --tests "*LocalAuthHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres` — PASS; no production changes required.
- Focused unit verification: `./gradlew :server:smp:test --tests "*PublishingHandlersTest" --tests "*LocalAuthHandlersTest"` — PASS.
- Focused Postgres verification: `./gradlew :server:smp:test --tests "*PublishingHandlersTransactionPostgresIntegrationTest" --tests "*LocalAuthHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres` — PASS.
- Production-change status: none; issue #193 is satisfied by existing `AtomicTransactionRunner` boundaries plus new database-backed rollback tests.
