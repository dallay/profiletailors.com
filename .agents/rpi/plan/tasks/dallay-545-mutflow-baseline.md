# DALLAY-545 — Integrate mutflow and establish baseline

Route: Delegated direct baseline (user chose baseline over Explicit SDD epic)
Scope: DALLAY-545 only. DALLAY-546/547/548 remain out of scope.
Status: Working
Updated: 2026-09-26

## Outcome

Integrate mutflow 1.5.0 as isolated, reversible Kotlin-native mutation engine behind Gradle
conventions, with explicit narrow baseline targets, advisory-only execution, production-artifact
guarantee, and documented commands plus troubleshooting. No blocking gate. No commercial dependency.

## Constraints

- Preserve unrelated dirty worktree files (PWA offline work). Do not include them in this change.
- Zero-comment policy: no comments, KDoc, TODO, or suppressions in new code.
- No new Detekt findings, no baseline growth, no weakened rules.
- Production JAR must stay clean (mutatedMain only for tests).
- Existing `test`, `postgresIntegrationTest`, `bddFastTest`, `bddPostgresTest` behavior unchanged.
- Repo truth: JDK 25, Kotlin 2.4.10, Spring Boot 4.0.8, JUnit 6.1.3. Issue text says JDK 21 /
  Kotlin 2.3.x — stale, do not copy into docs.
- mutflow 1.5.0 built against Kotlin 2.4.20 while repo pins 2.4.10. Compiler plugins are tightly
  coupled. Treat as pilot risk, verify locally, record outcome.

## Tasks

- [ ] RPI-001 Add mutflow version, library, and plugin aliases to `gradle/libs.versions.toml`
  Acceptance: `mutflow = "1.5.0"`, `gradle-mutflow` library, `mutflow` plugin alias present.
  Evidence:
- [ ] RPI-002 Add mutflow plugin classpath to `gradle/build-logic/build.gradle.kts`
  Acceptance: build-logic compiles, no new Detekt findings.
  Evidence:
- [ ] RPI-003 Create isolated `MutationTestingPlugin` convention (`testing/MutationTestingPlugin.kt`)
  Acceptance: applies `io.github.anschnapp.mutflow`, sets explicit narrow targets, honors
  `mutflow.enabled` property and `MUTFLOW_VERIFICATION_MODE`, does not alter production source sets.
  Evidence:
- [ ] RPI-004 Apply mutation convention to SMP backend only (`server/smp/build.gradle.kts` or
  SpringBootApplicationPlugin composition)
  Acceptance: `:server:smp:tasks` shows mutflow wiring, plain `test` still passes without
  `@MutFlowTest` changes.
  Evidence:
- [ ] RPI-005 Add build-logic TestKit coverage for mutation convention
  Acceptance: new `MutationTestingPluginTest` verifies plugin applies, targets explicit, disable flag
  works, production behavior unchanged.
  Evidence:
- [ ] RPI-006 Establish narrow baseline with one fast domain target plus `MutFlow.underTest` sample
  Acceptance: baseline runs deterministically in LENIENT mode, excludes BDD/Testcontainers/Spring
  contexts/generated/DTOs, report shows killed/survived summary.
  Evidence:
- [ ] RPI-007 Add production-artifact cleanliness check (`mutflow-verify` task or script)
  Acceptance: release JAR scan fails on `MutationRegistry` references, passes on clean build.
  Evidence:
- [ ] RPI-008 Document scope, exclusions, commands, troubleshooting in `docs/testing/`
  Acceptance: `docs/testing/mutation-testing.md` covers targets, exclusions, commands, env overrides,
  troubleshooting, rollback to advisory/disabled. Index updated. No stale JDK/Kotlin versions.
  Evidence:
- [ ] RPI-009 Run quality gates and record evidence
  Acceptance: `just backend-test-fast`, `just backend-check`, `just backend-build`,
  `just licence-check`, docs links. Exact Passed/Failed/Not run recorded.
  Evidence:

## Progress

- Intake exploration done: SpringBootApplicationPlugin test-task wiring, Kover+JaCoCo setup,
  CI versus quality-gate separation, 678 main / 447 test Kotlin files, BDD feature layout.
- mutflow verified: Apache-2.0 LICENSE, Gradle plugin 1.5.0 on Maven Central, K2 compiler path,
  JUnit6 `@MutFlowTest`, dual-compilation with clean production JAR, `targets`, `enabled`,
  `MUTFLOW_VERIFICATION_MODE` STRICT/LENIENT/DISABLED.
- Baseline candidate: `publishing/domain` has fast unit tests
  (`BulkValidationPipelineTest`, `BulkValidationPipelineCoverageTest`); `ideas/domain` is small
  (18-line policies) and suitable as minimal second target.

## Next step

Implement RPI-001 through RPI-004, then TestKit test before baseline sample.
