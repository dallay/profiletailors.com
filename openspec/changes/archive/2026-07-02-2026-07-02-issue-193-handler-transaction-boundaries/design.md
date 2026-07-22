# Design: Issue #193 Handler Transaction Boundary Proof

## Technical Approach

Add test-only Postgres/R2DBC integration coverage for the exact multi-write handler flows identified
in exploration. Production handlers already use `AtomicTransactionRunner`; the design preserves that
application port and the infrastructure `R2dbcAtomicTransactionRunner(TransactionalOperator)`
adapter. Tests inject deterministic failures on the second repository write, then assert the first
write was rolled back in the real database.

No spec delta exists yet in this change directory, so this design maps to the proposal acceptance
criteria and exploration findings.

## Architecture Decisions

| Option                                     | Tradeoff                                                                              | Decision                                                                          |
|--------------------------------------------|---------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| Keep `AtomicTransactionRunner` in handlers | Minimal blast radius; preserves hexagonal boundary                                    | Chosen: no production refactor unless focused tests prove regression.             |
| Add DB-backed rollback tests               | Slower than unit tests, but proves real R2DBC rollback                                | Chosen: use existing `@Tag("postgres")` Testcontainers pattern.                   |
| Move side effects into transactions        | Could simplify assertions, but risks publishing/session work before rollback is known | Rejected: events and token/session issuance stay post-commit/outside transaction. |
| Fail via wrapper interfaces                | Keeps production repositories unchanged; deterministic failure point                  | Chosen: delegate wrappers fail on second write only.                              |

## Data Flow

LinkedIn failure proof:

    CompleteLinkedInConnectionHandler
      ├─ provider/state/principal setup outside tx
      └─ AtomicTransactionRunner
           ├─ R2dbcSocialConnectionRepository.upsert()  ── writes social_connections
           └─ FailingSocialAccountRepository.upsert()   ── throws
        rollback asserted: no new social_connections/social_accounts rows
        side effect asserted: no ChannelEvent published

Identity failure proof:

    VerifyEmailHandler
      ├─ verifyEmailToken() outside tx
      └─ AtomicTransactionRunner
           ├─ markTokenUsed()       ── writes email_verification_tokens.used_at
           └─ updateEmailStatus()   ── throws
        rollback asserted: token remains unused; identity remains PENDING

    ResendVerificationHandler
      └─ AtomicTransactionRunner
           ├─ invalidateEmailTokens()          ── marks active tokens used
           └─ createEmailVerificationToken()   ── throws
        rollback asserted: previous token remains active; no new token exists

## File Changes

| File                                                                                                                               | Action            | Description                                                                                                                                                                                                                                                                                                         |
|------------------------------------------------------------------------------------------------------------------------------------|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` | Modify            | Add `CompleteLinkedInConnectionHandler` rollback test using real `R2dbcSocialConnectionRepository`, `R2dbcSocialAccountRepository`, real `R2dbcAtomicTransactionRunner`, and a `FailingSocialAccountRepository`. Reuse existing Postgres container, cleanup, workspace/principal seeding, and direct DB assertions. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt`    | Create            | Add focused Postgres tests for `VerifyEmailHandler` and `ResendVerificationHandler` using `R2dbcIdentityRegistrationGateway` wrapped by failure-injecting `IdentityRegistrationGateway` decorators. Seed `principals`, `user_identities`, and `email_verification_tokens`; assert DB state after rollback.          |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt`                               | Verify            | Existing unit boundary/order tests remain focused evidence.                                                                                                                                                                                                                                                         |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`                                  | Verify            | Existing fake transaction tests remain fast boundary/order coverage.                                                                                                                                                                                                                                                |
| Production files                                                                                                                   | No change planned | Modify only if new focused tests reveal real regression.                                                                                                                                                                                                                                                            |

## Interfaces / Contracts

Test seams are interface decorators only:

```kotlin
private class FailingSocialAccountRepository(
    private val delegate: SocialAccountRepository,
) : SocialAccountRepository by delegate {
    override suspend fun upsert(account: SocialAccount): SocialAccount = throw InjectedFailure()
}
```

Use the same pattern for `IdentityRegistrationGateway`, overriding only `updateEmailStatus` or
`createEmailVerificationToken` while delegating all earlier reads/writes to the real R2DBC gateway.

## Testing Strategy

| Layer       | What to Test                                          | Approach                                                               |
|-------------|-------------------------------------------------------|------------------------------------------------------------------------|
| Unit        | Handler transaction ordering and side-effect deferral | Existing `PublishingHandlersTest` and `LocalAuthHandlersTest`.         |
| Integration | Real Postgres rollback for second-write failures      | `@Tag("postgres")`, Testcontainers, `DatabaseClient` state assertions. |
| E2E         | None                                                  | Not needed; this is transaction-boundary proof only.                   |

Verification commands:

- Fast focused unit:
  `just backend-test-fast --tests "*PublishingHandlersTest" --tests "*LocalAuthHandlersTest"` if
  supported by the Just recipe; otherwise
  `./gradlew :server:smp:test --tests "*PublishingHandlersTest" --tests "*LocalAuthHandlersTest"`.
- Focused Postgres:
  `./gradlew :server:smp:test --tests "*PublishingHandlersTransactionPostgresIntegrationTest" --tests "*LocalAuthHandlersTransactionPostgresIntegrationTest" -DincludeTags=postgres`.
- Broader Postgres gate when Docker is available: `just backend-bdd-postgres` only if repository
  convention requires all Postgres-tagged checks for this issue.

## Migration / Rollout

No migration required. This is test hardening only.

## Open Questions

- [ ] Confirm exact Gradle project path/test-tag flag accepted by the current build before final
  task execution.
