# Design: Stabilize Local CI PostgreSQL Tests

## Technical Approach

Three concrete edits in one slice: (1) TDD fix the MockK `mockSaveRowsSpec()` helper so it
configures `bind(String, Int)` matching production's `bind("rowIndex${row.rowIndex}", row.rowIndex)`
in `R2dbcBulkImportJobRepository.saveRows`; (2) align local `just ci` step `[13/15]` with
`ci-local`/`backend-test-fast` by passing `-PexcludeTags=modularity,postgres`; (3) reconcile
`docs/testing/test-tags-and-env.md` against the actual command contract.

The CI-workflow scope (`.github/workflows/ci.yml` and `.github/workflows/quality-gate.yml`) is held
pending user choice between Option A and Option B (see [Held CI Decision](#held-ci-decision)).

## Architecture Decisions

| Decision | Choice | Trade-off | Rationale |
|---|---|---|---|
| MockK overload coverage | Configure `every { spec.bind(any<String>(), any<Int>()) } returns spec` in `mockSaveRowsSpec()` | Adds one `every` block | Production binds `rowIndex` as `Int`; relaxed mock returns disconnected fluent spec; `awaitSingle()` hangs. Mirrors existing `mockSaveSpec()` which already configures the `Int` overload (lines 290). |
| Regression test placement | Add `saveRows binds rowIndex as Int via bind(String, Int)` to `R2dbcBulkImportJobRepositoryTest` | New test, ~15 lines | Spec scenario requires assertion of `bind(String, Int)` invocation and `runTest` completion before fix. Failure proves the missing configuration; pass after fix proves the helper. |
| Coroutine harness for new test | `runTest` with explicit timeout via `withTimeout` or `@Test(timeout = …)` | Slightly more verbose than `runBlocking` | The user's instruction explicitly requires `runTest` timeout. The existing chunked 100 test uses `runBlocking` and stays untouched (out of scope). |
| `just ci` step `[13/15]` flag | Append `-PexcludeTags=modularity,postgres` to the Gradle invocation | Aligns with `ci-local`/`backend-test-fast`/`backend-test-fast` recipe (line 223) | Spec scenario requires matching exclusion exactly. Step label "Backend unit tests (fast)" was always a non-PostgreSQL contract; the flag was missing. |
| Docs posture | Remove "no exclusions by default" prose; describe `backend-test-fast` and `just ci` step `[13/15]` as excluding `postgres`; describe `ci-full` and `backend-postgres` as authoritative PostgreSQL lanes | Section rewrite | Spec scenario requires the document to match actual recipes; current prose contradicts the Justfile. |
| CI workflow scope | **Held** — see [Held CI Decision](#held-ci-decision) | User must choose A or B before `sdd-tasks` | State.yaml records `blocking: true` and `required_decision_before: [design, tasks]`. |
| Testcontainers dep graph | Untouched | No version change in this change | Spring Boot 4.0.8 selects core `2.0.5` while modules request `1.21.4`; bounded A/B experiment required before any version move. |
| `PostgresTestContainerSupport` lifecycle | Untouched | No readiness/wait/reuse change | 13 callers share this fixture; lifecycle changes must be isolated and proven. |
| `SpringBootApplicationPlugin` generic `excludeTags` mechanism | Untouched | No default policy change at the plugin level | Callers stay explicit; aligns with the proposal's "keep Gradle plugin generic" recommendation. |

## Data Flow

The MockK chain in `R2dbcBulkImportJobRepository.saveRows` (production lines 95–116) for each row
calls `spec.bind("rowIndex${row.rowIndex}", row.rowIndex)` where `rowIndex: Int`. With the helper
fix, the chain stays connected: every `bind(...)` returns `spec`, `spec.fetch().rowsUpdated()`
returns the configured `Mono.just(1L)`, and `awaitSingle()` completes. Without the fix, the
missing overload triggers MockK's relaxed fallback on `DatabaseClient.GenericExecuteSpec`, returns a
disconnected mock, and `awaitSingle()` never receives a signal.

```
saveRows → spec.bind("rowIndex…", rowIndex:Int) → spec.fetch().rowsUpdated().awaitSingle()
                                              ↑
                          every { spec.bind(any<String>(), any<Int>()) } returns spec  (NEW)
```

The Justfile change has no data flow impact; it only narrows the test-task Gradle filter.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcBulkImportJobRepositoryTest.kt` | Modify | Add regression test; add one `every { spec.bind(any<String>(), any<Int>()) } returns spec` to `mockSaveRowsSpec()`. Do not touch production binding in `R2dbcBulkImportJobRepository.kt`. |
| `Justfile` | Modify | Step `[13/15]` of `ci` (line 485) becomes `node scripts/gradle-run.mjs :server:smp:test --no-daemon -PexcludeTags=modularity,postgres`. |
| `docs/testing/test-tags-and-env.md` | Modify | Rewrite Overview (no-exclusions claim); rewrite `backend-test-fast` description; rewrite `postgres` tag section; add authoritative PostgreSQL lane statement. |
| `.github/workflows/ci.yml` | Conditional Modify (Option A only) | Add `-PexcludeTags=postgres` to `backend-unit` step (line 171). See Option A section. |
| `.github/workflows/quality-gate.yml` | Conditional Modify (Option A only) | Add `-PexcludeTags=postgres` to the `Run backend tests` step (line 40) and `Generate backend coverage reports` step (line 43). See Option A section. |
| `docs/testing/test-tags-and-env.md` | Conditional Modify (Option B only) | Add "Local vs remote CI divergence" section with rationale and re-evaluation criteria. See Option B section. |

## Held CI Decision

The user has **NOT** chosen between Option A and Option B. The orchestrator must collect the choice
before delegating `sdd-tasks`. Both paths are described below.

### Option A — Exclude `postgres` from remote ordinary/coverage lanes

**Workflow edits:**

1. `.github/workflows/ci.yml` `backend-unit` (line 171):
   `./gradlew :server:smp:test -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest
   -PexcludeTags=postgres --no-daemon`
2. `.github/workflows/quality-gate.yml` `Run backend tests` (line 40):
   `./gradlew :server:smp:test -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest
   :shared:common:test … :shared:shield:ratelimit:test -PexcludeTags=postgres --no-daemon`
3. `.github/workflows/quality-gate.yml` `Generate backend coverage reports` (line 43):
   `./gradlew :server:smp:koverXmlReport :server:smp:postgresIntegrationTest
   -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest … -PexcludeTags=postgres --no-daemon`

`backend-postgres` (line 215) stays untouched: it owns the dedicated PostgreSQL lane and remains
required by the `CI Gate`.

**Verification before merging:**

| Check | How |
|---|---|
| `backend-postgres` is required | Inspect `ci-gate` job `needs:` list (lines 333–344); confirm the job remains in the gate. |
| `koverXmlReport` semantics preserved | Confirm coverage generation step still invokes `koverXmlReport`; XML output paths unchanged (lines 47–55). |
| Codecov backend upload intact | Confirm `Upload Backend coverage (shared)` and `Upload SMP coverage (server)` steps still reference the same XML paths (lines 119–139). |
| SonarQube signal intact | Confirm `SonarQube Scan` step runs after the modified coverage step; `sonar-project.properties` path unchanged. |
| No duplicate PostgreSQL execution | Confirm `backend-unit` no longer invokes `postgresIntegrationTest` (it didn't before; Option A only adds the property). |

### Option B — Keep remote CI as-is; document local/remote divergence

**No workflow file edits.** `docs/testing/test-tags-and-env.md` gains a new section:

```text
## Local vs Remote CI Tag Exclusion Policy

Local `just ci` step [13/15] and `just backend-test-fast` exclude `@Tag("postgres")`.
Remote `.github/workflows/ci.yml` `backend-unit` and `.github/workflows/quality-gate.yml`
ordinary test/coverage lanes currently run `@Tag("postgres")` classes. The dedicated
`backend-postgres` job remains the authoritative PostgreSQL lane in both local and remote.

### Re-evaluation criteria for revisiting Option A

1. PostgreSQL Testcontainers mapped-port connection refusal is observed in `backend-unit`
   OR in the ordinary lane of `quality-gate.yml` on at least two consecutive CI runs.
2. `koverXmlReport` semantics for Option A are validated locally and the Codecov/Sonar
   delta is reviewed and accepted.
3. Owner, scope, and test surface are documented in an ADR (recommended follow-up).
```

**Verification:** diff confirms no `.github/workflows/` change; the new doc section is present;
`backend-postgres` job still required by the `CI Gate`.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit regression (NEW) | `R2dbcBulkImportJobRepositoryTest.saveRows binds rowIndex as Int via bind(String, Int)` | `runTest` with explicit timeout; `mockSaveRowsSpec()`; assert at least one `bind(any<String>(), any<Int>())` invocation via `verify`; assert test body completes. Fails before fix, passes after. |
| Existing unit (preserved) | `saveRows inserts single chunk`, `saveRows handles nullable fields and mediaUrls blank`, `saveRows chunked 100 splits 101 rows` | Run as-is; do not weaken `runTest` timeouts; do not change `runBlocking`; do not change `verify` assertions. After the helper fix, each MUST complete without `UncompletedCoroutinesError`. |
| Focused local | `just backend-test-fast` | Must exit zero. Document exact command, exit code, elapsed time in `verify-report.md`. |
| Focused PostgreSQL | `just backend-test-postgres` | Must execute. Outcome (pass/fail/setup-failure) recorded in `verify-report.md` with elapsed time. Testcontainers connection-refused cause remains unproven. |
| Documentation | `docs/testing/test-tags-and-env.md` | Manual diff check: removing prose that contradicts `backend-test-fast`/`just ci` step `[13/15]`; aligning authoritative PostgreSQL lane statement. |

## Migration / Rollout

No data migration. No phased rollout. The slice ships as a single conventional commit per
file (e.g. `fix(test): …`, `chore(just): …`, `docs(testing): …`).

Rollback: revert the helper `every` block, the `Justfile` step, and the docs edits. Conditional
workflow edits revert via a dedicated `revert:` commit if Option A is selected.

## Out of Scope

- `gradle/build-logic/.../SpringBootApplicationPlugin.kt` — generic `excludeTags` mechanism
  unchanged.
- `server/smp/src/test/kotlin/.../support/PostgresTestContainerSupport.kt` — lifecycle, readiness,
  reuse settings, Dory socket behavior untouched.
- `gradle/libs.versions.toml` and `server/smp/build.gradle.kts` — Testcontainers dep graph
  (`testcontainers:2.0.5` core vs `1.21.4` modules) unchanged; bounded A/B experiment deferred.
- Production code under `server/smp/src/main/kotlin/.../persistence/R2dbcBulkImportJobRepository.kt`
  — `Int` binding preserved as the authoritative contract.
- Product, frontend, consent, copy, `.agents/DESIGN.md`.

## Open Questions

- [ ] User selects Option A or Option B for the CI workflow scope before `sdd-tasks` runs.
- [ ] Owner and ADR scope for the durable lane-boundary policy (recommended follow-up).

## Risks

Mirrors `held_risks` in `state.yaml`.

| Risk | Mitigation |
|---|---|
| `postgres-testcontainers-cause` — Testcontainers `Connection refused` cause unproven; tag exclusion does not claim to repair Testcontainers. | Frame as a separate diagnostic/mitigation track; record outcome in `verify-report.md`; never change Testcontainers versions in this change without a bounded A/B experiment. |
| `testcontainers-graph-mismatch` — Mixed Testcontainers graph (Spring Boot 4.0.8 selects core `2.0.5` while project modules request `1.21.4`). | Documented risk; no version change in this change without a bounded dependency-alignment experiment. |
| `option-b-divergence` — Option B leaves local and remote contract divergent. | Record divergence in `docs/testing/test-tags-and-env.md` with rationale and re-evaluation criteria if Option B is selected. |
| `option-a-coverage` — Option A changes what `koverXmlReport` represents; coverage/Codecov/Sonar signal may shift. | Verify `backend-postgres` required, `koverXmlReport` coverage, Codecov/Sonar signal before merging if Option A is selected. |
| `mockk-overload-drift` — MockK relaxed behavior could mask future overload drift. | Regression test verifies `bind(String, Int)` invocation and uses `runTest` timeout. |
| `review-budget` — `additions + deletions` must stay within 400 lines. | First slice narrow; chain any diagnostic/fixture work as a separate SDD change if needed. |