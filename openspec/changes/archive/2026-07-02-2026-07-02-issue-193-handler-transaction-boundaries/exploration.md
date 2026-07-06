## Exploration: GitHub issue #193 handler transaction boundaries

### Current State
`CompleteLinkedInConnectionHandler` currently injects `AtomicTransactionRunner` and wraps the paired `socialConnectionRepository.upsert(...)` then `socialAccountRepository.upsert(...)` writes in `transactionRunner.runAtomically { ... }`; the channel event is published after the transaction block. `VerifyEmailHandler` currently injects `AtomicTransactionRunner` and wraps `markTokenUsed(...)` plus `updateEmailStatus(...)` in one transaction, then issues auth session after the transaction. `ResendVerificationHandler` currently injects `AtomicTransactionRunner` and wraps `invalidateEmailTokens(...)` plus `createEmailVerificationToken(...)` in one transaction, then publishes the email event outside the transaction. `R2dbcAtomicTransactionRunner` uses `TransactionalOperator.transactional(mono { block() }).awaitSingle()`, with `PersistenceConfig` providing `R2dbcTransactionManager` and `TransactionalOperator`.

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` — contains `CompleteLinkedInConnectionHandler`; transaction boundary is already present around connection/account upserts.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` — contains `VerifyEmailHandler` and `ResendVerificationHandler`; both transaction boundaries are already present.
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt` — application/domain transaction port used by handlers.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt` — R2DBC implementation using `TransactionalOperator`.
- `server/smp/src/main/kotlin/com/profiletailors/smp/config/PersistenceConfig.kt` — declares reactive transaction manager/operator.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt` — has unit coverage that `CompleteLinkedInConnectionHandler` starts/commits a transaction and suppresses event publication on downstream account upsert failure.
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt` — has unit coverage for `VerifyEmailHandler` and `ResendVerificationHandler` transaction ordering and rollback marker behavior.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` — existing Postgres transaction integration suite covers publication/job mutations, not LinkedIn connection or identity verification token/status mutations.

### Approaches
1. **Close as already implemented with focused verification only** — Keep production code unchanged; run/review focused handler tests and update issue with evidence.
   - Pros: Lowest risk; matches current code; avoids churn in already-correct transaction boundaries.
   - Cons: Leaves no real R2DBC rollback proof for these exact handler flows.
   - Effort: Low

2. **Add focused Postgres rollback integration tests for the exact issue flows** — Preserve current production code and add transaction integration tests proving real database rollback for LinkedIn connection/account and identity token/status pairs.
   - Pros: Converts current unit-level transaction-order coverage into acceptance-level persistence proof; safest if issue requires database-backed evidence.
   - Cons: Requires Postgres/Testcontainers test work under existing `postgres` tag; more setup and possibly slower.
   - Effort: Medium

3. **Refactor transaction runner or repository APIs** — Move paired writes into new repository methods or introduce narrower transaction services.
   - Pros: Could make atomic operations more explicit at port level.
   - Cons: Unnecessary given existing `AtomicTransactionRunner` pattern; higher blast radius and conflicts with current application-layer orchestration style.
   - Effort: High

### Recommendation
Do not change production code. The reported missing transaction boundaries are already implemented for all three handlers. The only remaining gap is acceptance confidence: add or run focused tests. If issue #193 requires code changes, implement Approach 2 as test-only hardening: add Postgres integration rollback tests for `CompleteLinkedInConnectionHandler` and, if desired, identity verification/resend flows using real repositories and `R2dbcAtomicTransactionRunner`. Keep external provider/OAuth/token lookup before the transaction and post-commit side effects (`ChannelEventPublisher.publish`, `EventPublisher.publish`, JWT/refresh issuance) outside the transaction, consistent with existing code.

### Risks
- Existing rollback unit tests use fake/recording transaction runners; they verify handler boundaries/order, not actual R2DBC rollback against `social_connections`, `social_accounts`, `email_verification_tokens`, or `identities`.
- `CompleteLinkedInConnectionHandler` rollback unit test currently observes no commit marker and no event publication, but the in-memory connection write may still have happened because the fake transaction runner cannot undo state.
- Adding integration tests for identity flows may need careful seeding of the current identity schema and may overlap with broader endpoint integration coverage.
- Production code is in a worktree named for the remediation issue, so stale issue/codegraph claims should be treated cautiously; current source is the authority.

### Ready for Proposal
Yes — tell the user/orchestrator that production transaction boundaries appear already satisfied for `CompleteLinkedInConnectionHandler`, `VerifyEmailHandler`, and `ResendVerificationHandler`; next phase should either produce a minimal test-hardening proposal or close the issue with exact file/line evidence after running focused tests.
