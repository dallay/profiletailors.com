# Tasks: Fix Restrictive Component Scan

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~250 (10 source files: 1 KDoc + meta-annotation, 1 filter drop, 1 preventive fix, 1 import swap, 1 annotation removal, 1 NEW @Configuration, 2 test files (1 NEW ArchUnit, 1 extended), 2 build files) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR with 3 logical-group commits |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Restore Spring default component scan and normalize bean annotations | PR 1 (single) | Commits 1+2+3 land together; commit 1 (meta+filter) compiles independently, commit 2 (normalization+properties) compiles only after 1, commit 3 (tests+ArchUnit) is the safety net. Each commit is independently revertable. |

## Phase 1: Foundation — custom `@Service` meta-annotation + preventive `RateLimitConfiguration` fix (commit 1 of 3)

These two edits are thematically linked: meta-annotating the custom `@Service` with `@Component` makes the `includeFilters` workaround in `RateLimitConfiguration` redundant, so both go in the same commit.

- [x] 1.1 Add `@org.springframework.stereotype.Component` as a meta-annotation to
  `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt`. Add a KDoc
  clarifying the dual role: hexagonal "domain application service" marker that is also discoverable
  as a Spring stereotype via the default filter (spec scenario
  "Custom `@Service` carries the required meta-annotations"). Keep
  `@Retention(AnnotationRetention.RUNTIME)`, `@Target(AnnotationTarget.CLASS)`, and
  `@MustBeDocumented`.
- [x] 1.2 In
  `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitConfiguration.kt`,
  drop the nested `@ComponentScan` annotation entirely. Keep `@Configuration` and
  `@EnableConfigurationProperties(RateLimitProperties::class)`. Keep the `bucketConfigurationFactory`
  `@Bean` method. Add a KDoc explaining that no nested scan filter is required because the custom
  `@Service` is now discoverable via the default Spring filter (spec scenario
  "RateLimitConfiguration has no nested `includeFilters` and keeps the config annotations").
  Remove the now-unused imports (`ComponentScan`, `FilterType`, `Service`).
- [x] 1.3 Commit Phase 1 as a single logical-group commit:
  `chore(common): meta-annotate custom @Service with @Component for default scan discovery`.
  Files in this commit: `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt`
  and
  `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitConfiguration.kt`.

## Phase 2: SmpApplication filter drop + annotation normalization + properties registration (commit 2 of 3)

These four edits are thematically linked: they normalize bean-annotation usage under the restored
default scan, register `PublishingCredentialsProperties` via the idiomatic
`@EnableConfigurationProperties` path, and add a co-located `@Configuration` file.

- [x] 2.1 In `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`, remove the
  `includeFilters` block from `@ComponentScan`. Keep the `excludeFilters` regex (matching
  `com\.profiletailors\.smp\.integration\..*` and `com\.profiletailors\.smp\.bdd\..*`) and add a
  KDoc explaining the regex is defense against accidentally moving test-only classes into `main`
  (spec scenario "SmpApplication has no `includeFilters` and retains a documented
  `excludeFilters`"). Remove the now-unused
  `import com.profiletailors.common.domain.Service`.
- [x] 2.2 In
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/CredentialEncryptionService.kt`,
  replace `import org.springframework.stereotype.Service` with
  `import com.profiletailors.common.domain.Service` and keep the `@Service` annotation usage
  unchanged (now resolving to the custom marker). Spec scenario
  "CredentialEncryptionService uses the custom marker and stays injectable".
- [x] 2.3 In
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsProperties.kt`,
  remove the `@Component` import and the `@Component` annotation. Keep
  `@ConfigurationProperties(prefix = "publishing.credentials.encryption")` unchanged (spec scenario
  "PublishingCredentialsProperties has no `@Component` and keeps its prefix").
- [x] 2.4 Create the NEW file
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsConfiguration.kt`
  with a top-level `class PublishingCredentialsConfiguration` annotated `@Configuration` and
  `@EnableConfigurationProperties(PublishingCredentialsProperties::class)`. Add a KDoc explaining
  the architectural role: co-located `@EnableConfigurationProperties` for the
  `publishing/credentials` bounded context, intentionally declares no `@Bean` methods today, and
  follows the project house style of dedicated `@Configuration` classes per bounded context
  (e.g. `IdentitySecurityConfiguration`, `TenancyWebConfiguration`). Spec scenario
  "PublishingCredentialsConfiguration registers the properties and carries a KDoc".
- [x] 2.5 Commit Phase 2 as a single logical-group commit:
  `refactor(smp): drop restrictive component scan filter and normalize bean annotations`.
  Files in this commit: `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`,
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/CredentialEncryptionService.kt`,
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsProperties.kt`,
  and the NEW
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsConfiguration.kt`.

## Phase 3: Tests + ArchUnit guard rail (commit 3 of 3)

These four edits add the safety net that makes the bug impossible to reintroduce silently.

- [x] 3.1 In `gradle/libs.versions.toml`, add `archunit = "1.4.2"` under `[versions]` and
  `archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }`
  under `[libraries]`. Verify with
  `./gradlew :server:smp:dependencies --configuration testRuntimeClasspath` (do not commit the
  verification output, only run it locally to confirm the artifact resolves).
- [x] 3.2 In `server/smp/build.gradle.kts`, add
  `testImplementation(libs.archunit.junit5)` to the `dependencies` block (in the
  `testImplementation` section, alongside the existing JUnit/Spring test starters). Spec scenario
  "The check runs as part of `./gradlew :server:smp:check`".
- [x] 3.3 Create the NEW file
  `server/smp/src/test/kotlin/com/profiletailors/smp/ComponentScanArchTest.kt` mirroring the
  structure of `tmp/example-code/cvix-main/server/engine/src/test/kotlin/com/cvix/ArchTest.kt`
  (same `ClassFileImporter` + `ArchRuleDefinition` DSL, `@BeforeEach setUp()` building
  `JavaClasses` via
  `ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.profiletailors.smp")`).
  Include exactly three tests, each with a KDoc:
  - `applicationLayerShouldNotUseSpringComponent()` — fail-strict. Uses
    `ArchRuleDefinition.noClasses().that().resideInAPackage("..application..").should().beAnnotatedWith(org.springframework.stereotype.Component::class.java).because(...).check(importedClasses)`.
    KDoc cites spec scenario "Build fails on Spring `@Component` in the smp application layer".
  - `applicationLayerShouldNotUseSpringRepository()` — advise-only. Wraps
    `.check(importedClasses)` in a `try { ... } catch (AssertionError) { /* log + swallow */ }`
    block; the test method records the violation in a captured message but never fails JUnit.
    KDoc documents the chosen mechanism (try/catch swallow + recorded message) and cites spec
    scenario "Build SHOULD fail on Spring `@Repository` in the smp application layer".
  - `infrastructureConfigShouldNotDeclareNestedIncludeFilters()` — fail-strict. Uses
    `ArchRuleDefinition.noClasses().that().resideInAPackage("..infrastructure.config..").should().beAnnotatedWith(org.springframework.context.annotation.ComponentScan::class.java)`
    combined with a follow-up `.andShould(...)` via a `CustomArchCondition` that asserts
    `getAnnotation(ComponentScan::class.java).includeFilters.isNotEmpty()` would be a violation.
    KDoc documents the DSL choice and the package scope
    (`com.profiletailors.*.infrastructure.config.*`).
- [x] 3.4 In
  `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt`, keep the existing
  `contextLoads()` and its `@SpringBootTest` configuration untouched (spec scenario
  "`SmpApplicationTests.contextLoads()` continues to pass"). Add `@Autowired private lateinit var
  applicationContext: ApplicationContext` to the class. Then add two new tests, each with a
  one-line KDoc:
  - `eventConsumersAreRegisteredUniquely()` — runs alphabetically before `loads...` because
    `e` < `l` in JUnit's default order. Calls
    `applicationContext.getBeansOfType(com.profiletailors.common.domain.bus.event.EventConsumer::class.java)`,
    groups entries by `value.javaClass.kotlin`, asserts each group has exactly one bean name, and
    asserts no two distinct bean names map to the same instance via `System.identityHashCode`
    (spec scenario "No `EventConsumer` is registered more than once after `EventConfiguration` is
    loaded" and "New test asserts `EventConsumer` registration uniqueness"). Import
    `com.profiletailors.common.domain.bus.event.EventConsumer` directly (public `fun interface`).
  - `loadsAllExpectedBeanStereotypes()` — resolves
    `applicationContext.getBean(Class.forName("com.profiletailors.smp.authorization.application.WorkspaceAuthorizationService"))`
    and asserts non-null (uses FQCN because the class is `internal`, per ADR 5.4). Then resolves
    `R2dbcApiKeyCredentialReplacementGateway`, `R2dbcLinkedInCredentialGateway`, and
    `AuthorizationProblemDetailsHandler` via direct import + `getBean(Class)` (all public).
    Asserts each is non-null. Cites spec scenario "New test asserts one bean per stereotype is
    present".
- [x] 3.5 Commit Phase 3 as a single logical-group commit:
  `test(smp): add regression tests and ArchUnit guard rail for component scan conventions`.
  Files in this commit: `gradle/libs.versions.toml`, `server/smp/build.gradle.kts`, the NEW
  `server/smp/src/test/kotlin/com/profiletailors/smp/ComponentScanArchTest.kt`, and
  `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt`.

## Phase 4: Verification & latent-issue capture

The apply phase is the right place to run the test suite; this phase captures any
previously-inactive `@Configuration` classes that surface new failures when they become active.

- [x] 4.1 Run `./gradlew :server:smp:test` from the repo root. Capture the full output (stdout +
  stderr) and the final PASS/FAIL summary. Do not commit the captured output.
- [x] 4.2 If a test fails because a previously-inactive `@Configuration` class (e.g.
  `EventConfiguration`, `LinkedInPublishingAdapters`, `IdentityBootstrapConfiguration`) is now
  active and is causing a context-failure that did not exist before, add a new top-level item
  under this Phase 4 using the Open Sub-Tasks Template from `design.md` section 9 (one sub-task
  per failure, each with the four required pieces of metadata: FQCN, verbatim error message,
  one-line fix, test+assertion proving the fix). Spec scenario
  "New context-failures are captured as sub-tasks" and
  "Each latent-issue sub-task carries the required metadata".

  **Captured sub-tasks** (see `proposal.md` "Open Sub-Tasks Discovered During Apply" for the
  full template entries with FQCNs, error messages, fixes, and tests):

  - **Sub-task 1**: `ComponentScanArchTest.applicationLayerShouldNotUseSpringComponent` flagged
    three smp application-layer classes still using Spring's `@Component` instead of the
    custom `@Service`. Migrate them.
  - **Sub-task 2**: `ComponentScanArchTest.infrastructureConfigShouldNotDeclareNestedIncludeFilters`
    failed with `failed to check any classes` because no class under
    `com.profiletailors.smp.*.infrastructure.config.*` is annotated with `@ComponentScan` (the
    desired steady state). The rule itself is well-formed; add `.allowEmptyShould(true)` so
    passing the steady state is green.

- [x] 4.3 For each captured sub-task in 4.2, address the fix within the same change (do NOT defer
  to a follow-up). Document the fix as a sub-sub-task numbered `4.3.1`, `4.3.2`, etc. under the
  parent sub-task from 4.2.

  - [x] 4.3.1 (Sub-task 1.1) Migrate
    `com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialHandler` and
    `com.profiletailors.smp.credentials.application.SecureRandomApiKeyCredentialValueFactory`
    to the custom `@Service` marker. Done — `ReplaceApiKeyCredentialInternal.kt` now imports
    `com.profiletailors.common.domain.Service` and both classes are annotated `@Service`.
  - [x] 4.3.2 (Sub-task 1.2) Migrate
    `com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsHandler` to the custom
    `@Service` marker. Done — `GetWorkspaceAuditEventsHandler.kt` now imports
    `com.profiletailors.common.domain.Service` and the class is annotated `@Service`.
  - [x] 4.3.3 (Sub-task 2) Add `.allowEmptyShould(true)` to the two `ArchRuleDefinition` chains
    in `ComponentScanArchTest.infrastructureConfigShouldNotDeclareNestedIncludeFilters`. Done.
- [x] 4.4 Re-run `./gradlew :server:smp:test` after each `4.3.x` fix to confirm the new test passes
  and no other test regressed. Repeat until the full suite is green.
  Result: 283 tests, 0 failures, 0 errors, 4 pre-existing skips.
  `SmpApplicationTests` (3 tests) and `ComponentScanArchTest` (3 tests) all pass.
- [x] 4.5 When `./gradlew :server:smp:test` passes with no latent-issue sub-tasks captured (or all
  captured sub-tasks are resolved and the re-run is green), the change is ready for the verify
  phase (`sdd-verify`). The verify phase will independently confirm every spec scenario is
  satisfied.
