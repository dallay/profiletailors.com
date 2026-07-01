# Transaction Remediation: Multi-Write Handlers

**Issue**: [#193](https://github.com/dallay/profiletailors.com/issues/193)

**Date**: 2026-07-01

## Status

Draft — pending user approval.

---

## Problem

Three handlers execute multiple database writes without transaction boundaries. If any write after the first succeeds but a subsequent write fails, the first write commits and the system enters an inconsistent state.

### Affected Handlers

| Handler | Writes | Failure Scenario |
|---------|--------|-----------------|
| `VerifyEmailHandler` | `markTokenUsed()` → `updateEmailStatus()` | Token consumed, email stays PENDING. User cannot re-verify. |
| `ResendVerificationHandler` | `invalidateEmailTokens()` → `createEmailVerificationToken()` | Old tokens invalidated, no new token created. User cannot verify. |
| `CompleteLinkedInConnectionHandler` | `upsert(connection)` → `upsert(account)` | Connection orphaned (no account). OAuth handshake partially succeeded. |

---

## Solution

Inject `AtomicTransactionRunner` into each handler and wrap multi-write operations in `runAtomically {}`.

### Pattern

```kotlin
private val transactionRunner: AtomicTransactionRunner

suspend fun handle(command: Command): Result {
    // reads and validation outside transaction
    val stored = gateway.read(...)

    // multi-write in single transaction
    val result = transactionRunner.runAtomically {
        gateway.write1(...)
        gateway.write2(...)
    }

    // side effects (event publishing) outside transaction
    eventPublisher.publish(event)

    return result
}
```

This pattern already exists in `RegisterUserHandler` (lines 113-154).

### Event Publishing Rule

`eventPublisher.publish()` **must stay outside** `runAtomically {}` to avoid blocking the transaction commit. Events are best-effort and should not be part of the DB transaction.

---

## Architecture

### Dependencies

- `AtomicTransactionRunner` — domain port in `shared/common`
- `R2dbcAtomicTransactionRunner` — Spring implementation using `TransactionalOperator`
- `PersistenceConfig` — already exposes `TransactionalOperator` bean

### Injection

`VerifyEmailHandler`, `ResendVerificationHandler`, and `CompleteLinkedInConnectionHandler` add `AtomicTransactionRunner` to their constructor parameters.

---

## Testing Strategy

### Unit Tests (fast, TDD)

Using existing `RecordingAtomicTransactionRunner` pattern that logs execution order.

**Test 1 — VerifyEmailHandler happy path**:
```
tx:start → markTokenUsed → updateEmailStatus → tx:commit → event:publish
```

**Test 2 — VerifyEmailHandler rollback**:
- `updateEmailStatus` throws → verify `markTokenUsed` did not persist

**Test 3 — ResendVerificationHandler happy path**:
```
tx:start → invalidateEmailTokens → createEmailVerificationToken → tx:commit → event:publish
```

**Test 4 — ResendVerificationHandler rollback**:
- `createEmailVerificationToken` throws → verify `invalidateEmailTokens` did not persist

**Test 5 — CompleteLinkedInConnectionHandler happy path**:
```
tx:start → upsert:connection → upsert:account → tx:commit → channel:publish
```

**Test 6 — CompleteLinkedInConnectionHandler rollback**:
- Second `upsert` throws → verify first `upsert` did not persist

### Integration Tests (Postgres, Testcontainers)

Following pattern of `PublishingWorkerTransactionPostgresIntegrationTest`.

**Test 7 — VerifyEmailHandler integration**:
- Run handler, kill second write → verify first write rolled back

**Test 8 — ResendVerificationHandler integration**:
- Run handler, kill second write → verify first write rolled back

**Test 9 — CompleteLinkedInConnectionHandler integration**:
- Run handler, kill second write → verify first write rolled back

---

## Files to Change

| File | Change |
|------|--------|
| `server/smp/src/main/kotlin/.../identity/application/LocalAuthHandlers.kt` | Inject `AtomicTransactionRunner`, wrap multi-write in `VerifyEmailHandler` and `ResendVerificationHandler` |
| `server/smp/src/main/kotlin/.../publishing/application/PublishingHandlers.kt` | Inject `AtomicTransactionRunner`, wrap multi-write in `CompleteLinkedInConnectionHandler` |
| `server/smp/src/test/kotlin/.../identity/application/LocalAuthHandlersTest.kt` | Add 4 unit tests for transaction order and rollback |
| `server/smp/src/test/kotlin/.../publishing/application/PublishingHandlersTest.kt` | Add 2 unit tests for transaction order and rollback |
| `server/smp/src/test/kotlin/.../identity/integration/LocalAuthTransactionPostgresIntegrationTest.kt` | New file: 2 integration tests for rollback |
| `server/smp/src/test/kotlin/.../publishing/integration/PublishingHandlersTransactionPostgresIntegrationTest.kt` | Extend with 2 rollback tests (file already exists) |

---

## Acceptance Criteria

- [ ] `VerifyEmailHandler` wraps `markTokenUsed` + `updateEmailStatus` in transaction
- [ ] `ResendVerificationHandler` wraps `invalidateEmailTokens` + `createEmailVerificationToken` in transaction
- [ ] `CompleteLinkedInConnectionHandler` wraps `upsert(connection)` + `upsert(account)` in transaction
- [ ] All unit tests pass: order verification + rollback scenarios
- [ ] All integration tests pass: real rollback on Postgres
- [ ] `RegisterUserHandler` unchanged (already correct)
