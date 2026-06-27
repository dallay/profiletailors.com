# Apply Progress: Backend PostgreSQL Testcontainers Migration

## Completed Tasks

- [x] 1.1 Added RED build-logic TestKit coverage for `postgresIntegrationTest` registration and fast `excludeTags=postgres` behavior.
- [x] 1.2 Registered `postgresIntegrationTest` in `SpringBootApplicationPlugin.kt` with `includeTags("postgres")` and Cucumber suite exclusions.
- [x] 1.3 Added `scripts/check-just-postgres-boundaries.sh` to assert Just command/tag boundaries.
- [x] 1.4 Added `backend-test-postgres` and wired `ci-full` to run PostgreSQL integration plus existing PostgreSQL BDD.
- [x] 2.1 Added support tests for shared PostgreSQL Testcontainers URL/property behavior.
- [x] 2.2 Added shared PostgreSQL Testcontainers support with JDBC/R2DBC URLs and dynamic property helper.
- [x] 2.3 Added cleanup isolation coverage for media-dependent tables including `workspace_file_blobs`.
- [x] 2.4 Added shared PostgreSQL database/Spring integration bases and ordered cleanup helper; updated existing H2 cleanup to include media tables.
- [x] 3.1 Added PostgreSQL-backed schema assertions for media constraints and indexes via `MediaPostgresSchemaConstraintsTest`.
- [x] 3.2 Restored PostgreSQL-native media dedup partial index, check constraints, primary key, and composite FK in Liquibase.
- [x] 3.3 Added representative PostgreSQL repository coverage for `ON CONFLICT`, CAS, `FOR UPDATE`, and `SKIP LOCKED` paths.
- [x] 3.4 Wired media repository/schema tests to shared PostgreSQL base while leaving pure tests DB-free.
- [x] 4.1 Added PostgreSQL invalid-row tests for composite FK, blob status/hash checks, and valid-row acceptance.
- [x] 4.2 Added PostgreSQL repository verification for media CAS, locking, skip-locked, conflict handling, and cleanup isolation.
- [ ] 4.3 `just backend-test-fast` not run due scope/time; targeted Docker-free support tests were run instead.
- [x] 4.4 Ran targeted `postgresIntegrationTest` coverage for schema and repository tests successfully.

## Evidence

- RED build logic: `./gradlew -p gradle/build-logic test --tests "...registers postgresIntegrationTest..."` failed before task registration.
- GREEN build logic: `./gradlew -p gradle/build-logic test --tests "com.profiletailors.buildlogic.springboot.SpringBootApplicationPluginTest"` passed.
- RED Just boundary: `bash scripts/check-just-postgres-boundaries.sh` failed before `backend-test-postgres` existed.
- GREEN Just boundary: `bash scripts/check-just-postgres-boundaries.sh` passed.
- RED support: `./gradlew :server:smp:test --tests "com.profiletailors.smp.integration.support.PostgresTestContainerSupportTest" --no-daemon -PexcludeTags=postgres,modularity` failed before support classes existed.
- GREEN support: same targeted command passed.
- RED schema: `./gradlew :server:smp:postgresIntegrationTest --tests "com.profiletailors.smp.infrastructure.db.MediaPostgresSchemaConstraintsTest" --no-daemon` failed before PostgreSQL-native constraints/indexes were restored.
- GREEN schema: same targeted command passed.
- GREEN media repository: `./gradlew :server:smp:postgresIntegrationTest --tests "com.profiletailors.smp.media.infrastructure.persistence.R2dbcMediaRepositoriesPostgresTest" --no-daemon` passed.

## Deviations

- PostgreSQL schema assertions were added in a dedicated `MediaPostgresSchemaConstraintsTest` rather than expanding the resource-only `LiquibaseBaselineChangelogTest`; this keeps Docker-backed checks tagged `postgres` and pure changelog resource tests Docker-free.
- `just backend-test-fast` was not run broadly because it is wider than targeted verification; targeted non-Docker support/build-logic checks were run.

## Remaining

- Optional: run full `just backend-test-fast` before PR if desired.
