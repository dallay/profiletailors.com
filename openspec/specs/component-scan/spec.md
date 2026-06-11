# Component Scan Specification

## Purpose

Define the Spring component-scan contract for `server/smp` and the shared
`com.profiletailors.common.domain.Service` marker, so the context reliably discovers every
stereotype (`@Component`, `@Repository`, custom-`@Service`, `@RestControllerAdvice`, etc.) without
an explicit `includeFilters`, and so regressions fail the build before reaching production CI.

## Path references

For brevity, paths below are shortened to the segment under the package root
(e.g. `.../publishing/infrastructure/credentials/CredentialEncryptionService.kt`). Full paths
resolve under the monorepo root.

## Requirements

### Requirement: Custom Service Annotation Contract

The custom `com.profiletailors.common.domain.Service` SHALL be a discoverable Spring stereotype
(via meta-annotation) and SHALL remain a usable semantic project marker.

#### Scenario: Custom `@Service` is auto-discovered by Spring's default filter

- GIVEN a class in `com.profiletailors.smp.*` is annotated with the custom `Service` (e.g.
  `WorkspaceAuthorizationService` in `.../authorization/application/`)
- WHEN the application context is refreshed
- THEN that class SHALL be registered as a Spring bean
- AND no explicit `includeFilters` in `SmpApplication` SHALL be required to discover it.

#### Scenario: Custom `@Service` carries the required meta-annotations

- GIVEN `shared/common/.../com/profiletailors/common/domain/Service.kt`
- WHEN the source is inspected
- THEN the annotation SHALL be meta-annotated with
  `@org.springframework.stereotype.Component`,
  `kotlin.annotation.Retention(AnnotationRetention.RUNTIME)`, and
  `kotlin.annotation.Target(AnnotationTarget.CLASS)`.

#### Scenario: Existing custom-`@Service` handlers continue to function identically

- GIVEN classes already annotated with the custom `Service` exist before the change
- WHEN `@Component` is meta-annotated and the `SmpApplication` `includeFilters` is removed
- THEN every previously-registered custom-`@Service` bean SHALL still appear exactly once
- AND the only observable change SHALL be that previously-skipped
  `@Component`/`@Repository` classes become discoverable.

### Requirement: SmpApplication Scan Filter

`SmpApplication` SHALL rely on Spring's default scan, SHALL NOT declare `includeFilters`, and
SHALL keep a documented `excludeFilters` block.

#### Scenario: SmpApplication has no `includeFilters` and retains a documented `excludeFilters`

- GIVEN `server/smp/.../SmpApplication.kt`
- WHEN the source is inspected
- THEN the `@ComponentScan` declaration SHALL NOT contain an `includeFilters` attribute
- AND SHALL retain an `excludeFilters` block matching
  `com\.profiletailors\.smp\.integration\..*` and `com\.profiletailors\.smp\.bdd\..*`
- AND a KDoc SHALL explain the purpose (guard against test classes in main).

#### Scenario: All standard Spring stereotypes are discoverable under `com.profiletailors.smp`

- GIVEN `SmpApplication` boots
- WHEN the context is refreshed
- THEN every class annotated with `@Component`, `@Repository`, Spring-`@Service`,
  `@Controller`, `@RestController`, or `@RestControllerAdvice` under `com.profiletailors.smp`
  SHALL be discoverable as a Spring bean.

### Requirement: RateLimitConfiguration Preventive Fix

`RateLimitConfiguration` SHALL NOT declare a nested `includeFilters`; it SHALL remain a valid
`@Configuration` with `@EnableConfigurationProperties(RateLimitProperties::class)`.

#### Scenario: RateLimitConfiguration has no nested `includeFilters` and keeps the config annotations

- GIVEN
  `shared/shield/ratelimit/.../RateLimitConfiguration.kt`
- WHEN the source is inspected
- THEN `@ComponentScan` on `RateLimitConfiguration` SHALL NOT contain an `includeFilters`
- AND the class SHALL be annotated with `@Configuration` and
  `@EnableConfigurationProperties(RateLimitProperties::class)`.

### Requirement: CredentialEncryptionService Normalization

`CredentialEncryptionService` SHALL use the custom `Service` marker instead of Spring's
`@Service` and SHALL remain discoverable via the default scan.

#### Scenario: CredentialEncryptionService uses the custom marker and stays injectable

- GIVEN `.../publishing/infrastructure/credentials/CredentialEncryptionService.kt`
- WHEN the source is inspected
- THEN the file SHALL import `com.profiletailors.common.domain.Service` and SHALL NOT import
  `org.springframework.stereotype.Service`
- AND the class SHALL be annotated with the custom marker
- AND on context refresh, `CredentialEncryptionService` SHALL be injectable by type into
  `R2dbcLinkedInCredentialGateway`.

### Requirement: PublishingCredentialsProperties Registration

`PublishingCredentialsProperties` SHALL be registered via
`@EnableConfigurationProperties(PublishingCredentialsProperties::class)` on a co-located
`@Configuration` named `PublishingCredentialsConfiguration`, and SHALL NOT carry `@Component`.

#### Scenario: PublishingCredentialsConfiguration registers the properties and carries a KDoc

- GIVEN the new file
  `.../publishing/infrastructure/credentials/PublishingCredentialsConfiguration.kt` exists
- WHEN the source is inspected
- THEN the class SHALL be annotated with `@Configuration` and
  `@EnableConfigurationProperties(PublishingCredentialsProperties::class)`
- AND SHALL carry a KDoc explaining that the file co-locates `@EnableConfigurationProperties`
  with the `publishing/credentials` bounded context and intentionally declares no `@Bean` methods
  today.

#### Scenario: PublishingCredentialsProperties has no `@Component` and keeps its prefix

- GIVEN `.../publishing/infrastructure/credentials/PublishingCredentialsProperties.kt`
- WHEN the source is inspected
- THEN the class SHALL NOT be annotated with `@org.springframework.stereotype.Component`
- AND SHALL still be annotated with
  `@ConfigurationProperties(prefix = "publishing.credentials.encryption")`.

### Requirement: Detekt / Static-Analysis Guard Rail

A build-time check SHALL fail `./gradlew :server:smp:check` if any class under the smp
application layer is annotated with Spring's `@Component` instead of the custom marker, and
SHOULD fail if such a class is annotated with Spring's `@Repository`.

#### Scenario: Build fails on Spring `@Component` in the smp application layer

- GIVEN the rule is wired into `./gradlew :server:smp:check`
- AND a class under
  `server/smp/src/main/kotlin/com/profiletailors/smp/.../application/...` is annotated with
  `org.springframework.stereotype.Component`
- WHEN `./gradlew :server:smp:check` is executed
- THEN the build SHALL fail with a message identifying the offending class.

#### Scenario: Build SHOULD fail on Spring `@Repository` in the smp application layer

- GIVEN the rule is wired into `./gradlew :server:smp:check`
- AND a class under
  `server/smp/src/main/kotlin/com/profiletailors/smp/.../application/...` is annotated with
  `org.springframework.stereotype.Repository`
- WHEN `./gradlew :server:smp:check` is executed
- THEN the build SHOULD fail (advisory; the project MAY legitimately need `@Repository` in the
  application layer in the future).

#### Scenario: The check runs as part of `./gradlew :server:smp:check`

- GIVEN the detekt (or equivalent) Gradle task is configured
- WHEN `./gradlew :server:smp:check` is executed
- THEN the rule SHALL run as part of that aggregate task
- AND a clean smp application layer SHALL complete without surfacing any rule violation.

### Requirement: Regression Test in SmpApplicationTests

`SmpApplicationTests` SHALL continue to assert the context loads, and SHALL add explicit
bean-presence assertions for each Spring stereotype the project uses.

#### Scenario: `SmpApplicationTests.contextLoads()` continues to pass

- GIVEN the existing test `contextLoads()` in
  `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt`
- WHEN `./gradlew :server:smp:test` is executed
- THEN the test SHALL pass without modification of its assertion body
- AND SHALL preserve its `@SpringBootTest` configuration (R2DBC, Liquibase, management endpoints).

#### Scenario: New test asserts one bean per stereotype is present

- GIVEN a new test (e.g. `loadsAllExpectedBeanStereotypes()`) is added to `SmpApplicationTests`
- WHEN the test runs
- THEN the context SHALL contain at least one bean for each of: custom-`@Service`
  (`WorkspaceAuthorizationService` in `.../authorization/application/`); `@Component`
  (`R2dbcLinkedInCredentialGateway` in `.../publishing/infrastructure/credentials/LinkedInCredentialGateway.kt`);
  `@Repository` (`R2dbcApiKeyCredentialReplacementGateway` in `.../credentials/infrastructure/R2dbcApiKeyCredentialReplacementGateway.kt`);
  `@RestControllerAdvice` (`AuthorizationProblemDetailsHandler` in `.../authorization/infrastructure/http/AuthorizationProblemDetailsHandler.kt`).

### Requirement: Event Consumer Double-Registration Guard

After `EventConfiguration` is loaded, every bean of type `EventConsumer` SHALL be registered
exactly once. A new test SHALL assert this uniqueness.

#### Scenario: No `EventConsumer` is registered more than once after `EventConfiguration` is loaded

- GIVEN the smp context is booted and
  `com.profiletailors.spring.boot.bus.event.EventConfiguration` is loaded
- AND the consumer interface is
  `com.profiletailors.common.domain.bus.event.EventConsumer<E : DomainEvent>` (declared in
  `shared/bus/.../com/profiletailors/common/domain/bus/event/EventConsumer.kt`)
- WHEN `applicationContext.getBeansOfType(EventConsumer::class.java)` is called
- THEN no two bean names SHALL map to the same consumer instance (reference equality)
- AND no consumer class SHALL appear under more than one bean name.

#### Scenario: New test asserts `EventConsumer` registration uniqueness

- GIVEN a new test (e.g. `eventConsumersAreRegisteredUniquely()`) is added to
  `SmpApplicationTests`
- WHEN the test runs
- THEN it SHALL call `applicationContext.getBeansOfType(EventConsumer::class.java)`
- AND SHALL group entries by `value.javaClass` and assert each group has exactly one bean name
- AND SHALL assert no two distinct bean names map to the same consumer instance (by reference).

### Requirement: Latent Issues Discovered During Apply (open sub-tasks)

If `./gradlew :server:smp:test` surfaces new context-failures during the apply phase caused by
previously-inactive `@Configuration` classes becoming active, those failures SHALL be captured
as sub-tasks and addressed in the same change.

#### Scenario: New context-failures are captured as sub-tasks

- GIVEN `./gradlew :server:smp:test` is executed in the apply phase
- AND a previously-inactive `@Configuration` (e.g. `EventConfiguration`,
  `LinkedInPublishingAdapters`, `IdentityBootstrapConfiguration`) becomes active and causes a
  context-failure that did not exist before
- WHEN the failure is observed
- THEN the failure SHALL be captured as an additional sub-task under
  "Open Sub-Tasks Discovered During Apply" in the proposal
- AND the sub-task SHALL be addressed in the same change (not deferred).

#### Scenario: Each latent-issue sub-task carries the required metadata

- GIVEN a new latent-issue sub-task is added
- WHEN the sub-task is documented
- THEN it MUST include all four pieces of metadata: (1) the bean or configuration that caused
  the failure (fully qualified class name), (2) the verbatim error message, (3) the chosen fix
  (one-line description), (4) the test that proves the fix works (test method name and the
  assertion that would have failed before and now passes).
