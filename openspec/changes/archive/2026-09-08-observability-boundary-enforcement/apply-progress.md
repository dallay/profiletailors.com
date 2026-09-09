# Apply Progress: `observability-boundary-enforcement` — PR 1 + PR 2 + PR 3 (Phases 1–3)

## Overview

- **Change:** `observability-boundary-enforcement`
- **Scope:** Phase 1 (tasks 1.1–1.4, storage emit migration), Phase 2 (tasks 2.1–2.3, parser reclassification), and Phase 3 (tasks 3.1–3.3, per-module bans + 3-doc sync). All phases complete.
- **Delivery:** auto-chain with chain strategy `feature-branch-chain`. PR 1 (storage emit) targets the change tracker branch. PR 2 (parser move) stacked on PR 1. This slice is PR 3 (bans + docs) stacked on PRs 1–2.
- **Mode:** Standard (strict TDD OFF per init verdict; `openspec/config.yaml` untouched).
- **Slice note (PR 3):** This slice added the blocking bans, the new presentation ban test, the presentation dependency cleanup, and the 3-doc sync itself. Phase 1/2 files were not modified. Fail-then-pass was demonstrated per module with temporary scratch violators (deleted afterwards) instead of reverting verified PR 1/2 files, because `git` is unusable in this worktree.
- **Slice note:** On entry to this slice, the Phase 2 end state was already present in the worktree (parser at the SBC path, old `..domain..` path absent, factory import rewritten, test relocated with no-dump coverage) and `tasks.md` 2.1–2.3 were already marked `[x]`. This slice verified that state against spec/design, ran the three required verifications green, and merged this record. No production or test code edits were required in this slice.

## Completed tasks

- [x] 1.1 Added `implementation(project(":shared:observability"))` to `shared/storage/build.gradle.kts`; verified `shared/observability/build.gradle.kts` keeps zero production dependencies (only test dependencies).
- [x] 1.2 Migrated 3 warns in `StorageApplicationService.kt` (upload/download/delete publish paths) and 1 warn in `GeneratePresignedUrlUseCase.kt` (presign publish path) to `emit(WARN, storage.operation.event.publish.failed)` with trailing `operationalEvents: OperationalEventSink = NoOpOperationalEventSink`. Cause is the emit error param, attributes are `operation`/`provider`/`bucket` only, message is constant text with no interpolated values, blank bucket skips the `bucket` attribute, SLF4J imports dropped.
- [x] 1.3 Added captured-sink tests: `PublishFailureEvents` nested class (5 tests) in `StorageApplicationServiceTest.kt` and 2 new tests in `GeneratePresignedUrlUseCaseTest`, asserting event name, WARN severity, attributes, absence of `key`, constant message, swallow-and-continue, and `CancellationException` rethrow with no emit.
- [x] 1.4 Construction sites keep compiling via default param with no edits: `StorageAutoConfiguration.kt`, `TestStorageConfiguration.kt`, `IntegrationTestBase.kt`, `ResourcePreviewEndpointTestBase.kt`, `WorkspaceAccessSummaryEndpointTestBase.kt`.
- [x] 2.1 Parser lives at `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParser.kt` with package `com.profiletailors.spring.boot.presentation.filter`; old `shared/presentation/.../domain/presentation/filter/RHSFilterParser.kt` absent; file contains no logger, no `OperationalEventSink` emit, no full-query-map output.
- [x] 2.2 `RHSFilterParserFactory.kt` is in the same package post-move with no stale import; test lives at `shared/spring-boot-common/src/test/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserTest.kt` with updated package/imports and no reference to the old `..domain..presentation.filter` path anywhere in the repo.
- [x] 2.3 Failure-path coverage present: `should not expose query values when operator is unsupported` and `should not expose query values when value format is invalid`, each asserting the thrown `FilterInvalidException` message does not contain the secret query value.
- [x] 3.1 Added `domainShouldNotDependOnObservabilityFrameworks` (`..domain..` bans `org.slf4j..`, `ch.qos.logback..`, `org.apache.logging.log4j..`, `io.opentelemetry..`, `io.micrometer..`, `tools.jackson..`, `com.fasterxml.jackson..`, `com.profiletailors.observability..`) and `applicationShouldNotDependOnObservabilityImplementations` (`..application..` bans the same set minus `com.profiletailors.observability..`, so the sink port stays usable) to `StorageArchTest`; created `PresentationArchTest` with equivalent coverage scoped to the presentation module's `..presentation`/`..criteria`/`..regexp` packages. Fail-then-pass demonstrated per module with temporary scratch violators (fail leg names the offending import; probes deleted afterwards); infrastructure owners (`StorageMetrics`, `StorageAutoConfiguration`, `S3RetryHelper`, ratelimit metrics/filter/gateway/config, post-move SBC parser) stay allowlisted by scope since rules match only `..domain..`/`..application..`. Pre-existing assertions unchanged.
- [x] 3.2 Added the same two ban rules to `RatelimitArchTest`; pass-only on migrated code (no application-layer violation), fail leg demonstrated with a temporary scratch violator naming the `org.slf4j` import in `..application..`, probe deleted afterwards.
- [x] 3.3 Synced `docs/observability-usage.md` (storage consumer bullet with event name/attrs/swallow-and-continue/cancellation behavior, storage dependency edge paragraph, stable-name list entry, captured-sink test-matrix row), `docs/observability-contracts.md` (§4 storage event contract: WARN, `operation`/`provider`/`bucket` + `cause`, `key`/payloads never emitted, blank bucket skips attribute, constant message), and `docs/architecture/shared/dependencies.md` (observability consumed-by storage + smp, storage depends-on observability, mermaid `STORAGE -->|impl| OBSERVABILITY` edge) to the implemented `storage.operation.event.publish.failed` contract and the `:shared:storage → :shared:observability` edge.

## Files changed

### PR 1 (Phase 1, prior slice — preserved)

| File | Action | What was done |
|------|--------|---------------|
| `shared/storage/build.gradle.kts` | Modified | Added `:shared:observability` edge |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/application/StorageApplicationService.kt` | Modified | Defaulted sink param, 3 warns to emit, blank-bucket helper, event/message constants |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/application/GeneratePresignedUrlUseCase.kt` | Modified | Defaulted sink param, 1 warn to emit, blank-bucket helper, event/message constants |
| `shared/storage/src/test/kotlin/com/profiletailors/storage/application/StorageApplicationServiceTest.kt` | Modified | Observability imports plus 5 captured-sink tests |
| `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageUseCaseTest.kt` | Modified | Observability imports plus 2 captured-sink tests |
| `openspec/changes/observability-boundary-enforcement/tasks.md` | Modified | Phase 1 tasks 1.1–1.4 marked [x]; Phase 2/3 untouched |

### PR 2 (Phase 2, this slice — verified, no edits)

| File | Action | What was done |
|------|--------|---------------|
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParser.kt` | Verified (no edit) | Already at target package with no log/emit; confirmed present and correct |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserFactory.kt` | Verified (no edit) | Same-package factory, no stale import; confirmed |
| `shared/spring-boot-common/src/test/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserTest.kt` | Verified (no edit) | Relocated test with updated package/imports plus 2 no-dump tests; 12/12 pass |
| `shared/presentation/src/main/kotlin/com/profiletailors/common/domain/presentation/filter/RHSFilterParser.kt` | Verified absent (no edit) | Old `..domain..` path gone; presentation main has zero Jackson/SLF4J imports |
| `openspec/changes/observability-boundary-enforcement/tasks.md` | Verified (no edit) | 2.1–2.3 already `[x]`, 3.1–3.3 already `[ ]`; 1.3 already carries the `(class GeneratePresignedUrlUseCaseTest)` file-vs-class clarification, so the carried drive-by fix needed no further edit |
| `openspec/changes/observability-boundary-enforcement/apply-progress.md` | Modified | Merged this PR 2 record with the preserved PR 1 record |

### PR 3 (Phase 3, this slice — bans + docs)

| File | Action | What was done |
|------|--------|---------------|
| `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageArchTest.kt` | Modified | Added 2 blocking ban tests only; existing assertions untouched |
| `shared/shield/ratelimit/src/test/kotlin/com/profiletailors/ratelimit/RatelimitArchTest.kt` | Modified | Added 2 blocking ban tests only; existing assertions untouched |
| `shared/presentation/src/test/kotlin/com/profiletailors/common/domain/presentation/PresentationArchTest.kt` | Created | New ban test with the same 2 rules scoped to the presentation module packages; application rule carries `.allowEmptyShould(true)` (established `LeadCaptureArchTest`/`ComponentScanArchTest` pattern — the module has no `..application..` package) |
| `shared/presentation/build.gradle.kts` | Modified | Added `testImplementation(libs.archunit.junit5)`; removed verified-unused `implementation(libs.jackson.module.kotlin)` and `implementation(libs.slf4j.api)` per design line 68 (zero Jackson/SLF4J imports in presentation main + test) |
| `docs/observability-usage.md` | Modified | Storage consumer, edge, stable name, and test-matrix rows |
| `docs/observability-contracts.md` | Modified | §4 storage event contract |
| `docs/architecture/shared/dependencies.md` | Modified | Storage-to-observability edge in tables and mermaid graph |
| `openspec/changes/observability-boundary-enforcement/tasks.md` | Modified | Phase 3 tasks 3.1–3.3 marked [x]; 1.x/2.x left checked |
| `openspec/changes/observability-boundary-enforcement/apply-progress.md` | Modified | Merged this PR 3 record with the preserved PR 1 + PR 2 records |

## Verification evidence

### PR 1 (preserved)

- `./gradlew :shared:storage:compileKotlin --no-daemon`: BUILD SUCCESSFUL.
- `./gradlew :shared:storage:test --no-daemon`: BUILD SUCCESSFUL. New coverage: `PublishFailureEvents` 5 tests, 0 failures; `GeneratePresignedUrlUseCaseTest` 9 tests (7 existing + 2 new), 0 failures. Pre-existing compiler warnings in `R2StorageUnitTests`, `S3StorageUnitTests`, `StorageAutoConfigurationR2Test` unchanged.
- `./gradlew :shared:storage:detekt :shared:storage:spotlessKotlinCheck --no-daemon`: BUILD SUCCESSFUL after one `spotlessApply` formatting pass scoped to `:shared:storage`.
- `./gradlew :shared:storage:test --no-daemon --rerun-tasks`: BUILD SUCCESSFUL after formatting.

### PR 2 (this slice, foreground)

- `./gradlew :shared:spring-boot-common:compileKotlin --no-daemon`: BUILD SUCCESSFUL.
- `./gradlew :shared:spring-boot-common:test --tests "com.profiletailors.spring.boot.presentation.filter.*" --no-daemon`: BUILD SUCCESSFUL. `RHSFilterParserTest` 12 tests, 0 failures; `RHSFilterParserFactoryTest` 2 tests, 0 failures; 14/14 pass. Pre-existing test-compile warnings in `ApiControllerTest`, `OffsetPagePresenterTest`, `ReactiveSearchRepositoryImplTest` unchanged and outside this slice.
- `./gradlew :shared:presentation:compileKotlin --no-daemon`: BUILD SUCCESSFUL (old home still compiles with the parser gone).
- Repo-wide grep for `common.domain.presentation.filter` returns no matches; grep for log/emit/Logger/Sink in the moved `RHSFilterParser.kt` returns no matches.

### PR 3 (this slice, foreground)

- `./gradlew :shared:storage:test --tests "*.StorageArchTest" --no-daemon` with a temporary `ObservabilityBanScratchProbe` (SLF4J logger in `..application..` main source): BUILD FAILED as required — `applicationShouldNotDependOnObservabilityImplementations()` fails naming `com.profiletailors.storage.application.ObservabilityBanScratchProbe` → `org.slf4j.Logger/LoggerFactory`; the other 3 tests (incl. the domain ban and both pre-existing assertions) pass. Probe deleted afterwards.
- `./gradlew :shared:shield:ratelimit:test --tests "*.RatelimitArchTest" --no-daemon` with the same probe shape in `com.profiletailors.ratelimit.application`: BUILD FAILED as required — application ban fails naming the probe's `org.slf4j` dependency (3 violations); other 3 tests pass. Probe deleted afterwards.
- `./gradlew :shared:presentation:test --tests "*.PresentationArchTest" --no-daemon` with the probe in `com.profiletailors.common.domain.presentation` (plus a temporarily restored `slf4j.api` edge, removed again afterwards): BUILD FAILED as required — `domainShouldNotDependOnObservabilityFrameworks()` fails naming the probe's `org.slf4j` dependency. First attempt also exposed ArchUnit's `failOnEmptyShould` on the vacuous application rule (no `..application..` package in the module); resolved with the established `.allowEmptyShould(true)` pattern, after which the fail leg shows exactly 1 failure (domain ban) and the application rule passes. Probe and temporary edge deleted afterwards.
- `./gradlew :shared:storage:test --tests "*.StorageArchTest" :shared:shield:ratelimit:test --tests "*.RatelimitArchTest" :shared:presentation:test --no-daemon` (final, probes gone): BUILD SUCCESSFUL. `StorageArchTest` 4/4 pass, `RatelimitArchTest` 4/4 pass, `:shared:presentation:test` full suite 182/182 pass with 0 failures (incl. new `PresentationArchTest` 2/2), proving the Jackson/SLF4J dependency removal is safe.
- `./gradlew :shared:storage:detekt :shared:storage:spotlessKotlinCheck :shared:presentation:detekt :shared:presentation:spotlessKotlinCheck :shared:shield:ratelimit:detekt :shared:shield:ratelimit:spotlessKotlinCheck --no-daemon`: BUILD SUCCESSFUL, no new findings.
- Scratch-probe grep after the runs returns no matches; `StorageAutoConfiguration.kt:81` `logger.warn` confirmed still present and allowlisted infrastructure logging, untouched.

## Work Unit Evidence

### PR 1 (preserved)

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :shared:storage:test --no-daemon` — BUILD SUCCESSFUL; `PublishFailureEvents` 5/5 pass, `GeneratePresignedUrlUseCaseTest` 9/9 pass, 0 failures |
| Runtime harness command/scenario and exact result | N/A — no runtime boundary exists for this slice; captured fake-sink assertions prove the emit behavior per the tasks forecast |
| Rollback boundary | Revert PR 1 files listed above; restores 4 SLF4J warns, drops the `:shared:storage` to `:shared:observability` edge |

### PR 2 (this slice)

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :shared:spring-boot-common:test --tests "com.profiletailors.spring.boot.presentation.filter.*" --no-daemon` — BUILD SUCCESSFUL; `RHSFilterParserTest` 12/12 pass (incl. 2 no-query-dump tests), `RHSFilterParserFactoryTest` 2/2 pass, 0 failures |
| Runtime harness command/scenario and exact result | N/A — no runtime boundary exists for this slice; factory-resolve plus failure-path assertions prove the behavior per the tasks forecast |
| Rollback boundary | Revert PR 2 (restore parser in `..domain..` with old imports); PR 1 still compiles independently |

### PR 3 (this slice)

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :shared:storage:test --tests "*.StorageArchTest" :shared:shield:ratelimit:test --tests "*.RatelimitArchTest" :shared:presentation:test --no-daemon` — BUILD SUCCESSFUL; `StorageArchTest` 4/4, `RatelimitArchTest` 4/4, `:shared:presentation:test` 182/182, 0 failures. Fail legs (each with a temporary scratch SLF4J violator, deleted afterwards): storage application ban fails naming the probe, ratelimit application ban fails naming the probe, presentation domain ban fails naming the probe |
| Runtime harness command/scenario and exact result | N/A — no runtime boundary exists for this slice; ArchUnit bans plus doc-vs-code diff prove it per the tasks forecast |
| Rollback boundary | Revert PR 3 (ban tests, `PresentationArchTest`, presentation build file, 3 docs); bans removed, docs restored; PRs 1–2 still compile |

## Deviations from design

PRs 1–2: None — verified state matches design (parser at `com.profiletailors.spring.boot.presentation.filter.RHSFilterParser` in `:shared:spring-boot-common`, factory import dropped as same-package, no full-query-map log/emit, presentation main free of Jackson/SLF4J).

PR 3: Two deliberate, design-consistent additions, no silent freelancing.
- `PresentationArchTest.applicationShouldNotDependOnObservabilityImplementations` carries `.allowEmptyShould(true)` because the presentation module has no `..application..` package and ArchUnit fails empty `should` checks by default. This is the established repo pattern (`LeadCaptureArchTest`, `ComponentScanArchTest`), not a weakening: the rule still bans if such classes ever appear, and the domain ban enforces today.
- `shared/presentation/build.gradle.kts` drops the verified-unused `jackson.module.kotlin` and `slf4j.api` implementation deps per design line 68 ("verify and remove"); zero Jackson/SLF4J imports in presentation main + test, full `:shared:presentation:test` suite green afterwards. `archunit.junit5` test dep added to match the storage/ratelimit test setup.
- Fail-then-pass used temporary scratch violators (deleted; grep confirms none remain) instead of reverting verified PR 1/2 files, since `git` is unusable in this worktree. Each fail leg names the exact offending `..application..`/`..domain..` → `org.slf4j..` edge the ban exists to catch, which is the same edge the pre-migration code carried.

## Issues found

### PRs 1–2 (preserved)

- Container had no JDK on slice entry; installed `openjdk-21-jdk-headless` via apt to run Gradle verification (environment-only, no repo change). Prior slice used JDK 25; JDK 21 built and tested this slice green.
- `git` unusable in this worktree (linked-worktree `.git` path absent); changed-line count not measured via `git diff`. Scope kept to verification plus this merged progress record; no production/test files edited.
- Native attempt ledger unavailable in this environment; verification run directly in a bounded-attempt mindset (compile, filtered test, presentation compile).
- `StorageAutoConfiguration.kt:81` `logger.warn` confirmed allowlisted infrastructure logging per carried input; untouched.
- Observation for verify phase (not changed in this slice): `RHSFilterParser.convert` builds `FilterInvalidException("Can't convert operand. Operand: $operand, Type: $clazz")`, which interpolates the operand value into the exception message. The two no-dump tests cover the unsupported-operator and invalid-format paths only, not the convert-failure path. Left unchanged as pre-existing message text outside the literal 2.1–2.3 log/emit removal; verify may judge whether that path needs the same treatment as a follow-up.

### PR 3 (this slice)

- `git` still unusable in this worktree (linked-worktree `.git` path absent); changed-line count not measured via `git diff`, no commits created. Fail-then-pass run via temporary scratch violators with no revert of verified files; probes deleted and absence re-verified by grep.
- Native attempt ledger unavailable in this environment; verification run directly in a bounded-attempt mindset (per-module fail legs, combined pass run, detekt + spotless).
- ArchUnit `failOnEmptyShould` discovery: the presentation application ban initially failed on an empty scope (module has no `..application..` package); resolved with the established `.allowEmptyShould(true)` pattern, not by dropping the rule.
- `tasks.md` 1.3 already carries the `(class GeneratePresignedUrlUseCaseTest)` file-vs-class clarification — confirmed, no re-edit.

## Remaining work

All tasks complete (1.1–1.4, 2.1–2.3, 3.1–3.3). Ready for independent verify (`sdd-verify`), then archive.

## References

- `openspec/changes/observability-boundary-enforcement/tasks.md`
- `openspec/changes/observability-boundary-enforcement/specs/platform-governance/spec.md`
- `openspec/changes/observability-boundary-enforcement/design.md`
