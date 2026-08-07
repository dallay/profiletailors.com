# Apply Progress: Spring wiring and Detekt corrections — social-content PR2

## Scope

Continued the `linkedin-company-pages-community-inbox` PR2 apply slice without changing branches or Git history. Extracted the current Spring test result from XML, fixed only schema-supported wiring, and corrected PR2 Detekt findings while preserving safe-off Community Management and personal LinkedIn publishing.

## Delivery

- Chain strategy: `size-exception`
- Delivery strategy: `exception-ok`
- Layer/base: existing `feature/linkedin-pages-02-sync-calendar` worktree; no branch or history changes
- Implemented slice: supported `SocialContentBatchWriter` bean wiring, fail-closed unsupported dependencies, and behavior-preserving Detekt cleanup

## Spring evidence

- Focused command: `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --no-daemon --tests 'com.profiletailors.smp.PlatformBootstrapContextTest' --tests 'com.profiletailors.smp.SmpApplicationTests'`
- Result: `BUILD SUCCESSFUL`; XML files report `PlatformBootstrapContextTest` 2 tests, 0 failures/errors and `SmpApplicationTests` 4 tests, 0 failures/errors.
- The XML no longer contains `ParameterResolutionException`, `NoSuchBeanDefinitionException`, `No qualifying bean`, `UnsatisfiedDependencyException`, or `BeanCreationException`. The previously observed bean issue was resolved by retaining a single `socialContentBatchWriter` configuration bean and not inventing unsupported actor/evidence repositories.
- Schema limitation remains real: no production `SocialContentActorRepository` or `SocialContentApprovalEvidenceRepository`; sync handler continues to fail closed when those dependencies are unavailable.

## Completed

- [x] Preserved the sole schema-supported `SocialContentBatchWriter` bean in `SocialContentConfiguration`; no duplicate repository registration.
- [x] Kept unsupported actor/evidence persistence absent and fail-closed; no migration or fake production adapter added.
- [x] Split `DefaultSocialContentAccessGate.validate` into focused validation methods without changing denial ordering or policy behavior.
- [x] Removed duplicate fully-qualified references in `LinkedInCommunityManagementAdapter`.
- [x] Extracted repeated SQL bind parameter names in `R2dbcSocialContentRepositories` into constants and retained all SQL semantics.
- [x] Removed an unnecessary fully qualified calendar-query type and wrapped long expressions.
- [x] Reformatted long test declarations and moved the social-content BDD test configuration to its parent package to satisfy Detekt class-ordering/package naming checks; converted repeated JSON body literals to raw strings.
- [x] Added `newCheckpoint(actor)` helper in `SocialContentSyncHandler` to remove a long constructor expression without changing checkpoint behavior.
- [x] Applied the existing project-approved `@Suppress("TooManyFunctions")` style to the established problem-details advice class; no endpoint mapping changed.

## Verification

- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --no-daemon --tests 'com.profiletailors.smp.PlatformBootstrapContextTest' --tests 'com.profiletailors.smp.SmpApplicationTests'` — PASS; `PlatformBootstrapContextTest` 2/2 and `SmpApplicationTests` 4/4.
- `SMP_DB_TEST_PASSWORD=test just backend-lint` — PASS; `:server:smp:detekt BUILD SUCCESSFUL`.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --no-daemon --tests 'com.profiletailors.smp.PlatformBootstrapContextTest' --tests 'com.profiletailors.smp.SmpApplicationTests' --tests 'com.profiletailors.smp.publishing.application.SocialContentApplicationHandlersTest' --tests 'com.profiletailors.smp.publishing.application.SocialContentAccessGateTest' --tests 'com.profiletailors.smp.publishing.application.SocialContentSyncHandlerTest' --tests 'com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInCommunityManagementAdapterTest' --tests 'com.profiletailors.smp.publishing.infrastructure.http.SocialContentControllersTest' --tests 'com.profiletailors.smp.publishing.infrastructure.http.PublishingProblemDetailsHandlerTest'` — PASS (`BUILD SUCCESSFUL`).
- `SMP_DB_TEST_PASSWORD=test just backend-test-fast` — PASS (`BUILD SUCCESSFUL`).
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --no-daemon --tests 'com.profiletailors.smp.bdd.fast.CucumberFastIntegrationTest'` — PASS (`BUILD SUCCESSFUL`).
- `git diff --check` — PASS; no output.

## Remaining / classification

- Task 2.2 remains partial and schema-blocked: actor persistence, approval-evidence persistence, and provider-portable checkpoint mapping cannot be completed honestly with the current schema. No migrations were added.
- The full worktree remains intentionally dirty with the pre-existing PR2 implementation plus these corrections; no commit/push/PR was performed.

## Explicit SocialAccountRepository BDD wiring

- Scope: test-only Spring wiring for the fast and Postgres Cucumber contexts. Production application handlers remain unchanged and the social-content fake is not `@Primary`.
- RED: `TestProfileIsolationConfigurationTest.unqualified social account repository wiring exposes the current ambiguity()` creates two unqualified `SocialAccountRepository` beans and records the wrapped `NoUniqueBeanDefinitionException` from Spring's `UnsatisfiedDependencyException`.
- GREEN: `SocialContentBddTestConfiguration` names the fake `socialContentAccountRepository`, adds a test-only `@Primary` alias that returns the existing `R2dbcSocialAccountRepository` for legacy unqualified consumers, and explicitly constructs the single social-content sync handler with `@Qualifier("socialContentAccountRepository")`.
- Wiring contract test: `TestProfileIsolationConfigurationTest.legacy handlers use r2dbc while social content handlers use the bdd fake()` asserts identity for legacy R2DBC, the qualified fake, and the actual `SocialContentSyncCommandHandler` dependency.
- Both `CucumberSpringConfiguration` and `CucumberPostgresSpringConfiguration` import `SocialContentBddTestConfiguration`.

## Explicit wiring verification

- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --no-daemon --console=plain --tests 'com.profiletailors.smp.integration.TestProfileIsolationConfigurationTest' --max-workers=1` — PASS; 4 tests, 0 failures/errors.
- `SMP_DB_TEST_PASSWORD=test just backend-bdd-fast` — PASS; 189 scenarios, 0 failures/errors.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:bddPostgresTest --no-daemon --console=plain --max-workers=1` — PASS; `BUILD SUCCESSFUL`.
- `SMP_DB_TEST_PASSWORD=test just backend-test-fast` — PASS; `BUILD SUCCESSFUL` after the test result binary was refreshed.
- `SMP_DB_TEST_PASSWORD=test just backend-lint` — PASS; `BUILD SUCCESSFUL`.
- `git diff --check` — PASS; no output.

## Spotless formatting pass

- Scope: PR2 Kotlin files in the existing `feature/linkedin-pages-02-sync-calendar` worktree only; no legacy BDD scenarios were changed.
- Baseline: `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:spotlessKotlinCheck --no-daemon --console=plain` failed with three violations shown explicitly and 20 additional violations reported by Spotless, all within the PR2 social-content diff.
- Formatter: ran the repository task `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:spotlessKotlinApply --no-daemon --console=plain` successfully. The resulting changes are ktlint formatting/import ordering/line wrapping only; no suppressions or semantic edits were added.
- RED→GREEN: reran the exact `spotlessKotlinCheck` command successfully after the official formatter.
- Additional verification: `SMP_DB_TEST_PASSWORD=test just backend-lint` passed (`:server:smp:detekt BUILD SUCCESSFUL`); `git diff --check` passed with no output.
- No task checkbox was changed: this pass addressed formatting/lint only, and the five legacy BDD failures remain out of scope.
