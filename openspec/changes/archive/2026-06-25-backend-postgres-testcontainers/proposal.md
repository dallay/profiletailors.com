# Proposal: Backend PostgreSQL Testcontainers Migration

## Intent

Stop weakening production PostgreSQL schema and SQL behavior to satisfy H2. Important backend DB, Liquibase, repository, and Spring integration tests should run against PostgreSQL Testcontainers, while pure domain/application tests remain fast and DB-free.

## Scope

### In Scope
- Add shared PostgreSQL Testcontainers support for backend repository and Spring integration tests.
- Selectively migrate tests that validate SQL, constraints, indexes, Liquibase, R2DBC behavior, locking, or concurrency.
- Keep fast pure tests excluding Docker-backed `postgres` tests.
- Add Gradle/Just commands so `ci-local` stays fast and `ci-full` can include PostgreSQL integration plus BDD.
- Coordinate with active `media-asset-dedup` by restoring/verifying PostgreSQL-only media constraints.

### Out of Scope
- Migrating every H2 test wholesale.
- Replacing pure unit/application tests with containers.
- New product behavior for media assets.
- Broader CI redesign beyond clear fast vs Docker-backed commands.

## Capabilities

### New Capabilities
- `backend-postgres-testcontainers`: Backend test infrastructure policy for PostgreSQL-backed integration, migration, and repository verification.

### Modified Capabilities
- None. Media-library product requirements remain unchanged; this change improves PostgreSQL verification for existing/active media constraints.

## Approach

Use selective migration. Keep H2 only where it supports cheap adapter smoke tests or simple mapping checks. Move production-semantics tests to shared PostgreSQL Testcontainers bases tagged `postgres`. First consumer: `media-asset-dedup` constraints for `workspace_file_blobs`/`media_assets`, including composite FK, partial GC index, status/hash checks, CAS, `FOR UPDATE`, `SKIP LOCKED`, and `ON CONFLICT` behavior.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/test/kotlin/.../support` | New/Modified | Shared PostgreSQL container bases/helpers |
| `server/smp/src/test/kotlin/**/R2dbc*Test.kt` | Modified | Selective migration from H2 |
| `server/smp/src/main/resources/db/changelog/media/*` | Modified | Re-enable PostgreSQL-native constraints/indexes where required |
| `gradle/build-logic/.../SpringBootApplicationPlugin.kt` | Modified | Tagged PostgreSQL integration task |
| `Justfile` | Modified | Explicit fast vs Postgres commands |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Docker/Testcontainers flakiness | Med | Keep outside fast suite; document commands |
| Data leakage between tests | Med | Central cleanup including `workspace_file_blobs` |
| Media-dedup overlap | Med | Treat this as verification enabler, not ownership transfer |
| Slower CI | Low | Run in `ci-full`, not `ci-local` |

## Tradeoffs

Selective migration gives production parity without punishing every edit loop. It keeps some H2 coverage, but removes H2 as the authority for PostgreSQL-only behavior.

## Rollout Plan

1. Add shared Postgres test support and command wiring.
2. Migrate highest-risk DB/Liquibase tests.
3. Restore media-dedup PostgreSQL constraints.
4. Add focused regression coverage, then include in `ci-full`.

## Rollback Plan

Revert changed test bases, command wiring, migrated tests, and restored media changelog constraints. Keep pure test commands unchanged, so fallback is `backend-test-fast`/`ci-local` while Postgres suite is repaired.

## Dependencies

- Local/CI Docker availability.
- Active `media-asset-dedup` verification timing.

## Success Criteria

- [ ] Fast pure tests still run without Docker.
- [ ] PostgreSQL integration command runs tagged DB tests.
- [ ] `ci-full` includes Postgres integration coverage.
- [ ] Media dedup PostgreSQL constraints and concurrency SQL are verified on PostgreSQL.
