# Spec: Stabilize Local CI PostgreSQL Tests

## Purpose

Two proven independent failures: (a) `R2dbcBulkImportJobRepositoryTest.mockSaveRowsSpec()` does not
configure `bind(String, Int)`, so the relaxed mock returns a disconnected fluent spec and
`awaitSingle()` hangs in any `saveRows` path; (b) `just ci` step `[13/15]` is labelled "unit tests
(fast)" but is missing `-PexcludeTags=modularity,postgres`, contradicting `just ci-local` and
`just backend-test-fast`. The PostgreSQL Testcontainers `Connection refused` cause is **not proven**
and is not repaired by tag exclusion.

## Scope

In: configure `bind(String, Int)` in `mockSaveRowsSpec()` under TDD; add
`-PexcludeTags=modularity,postgres` to `just ci` step `[13/15]`; add a regression test asserting
`bind(String, Int)` invocation and `runTest` completion; reconcile `docs/testing/test-tags-and-env.md`.
Out: production binding, frontend, consent, copy, `.agents/DESIGN.md`, `SpringBootApplicationPlugin`,
`PostgresTestContainerSupport` lifecycle, Testcontainers dep graph.

## HELD Decision — Option A vs Option B

- **Option A** — exclude `postgres` from `.github/workflows/ci.yml` `backend-unit` and
  `.github/workflows/quality-gate.yml` ordinary test/coverage lanes; `backend-postgres` stays
  authoritative. Requires `backend-postgres` required, `koverXmlReport` preserved, Codecov/Sonar
  intact.
- **Option B** — keep remote CI as-is; change only local `just ci`. Document local/remote divergence
  in `docs/testing/test-tags-and-env.md` with rationale and re-evaluation criteria.

User has **NOT** chosen. Orchestrator MUST collect the choice before delegating `sdd-tasks`; no
workflow file is modified until then.

## ADDED Requirements

### Requirement: MockK Helper Configures `bind(String, Int)` Under TDD

`mockSaveRowsSpec()` MUST configure `bind(String, Int)`. A regression test MUST be added first; it
MUST fail by asserting both `bind(String, Int)` invocation and `runTest` completion. The regression
test MUST NOT weaken existing tests by removing `runTest` timeouts or `verify`. After the fix, the
three `saveRows` tests MUST complete deterministically without `UncompletedCoroutinesError`.

#### Scenario: Regression test fails then passes under TDD

- GIVEN `mockSaveRowsSpec()` initially lacks `bind(String, Int)` configuration
- WHEN the regression test runs before the helper is configured
- THEN it MUST fail with coroutine timeout OR unmet `verify { ... bind(...Int) }` within `runTest`
- WHEN the helper is configured with `every { spec.bind(any<String>(), any<Int>()) } returns spec`
- THEN the same test MUST complete within `runTest` and assert ≥1 `bind(String, Int)` invocation

#### Scenario: Existing `saveRows` tests complete deterministically after fix

- GIVEN the helper is configured
- WHEN the three existing `saveRows` tests run
- THEN each MUST complete without `UncompletedCoroutinesError` within `runTest`

### Requirement: `just ci` Step `[13/15]` Skips `@Tag("postgres")`

`just ci` step `[13/15]` MUST invoke Gradle with `-PexcludeTags=modularity,postgres`, exactly as
`just ci-local` and `just backend-test-fast`. No `@Tag("postgres")` class MAY be invoked by that step.
`just ci-full` and `.github/workflows/ci.yml` `backend-postgres` MUST stay authoritative.

#### Scenario: Step `[13/15]` excludes `postgres`; dedicated lane stays authoritative

- GIVEN the Justfile step is updated
- WHEN the user runs `just ci`
- THEN step `[13/15]` MUST pass `-PexcludeTags=modularity,postgres` and invoke no `postgres`-tagged class
- AND `just ci-full` and the `backend-postgres` job MUST remain the authoritative PostgreSQL lanes

### Requirement: Test-Tag Documentation Matches Actual Contract

`docs/testing/test-tags-and-env.md` MUST describe the actual contract. Contradicting prose — e.g.,
"no exclusions by default in CI", `postgres` tests "run by default in CI", or `backend-test-fast`
includes PostgreSQL tests — MUST be removed.

#### Scenario: Documentation describes correct lanes and removes contradicting prose

- GIVEN the spec is applied
- WHEN the user reads `docs/testing/test-tags-and-env.md`
- THEN `backend-test-fast` and `just ci` step `[13/15]` MUST be described as excluding `postgres`
- AND `ci-full` plus `backend-postgres` MUST be the authoritative PostgreSQL lanes
- AND no paragraph MAY assert `postgres` tests "run by default in CI" or that `backend-test-fast` includes them

## Verification Slice

### Requirement: Focused Runs Are Documented

`just backend-test-fast` MUST pass. `just backend-test-postgres` MUST execute (Testcontainers cause
unproven). Exact commands and outcomes MUST be in `verify-report.md`.

#### Scenario: `just backend-test-fast` passes

- GIVEN the helper is configured and the Justfile step is updated
- WHEN `just backend-test-fast` runs
- THEN it MUST exit zero

#### Scenario: `just backend-test-postgres` runs and outcome is recorded

- GIVEN the documentation is reconciled
- WHEN `just backend-test-postgres` runs
- THEN the command MUST execute and the exact command, exit code, elapsed time, and outcome MUST be in `verify-report.md`