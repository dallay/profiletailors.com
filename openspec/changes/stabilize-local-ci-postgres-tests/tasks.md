# Tasks: Stabilize Local CI PostgreSQL Tests

## Review Workload Forecast

Decision needed before apply: Yes
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

## Phase 1: TDD MockK fix — `R2dbcBulkImportJobRepositoryTest`

Deps: MockK; `mockSaveRowsSpec()` lines 298–309.

- [ ] 1.1 RED — add regression `saveRows binds rowIndex as Int via bind(String, Int)` (~15 lines, `runTest` + timeout + `verify { spec.bind(any<String>(), any<Int>()) }`). Confirm FAILS. Preserve three existing `saveRows` tests; do NOT weaken assertions.
- [ ] 1.2 GREEN — add `every { spec.bind(any<String>(), any<Int>()) } returns spec` inside `mockSaveRowsSpec()` after the `String` overload. Re-run; all `saveRows` tests + regression complete without `UncompletedCoroutinesError`.

## Phase 2: Justfile `just ci` step `[13/15]` alignment

Deps: `backend-test-fast` recipe line 223; Gradle `-PexcludeTags=modularity,postgres`.

- [ ] 2.1 Append `-PexcludeTags=modularity,postgres` to Justfile line 485 step `[13/15]`. After-line: `just _ci-step "[13/15] Backend: unit tests (fast)" "." node scripts/gradle-run.mjs :server:smp:test --no-daemon -PexcludeTags=modularity,postgres`.

## Phase 3: Docs reconciliation — `docs/testing/test-tags-and-env.md`

Deps: Phase 1 + Phase 2 final wording.

- [ ] 3.1 Remove "no exclusions by default in CI" from Overview; rewrite `backend-test-fast` description to exclude `@Tag("postgres")`; rewrite `@Tag("postgres")` section naming `just ci` step `[13/15]` + `just backend-test-fast` as excluding `postgres` and `just ci-full` + `backend-postgres` as authoritative PostgreSQL lanes.

## Phase 4: BLOCKED on `open_decisions[ci-workflow-scope]`

- [ ] 4.0 **BLOCKED** — orchestrator MUST collect Option A vs B before Phase 5/6. Reference: `open_decisions[ci-workflow-scope]` in `state.yaml`. A → Phase 5; B → Phase 6.

## Phase 5: Option A — CI workflow edits (conditional)

Deps (Path A): `backend-postgres` required by `CI Gate` (333–344); `koverXmlReport`/Codecov/Sonar preserved; decision = A.

- [ ] 5.1 **BLOCKED** (A) — append `-PexcludeTags=postgres` to `.github/workflows/ci.yml` `backend-unit` (line 171).
- [ ] 5.2 **BLOCKED** (A) — append `-PexcludeTags=postgres` to `.github/workflows/quality-gate.yml` `Run backend tests` (line 40).
- [ ] 5.3 **BLOCKED** (A) — append `-PexcludeTags=postgres` to `.github/workflows/quality-gate.yml` `Generate backend coverage reports` (line 43).
- [ ] 5.4 Pre-merge verification (A): `backend-postgres` still required; `koverXmlReport`/Codecov/Sonar steps unchanged. Record in `verify-report.md`.

## Phase 6: Option B — Docs-only divergence section (conditional)

Deps (Path B): Phase 3 complete; decision = B.

- [ ] 6.1 **BLOCKED** (B) — append "Local vs Remote CI Tag Exclusion Policy" to `docs/testing/test-tags-and-env.md`: local `just ci` `[13/15]` + `just backend-test-fast` exclude `@Tag("postgres")`; remote `ci.yml` `backend-unit` + `quality-gate.yml` ordinary lanes run it; `backend-postgres` authoritative in both. Add "Re-evaluation criteria" subsection (two consecutive connection-refused CI runs; locally-validated `koverXmlReport` + accepted Codecov/Sonar delta; documented ADR owner).

## Phase 7: Verification slice

Deps: Docker + Testcontainers (Dory); `postgresIntegrationTest`; `just backend-test-postgres`; `backend-postgres` GitHub job.

- [ ] 7.1 Run `just backend-test-fast`; MUST exit zero. Record command, exit code, elapsed in `verify-report.md`. Failure MUST NOT claim green.
- [ ] 7.2 Run `just backend-test-postgres` (Docker reachable); MUST execute. Record command, exit code, elapsed, outcome in `verify-report.md`. `Connection refused` cause unproven; do NOT claim repaired.
- [ ] 7.3 Confirm three existing `saveRows` tests + new regression complete without `UncompletedCoroutinesError`. Record in `verify-report.md`.

## Commits

1.2 `fix(test): configure bind(String, Int) in mockSaveRowsSpec` · 2.1 `chore(just): skip @Tag("postgres") in ci step [13/15]` · 3.1 `docs(testing): align test-tags doc with local fast/non-postgres contract` · 5.1 `ci(test): exclude postgres from backend-unit` · 5.2 `ci(test): exclude postgres from quality-gate Run backend tests` · 5.3 `chore(ci): exclude postgres from quality-gate coverage step` · 6.1 `docs(testing): document local/remote ci divergence`.
