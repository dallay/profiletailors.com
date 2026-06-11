# Proposal: Fix Restrictive Component Scan

## Intent

`server/smp` fails to start in production CI with a `BeanDefinitionStoreException` on
`ReplaceApiKeyCredentialHandler` because `SmpApplication` declares an explicit `includeFilters` on
`@ComponentScan`, which suppresses Spring's default filters. The custom filter only recognizes the
project marker `com.profiletailors.common.domain.Service` and `@RestControllerAdvice`, so every
`@Component` and `@Repository` bean in the `com.profiletailors.smp` package is silently skipped.
This breaks the entire R2DBC persistence layer (17 `@Repository` classes), four `@Component`
gateways/handlers, and the publishing `CredentialEncryptionService`. The bug was introduced in
PR #39 (LinkedIn publishing MVP) when the restrictive scan was added; the user need is to make
`./gradlew :server:smp:test` pass and restore a working application context with no behavioural
regressions beyond enabling the beans the project already declares.

## Scope

### In Scope

- Meta-annotate `com.profiletailors.common.domain.Service` with `@Component` so Spring's default
  filter picks it up while keeping the custom marker as a semantic project convention.
- Drop the `includeFilters` block in `SmpApplication.kt`; keep `excludeFilters` with a KDoc
  explaining it guards against test classes being moved into `main`.
- Apply the same preventive fix to `RateLimitConfiguration` (remove its nested `includeFilters`).
- Normalize Spring's `@Service` to the custom `@Service` in
  `publishing/infrastructure/credentials/CredentialEncryptionService.kt`.
- Remove redundant `@Component` from `PublishingCredentialsProperties.kt` and register it via
  `@EnableConfigurationProperties` on a co-located `@Configuration`.
- Add a regression test in `SmpApplicationTests` asserting beans of each stereotype are present;
  keep the existing `contextLoads()`.
- Run `./gradlew :server:smp:test` after the change; capture any new failures as sub-tasks.
- Address latent issues surfaced by previously-inactive `@Configuration` classes becoming active
  (e.g. `EventConfiguration`, `LinkedInPublishingAdapters`, `IdentityBootstrapConfiguration`), as
  discovered during `./gradlew :server:smp:test` in the apply phase.
- Add a `detekt` (or grep-based CI) rule that fails the build if any
  `server/smp/src/main/kotlin/.../application/...` class is annotated with Spring's `@Component`
  or `@Repository` instead of the custom marker. This is a regression guard rail that complements
  the test-based check in `SmpApplicationTests`.

### Out of Scope

- Split package `shared/bus` vs `shared/common` under `com.profiletailors.common.domain.bus`.
- Re-enabling the disabled `ModularStructureTest` and `ModularityVerificationTest`.
- Broadening `scanBasePackages` to `com.profiletailors.*` (Option B).
- Shared-module scan coverage (`shared/spring-boot-common`, `shared/storage`,
  `shared/shield/ratelimit`).
- Any new behavioural feature or API change.

## Approach

Adopt **Option C** from exploration: meta-annotate the custom `@Service` with `@Component` and
remove the `includeFilters` block. This is the smallest semantic change that both fixes the bug
and preserves the team's apparent intent of using a custom marker for use-case handlers. After
the meta-annotation, Spring's default filter discovers the custom `@Service` automatically
alongside `@Repository`, `@Component`, `@Controller`, and `@RestControllerAdvice`, so no
`includeFilters` is needed. The custom annotation stays as a hexagonal "domain application
service" marker for IDE/static-analysis purposes, but it is no longer the only path to bean
registration. The exclude regex is retained as documented defense against accidentally moving
test-only classes into the main source set. The same fix is applied preventively to
`RateLimitConfiguration` so the anti-pattern cannot reappear there. The single Spring-`@Service`
use in `server/smp` is normalized to the custom marker for consistency. The redundant
`@Component` on `PublishingCredentialsProperties` is replaced with explicit
`@EnableConfigurationProperties` registration, which is the idiomatic Spring Boot pattern.

### Shared-module bean activation state (out of scope, documented for context)

The `shared/*` modules carry a mix of bean activation paths that this change does **not** alter.
`shared/spring-boot-common` is wired in through Spring Boot's `@AutoConfiguration` mechanism via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, which today
lists only `AppAutoConfiguration`. `EventConfiguration`, `LinkedInPublishingAdapters`, and
`IdentityBootstrapConfiguration` are therefore inactive in the smp context until they are added to
that imports file or otherwise explicitly imported. `shared/storage` and `shared/shield/ratelimit`
are intended to follow the same `@AutoConfiguration` path once the smp module declares a
dependency on them. This change does not modify the shared-module scan strategy; it only restores
symmetry within `com.profiletailors.smp` by removing the restrictive `includeFilters` and
meta-annotating the custom `@Service` so that smp's own `@Component` and `@Repository` classes
stop being silently skipped.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt` | Modified | Add `@Component` meta-annotation; KDoc clarifies dual role. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` | Modified | Drop `includeFilters`; keep `excludeFilters` with KDoc. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitConfiguration.kt` | Modified | Remove nested `includeFilters`; keep `@Configuration` and `@EnableConfigurationProperties`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/CredentialEncryptionService.kt` | Modified | Switch import from `org.springframework.stereotype.Service` to the custom marker. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsProperties.kt` | Modified | Remove `@Component`; co-locate `@EnableConfigurationProperties` on a new `PublishingCredentialsConfiguration.kt`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsConfiguration.kt` | New | Co-located `@Configuration` carrying `@EnableConfigurationProperties(PublishingCredentialsProperties::class)` and a KDoc explaining its architectural role. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt` | Modified | Keep `contextLoads()`; add bean-presence assertions per stereotype and an event-consumer uniqueness assertion. |
| `detekt.yml` (or equivalent Gradle hook) | Modified | Add a custom rule that fails the build if any `server/smp/src/main/kotlin/.../application/...` class is annotated with Spring's `@Component` or `@Repository` instead of the custom marker. |
| `openspec/changes/fix-restrictive-component-scan/` | New | Proposal, specs, design, tasks, verify-report for this change. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Meta-annotated `@Service` is auto-discovered by Spring's default filter outside the restrictive scan, double-registering beans | Low | The original filter only matched `Service::class`; meta-annotating means the same classes are still registered once, just by a different path. Verified by `contextLoads()`. |
| Previously-inactive beans (e.g. from `EventConfiguration`, `LinkedInPublishingAdapters`, `IdentityBootstrapConfiguration`) cause new context-failures | Medium | Run `./gradlew :server:smp:test` after the change; add any fixing sub-tasks to the proposal's "Open Sub-Tasks Discovered During Apply" section. |
| Kotlin `internal class` visibility causes Spring reflection issues | Low | Existing test `WorkspaceAccessSummaryEndpointTestBase` already injects `internal` beans, proving the pattern works. |
| Removing the include filter changes which `@RestControllerAdvice` beans are picked up | Low | `@RestControllerAdvice` is on the default-filter list; behaviour is identical to the explicit filter. |
| Shared modules (`shared/spring-boot-common`, `shared/storage`, `shared/shield/ratelimit`) are not in `scanBasePackages` and remain inactive | Medium | Documented as out of scope; flagged in Future Work. |
| `RateLimitConfiguration` fix is preventive only — the module is not yet scanned into the smp context | Low | The change is small and reduces the chance of recurrence; no behavioural impact today. |
| `@EnableConfigurationProperties` for `PublishingCredentialsProperties` collides with a manual `@Bean` declaration elsewhere | Low | Properties were only injectable today via the redundant `@Component`; explicit registration is the documented Spring Boot pattern. |
| Regression test in `SmpApplicationTests` is not strong enough to catch future scan regressions | Low | Test asserts at least one bean of each stereotype is present, including a custom-`@Service` handler, a `@Component` gateway, a `@Repository` port, and a `@RestControllerAdvice`. |
| Modulith event subscription (currently inactive) starts wiring consumers and reveals double-publishers | Medium | Bus tests cover the wiring; the design phase should confirm there are no ad-hoc consumer constructions. |

## Rollback Plan

Revert each file change in a single commit:

1. Remove `@Component` from `com.profiletailors.common.domain.Service`.
2. Restore the `includeFilters = [ComponentScan.Filter(ANNOTATION, classes = [Service::class, RestControllerAdvice::class])]` block in `SmpApplication.kt`.
3. Restore the `includeFilters = [ComponentScan.Filter(ANNOTATION, classes = [Service::class])]` block in `RateLimitConfiguration.kt`.
4. Switch `CredentialEncryptionService` back to `org.springframework.stereotype.Service`.
5. Re-add `@Component` to `PublishingCredentialsProperties` and remove the `@EnableConfigurationProperties` registration.

The reverted state matches the pre-change behaviour exactly. Because the only persistent
behaviour change is the addition of new beans to the context (no schema changes, no API
changes, no configuration changes), rollback is safe at any point. If the regression test was
added in this change, it is also reverted. No database migration is involved.

## Future Work

- Resolve the split package between `shared/bus` and `shared/common` under
  `com.profiletailors.common.domain.bus` (eventual class-loading ambiguity under reflection-heavy
  libraries).
- Re-enable `ModularStructureTest` and `ModularityVerificationTest` once the modulith boundary
  violations noted in the `@Disabled` reasons are resolved.
- Broaden `scanBasePackages` to `com.profiletailors.*` (Option B) if the team later wants
  `shared/spring-boot-common`, `shared/storage`, and `shared/shield/ratelimit` scanned through
  the main app rather than via `@AutoConfiguration` / META-INF imports.

## Success Criteria

- [ ] `./gradlew :server:smp:test` passes, including `SmpApplicationTests.contextLoads()`.
- [ ] The new regression test asserts that at least one bean of each stereotype is present in
  the context: a custom-`@Service` handler, a `@Component` gateway, a `@Repository` persistence
  port, and a `@RestControllerAdvice`.
- [ ] The custom `com.profiletailors.common.domain.Service` marker is meta-annotated with
  `@Component` and remains usable as a project-style semantic marker.
- [ ] `SmpApplication.kt` no longer declares an `includeFilters` block; the `excludeFilters` is
  preserved with a KDoc explaining its purpose.
- [ ] `RateLimitConfiguration.kt` no longer declares a nested `includeFilters` block.
- [ ] `CredentialEncryptionService` uses the custom `@Service` marker, matching the rest of the
  codebase.
- [ ] `PublishingCredentialsProperties` is registered via `@EnableConfigurationProperties` on a
  co-located `PublishingCredentialsConfiguration` class (carrying a KDoc) and no longer carries a
  redundant `@Component`.
- [ ] A new `eventConsumersAreRegisteredUniquely()` regression test asserts that after
  `EventConfiguration` is loaded, every bean of type `EventConsumer` is registered exactly once in
  the application context.
- [ ] A `detekt` (or equivalent build-time) rule is wired into `./gradlew :server:smp:check` and
  fails the build if any class under
  `server/smp/src/main/kotlin/com/profiletailors/smp/.../application/...` is annotated with Spring's
  `@Component` or `@Repository` instead of the custom marker.

## Open Sub-Tasks Discovered During Apply

### Sub-task 1: Migrate application-layer `@Component` classes to the custom `@Service` marker (caught by `ComponentScanArchTest.applicationLayerShouldNotUseSpringComponent`)

- **Bean/Config (FQCN)**:
  - `com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialHandler`
  - `com.profiletailors.smp.credentials.application.SecureRandomApiKeyCredentialValueFactory`
  - `com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsHandler`
- **Error message (verbatim)**:
  `Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in a package '..application..' should be annotated with @Component ...' was violated (3 times): Class <com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialHandler> is annotated with @Component in (ReplaceApiKeyCredentialInternal.kt:0) ...`
- **Fix (one-line)**: Replace `import org.springframework.stereotype.Component` and `@Component` with `import com.profiletailors.common.domain.Service` and `@Service` on the three handler/factory classes; they are use-case handlers and the smp application layer must use the project marker.
- **Test that proves the fix (file:method + assertion)**: `ComponentScanArchTest.applicationLayerShouldNotUseSpringComponent()` — `ArchRuleDefinition.noClasses().that().resideInAPackage("..application..").should().beAnnotatedWith(org.springframework.stereotype.Component::class.java).check(importedClasses)` passes after the migration.

### Sub-task 1.1: Sub-sub-task for ReplaceApiKeyCredentialInternal.kt

- **Bean/Config (FQCN)**: `com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialHandler` and `com.profiletailors.smp.credentials.application.SecureRandomApiKeyCredentialValueFactory`
- **Error message (verbatim)**: same ArchUnit violation, two classes.
- **Fix (one-line)**: swap `org.springframework.stereotype.Component` import and `@Component` annotations for the custom marker.
- **Test that proves the fix**: `ComponentScanArchTest.applicationLayerShouldNotUseSpringComponent()` re-runs and passes; `SmpApplicationTests.contextLoads()` continues to pass (proves the meta-annotated `@Service` is discoverable).

### Sub-task 1.2: Sub-sub-task for GetWorkspaceAuditEventsHandler.kt

- **Bean/Config (FQCN)**: `com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsHandler`
- **Error message (verbatim)**: same ArchUnit violation, one class.
- **Fix (one-line)**: swap `org.springframework.stereotype.Component` import and `@Component` annotation for the custom marker.
- **Test that proves the fix**: `ComponentScanArchTest.applicationLayerShouldNotUseSpringComponent()` re-runs and passes; `SmpApplicationTests.contextLoads()` continues to pass.

### Sub-task 2: `infrastructureConfigShouldNotDeclareNestedIncludeFilters` ArchUnit rule fired `allowEmptyShould`

- **Bean/Config (FQCN)**: the rule itself (no class is the offender; the test fixture is).
- **Error message (verbatim)**: `Rule 'no classes that reside in a package '..infrastructure.config..' should be annotated with @ComponentScan ...' failed to check any classes. This means either that no classes have been passed to the rule at all, or that no classes passed to the rule matched the that() clause. To allow rules being evaluated without checking any classes you can either use ArchRule.allowEmptyShould(true) on a single rule or set the configuration property archRule.failOnEmptyShould = false to change the behavior globally.`
- **Fix (one-line)**: chain `.allowEmptyShould(true)` on both `ArchRuleDefinition` chains so the rule passes when the codebase has no `infrastructure.config.*` class with `@ComponentScan` (the desired steady state).
- **Test that proves the fix**: `ComponentScanArchTest.infrastructureConfigShouldNotDeclareNestedIncludeFilters()` passes; manually introducing a nested `@ComponentScan` with a non-empty `includeFilters` would still fail the second rule.
