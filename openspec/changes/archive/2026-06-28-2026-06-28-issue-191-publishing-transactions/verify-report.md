# Verification Report: Publishing Mutation Transactions

- Change: `2026-06-28-issue-191-publishing-transactions`
- Mode: OpenSpec filesystem
- Verdict: **PASS WITH WARNINGS**
- Verified at: 2026-06-28

## Completeness Table

| Area | Result | Evidence |
|---|---:|---|
| Proposal/spec/design/tasks/prior report read | PASS | All required OpenSpec artifacts and the previous failed verification were inspected |
| Current implementation and diff inspected | PASS | Production handler, application tests, PostgreSQL integration tests, and current Git diff inspected |
| Tasks | 13/14 checked | Tasks 1.1–4.4 accurately reflect implemented/focused evidence; broad CI task 4.5 remains correctly unchecked |
| Exactly five transactional workflows | PASS | Create/Edit/Cancel/Retry/Reschedule inject and invoke `AtomicTransactionRunner`; Delete has no runner wiring |
| Application framework neutrality | PASS | No Spring, Reactor, coroutine-Reactor, or `TransactionalOperator` references exist in `publishing/application` |
| Runtime application matrix | PASS | Success invokes runner exactly once for all five workflows; representative authorization, lifecycle, capability, and external-read/media failures assert zero invocations and zero publication/job writes |
| Persisted-result semantics | PASS | Edit, Retry, and Reschedule explicitly test normalized persisted identity/workspace/status/schedule values in both returned result and replacement job |
| PostgreSQL transaction evidence | PASS | Ten real PostgreSQL tests cover paired commit and rollback for all five workflows |
| Delete unchanged | PASS | Existing Delete path and tests remain; no transaction-runner dependency was added |

## Build / Test / Coverage Evidence

Commands executed fresh during this verification:

| Command | Result | Evidence |
|---|---:|---|
| `./gradlew :server:smp:test --tests '*HexagonalArchTest' --tests '*PublishingHandlersTest' --no-daemon --rerun-tasks` | PASS | `BUILD SUCCESSFUL`; 24 actionable tasks executed |
| `./gradlew :server:smp:postgresIntegrationTest --tests '*PublishingHandlersTransactionPostgresIntegrationTest' --no-daemon --rerun-tasks` | PASS | `BUILD SUCCESSFUL`; 24 actionable tasks executed; real Testcontainers PostgreSQL suite ran |
| `./gradlew :server:smp:spotlessCheck --no-daemon --rerun-tasks` | PASS | `BUILD SUCCESSFUL`; 4 actionable tasks executed |
| Broad CI (`just ci`, `just ci-full`) | NOT RUN | Explicitly excluded from this verification; task 4.5 remains unchecked |

No separate coverage report was generated; configured OpenSpec coverage threshold is zero. Runtime scenario coverage was established through the focused application, architecture, and PostgreSQL suites.

## Spec Compliance Matrix

| Requirement / Scenario | Implementation Evidence | Passing Runtime Evidence | Status |
|---|---|---|---:|
| Atomic Publication and Job Mutations | Five handlers wrap paired publication/job writes in one runner boundary | Application success tests and ten PostgreSQL commit/rollback tests | PASS |
| Paired mutation commits | Both durable operations execute inside `runAtomically` | PostgreSQL commit case passes for Create/Edit/Cancel/Retry/Reschedule | PASS |
| Create job mutation fails | Create persists then enqueues within one transaction | PostgreSQL rollback proves no publication, asset links, or job | PASS |
| Existing workflow job mutation fails | Edit/Cancel/Retry/Reschedule publication mutations precede forced job-side failure inside the real runner | PostgreSQL rollback cases preserve prior publication/assets and pre-existing job | PASS |
| Framework-Neutral Transaction Orchestration | Application depends only on shared `AtomicTransactionRunner`; reads/validation precede runner | `HexagonalArchTest` and application tests pass fresh | PASS |
| Validation fails before transaction | Authorization, lifecycle, and capability rejection paths remain outside runner | Representative tests assert runner zero and publication/job write counters zero | PASS |
| External read/media resolution fails before transaction | Account/publication/media reads occur before runner | Media-unavailable path asserts runner zero and no writes; missing-resource tests pass | PASS |
| Jobs Use Persisted Publication Result | Replacement job helper receives repository-returned `persisted` value | Explicit normalized-result tests pass for Edit, Retry, and Reschedule | PASS |
| Delete Behavior Is Unchanged | Delete has no runner constructor parameter and retains existing repository path | Existing Delete behavior tests pass in focused suite | PASS |
| PostgreSQL Evidence for Publishing Transactions | Real PostgreSQL, R2DBC repositories, transaction manager, and `R2dbcAtomicTransactionRunner` | Five commit plus five rollback tests pass fresh | PASS |

## Correctness Table

| Finding | Judge A | Judge B | Severity | Status |
|---|---:|---:|---|---|
| Runner invoked exactly once on successful Create/Edit/Cancel/Retry/Reschedule | ✅ | ✅ | OK | Confirmed |
| Authorization/lifecycle/capability/external-read-media rejection occurs before runner with no writes | ✅ | ✅ | OK | Confirmed |
| Edit/Retry/Reschedule replacement jobs and results use normalized persisted values | ✅ | ✅ | OK | Confirmed |
| Real PostgreSQL commit and rollback evidence exists for all five workflows | ✅ | ✅ | OK | Confirmed |
| Publishing application contains no Spring/Reactor transaction APIs | ✅ | ✅ | OK | Confirmed |
| Delete has no transaction runner and its behavior tests remain green | ✅ | ✅ | OK | Confirmed |
| Broad CI task 4.5 was not executed | ✅ | ✅ | WARNING | Confirmed |
| Edit/Retry/Reschedule failure decorator throws before delegating replacement, rather than exercising the design's proposed delete-then-failed-insert mechanism | ✅ | ✅ | WARNING (design deviation) | Confirmed |
| Rollback job query compares selected columns rather than snapshotting every persisted job column | ✅ | ✅ | WARNING | Confirmed |

## Design Coherence Table

| Design Decision | Code/Test Evidence | Status |
|---|---|---:|
| Use `AtomicTransactionRunner.runAtomically` in exactly five handlers | Implemented and explicitly tested once per successful workflow | PASS |
| Derive replacement jobs from persisted publication | Implemented and normalized-return tested for Edit/Retry/Reschedule | PASS |
| Keep reads, authorization, lifecycle/capability checks, and media resolution outside transactions | Source and zero-runner/no-write tests confirm representative paths | PASS |
| Prove commit/rollback with real PostgreSQL/R2DBC | Ten fresh Testcontainers tests pass | PASS WITH WARNING |
| Keep adapter location and add no production contract | Existing port and adapter reused unchanged | PASS |
| Delete remains outside transaction change | No runner wiring; existing behavior retained | PASS |

## Issues

### CRITICAL

None.

### WARNING

1. **Broad CI remains deferred.** Task 4.5 (`just infra-up && just ci-full`, followed by `just infra-down`) is correctly unchecked and must be completed before final PR readiness/push under repository policy.
2. **The PostgreSQL replacement-failure mechanism is narrower than the design described.** Edit/Retry/Reschedule call a failing repository decorator that throws before delegating `replaceForPublication`; this still proves the preceding real PostgreSQL publication mutation rolls back and the original job survives, satisfying the delta spec, but it does not exercise the design's stronger proposed delete-then-failed-insert path.
3. **Pre-existing job rollback comparison is selective.** Tests prove survival by ID (and status for Cancel), but do not snapshot every job column before and after rollback.

### SUGGESTION

- Before release, strengthen Edit/Retry/Reschedule rollback tests with a database-level failure after replacement deletion and compare the complete relevant job row.
- Run task 4.5 when broad CI infrastructure is available.

## Final Verdict

**PASS WITH WARNINGS** — all delta-spec scenarios now have passing runtime evidence. The complete application matrix is present, persisted-result normalization is explicitly covered for Edit/Retry/Reschedule, all five workflows have fresh real-PostgreSQL commit/rollback evidence, architecture neutrality holds, and Delete remains unchanged. Verification may advance to archive, while broad CI 4.5 and the stronger replacement-failure mechanism remain follow-up warnings.
