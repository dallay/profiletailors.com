# Technical Debt Remediation and Java 25

**Last Updated:** 2026-09-05
**Status:** In progress; not a completion report

## Overview

The scope combines the supplied deprecation/code-smell audit with Java 25 and the
latest stable Kotlin release supporting that JVM target. Audit findings are hypotheses
until verified against current consumers, tests, configuration, and persisted data.

Implement independently reviewable slices. Do not grow baselines, add suppressions,
weaken checks, or publish/deploy changes without authorization.

## Changes

| Slice | Scope and acceptance criteria | Status |
| --- | --- | --- |
| Platform | Java 25 toolchain and bytecode, CI, devcontainer, image runtime, matching Kotlin; build, tests, analyzers and image smoke verified | Configuration updated; verification in progress |
| Media CAS convergence | Inventory internal/external v1 consumers and persisted legacy rows; migrate provider imports, endpoints, commands, handlers and storage ownership; remove legacy frontend exports, `PROCESSING` and `generateStorageKey` only after safe migration | Pending |
| Publishing | Remove structural and exception file suppressions in bulk handlers, creation service, repository, controller, API and models through cohesive refactoring, not relocated suppressions | Pending |
| Media orchestration | Separate upload claiming, streaming verification, finalization and failure recovery where consumers justify boundaries; preserve cancellation, transaction atomicity, deduplication and cleanup | Pending |
| Ports and provisioning | Review MediaAssetRepository, WorkspaceFileBlobRepository and PublicationRepository consumer needs; segregate only meaningful ports; simplify workspace provisioning while preserving the application transaction boundary | Pending |
| Kotlin warnings | Add compiler-visible deprecations only where a compatibility interval is necessary; migrate consumers, reach zero compiler warnings, then enable warnings-as-errors | Pending |
| Detekt governance | Unify effective module policy, validate configuration against the installed version, fail configuration warnings, enforce no baseline growth and reduce structural findings first | Pending |
| Frontend typing | Remove production explicit any, enable noExplicitAny errors, replace broad UI overrides with compliant code and re-enable correctness rules | Pending |
| Accessibility | Review scheduler/calendar keyboard alternatives, chart data access, and modal Escape, focus trap, focus restoration and close controls; fix unjustified suppressions | Pending |
| Suppression maintenance | Inspect the actual auditor and execution evidence, run a bounded audit, and configure periodic maintenance without duplicating an existing automation | Pending |
| Low-risk dependencies | Verify consumers and remove redundant coroutines-jdk8 dependency and alias when safe | Pending |
| Cosmetic baseline | Address safe cosmetics last; do not launch another TODO/FIXME or ts-ignore campaign without new evidence | Deferred |

## Current Evidence

- The repository already pins Kotlin 2.4.10 and Gradle 9.7.1. Kotlin 2.4.10 is
  the latest stable release listed by the official release page on 2026-09-05.
- Java was pinned to 21 in AppConfiguration, the version catalog, CI and devcontainer.
  These now target 25. Paketo receives BP_JVM_VERSION from the JDK catalog.
- Legacy reserveAsset/uploadAsset service exports and their tests still exist.
  The similarly named uploadAsset in useUploadAsset is not evidence of a legacy consumer.
- UnsplashMediaProviderHandlers still calls generateStorageKey: deleting the helper
  without migrating provider imports would break a real consumer.
- BulkPublishingHandlers still has structural file suppressions.
- KotlinLibraryPlugin and SpringBootApplicationPlugin disagree on buildUponDefaultConfig.
- Detekt's configuration header names alpha.3 while the catalog pins alpha.6;
  configuration warningsAsErrors is false. This is separate from compiler warnings.
- Biome still sets noExplicitAny to warn and disables it in overrides.
- The backend-lint-shared recipe masks failures with a fallback echo; direct Gradle
  Detekt execution was used for trustworthy exit status. Correct the recipe in the
  governance slice.
- Local environment quirks (not product findings): the `docker compose` CLI plugin
  symlink points at a removed OrbStack path, so `just infra-up` is broken in this
  environment while the `docker-compose` binary works; `bootBuildImage` also fails
  against the default Docker config because of empty `auths` entries (`'username'
  must not be null`) and needs a stripped config plus `DOCKER_HOST` pointed at the
  dory socket. Neither quirk is caused by the Java 25 change.
- No external API-consumer inventory, production row inspection or auditor execution
  history has been verified in this work. Do not infer safety from their absence locally.

### Local platform verification (2026-09-05, fresh runs)

- Build-logic suite: 10 tests passed (AppConfigurationTest 5, SpringBootApplicationPluginTest 4, LicenceReportPluginTest 1).
- SMP main and test Kotlin compilation (forced `--rerun-tasks`): passed with zero Kotlin warnings after the boxed-primitive cleanup.
- R2dbcBulkImportJobRepositoryTest: 13 tests passed.
- SMP SpotlessCheck (forced rerun): passed. Detekt across SMP and all shared modules (forced rerun, 15 tasks): passed.
- Generated SMP Kotlin classes: 2,317 inspected, all class-file major version 69 (Java 25).
- Fast unit suite (`:server:smp:test -PexcludeTags=modularity,postgres`): 2,037 tests, 0 failures.
- PostgreSQL integration suite: 378 tests with 1 first-run failure (`PublishingWorkerTransactionPostgresIntegrationTest` initializationError, connection refused to the Testcontainers-assigned port); the failing test passed alone on rerun, so the full-suite failure reads as a flaky container-startup race, not a Java 25 regression.
- BDD fast suite: 224 tests with 1 first-run failure (reset-password "token invalidated by a newer request", 15s blocking-read timeout); full rerun passed 224/224, so the first failure reads as flaky timing under load.
- BDD Postgres suite: 224 tests, 0 failures (Testcontainers; `infra-up` was not required).
- Gradle detected SDKMAN Java 25 installations and ran on Temurin 25.0.1. Local `java -version` is Temurin 25.0.1 LTS.
- Pinned Paketo image `profiletailors/smp:local-verify` built successfully. Image runtime inspected directly: BellSoft Liberica 25.0.3 LTS, confirming `BP_JVM_VERSION=25` was honored by the pinned builder.
- Release-image smoke passed: readiness UP, liveness UP, 89 Liquibase migrations, 0 development seed changesets.
- Documentation freshness and git diff whitespace validation: passed.
- Remote CI, a published image, deployment and live acceptance are not established by the checks above.

The test JVM emitted a class-data-sharing warning from bootstrap instrumentation.
This is not a Kotlin compiler warning and has not been suppressed.

## Usage and Verification

Start by validating the platform slice independently. Run build-logic tests, compilation,
Spotless, Detekt for SMP/shared modules, unit/architecture tests, PostgreSQL integration
tests and BDD. Build the pinned Paketo image and inspect its Java version before smoke
testing it. A configured runtime version does not prove that the pinned builder contains it.

Then implement Media convergence with characterization tests before deleting legacy code.
Keep the remaining slices separately reviewable and update their evidence here.

Remote CI, a published image, deployment and live acceptance are separate gates; local
compilation does not establish any of them.

## Troubleshooting

Java 25 bytecode cannot run on Java 21. Roll back application artifacts together with
their matching runtime if needed; do not put Java 25 artifacts into the previous runtime.
Keep irreversible data cleanup out of the platform slice. If a tool cannot inspect Java 25
bytecode, resolve its compatibility rather than disabling it or hiding its findings.

## References

- [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- [Kotlin 2.3 Java 25 support](https://kotlinlang.org/docs/whatsnew23.html)
- [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html)
- [Spring Boot 4 Java 25 support](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/)
- [Paketo JVM configuration](https://paketo.io/docs/howto/java/)
