# Tasks: Observability Boundary Enforcement

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~480–600 (additions + deletions; parser move counts twice) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 storage emit → PR 2 parser move → PR 3 bans + docs |
| Delivery strategy | auto-chain |
| Chain strategy | pending (orchestrator collects before apply; do not decide here) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Storage emit migration + Gradle edge | PR 1 | `./gradlew :shared:storage:test --tests "com.profiletailors.storage.application.*"` | N/A — captured fake-sink assertions prove runtime | Revert PR 1; restores 4 SLF4J warns, drops observability edge |
| 2 | Parser move + factory/test rewrites + no-dump test | PR 2 | `./gradlew :shared:spring-boot-common:test --tests "com.profiletailors.spring.boot.presentation.filter.*"` | N/A — factory-resolve plus failure-path assertions prove behavior | Revert PR 2; restores parser in `..domain..`, old imports |
| 3 | Per-module bans with fail-then-pass + 3-doc sync | PR 3 | `./gradlew :shared:storage:test --tests "*.StorageArchTest" :shared:shield:ratelimit:test --tests "*.RatelimitArchTest" :shared:presentation:test` | N/A — ArchUnit bans plus doc-vs-code diff prove it | Revert PR 3; bans removed, docs restored; PR 1–2 still compile |

Inputs: `strict_tdd` unresolved, config untouched, `chain_strategy` undecided; zero comments; assertions unchanged.

## Phase 1: Storage emit migration

- [x] 1.1 Add `implementation(project(":shared:observability"))` to `shared/storage/build.gradle.kts`; verify `shared/observability/build.gradle.kts` keeps zero production dependencies. Verify: `./gradlew :shared:storage:compileKotlin`.
- [x] 1.2 Migrate 3 warns in `shared/storage/src/main/kotlin/com/profiletailors/storage/application/StorageApplicationService.kt` and 1 warn in `shared/storage/src/main/kotlin/com/profiletailors/storage/application/GeneratePresignedUrlUseCase.kt` to `emit(WARN, storage.operation.event.publish.failed)` with trailing `operationalEvents: OperationalEventSink = NoOpOperationalEventSink`; `cause` is the emit error param, not an attribute; attributes are `operation`/`provider`/`bucket` only; constant message text; blank bucket skips attribute; drop SLF4J imports. Verify: `./gradlew :shared:storage:compileKotlin`.
- [x] 1.3 Add captured-sink tests in `shared/storage/src/test/kotlin/com/profiletailors/storage/application/StorageApplicationServiceTest.kt` and `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageUseCaseTest.kt` (class `GeneratePresignedUrlUseCaseTest`) asserting name, WARN, attributes, absence of `key`, swallow-and-continue, and `CancellationException` rethrow with no emit. Verify: Unit 1 command.
- [x] 1.4 Keep construction sites compiling via default param: `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/StorageAutoConfiguration.kt`, `server/smp/src/test/kotlin/com/profiletailors/smp/test/TestStorageConfiguration.kt`, `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt`, `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/ResourcePreviewEndpointTestBase.kt`, `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/WorkspaceAccessSummaryEndpointTestBase.kt`. Verify: `./gradlew :shared:storage:test`.

## Phase 2: Parser reclassification

- [x] 2.1 Move `shared/presentation/src/main/kotlin/com/profiletailors/common/domain/presentation/filter/RHSFilterParser.kt` to `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParser.kt` with new package; remove full-query-map log/emit. Verify: `./gradlew :shared:spring-boot-common:compileKotlin`.
- [x] 2.2 Rewrite import in `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserFactory.kt` and move `shared/presentation/src/test/kotlin/com/profiletailors/common/domain/presentation/filter/RHSFilterParserTest.kt` to `shared/spring-boot-common/src/test/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserTest.kt` with updated package/imports. Verify: Unit 2 command.
- [x] 2.3 Extend failure-path coverage asserting no log or event output contains the full query map. Verify: Unit 2 command.

## Phase 3: Enforcement and docs

- [x] 3.1 Add blocking domain/application bans (SLF4J, Jackson, OTel, Micrometer) to `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageArchTest.kt` and create `shared/presentation/src/test/kotlin/com/profiletailors/common/domain/presentation/PresentationArchTest.kt`; run each fail-then-pass (reverted migration fails, migrated passes, infrastructure owners allowlisted, assertions unweakened). Verify: Unit 3 command.
- [x] 3.2 Add equivalent blocking bans to `shared/shield/ratelimit/src/test/kotlin/com/profiletailors/ratelimit/RatelimitArchTest.kt` as pass-only (no application-layer violation expected). Verify: Unit 3 command.
- [x] 3.3 Sync `docs/observability-usage.md`, `docs/observability-contracts.md`, `docs/architecture/shared/dependencies.md` to the implemented event name, attributes, and storage-to-observability edge. Verify: reviewer diff of 3 docs vs code.
