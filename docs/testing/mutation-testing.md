# Mutation Testing with mutflow

## Overview

Backend mutation testing uses mutflow 1.5.0 as a Kotlin-native advisory guardrail for domain and
application logic. It verifies that fast JUnit tests detect behavioral changes, complementing line
coverage, integration tests, and Gherkin BDD without replacing them.

The integration is isolated behind the `com.profiletailors.mutation.testing` convention plugin. It
can be upgraded, replaced, or disabled without changing production behavior or existing test-task
contracts.

Current stack: JDK 25, Kotlin 2.4.10, Spring Boot 4.0.8, JUnit Platform 6.1.3.

## Changes

- mutflow 1.5.0 pinned in `gradle/libs.versions.toml` plus build-logic classpath wiring.
  The mutflow artifacts pull `kotlin-stdlib` 2.4.20 onto the buildscript classpath, which broke
  `:server:smp:detekt` (compiled with Kotlin 2.4.10, running with 2.4.20). Fixed by excluding
  `kotlin-stdlib` and `kotlin-gradle-plugin-api` from the build-logic dependency on the mutflow
  plugin; Detekt is green again with no rule weakened.
- `MutationTestingPlugin` applies `io.github.anschnapp.mutflow`, sets explicit baseline targets,
  defaults verification to LENIENT, and propagates `MUTFLOW_VERIFICATION_MODE` to test JVMs.
- `verifyMutationClean` task fails when a production JAR references mutflow runtime switches.
  Verified green on the release layout.
- `mutatedMain` carries no resources: mutflow mirrors `main` resources into `mutatedMain`, which
  duplicated `db/changelog/db.changelog-master.yaml` on the test classpath and broke every
  Liquibase-backed Postgres test (`Found 2 files with the path`). The convention now clears
  `mutatedMain` resources in `afterEvaluate`. Verified: `R2dbcConsentRepositoryTest` failed with
  mutflow before the fix, passed with `-Pmutflow.enabled=false`, and passes with mutflow enabled
  after the fix.
- Baseline targets: `BulkValidationPipeline` and `PublicationLifecyclePolicy` in
  `com.profiletailors.smp.publishing.domain`.
- Baseline samples: `PublicationRetryMutationBaselineTest` and `BulkValidationMutationBaselineTest`,
  both `@MutFlowTest(verificationMode = LENIENT)`.
- Advisory workflow: `.github/workflows/mutation-testing.yml` (dispatch, weekly schedule,
  relevant-path PRs only, 30-minute timeout, 14-day artifacts). It is not part of the CI Gate,
  so survivors never block merges.

## Usage

### Target scope

Mutated in this baseline:

- Domain invariants
- Application handlers and policies exercised by fast unit tests
- Authorization and token rules when covered by fast unit tests
- State transitions such as publication retry lifecycle
- Business validation such as bulk import validation

Validated by existing suites instead:

- Controllers and HTTP contracts
- Spring annotations and configuration
- R2DBC and PostgreSQL adapters
- External providers
- Full capability flows through Gherkin BDD

Excluded from mutation execution: BDD suites, Testcontainers paths, full Spring contexts, generated
code, DTO-only code, and trivial configuration. Those suites still run, but only `@MutFlowTest`
classes with `MutFlow.underTest` blocks execute mutation runs.

### Commands

```bash
MUTFLOW_VERIFICATION_MODE=LENIENT ./gradlew :server:smp:test --tests "*MutationBaseline*"
./gradlew :server:smp:verifyMutationClean
./gradlew :server:smp:test -Pmutflow.enabled=false
MUTFLOW_VERIFICATION_MODE=DISABLED ./gradlew :server:smp:test
```

Baseline behavior: LENIENT reports survivors without failing the build. STRICT is reserved for a
future calibrated gate after stability and actionability are measured. DISABLED runs only the
baseline discovery path.

### Adding a mutation test

```kotlin
@MutFlowTest(verificationMode = VerificationMode.LENIENT)
class RetryPolicyMutationTest {
    @Test
    fun `retry allowed only from failed state`() {
        MutFlow.underTest {
            PublicationLifecyclePolicy.requireRetryable(failed)
        }
    }
}
```

Keep targets explicit in the convention plugin. Do not mutate the entire backend by default.

## Baseline (measured 2026-09-26, LENIENT)

Command:

```bash
MUTFLOW_VERIFICATION_MODE=LENIENT ./gradlew :server:smp:test --no-daemon --no-configuration-cache \
  --tests "com.profiletailors.smp.publishing.domain.PublicationRetryMutationBaselineTest" \
  --tests "com.profiletailors.smp.publishing.domain.BulkValidationMutationBaselineTest"
```

Results from `server/smp/build/test-results/test/*MutationBaseline*.xml`:

- `PublicationLifecyclePolicy.requireRetryable`: 2 discovered, 2 killed, 0 survived, 0 timed out
  (`PublishingPolicies.kt:67` `!=` to `==`, `requireRetryable()` body removal).
- `BulkValidationPipeline` blank plus invalid header: 3 discovered, 3 killed, 0 survived,
  0 timed out (`BulkValidationPipeline.kt:42` `isBlank()` inversion, `:45` `isEmpty()` inversion,
  `:47` `isBlank()` inversion).
- Totals: 5 attempted, 5 killed, 0 survived, 0 timed out, 0 tooling failures.
- Only the two selected classes ran; `bddFastTest`, `bddPostgresTest` and
  `postgresIntegrationTest` were not executed.
- `verifyMutationClean`: green. `backend-lint` (Detekt): green after the stdlib exclusion.
  `licence-check`: green with mutflow reported as Apache-2.0. Build-logic `MutationTestingPluginTest`:
  3 passed.

Coroutine finding: `MutFlow.underTest` takes a plain `() -> T` lambda, so suspend production code
needs a `runBlocking` bridge inside the block. `runTest` cannot host that bridge, use a plain
`@Test` with `runBlocking` inside `underTest`. Both bulk cases kill their mutants this way while
the mutated code stays thread-confined.

No blocking threshold is active. A protected-scope gate requires a completed pilot, at least 10
representative advisory runs, and demonstrated stability, actionability and maintenance cost.

### Rollback

- Advisory mode: `MUTFLOW_VERIFICATION_MODE=LENIENT`.
- Disabled mode: `./gradlew :server:smp:test -Pmutflow.enabled=false` or `mutflow.enabled=false`
  in `gradle.properties`, or `mutflow { enabled = false }`.
- Disabling removes compiler instrumentation and extra compilation. Production artifacts stay clean.

## Troubleshooting

### NoClassDefFoundError during compilation

Cause: mutflow 1.5.0 was built against Kotlin 2.4.20 while the repository pins Kotlin 2.4.10.
Compiler plugins are tightly coupled to compiler internals.

Fix: align the Kotlin version with the mutflow release, or pin a mutflow release matching the
repository Kotlin version, then rerun the baseline sample.

### Zero mutations discovered

Cause: code under test was not reached inside `MutFlow.underTest`, or the class is not in the
configured target list.

Fix: confirm the Gradle `targets` entry matches the class or package pattern, and that the
production call sits inside `MutFlow.underTest`.

### Surviving mutation in LENIENT mode

Cause: missing assertion, uncovered boundary, or pseudo-tested logic.

Fix: classify the survivor, add the boundary assertion, rerun the focused mutation test. Do not
optimize a global score mechanically.

### "detekt was compiled with Kotlin X but is currently running with Y"

**Cause:** The mutflow plugin artifacts depend on a newer `kotlin-stdlib` than the repository
Kotlin version, and that version leaked onto the buildscript classpath.

**Fix:** Keep the exclusion of `kotlin-stdlib` and `kotlin-gradle-plugin-api` from the build-logic
dependency on the mutflow plugin. Never downgrade Detekt or its rules to accommodate the new
dependency. If the Kotlin version is upgraded, revalidate `backend-lint` before changing the
mutflow version.

### Timed-out mutation

Cause: a flipped loop condition created a long or infinite loop.

Fix: add `// mutflow:ignore` with a reason on the affected line, or narrow the target.

### Liquibase reports duplicate changelog files

Cause: `mutatedMain` mirrored `main` resources, so the changelog existed twice on the test
classpath and Liquibase strict mode refused to choose.

Fix: the convention clears `mutatedMain` resources. Never set
`liquibase.duplicateFileMode=WARN` to accommodate the tooling. If a new duplicate appears,
inspect which source set contributes it before changing anything.

### Suspend production code inside `underTest`
**Cause:** `MutFlow.underTest` accepts a plain `() -> T` lambda, so a suspend call does not
compile directly inside the block, and `runTest` cannot host a nested `runBlocking` bridge.

**Fix:** Use a plain `@Test` with `runBlocking` inside `underTest`, keeping the mutated code
thread-confined so session tracking holds.

Cause: a flipped loop condition created a long or infinite loop.

Fix: add `// mutflow:ignore` with a reason on the affected line, or narrow the target.

### Production JAR check fails

Cause: a JAR contains `io/github/anschnapp/mutflow` references, meaning mutation switches leaked
into production bytecode.

Fix: verify the build used the standard plugin wiring with separate `mutatedMain` compilation, then
rebuild and rerun `verifyMutationClean` before release packaging.

## References

- mutflow repository: [anschnapp/mutflow](https://github.com/anschnapp/mutflow)
- mutflow license: Apache License 2.0
- Baseline convention: `gradle/build-logic/src/main/kotlin/com/profiletailors/buildlogic/testing/MutationTestingPlugin.kt`
- Baseline sample: `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/PublicationRetryMutationBaselineTest.kt`
- Related test policy: `docs/testing/test-tags-and-env.md`
- Linear epic: DALLAY-544 with baseline workstream DALLAY-545
