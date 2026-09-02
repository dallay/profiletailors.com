# Exploration: Stabilize Local CI PostgreSQL Tests

## Current State

This is a technical test-harness and CI change, not a product capability. The repository's
OpenSpec guide says that CI quality gates, dependency policy, test-harness plans, and deployment
plumbing normally belong in workflows, ADRs, skills, testing documentation, or operational
documentation rather than in `openspec/specs/`. The artifact is nevertheless kept here because the
orchestrator explicitly requested an SDD exploration for this named change.

The backend uses Gradle convention plugins. `SpringBootApplicationPlugin` applies the optional
`-PincludeTags` and `-PexcludeTags` values to every Gradle `Test` task. It registers
`postgresIntegrationTest` with `includeTags("postgres")`, while the normal `test` task excludes
only the two Cucumber test classes. Therefore `:server:smp:test` runs `@Tag("postgres")` tests
unless the caller supplies `-PexcludeTags=postgres`.

The command boundary is inconsistent:

- `just backend-test-fast` currently runs `:server:smp:test -PexcludeTags=modularity,postgres`.
- `ci-local` uses the same exclusions.
- `just ci` step `[13/15]` invokes `:server:smp:test --no-daemon` with no tag exclusions.
- `.github/workflows/ci.yml` `backend-unit` also invokes `:server:smp:test` without a `postgres`
  exclusion, while a separate conditional `backend-postgres` job invokes
  `postgresIntegrationTest`.
- `quality-gate.yml` likewise runs the ordinary `test` task and then the dedicated PostgreSQL
  task, without an explicit tag exclusion on the ordinary task.
- `lefthook.yml` pre-push explicitly excludes `postgres`; this is a conservative local hook, not a
  full PostgreSQL validation lane.

The intended separation is visible in `ci-full`: it runs the fast non-PostgreSQL suite first and
then runs `postgresIntegrationTest` and `bddPostgresTest`. The repository's test-tag document,
however, still says that CI runs PostgreSQL tests by default and that `backend-test-fast` includes
them, which conflicts with the current command recipes. This documentation drift must be resolved
as part of the implementation decision rather than silently perpetuated.

The PostgreSQL integration tests use `PostgresTestContainerSupport.newContainer("...")`,
`@Testcontainers(disabledWithoutDocker = true)`, and `PostgresDatabaseTestBase`. The base registers
the container's mapped host/port for R2DBC and JDBC, then applies a Liquibase baseline through
`DriverManager` in `@BeforeEach` before cleaning the database. `R2dbcPublishingRepositoriesTest`
and `R2dbcPublishingRepositoriesUnitTest` are both `@Tag("postgres")` classes and inherit that
setup. `R2dbcBulkImportJobRepositoryTest` is deliberately a plain MockK unit test and is not
PostgreSQL-tagged.

## Reproduction Evidence

### PostgreSQL setup failure

The orchestrator's bounded focused runs selected `R2dbcPublishingRepositoriesTest` through both
the ordinary `test` task and `postgresIntegrationTest`. In each run, Testcontainers reported
`postgres:18-alpine` starting in about 1.05 seconds with a mapped JDBC port such as `localhost:32818`,
then `PostgresDatabaseTestBase.applyLiquibaseBaseline` immediately failed with
`PSQLException: Connection refused` for that mapped endpoint. Fourteen tests failed during shared
setup, before repository SQL or assertions ran.

This is not evidence that Docker or the PostgreSQL image is globally unusable. A manual
`docker run postgres:18-alpine` on the active Dory socket accepted `pg_isready` and JDBC/psql
connections. The active Docker context is `dory`, the Dory socket is reachable, and the focused
failure is specific to the Testcontainers-created endpoint or its lifecycle/port publication.

The local `~/.testcontainers.properties` contains:

```text
docker.host=unix:///Users/acosta/.dory/dory.sock
testcontainers.reuse.enabled=true
```

The test containers do not explicitly call `.withReuse(true)`. `TESTCONTAINERS_RYUK_DISABLED=true`
did not repair the focused PostgreSQL repository run. A separate invocation that selected no tests
completed successfully, so it is not passing evidence.

Dependency resolution also exposes a real alignment risk: the project declares the Testcontainers
BOM/modules at `1.21.4`, but Spring Boot `4.0.8` selects core
`org.testcontainers:testcontainers:2.0.5`; `org.testcontainers:postgresql` and
`org.testcontainers:junit-jupiter` remain `1.21.4`. This mixed graph is a candidate cause or
amplifier, but the current evidence does not prove causality. It needs a bounded dependency-alignment
experiment before changing versions.

`javap` confirms the Kotlin companion `@Container val` is emitted as a private static final field
with the Testcontainers annotation. A missing static container field is therefore not the primary
cause indicated by the current evidence.

### MockK unit-test hang

The long-running process owned by this worktree was reproduced with the exact test method
`R2dbcBulkImportJobRepositoryTest.saveRows chunked 100 splits 101 rows`. A bounded run with
`-Djunit.jupiter.execution.timeout.default=5s` produced a deterministic failure after the coroutine
test timeout:

```text
R2dbcBulkImportJobRepositoryTest > saveRows inserts single chunk() FAILED
kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
```

The attempted `chunked 100 splits 101 rows` reproduction remained stuck in the test worker. The
thread dump identified the exact wait:

```text
R2dbcBulkImportJobRepositoryTest.saveRows chunked 100 splits 101 rows(R2dbcBulkImportJobRepositoryTest.kt:146)
  kotlinx.coroutines.BlockingCoroutine.joinBlocking
  kotlinx.coroutines.runBlocking
```

The repository production method binds `rowIndex` with `DatabaseClient.GenericExecuteSpec.bind(String,
Object)` using an `Int`. The test helper `mockSaveRowsSpec()` configures typed overloads for
`bind(String, String)`, `bind(String, Instant)`, and `bind(String, Boolean)`, plus two `bindNull`
overloads, but not `bind(String, Int)`. Because the mock is `relaxed = true`, the missing overload
can return a relaxed mock rather than the configured fluent spec. That breaks the chain reaching the
configured `fetch().rowsUpdated()` publisher, leaving `awaitSingle()` without completion. The same
helper is used by the single-chunk and nullable-field tests, so the issue is broader than the 101-row
case. The exact overload mismatch is strongly supported by source and the deterministic timeout;
the implementation phase should add a failing regression assertion that proves completion and then
configure or otherwise model the correct overload without weakening the test.

The test was introduced with the bulk-scheduling change in commit `2a1946b6` (test file added in
`3ba59659`, then included in the merged bulk change). The parent of that change had no such test, so
this hang is new with the bulk repository coverage rather than an old repository-test behavior.

The investigation-owned hung Gradle process/test worker was terminated by process ID after its
worktree and command ownership were verified. Other long-running test workers belonged to different
worktrees and were not terminated. The worktree remained clean after the investigation.

## Affected Areas

- `Justfile` — step `[13/15]` of `ci` does not express the fast/non-PostgreSQL boundary already used
  by `ci-local` and `backend-test-fast`; `ci-full` is the explicit PostgreSQL lane.
- `.github/workflows/ci.yml` — `backend-unit` runs the ordinary test task while a separate
  `backend-postgres` job runs `postgresIntegrationTest`; whether the ordinary job should exclude
  `postgres` is a scope decision, not a local-only detail.
- `.github/workflows/quality-gate.yml` — ordinary backend tests and coverage invoke the same mixed
  boundary and may duplicate or expose the same Testcontainers failures.
- `lefthook.yml` — already excludes `postgres`; its comments and command should remain consistent
  with the chosen local-vs-CI contract.
- `docs/testing/test-tags-and-env.md` — claims no exclusions by default in CI and says the fast
  command includes PostgreSQL tests, contrary to current recipes and the proposed separation.
- `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/springboot/SpringBootApplicationPlugin.kt`
  — owns tag property behavior and dedicated PostgreSQL task registration; it is a likely place for
  a durable task-boundary fix only if callers cannot be made explicit.
- `gradle/build-logic/src/test/kotlin/com/profiletailors/buildlogic/springboot/SpringBootApplicationPluginTest.kt`
  — existing contract test proves that `-PexcludeTags=postgres` excludes tagged tests, but does not
  prove the ordinary task's default policy or the dedicated task's interaction with global excludes.
- `gradle/libs.versions.toml` and `server/smp/build.gradle.kts` — declare the Testcontainers BOM and
  modules; dependency alignment must be experimentally validated before a version change.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/PostgresTestContainerSupport.kt`
  — shared container startup, mapped URL construction, and Liquibase baseline path used by all
  PostgreSQL-tagged integration classes.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/R2dbcPublishingRepositoriesTest.kt`
  and `.../R2dbcPublishingRepositoriesUnitTest.kt` — representative PostgreSQL-tagged classes
  failing in shared baseline setup.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcBulkImportJobRepository.kt`
  and `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcBulkImportJobRepositoryTest.kt`
  — production `Int` binding and the MockK helper mismatch behind the deterministic unit-test hang.
- `openspec/README.md` and `openspec/config.yaml` — establish that this is technical CI/test-harness
  work and that OpenSpec is being used here because the SDD orchestration explicitly requires it.

Product files and `.agents/DESIGN.md` were inspected and are not affected: this change alters local
and CI validation behavior, not product behavior, UX, copy, consent, or web surfaces. No product
capability spec or frontend artifact should be added.

## Approaches

1. **Explicit command-boundary separation** — make `ci` step `[13/15]` use the same non-PostgreSQL
   test command as `ci-local`/`backend-test-fast`; fix the MockK overload test; keep PostgreSQL
   validation in `ci-full` and the dedicated `backend-postgres` job. Decide explicitly whether
   `backend-unit` and `quality-gate` should also add `-PexcludeTags=postgres` to avoid duplicate
   container execution.
   - Pros: smallest blast radius; preserves the dedicated PostgreSQL lane; makes local `just ci`
     bounded and predictable; directly fixes the proven unit hang; keeps the Gradle plugin generic.
   - Cons: if the PostgreSQL Testcontainers failure is a genuine CI issue, excluding it from an
     ordinary unit job can delay feedback unless the dedicated job is reliable and required.
   - Effort: Medium

2. **Make ordinary `test` non-PostgreSQL by default in the convention plugin** — change the Gradle
   `test` task policy so PostgreSQL-tagged tests never run there, and reserve `postgresIntegrationTest`
   for that tag. Add plugin contract tests and update every command/documentation entry.
   - Pros: prevents future callers from accidentally mixing unit and container suites; encodes the
     task boundary at its owner instead of repeating a property in recipes.
   - Cons: changes implicit Gradle behavior for all callers; may conflict with the documented
     no-exclusions CI posture and with coverage commands that intentionally expect all tests; the
     dedicated task's global `excludeTags` behavior needs careful design; larger compatibility and
     review surface.
   - Effort: Medium/High

3. **Align or upgrade Testcontainers dependencies first** — resolve the Spring Boot 4.0.8-managed
   core `2.0.5` versus project `1.21.4` module graph, then re-run the focused PostgreSQL classes.
   - Pros: removes a concrete dependency graph inconsistency and could repair endpoint lifecycle
     behavior without changing test ownership.
   - Cons: no evidence yet that this mismatch causes the immediate connection refusal; upgrades can
     introduce API/runtime changes and lockfile-equivalent Gradle review; it does not address the
     independent MockK hang or the command mismatch.
   - Effort: Medium

4. **Harden Testcontainers lifecycle/connectivity** — add explicit wait strategy/readiness checks,
   disable or control reuse, or change container lifecycle/driver setup in the shared PostgreSQL
   test base.
   - Pros: targets the observed failure boundary directly and may make Dory/mapped-port startup
     races diagnosable or reliable.
   - Cons: risks masking a dependency mismatch or changing a shared fixture for thirteen callers;
     reuse settings are user-machine-specific; more lifecycle code means more maintenance. It must
     not be attempted until a bounded experiment distinguishes readiness/port publication from
     dependency incompatibility.
   - Effort: Medium/High

## Recommendation

Use **Approach 1 as the first implementation slice**, with a deliberate CI scope decision before
apply:

- Treat the ordinary local `just ci` backend step as a fast, non-PostgreSQL lane, matching
  `ci-local`, `backend-test-fast`, and the separate `ci-full` PostgreSQL lane.
- Fix `R2dbcBulkImportJobRepositoryTest`'s missing `Int` bind overload under TDD and add a focused
  completion regression check. Do not alter production binding merely to accommodate a broken mock.
- Keep `SpringBootApplicationPlugin`'s generic `excludeTags` mechanism unchanged initially. Add or
  strengthen plugin tests only if the selected command contract requires it.
- Before implementation, run bounded experiments that compare the representative PostgreSQL tests
  with the currently resolved mixed dependencies and an aligned Testcontainers graph, and inspect
  readiness/port publication without touching unrelated worktrees. If alignment alone fixes the
  PostgreSQL failure, split it into a separate dependency-focused work unit if the changed surface
  would exceed the review budget.
- Decide whether `.github/workflows/ci.yml` `backend-unit` and `quality-gate.yml` ordinary test and
  coverage invocations are intended to be unit/non-PostgreSQL lanes. The strongest consistent
  contract is to exclude `postgres` there because dedicated PostgreSQL jobs already exist, while
  still keeping those dedicated jobs required. If the team intentionally wants ordinary CI to run
  all PostgreSQL tests, limit the local recipe change and record why local and remote contracts
  differ.
- Update `docs/testing/test-tags-and-env.md` only after that decision. The current prose cannot
  remain simultaneously true with the current recipes.

Do not promote this to a product capability spec. A durable ADR may be appropriate if the team
chooses a new ordinary-test versus dedicated-integration-test policy or changes dependency
direction; otherwise the workflow/testing documentation and focused tests are the owning artifacts.

## Risks

- **PostgreSQL failure cause is unresolved.** The connection refusal occurs in shared Liquibase setup
  after Testcontainers reports startup, but the exact Dory/Testcontainers/dependency cause is not
  proven. Do not claim that tag exclusions fix PostgreSQL integration behavior; they only keep the
  fast lane from entering it.
- **Mixed Testcontainers graph.** Boot-managed core `2.0.5` with project modules `1.21.4` is a
  concrete alignment risk, but changing it without a controlled A/B experiment could introduce a
  second regression.
- **Duplicate CI coverage.** Excluding PostgreSQL from `backend-unit` or quality coverage reduces
  duplicate execution only if the dedicated PostgreSQL job remains required and its trigger/path
  behavior covers the same changes.
- **Coverage semantics.** Moving `postgres` out of ordinary `test`/coverage changes what reports
  represent; the quality gate and report expectations must be checked before changing
  `quality-gate.yml`.
- **MockK relaxed behavior.** Adding a typed overload should fix the fluent chain, but the test should
  verify the expected `bind` calls so a future overload mismatch fails quickly instead of hanging.
- **Fixture blast radius.** `PostgresDatabaseTestBase` has thirteen callers; lifecycle changes affect
  all PostgreSQL integration classes and must be isolated and tested with representative classes.
- **Local process ownership.** The investigation left unrelated worktree workers untouched; future
  diagnostics must continue to terminate only processes proven to belong to this worktree.
- **Review size.** A combined Justfile, workflows, dependency, shared fixture, unit-test, plugin-test,
  and documentation change may exceed the repository's 400-line review budget; `sdd-tasks` must
  forecast this and recommend chained slices if necessary.

## Ready for Proposal

**Yes, with one explicit decision required before proposal approval:** whether the remote ordinary
`backend-unit` and `quality-gate` test invocations should adopt the same non-PostgreSQL boundary as
local `ci`, given that dedicated PostgreSQL jobs already exist. The root cause of the MockK hang is
sufficiently supported for a proposal. The PostgreSQL connection refusal remains an investigation
risk and should be framed as a separate diagnostic/mitigation track, not declared fixed by tag
exclusion.

Bounded evidence collected:

| Command or inspection | Result |
| --- | --- |
| `just -l` | PASS; command hub exposes `ci`, `ci-local`, `ci-full`, `backend-test-fast`, and `backend-test-postgres`. |
| `git status --short --branch` | PASS; clean `main` before and after investigation. |
| `./gradlew :server:smp:dependencyInsight --dependency org.testcontainers:testcontainers --configuration testRuntimeClasspath --no-daemon --console=plain` | PASS; resolves core `2.0.5` through Spring Boot while the project BOM/modules request `1.21.4`. |
| Same command for `org.testcontainers:postgresql` | PASS; resolves `1.21.4`. |
| `docker context show`, Dory socket connect, `docker info` | PASS; context `dory`, socket reachable, server `29.6.1`. |
| Manual `postgres:18-alpine` container readiness/connectivity check | PASS; manual container accepted readiness and JDBC/psql connections. |
| Focused `:server:smp:test --tests '*R2dbcPublishingRepositoriesTest*'` | FAIL; 14 setup failures in about 20s with mapped-port connection refused. |
| Focused `:server:smp:postgresIntegrationTest --tests '*R2dbcPublishingRepositoriesTest*'` | FAIL; same 14 setup failures. |
| Focused `R2dbcBulkImportJobRepositoryTest` single-chunk/101-row bounded runs | FAIL; coroutine timeout/hang; thread dump stops at `runBlocking` line 146. |
| `TESTCONTAINERS_RYUK_DISABLED=true` focused PostgreSQL attempt | INCONCLUSIVE/NO FIX; it did not fix the focused run, and the successful no-test invocation did not exercise behavior. |

## Unresolved Questions for Proposal

1. Is the canonical remote contract `backend-unit` plus `quality-gate` ordinary tests excluding
   `postgres`, with `backend-postgres` as the required integration lane, or must ordinary CI retain
   all PostgreSQL tests for coverage?
2. Does a controlled aligned Testcontainers version graph remove the mapped-port connection refusal,
   or is the failure caused by Dory port publication/readiness/reuse behavior?
3. Should quality coverage include PostgreSQL execution, and if so should it use only the dedicated
   task rather than running tagged tests through ordinary `test` first?
4. Should the shared fixture explicitly disable reuse or add a readiness assertion, and what evidence
   would justify that change without affecting all thirteen callers?
