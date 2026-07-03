# ADR-0012: Reactive Transaction Strategy Standard

**Status:** Accepted
**Date:** 2026-07-03
**Deciders:** Platform Team

## Context

The SMP backend uses R2DBC with a reactive stack (WebFlux, Coroutines). After a full codebase audit during the reactive transaction remediation epic (#197), three different transaction approaches were found in use:

1. **`TransactionalOperator.transactional(mono { ... })`** — Programmatic reactive pattern
2. **`@Transactional` annotation** — Declarative AOP (CGLIB proxy)
3. **Raw R2DBC `Connection` API** — Manual connection management

This inconsistency created maintenance burden and subtle bugs.

## Decision

All multi-statement database operations **MUST** use `TransactionalOperator.transactional { mono { ... } }`.

### Why `TransactionalOperator`?

- **Reactive-native:** Binds to Reactor `Context`, not thread — the correct reactive model
- **Works everywhere:** No proxy needed — works on any class, final or not
- **Explicit:** Transaction boundary is visible in code, not hidden in annotation
- **Testable:** Easy to inject mock/spy in unit tests

### Why NOT `@Transactional`?

- Kotlin classes are `final` by default. Spring's CGLIB proxy needs `open` classes
- Keeping handlers final (for design discipline) means `@Transactional` silently fails
- Mixing `@Transactional` with Kotlin `suspend` functions has edge cases in reactive context
- The codebase policy explicitly notes this limitation in `PersistenceConfig.kt`

### Why NOT raw `Connection` API?

- Bypasses Spring Data R2DBC's `DatabaseClient` abstraction
- Manual resource management (connection leaks if finally block fails)
- Inconsistent with every other repository
- No automatic rollback-on-exception behavior
- Harder to test and mock

## Implementation Pattern

```kotlin
class MyRepository(
    private val databaseClient: DatabaseClient,
    private val transactionalOperator: TransactionalOperator,
) : MyRepositoryInterface {

    suspend fun multiWriteOperation(...): Result {
        return transactionalOperator.transactional(mono {
            // All DB operations inside mono { }
            val first = databaseClient.sql(INSERT_FIRST)....
            val second = databaseClient.sql(UPDATE_SECOND)....
            mono { Result.success(Unit) }
        }).awaitSingle()
    }
}
```

## Consequences

### Positive
- Single, consistent transaction strategy across the codebase
- No silent failures from proxy edge cases
- Explicit transaction boundaries aid debugging

### Negative
- Slightly more verbose than `@Transactional`
- Requires injecting `TransactionalOperator` into repositories that do multi-statement writes

## Migration

The following locations were migrated as part of issue #195:

| File | Before | After |
|------|--------|-------|
| `R2dbcPublicationRepository.kt` | `@Transactional deleteUnpublished()` | `TransactionalOperator` wrapper |
| `R2dbcApiKeyCredentialReplacementGateway.kt` | Raw `Connection` API | `DatabaseClient` + `TransactionalOperator` |

## References

- `PersistenceConfig.kt` — Transaction infrastructure configuration
- `R2dbcAtomicTransactionRunner.kt` — Reusable transaction runner for handlers
- [Epic #197](https://github.com/dallay/profiletailors.com/issues/197) — Reactive Transaction Remediation
