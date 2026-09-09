# Verify Report: `observability-boundary-enforcement`

## Change

- Change: `observability-boundary-enforcement`
- Spec: `openspec/changes/observability-boundary-enforcement/specs/platform-governance/spec.md` (4 requirements, 6 scenarios)
- Tasks: `openspec/changes/observability-boundary-enforcement/tasks.md` (10/10 checked: 1.1–1.4, 2.1–2.3, 3.1–3.3)
- Design: `openspec/changes/observability-boundary-enforcement/design.md`
- Proposal: `openspec/changes/observability-boundary-enforcement/proposal.md`
- Apply record: `openspec/changes/observability-boundary-enforcement/apply-progress.md` (PR 1+2+3)
- Mode: Standard (strict TDD OFF for execution; see WARNING W2 on `openspec/config.yaml` `strict_tdd: true` drift)
- Verifier constraints: no source edits; no child sub-agents; git unusable in this worktree (linked-worktree `.git` absent), so no diff evidence — proof via direct reads plus fresh test runs; attempt ledger unavailable
- Verdict: **PASS WITH WARNINGS** (no CRITICAL, no archive blocker; 2 WARNINGs + 1 SUGGESTION carried as follow-ups)

## Completeness

| Task | Status | Evidence |
|---|---|---|
| 1.1 `:shared:observability` edge on `:shared:storage`; observability keeps zero prod deps | Complete | `shared/storage/build.gradle.kts:11` has `implementation(project(":shared:observability"))`; `shared/observability/build.gradle.kts` has only test deps; `:shared:storage:compileKotlin` green inside full test run |
| 1.2 3 warns in `StorageApplicationService` + 1 in `GeneratePresignedUrlUseCase` to `emit(WARN, storage.operation.event.publish.failed)` with defaulted `NoOpOperationalEventSink`, cause-as-error-param, `operation`/`provider`/`bucket` only, constant message, blank-bucket skip, SLF4J dropped | Complete | Direct read of both files confirms exact shape; grep over `shared/storage/.../application` for `slf4j\|Logger\|logger.` returns zero matches |
| 1.3 Captured-sink tests (name, WARN, attrs, no `key`, swallow-and-continue, cancellation rethrow/no-emit) | Complete | `StorageApplicationServiceTest.PublishFailureEvents` 5/5; `GeneratePresignedUrlUseCaseTest` 9/9 incl. 2 new emit tests; fresh full `:shared:storage:test` 261 tests, 0 failures/errors |
| 1.4 Construction sites compile via default param | Complete | No edits needed by design; full storage suite compiles and passes; `StorageAutoConfiguration.kt:81` `logger.warn` untouched (allowlisted infrastructure) |
| 2.1 Parser moved to `com.profiletailors.spring.boot.presentation.filter.RHSFilterParser` in `:shared:spring-boot-common`; no full-query-map log/emit | Complete | New path present with correct package; old `..domain..` path absent (repo grep for `common.domain.presentation.filter` returns zero); grep for `Logger\|OperationalEventSink\|emit(` in moved parser returns zero |
| 2.2 Factory import rewritten; test relocated with updated package/imports | Complete | `RHSFilterParserFactory.kt` same-package, no stale import; `RHSFilterParserTest.kt` at SBC path with updated package; SBC filter suite 14/14 pass |
| 2.3 Failure-path no-dump coverage | Complete | 2 tests (`unsupported-operator`, `invalid-format`) assert `doesNotContain(secret)`; 12/12 parser tests pass (see WARNING W1 for the uncovered `convert` path) |
| 3.1 Blocking bans in `StorageArchTest` + new `PresentationArchTest`, fail-then-pass, infra allowlisted, assertions unweakened | Complete | `StorageArchTest` 4/4, `PresentationArchTest` 2/2 in fresh runs; pre-existing assertions byte-identical alongside 2 additive rules; infra owners (`StorageMetrics`, `StorageAutoConfiguration`, `S3RetryHelper`) remain in `..infrastructure..` scope only |
| 3.2 Equivalent bans in `RatelimitArchTest`, pass-only on migrated code | Complete | `RatelimitArchTest` 4/4 fresh; infra owners (`BucketConfigurationFactory`, `RateLimitingFilter`, `Bucket4jRateLimiter`, `SpringRateLimitEventPublisher`) remain `..infrastructure..`-scoped |
| 3.3 3-doc sync (event name/attrs + storage→observability edge) | Complete | `docs/observability-usage.md`, `docs/observability-contracts.md`, `docs/architecture/shared/dependencies.md` all carry `storage.operation.event.publish.failed`, WARN, `operation`/`provider`/`bucket` + `cause`, no-`key`, blank-bucket skip, and the `STORAGE -->|impl| OBSERVABILITY` edge; strings match code constants |

## Build / test / coverage evidence (fresh runs by this verifier)

All commands run with `--no-daemon` from `/workspace` on OpenJDK 21.0.12. `just` is not installed in this environment, so exact Gradle tasks were invoked directly.

| Command | Result | Detail |
|---|---|---|
| `./gradlew :shared:storage:test --tests "*.StorageArchTest" :shared:shield:ratelimit:test --tests "*.RatelimitArchTest" :shared:presentation:test --no-daemon --rerun-tasks` | EXIT 0, BUILD SUCCESSFUL (29s) | `StorageArchTest` 4/4, `RatelimitArchTest` 4/4, `:shared:presentation:test` 182/182, 0 failures/errors (per-test XML, see below) |
| `./gradlew :shared:spring-boot-common:test --tests "com.profiletailors.spring.boot.presentation.filter.*" --no-daemon --rerun-tasks` | EXIT 0, BUILD SUCCESSFUL (18s) | `RHSFilterParserTest` 12/12, `RHSFilterParserFactoryTest` 2/2, 0 failures (XML); pre-existing test-compile warnings in `ApiControllerTest`, `OffsetPagePresenterTest`, `ReactiveSearchRepositoryImplTest` unchanged, outside slice |
| `./gradlew :shared:storage:test --no-daemon --rerun-tasks` | EXIT 0, BUILD SUCCESSFUL (42s) | Full storage module: 261 tests, 0 failures, 0 errors (skips are pre-existing Testcontainers-gated integration tests); includes `PublishFailureEvents` 5/5 and `GeneratePresignedUrlUseCaseTest` 9/9; pre-existing warnings in `R2StorageUnitTests`, `S3StorageUnitTests`, `StorageAutoConfigurationR2Test` unchanged |
| `./gradlew :shared:storage:detekt :shared:storage:spotlessKotlinCheck :shared:presentation:detekt :shared:presentation:spotlessKotlinCheck :shared:shield:ratelimit:detekt :shared:shield:ratelimit:spotlessKotlinCheck :shared:spring-boot-common:detekt :shared:spring-boot-common:spotlessKotlinCheck --no-daemon` | EXIT 0, BUILD SUCCESSFUL (10s) | No new findings on any touched module |
| Coverage (Kover) | NOT RUN | No coverage gate is part of this change's task forecast; unit/ArchUnit evidence is the specified proof |

Per-suite XML totals (fresh):

- `StorageArchTest`: tests=4 skipped=0 failures=0 errors=0
- `RatelimitArchTest`: tests=4 skipped=0 failures=0 errors=0
- `PresentationArchTest`: tests=2 skipped=0 failures=0 errors=0
- `:shared:presentation:test` total: tests=182 failures=0 errors=0
- SBC filter total: tests=14 (12 parser + 2 factory) failures=0 errors=0
- `:shared:storage:test` total: tests=261 failures=0 errors=0

Fail-then-pass provenance: this verifier did not re-introduce scratch violators (prior slices already demonstrated per-module fail legs with temporary probes, deleted afterwards, grep-confirmed absent). Current green runs are the pass legs on migrated code; ban rule text was re-read here and matches the design's blocking scope. Pre-existing assertions in `StorageArchTest`/`RatelimitArchTest` are unchanged (2 original + 2 additive rules each).

Static reads backing the runs:

- Storage application has zero `org.slf4j`/`jackson`/`micrometer`/`opentelemetry` imports; only `com.profiletailors.observability` port imports remain (allowed by the application ban's minus-port scope).
- Presentation main has zero `jackson`/`slf4j`/`micrometer`/`opentelemetry`/`observability` matches; `build.gradle.kts` no longer declares `jackson.module.kotlin`/`slf4j.api`, adds `archunit.junit5` test dep.
- Ratelimit `jackson`/`slf4j` matches are confined to `..infrastructure..` (`BucketConfigurationFactory`, `RateLimitingFilter`, `Bucket4jRateLimiter`, `SpringRateLimitEventPublisher`).
- `StorageObservation.Operations` constants are exactly `upload`/`download`/`delete`/`presign` (+ `copy`/`list` unused by this contract), matching the docs' `operation` vocabulary.
- Event constants in both application files are exactly `storage.operation.event.publish.failed` / `Storage operation event publish failed`.

## Spec compliance matrix

| Requirement / Scenario | Status | Covering test (passed at runtime) |
|---|---|---|
| Per-module observability import bans — Ban fails on known violation then passes after migration | COMPLIANT | `StorageArchTest.domainShouldNotDependOnObservabilityFrameworks` + `applicationShouldNotDependOnObservabilityImplementations` (4/4 fresh); `RatelimitArchTest` equivalents (4/4 fresh); `PresentationArchTest` 2 rules (2/2 fresh). Fail legs evidenced by prior-slice scratch-probe runs (storage application ban named `..application..` probe → `org.slf4j`; ratelimit likewise; presentation domain ban named `..domain..` probe); pass legs re-run green here with probes absent |
| Per-module bans — Infrastructure logging remains permitted | COMPLIANT | Allowlist by scope (rules match only `..domain..`/`..application..`): `StorageAutoConfiguration.kt:81,128` + `S3RetryHelper` + `StorageMetrics` SLF4J/Micrometer still present and suites pass; ratelimit infra SLF4J/Jackson still present and suite passes; post-move SBC parser (Jackson `ObjectMapper`, outside any `..domain..`/`..application..` package) compiles and filter suite passes |
| Storage publish-failure events via sink — Publish failure emits key-safe event and continues | COMPLIANT | `PublishFailureEvents` upload/download/delete tests (name `storage.operation.event.publish.failed`, WARN, `operation`/`provider`/`bucket`, `cause` is `IllegalStateException`, `attributes.containsKey("key") == false`, constant message, operation completes / content intact) + `GeneratePresignedUrlUseCaseTest.publish failure emits WARN event without key and continues` + blank-bucket test (`bucket` key absent) — all green in the 261-test storage run |
| Storage publish-failure events — Cancellation is never swallowed | COMPLIANT | `PublishFailureEvents.cancellation during publish rethrows without emitting` (upload/download/delete all rethrow `CancellationException`, `events` empty) + `GeneratePresignedUrlUseCaseTest.cancellation during publish rethrows without emitting` — green |
| RHSFilterParser reclassified — Parser resolves outside domain | COMPLIANT | Parser at `com.profiletailors.spring.boot.presentation.filter` (no `..domain..` segment); factory same-package with no stale import; `RHSFilterParserTest` 12/12 + factory 2/2 green; presentation main zero Jackson/SLF4J; no `..domain..` class imports Jackson/SLF4J (presentation ban green); moved parser contains no logger/emit; 2 no-dump tests assert exception messages exclude the secret value |
| Docs synchronized — Docs match implementation | COMPLIANT | `observability-contracts.md` §4, `observability-usage.md` (consumer bullet, edge paragraph, stable-name entry, test-matrix row), `dependencies.md` (consumed-by/depends-on tables + mermaid edge) all state the exact implemented name, severity, attributes + `cause`, no-`key`/payload rule, blank-bucket skip, constant message, and `:shared:storage → :shared:observability` edge — verified by direct read against code constants |

Requirement/scenario counts above are the actual spec counts (4 requirements, 6 scenarios). No totals invented.

## Correctness

| Check | Result |
|---|---|
| `emit` uses `Severity.WARN`, stable dotted name, `cause` as error param (not attribute) | Holds in both application files (3 sites + 1 site) |
| Attributes are `operation`/`provider`/`bucket` only; `key`/payloads/metadata/expiry/requesterId never emitted | Holds; tests assert `containsKey("key") == false` and constant message `doesNotContain(KEY)` |
| Blank bucket skips `bucket` attribute | Holds in both `emitPublishFailure` helpers; dedicated test green |
| Swallow-and-continue preserved; `CancellationException` rethrown with no emit, including inside `channelFlow` download path | Holds; `catch (e: CancellationException) { throw e }` precedes generic `catch` at every publish site; tests green |
| Defaulted `NoOpOperationalEventSink` trailing param keeps construction sites compiling | Holds; no construction-site edits; full compile+test green |
| Parser move preserves behavior; no full-query-map log/emit | Holds; 14/14 SBC filter tests green; parser file has no log/emit |
| Zero-comment policy on new code | Holds; new emit helpers, ban rules, and tests introduce no comments; KDoc in touched files is pre-existing |
| No weakened assertions | Holds; ban rules are purely additive; original ArchUnit assertions intact |
| No new Detekt/Spotless findings | Holds per fresh static run above |

## Design coherence

| Design decision | Implementation |
|---|---|
| Single `storage.operation.event.publish.failed` + `operation` attr reusing `StorageObservation.Operations` | Implemented exactly; `upload`/`download`/`delete`/`presign` constants reused |
| Move parser to SBC `spring.boot.presentation.filter` next to factory | Implemented exactly |
| Migrate → enable per module (storage → presentation → ratelimit); bans blocking | Implemented; all three ban suites blocking and green |
| Trailing `operationalEvents = NoOpOperationalEventSink` | Implemented in both application classes |
| `SortParser`/`OffsetPagePresenter` out of ban scope (no `..domain..`/`..application..` segment) | Respected; no widening, no change |
| `:shared:observability` zero prod deps → acyclic edge | Holds; build file has test-only deps |
| Presentation Jackson/SLF4J `implementation` deps verified unused and removed | Done; full 182-test presentation suite green afterwards |
| `PresentationArchTest` application rule `.allowEmptyShould(true)` | Justified: module has no `..application..` package; established `LeadCaptureArchTest`/`ComponentScanArchTest` pattern; rule still bans if such classes ever appear |
| Fail-then-pass via temporary scratch violators (git unusable) rather than reverting verified PR 1/2 files | Accepted deviation; each fail leg named the exact banned edge the rule exists to catch |

## Issues

### WARNINGS

- **W1 — `RHSFilterParser.convert` interpolates the operand value into `FilterInvalidException` text (flagged candidate (a)).** Line 56 builds `"Can't convert operand. Operand: $operand, Type: $clazz"`, and `parse` re-wraps via `FilterInvalidException(e.message)`, so the raw operand propagates to the caller. The two no-dump tests cover only the unsupported-operator and invalid-format paths; the convert path (reachable, e.g. non-numeric secret for a numeric field) has no `doesNotContain(secret)` assertion. Severity is WARNING, not CRITICAL: the spec bans logging/emitting the *full query map or payload*, and this file has no logger/emit — the leak is a single value in a client-visible exception message, not a full-map log. The spec scenario therefore still passes literally. Follow-up (not this change): redact the convert failure to a value-free message (e.g. type/property only) and add the third no-dump test; consider the sibling `processQueryEntry` property interpolation at the same time. No fix applied per instructions.
- **W2 — `strict_tdd` configuration drift.** `openspec/config.yaml` declares `strict_tdd: true`, while this change executed in Standard mode with `config.yaml` deliberately untouched (session-recorded drift, not this slice's to fix). Runtime proof is complete (fresh focused suites + static checks above), so this is not an archive blocker, but the next change should reconcile the config or run under the strict-TDD verifier module.

### SUGGESTIONS

- **S1 — Residual risk sweep (flagged candidate (b)): no additional blocker found.** Greps confirm no `..domain..`/`..application..` SLF4J/Jackson outside allowlisted `..infrastructure..`; no stale `common.domain.presentation.filter` references; sink-redaction hardening and legacy `info`/`warn`/`error` deprecation remain explicitly out of scope per spec Non-Requirements. Minor hygiene notes for a future change (not this one): `GeneratePresignedUrlUseCaseTest` uses `runBlocking` inside `runTest` in several tests (works, but idiomatically redundant); storage test-compile warnings (`URL` deprecation, unchecked casts, always-true instance checks) and SBC test-compile warnings are pre-existing and untouched.

No CRITICAL findings. No spec violation requiring a fix before archive.

## Verdict

**PASS WITH WARNINGS** — all 10 tasks complete, all 4 requirements / 6 scenarios compliant with covering tests passed at runtime, design coherent, Detekt/Spotless clean on all touched modules. Warnings W1–W2 and suggestion S1 are follow-up candidates recorded above; none blocks archive. Recommended next step is archive with W1 filed as the parser-message follow-up.
