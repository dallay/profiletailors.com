# Design: Backend PostgreSQL Testcontainers Migration

## Technical Approach

Introduce shared PostgreSQL Testcontainers support for backend tests that validate real database semantics, while preserving the current fast H2/DB-free loop. The proposal maps to a selective migration: production SQL, Liquibase, constraints, indexes, locking, and R2DBC behavior move to `@Tag("postgres")`; pure domain/application tests and cheap adapter smoke tests stay outside Docker.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Shared Postgres support | Add reusable test support under `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support`, e.g. `PostgresTestContainerSupport` plus repository/Spring base helpers. | Keep per-test container blocks; replace all H2 bases. | Existing Postgres tests duplicate `PostgreSQLContainer`, `DynamicPropertySource`, and Liquibase wiring. Central support cuts drift without slowing every test. |
| Tag/command boundary | All Docker-backed tests use `@Tag("postgres")`; Gradle gets a dedicated `postgresIntegrationTest` task; Just exposes `backend-test-postgres`. | Run tagged tests through `test -PincludeTags=postgres`; include Postgres in `backend-check`. | Dedicated task is discoverable and keeps `backend-test-fast`/`ci-local` intentionally Docker-free. |
| H2 migration policy | Migrate only tests proving SQL, migrations, constraints, indexes, locks, `ON CONFLICT`, `FOR UPDATE`, or `SKIP LOCKED`. | Migrate every H2 test; keep only targeted regression tests. | Selective migration removes H2 as production authority while preserving feedback speed. |
| Cleanup/isolation | Central ordered cleanup includes `workspace_file_blobs`, media tables, publishing tables, auth/identity tables; each test starts from Liquibase baseline and deletes child-before-parent data. | Per-test ad hoc cleanup; recreate container per class. | Shared cleanup prevents leakage and avoids expensive container churn. |
| Media constraints | Re-enable PostgreSQL-native constraints/indexes in `media/002-add-workspace-file-blobs.yaml` and verify on Postgres. | Keep schema H2-compatible. | Production is PostgreSQL; media dedup depends on composite FK, partial GC index, status/hash checks, and concurrency-safe CAS behavior. |

## Data Flow

    Gradle/Just command ──→ JUnit tag filter ──→ postgres test base
             │                        │              │
             └── fast excludes ───────┘              ├── PostgreSQLContainer
                                                     ├── Liquibase baseline
                                                     └── ordered cleanup + seed

For migrated repository/Spring tests, JUnit starts the shared container once per class/JVM scope, registers R2DBC and Liquibase JDBC properties, applies `db.changelog-master.yaml`, cleans tables, seeds scenario data, then exercises production SQL against PostgreSQL.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/PostgresTestContainerSupport.kt` | Create | Shared container image, JDBC/R2DBC URLs, credentials, dynamic property helpers. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/PostgresDatabaseTestBase.kt` | Create | No-Spring repository base with Liquibase and ordered cleanup. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/PostgresIntegrationTestBase.kt` | Create/Modify | Spring/WebTestClient base or adapter for Postgres-backed integration tests. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/*Cleanup*` | Create/Modify | Central cleanup order including `workspace_file_blobs`. |
| `server/smp/src/test/kotlin/**/R2dbc*Test.kt` | Modify | Selectively migrate production-semantics tests from H2 base. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt` | Modify | Add Postgres Liquibase/schema assertions. |
| `server/smp/src/main/resources/db/changelog/media/002-add-workspace-file-blobs.yaml` | Modify | Restore PostgreSQL constraints/indexes for media dedup. |
| `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/springboot/SpringBootApplicationPlugin.kt` | Modify | Register `postgresIntegrationTest` with `includeTags("postgres")`, excluding Cucumber suites. |
| `Justfile` | Modify | Add `backend-test-postgres`; include it in `ci-full`, not `ci-local`. |

## Interfaces / Contracts

```kotlin
interface PostgresTestContainerSupport {
    fun r2dbcUrl(): String
    fun jdbcUrl(): String
    fun username(): String
    fun password(): String
}
```

Gradle contract: `:server:smp:test -PexcludeTags=modularity,postgres` remains fast; `:server:smp:postgresIntegrationTest` runs non-Cucumber `postgres` tests; `:server:smp:bddPostgresTest` remains the BDD suite.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Tag filtering and helper URL construction | Fast Kotlin tests where practical. |
| Integration | Liquibase applies to PostgreSQL; cleanup isolates tests; migrated repositories preserve behavior | `postgresIntegrationTest` with Testcontainers. |
| E2E | Not applicable | Backend-only test infrastructure change. |

## Migration / Rollout

No data migration required. Roll out in order: shared support, Gradle/Just commands, migrate highest-risk H2 bases, restore media-dedup PostgreSQL schema constraints, then wire `ci-full` to run Postgres integration plus Postgres BDD.

## Open Questions

- [ ] Exact reusable base naming can be finalized during implementation.
- [ ] CI runner Docker availability/timeout policy must be confirmed before making Postgres integration required outside `ci-full`.
