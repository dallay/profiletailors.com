# Proposal: Issue #193 Handler Transaction Boundary Proof

## Intent

Close GitHub issue #193 with database-backed evidence that handler multi-write flows are atomic.
Exploration found `CompleteLinkedInConnectionHandler`, `VerifyEmailHandler`, and
`ResendVerificationHandler` already use `AtomicTransactionRunner`; the remaining gap is focused
Postgres/R2DBC rollback proof for the exact flows.

## Scope

### In Scope

- Add focused Postgres/R2DBC rollback integration tests for LinkedIn connection + account writes.
- Add focused Postgres/R2DBC rollback integration tests for email verification token + identity
  email-status writes.
- Verify existing focused handler tests for `CompleteLinkedInConnectionHandler`,
  `VerifyEmailHandler`, and `ResendVerificationHandler` still pass.
- Document evidence against GitHub issue #193 acceptance points.

### Out of Scope

- Production code changes unless focused tests expose a real regression.
- Refactoring transaction runner, repository APIs, or handler architecture.
- Broad endpoint/E2E coverage unrelated to rollback proof.

## Capabilities

### New Capabilities

None — test hardening only; no new product capability.

### Modified Capabilities

None — existing `email-verification`, `oauth-initiation-api`, and publishing behavior requirements
stay unchanged.

## Approach

Keep production code unchanged. Add narrow integration coverage using real Postgres/R2DBC
repositories and `R2dbcAtomicTransactionRunner`, forcing the second write in each paired operation
to fail and proving the first write rolls back. Continue treating external provider calls and
post-commit side effects as outside transaction boundaries.

## Affected Areas

| Area                                                                                                 | Impact   | Description                                     |
|------------------------------------------------------------------------------------------------------|----------|-------------------------------------------------|
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/`                          | Modified | Add LinkedIn connection/account rollback proof. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/integration/`                            | Modified | Add identity token/status rollback proof.       |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt` | Verified | Existing focused handler transaction tests.     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`    | Verified | Existing verify/resend transaction tests.       |

## Risks

| Risk                                                             | Likelihood | Mitigation                                                                |
|------------------------------------------------------------------|------------|---------------------------------------------------------------------------|
| Postgres/Testcontainers setup is slower or environment-sensitive | Med        | Keep tests focused and tagged consistently with existing Postgres suites. |
| Identity fixture setup is brittle                                | Med        | Seed only required identities/tokens and assert direct database state.    |
| Tests reveal production regression                               | Low        | Limit production fix to the failing transaction boundary only.            |

## Rollback Plan

Revert the new integration tests and any minimal regression fix introduced by them. No schema, API,
or behavior migration is planned.

## Dependencies

- Existing Postgres/R2DBC integration test infrastructure.
- Existing `AtomicTransactionRunner` and real R2DBC repository wiring.

## Acceptance Criteria Mapping to GitHub Issue #193

- [ ] `CompleteLinkedInConnectionHandler` has database-backed proof that connection/account writes
  roll back atomically.
- [ ] `VerifyEmailHandler` has database-backed proof that token consumption/status update rolls back
  atomically.
- [ ] `ResendVerificationHandler` remains covered by focused existing tests or equivalent
  verification evidence.
- [ ] No production code changes unless a focused test proves a regression.

## Success Criteria

- [ ] Focused unit handler tests pass.
- [ ] New Postgres/R2DBC rollback tests fail without transaction rollback and pass with current
  transaction boundaries.
- [ ] Evidence is sufficient to close issue #193.
