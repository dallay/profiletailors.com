## Exploration: Backend PostgreSQL Testcontainers Migration

### Current State
Backend production runtime is PostgreSQL/R2DBC with Liquibase, and `server/smp/build.gradle.kts` already includes PostgreSQL, R2DBC PostgreSQL, H2, R2DBC H2, and Testcontainers PostgreSQL test dependencies. Most database-facing tests still run against H2 in PostgreSQL mode, either through `DatabaseUnitTestBase`, `IntegrationTestBase`, or per-test H2 connection factories. PostgreSQL Testcontainers is already used for selected `@Tag("postgres")` tests and the BDD Postgres suite. Fast commands intentionally exclude `postgres` and `modularity` tags, while `ci-full` currently runs only Postgres BDD after `ci-local`.

The active `media-asset-dedup` change is in `apply` with `verify` next. Its media blob Liquibase migration was recently weakened for H2 compatibility: composite FK, partial index, and CHECK-style PostgreSQL constraints were removed/avoided, which is exactly the pressure this change should address.

### Affected Areas
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/DatabaseUnitTestBase.kt` — central H2 + Liquibase base for repository-style infrastructure tests; primary candidate to replace or split with PostgreSQL Testcontainers support.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt` — Spring/WebTestClient integration base still assumes H2 defaults; Postgres endpoint tests override pieces manually.
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/postgres/*` — existing Testcontainers pattern for BDD PostgreSQL variant; useful but specific to Cucumber.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingQueuePostgresIntegrationTest.kt` — existing repository/Spring Testcontainers pattern using `@Tag("postgres")`, `@DynamicPropertySource`, and Liquibase JDBC properties.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/*PostgresIntegrationTest.kt` — endpoint-level PostgreSQL integration examples that override R2DBC and Liquibase properties.
- `server/smp/src/test/kotlin/**/R2dbc*Test.kt` — many infrastructure tests use direct H2 factories or `DatabaseUnitTestBase`; important DB behavior should migrate selectively, not wholesale.
- `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt` — currently text-only verification; should grow into a real PostgreSQL Liquibase context/schema assertion.
- `server/smp/src/main/resources/db/changelog/media/002-add-workspace-file-blobs.yaml` — media dedup schema currently lacks the PostgreSQL-only constraints/indexes removed for H2 compatibility.
- `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/springboot/SpringBootApplicationPlugin.kt` — owns JUnit tag filtering and custom BDD test tasks; likely place for a dedicated Postgres integration test task.
- `Justfile` — already has `backend-test-fast`, `backend-bdd-postgres`, `ci-local`, and `ci-full`; should expose clear commands for fast vs Docker-backed PostgreSQL integration tests.
- `openspec/changes/media-asset-dedup/state.yaml` — active change is at `apply -> verify`; this migration should be coordinated because it unlocks proper verification of its DB constraints.

### Approaches
1. **Add shared PostgreSQL test base and migrate important DB tests selectively** — create a reusable PostgreSQL Testcontainers base/helper for repository and Spring/Liquibase tests, keep pure domain/application tests untouched, tag Docker-backed tests with `postgres`.
   - Pros: Aligns tests with production DB semantics; contains cost with tags; reduces repeated container boilerplate; preserves fast unit suite.
   - Cons: Requires careful classification of existing H2 tests; initial migration may expose real schema/query issues.
   - Effort: Medium

2. **Replace all H2 database tests with PostgreSQL Testcontainers** — remove H2 from DB-facing tests broadly and run all infrastructure/integration tests against containers.
   - Pros: Strongest production parity; eliminates H2 compatibility hacks.
   - Cons: Slower default feedback loop; likely disrupts `backend-test-fast`/CI local; over-migrates tests that are merely exercising mapper logic.
   - Effort: High

3. **Keep H2 as default and add only targeted PostgreSQL regression tests** — leave most current bases untouched; add new Postgres tests only for PostgreSQL-specific SQL, Liquibase constraints, media dedup indexes/FKs, and known H2 gaps.
   - Pros: Lowest disruption; fastest to implement; preserves all existing commands.
   - Cons: H2 compatibility pressure remains; duplicate coverage patterns continue; future schema work may keep weakening migrations for H2.
   - Effort: Low

### Recommendation
Use Approach 1, with a narrow migration policy: pure domain/application tests remain DB-free; DB repository tests and Spring/Liquibase context tests that validate SQL, constraints, indexes, R2DBC behavior, or migrations move to PostgreSQL Testcontainers and get `@Tag("postgres")`; fast tests continue excluding `postgres`. Add a dedicated Gradle/Just command for tagged PostgreSQL integration tests so `ci-local` stays fast and `ci-full` can include both Postgres BDD and Postgres integration coverage.

For `media-asset-dedup`, prioritize PostgreSQL schema verification for `workspace_file_blobs` and `media_assets`: composite FK behavior, partial GC index, status/hash CHECK constraints, and CAS repository concurrency queries (`FOR UPDATE`, `SKIP LOCKED`, `ON CONFLICT`). This should happen before archive/production hardening of that change.

### Risks
- Docker/Testcontainers availability can make local and CI runs flaky unless commands clearly separate fast and Docker-backed suites.
- A shared container strategy must avoid cross-test data leakage; cleanup order now misses `workspace_file_blobs` in at least the H2 bases.
- Re-enabling PostgreSQL-only constraints may break current Liquibase changeSets or existing fixtures that were made H2-compatible.
- If this lands while `media-asset-dedup` is still mid-verify, task ownership may blur between schema hardening and feature verification.

### Ready for Proposal
Yes — propose a dedicated `backend-postgres-testcontainers` change that introduces PostgreSQL test support and commands first, migrates important DB/Liquibase tests second, and then re-enables PostgreSQL-specific Liquibase constraints/indexes, with `media-asset-dedup` called out as the first consumer/risk driver.
