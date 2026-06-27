# Tasks: Backend PostgreSQL Testcontainers Migration

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 300-450 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | PostgreSQL test infrastructure and command separation | PR 1 | Shared support, Gradle/Just wiring, fast suite protected |
| 2 | Representative DB migration and media schema verification | PR 1 | Tagged tests, cleanup, PostgreSQL-native constraints |

## Phase 1: Foundation / Infrastructure

- [x] 1.1 RED: Add tests for `SpringBootApplicationPlugin.kt` proving fast tests exclude `postgres` and `postgresIntegrationTest` includes only `postgres` non-Cucumber tests.
- [x] 1.2 GREEN: Register `postgresIntegrationTest` in `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/springboot/SpringBootApplicationPlugin.kt`.
- [x] 1.3 RED: Add Just command checks or scripted assertions for `backend-test-fast`, `backend-test-postgres`, `ci-local`, and `ci-full` tag boundaries.
- [x] 1.4 GREEN: Update `Justfile` so `backend-test-postgres` runs Postgres integration tests and `ci-full` includes it plus existing Postgres BDD.

## Phase 2: Shared PostgreSQL Test Support

- [x] 2.1 RED: Create failing support tests/spec checks for shared R2DBC/JDBC/Liquibase property generation in `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support`.
- [x] 2.2 GREEN: Create `PostgresTestContainerSupport.kt` with shared container image, credentials, JDBC/R2DBC URLs, and dynamic property helpers.
- [x] 2.3 RED: Add cleanup isolation test covering media and dependent backend tables, including `workspace_file_blobs`.
- [x] 2.4 GREEN: Create `PostgresDatabaseTestBase.kt`, `PostgresIntegrationTestBase.kt`, and ordered cleanup helper deleting child-before-parent rows.

## Phase 3: Selective Migration / Schema Hardening

- [x] 3.1 RED: Add PostgreSQL Liquibase assertions in `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt` for schema/index/constraint presence.
- [x] 3.2 GREEN: Restore PostgreSQL-native media constraints/indexes in `server/smp/src/main/resources/db/changelog/media/002-add-workspace-file-blobs.yaml`.
- [x] 3.3 RED: Convert representative `server/smp/src/test/kotlin/**/R2dbc*Test.kt` cases to `@Tag("postgres")` for constraints, indexes, locks, `ON CONFLICT`, `FOR UPDATE`, and `SKIP LOCKED`.
- [x] 3.4 GREEN: Wire migrated repository/Spring tests to shared Postgres bases; leave pure domain/application tests Docker-free.

## Phase 4: Verification / Cleanup

- [x] 4.1 RED: Add PostgreSQL-backed media invalid-row tests for composite FK, status/hash checks, and valid-row acceptance.
- [x] 4.2 GREEN: Add repository verification for media CAS, row-locking, skip-locked, and conflict-handling persistence outcomes.
- [x] 4.3 Run `just backend-test-fast` to prove Docker-free tests exclude `postgres`.
  - Passed 2026-06-25: `just backend-test-fast` completed `:server:smp:test -PexcludeTags=modularity,postgres` successfully in 48s.
- [x] 4.4 Run `just backend-test-postgres` to prove tagged PostgreSQL Testcontainers coverage passes and cleanup prevents leakage.
