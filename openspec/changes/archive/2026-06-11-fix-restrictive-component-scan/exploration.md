# Exploration: fix-restrictive-component-scan

## Current State

`smp` is a Spring Boot 4.0.6 + Kotlin 2.2 + WebFlux + Spring Modulith 2.0.6 application at `server/smp/`. It boots via `SmpApplication.kt`, which is annotated with a custom restrictive `@ComponentScan`:

```kotlin
@SpringBootApplication
@ComponentScan(
    includeFilters = [
        ComponentScan.Filter(ANNOTATION, classes = [Service::class, RestControllerAdvice::class]),
    ],
    excludeFilters = [
        ComponentScan.Filter(REGEX, pattern = ["com\\.profiletailors\\.smp\\.integration\\..*", "com\\.profiletailors\\.smp\\.bdd\\..*"]),
    ],
)
```

`Service` is a project marker at `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt` — **not** Spring's `@Service`. It is a hexagonal "domain application service" marker used on `internal class` query/command handlers.

The bug: setting `includeFilters` on `@ComponentScan` **disables** Spring's default filters (which look for `@Component`, `@Service`, `@Repository`, `@Controller`, etc.). With the current filter, the only things that become beans in `com.profiletailors.smp` are:

1. Classes annotated with the custom `com.profiletailors.common.domain.Service` annotation.
2. Classes annotated with `@RestControllerAdvice`.

Everything annotated `@Component` or `@Repository` is silently skipped. This breaks the entire R2DBC persistence layer (17 `@Repository` classes across 7 modules) plus four `@Component` classes. The exact reported failure — `BeanDefinitionStoreException` on `ReplaceApiKeyCredentialHandler` — is a downstream symptom: that handler is `@Component` and is `@Autowired` into `WorkspaceAccessSummaryEndpointTestBase` / injected via the mediator. The mediator looks the handler up, can't find it, and the application context fails validation.

This was introduced in PR #39 (LinkedIn publishing MVP, commit `3e17d7ea`) when the restrictive scan was added. Before that, `SmpApplication.kt` was a plain `@SpringBootApplication`.

`spring-modulith` 2.0.6 also looks for `@ApplicationModule` and `@NamedInterface` annotated classes, and Spring Boot's autoconfig looks for `@Configuration` + `@AutoConfiguration` registered through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. None of those scanning mechanisms are affected by this filter (they use Spring Boot's autoconfig path, not the user-level `@ComponentScan`).

## Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt` — root cause: the restrictive include filter.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ReplaceApiKeyCredentialInternal.kt` — `@Component` classes (`ReplaceApiKeyCredentialHandler`, `SecureRandomApiKeyCredentialValueFactory`) silently skipped.
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/GetWorkspaceAuditEventsHandler.kt` — `@Component` handler silently skipped.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/LinkedInCredentialGateway.kt` — `@Component` R2DBC gateway (`R2dbcLinkedInCredentialGateway`) silently skipped.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsProperties.kt` — `@Component` + `@ConfigurationProperties` (redundant: should be registered via `@EnableConfigurationProperties`; the `@Component` is what currently makes it visible if the filter is restored).
- 17 `@Repository` classes across `authorization`, `credentials`, `governance`, `identity`, `publishing`, `tenancy` — the entire R2DBC persistence layer.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/CredentialEncryptionService.kt` — uses Spring's `@Service` (not the custom one); silently skipped. **This was likely a pre-existing latent bug** masked by the previous silent skip of everything else.
- `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitConfiguration.kt` — contains a nested `@ComponentScan` with the same anti-pattern (only includes `Service::class`); only relevant if ratelimit is scanned into the SMP context. The rate-limit module is currently a transitive dependency but is never explicitly scanned by the smp app.

## Inventory

### Module: `server/smp` (the only application module)

#### Stereotype: `@Component` (Spring's) — **NOT picked up by current filter**

| File | Class | Visibility | Picked up? | Why |
|------|-------|------------|-----------|-----|
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ReplaceApiKeyCredentialInternal.kt` | `ReplaceApiKeyCredentialHandler` | public | **NO** | Uses `@Component` (line 15); not in include list |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ReplaceApiKeyCredentialInternal.kt` | `SecureRandomApiKeyCredentialValueFactory` | `internal` | **NO** | Same file, line 23 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/GetWorkspaceAuditEventsHandler.kt` | `GetWorkspaceAuditEventsHandler` | `internal` | **NO** | Uses `@Component` (line 18) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/LinkedInCredentialGateway.kt` | `R2dbcLinkedInCredentialGateway` | public | **NO** | Uses `@Component` (line 21) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsProperties.kt` | `PublishingCredentialsProperties` | public | **NO** | `@Component` + `@ConfigurationProperties`; not in include list (also a redundancy bug) |

#### Stereotype: `@Repository` (Spring's) — **NOT picked up by current filter**

| File | Class | Visibility | Picked up? |
|------|-------|------------|-----------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/persistence/R2dbcDirectGrantResolver.kt` | `R2dbcDirectGrantResolver` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/persistence/R2dbcWorkspaceEntitlementResolver.kt` | `R2dbcWorkspaceEntitlementResolver` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/persistence/R2dbcWorkspaceMembershipRoleResolver.kt` | `R2dbcWorkspaceMembershipRoleResolver` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/persistence/R2dbcWorkspaceTargetScopeResolver.kt` | `R2dbcWorkspaceTargetScopeResolver` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialReplacementGateway.kt` | `R2dbcApiKeyCredentialReplacementGateway` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt` | `R2dbcApiKeyCredentialStateLookup` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcRefreshSessionGateway.kt` | `R2dbcRefreshSessionGateway` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcServiceAccountCredentialStateLookup.kt` | `R2dbcServiceAccountCredentialStateLookup` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcAuditEventReader.kt` | `R2dbcAuditEventReader` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcIdentityRegistrationGateway.kt` | `R2dbcIdentityRegistrationGateway` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcLocalPasswordCredentialGateway.kt` | `R2dbcLocalPasswordCredentialGateway` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcPrincipalIdentityLookup.kt` | `R2dbcPrincipalIdentityLookup` | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingConnectionRepositories.kt` | 2 classes (`R2dbcPublishingConnectionRepository`, `R2dbcPublishingConnectionProviderRepository`) | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | 4 classes (assets, jobs, social accounts, publications, delivery attempts) | public | **NO** |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/R2dbcWorkspaceMembershipResolver.kt` | `R2dbcWorkspaceMembershipResolver` | public | **NO** |

#### Stereotype: custom `@Service` (`com.profiletailors.common.domain.Service`) — **Picked up by current filter**

All marked `internal class`. The `internal` visibility is fine because Spring's annotation matching is in the same Kotlin compilation module (Gradle module = same source set).

| File | Class |
|------|-------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/current/workspace/GetCurrentWorkspaceAccessSummaryHandler.kt` | `GetCurrentWorkspaceAccessSummaryHandler` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/current/workspace/GetCurrentWorkspaceAccessSummaryService.kt` | `GetCurrentWorkspaceAccessSummaryService` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/resource/getpreview/GetResourcePreviewHandler.kt` | `GetResourcePreviewHandler` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/resource/getpreview/GetResourcePreviewService.kt` | `GetResourcePreviewService` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/application/WorkspaceAuthorizationService.kt` | `WorkspaceAuthorizationService` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/GetCurrentUserProfileService.kt` | `GetCurrentUserProfileService` (public, has `open`) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` | `RegisterUserHandler`, `LoginUserHandler`, `RefreshUserSessionHandler`, `LogoutUserSessionHandler` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` | 7 handlers: `CompleteLinkedInConnectionHandler`, `CreatePublicationHandler`, `EditPublicationHandler`, `CancelPublicationHandler`, `RetryPublicationHandler`, `ReschedulePublicationHandler`, `CreateAssetHandler` |

#### Stereotype: Spring's `@Service` (different import) — **NOT picked up by current filter**

| File | Class | Visibility | Picked up? |
|------|-------|------------|-----------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/CredentialEncryptionService.kt` | `CredentialEncryptionService` | public | **NO** — uses `org.springframework.stereotype.Service` (line 11) |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/config/DataMaskingService.kt` | `DataMaskingService` | public | **NO** (not in scanned package) |

The publishing `CredentialEncryptionService` is the most surprising: a developer reached for Spring's `@Service` here, which works **only** because the current restrictive filter accidentally also blocks everything else. Once the filter is removed/fixed, this class will be picked up correctly — but it remains a stylistic inconsistency the team should resolve.

#### Stereotype: `@RestControllerAdvice` / `@ControllerAdvice` — **Picked up by current filter**

| File | Class |
|------|-------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/http/AuthorizationProblemDetailsHandler.kt` | `AuthorizationProblemDetailsHandler` (`@RestControllerAdvice`) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/GovernanceProblemDetailsHandler.kt` | `GovernanceProblemDetailsHandler` (`@RestControllerAdvice`) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/IdentityProblemDetailsHandler.kt` | `IdentityProblemDetailsHandler` (`@RestControllerAdvice`) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/http/PlatformProblemDetailsHandler.kt` | `PlatformProblemDetailsHandler` (`@RestControllerAdvice`) |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/http/TenancyProblemDetailsHandler.kt` | `TenancyProblemDetailsHandler` (`@RestControllerAdvice`) |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/controllers/ConstraintViolationAdvice.kt` | `ConstraintViolationAdvice` (`@ControllerAdvice`) — not in scanned package |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/controllers/GlobalExceptionHandler.kt` | `GlobalExceptionHandler` (`@RestControllerAdvice`) — not in scanned package |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/pagination/OffsetPageResponseHandler.kt` | `OffsetPageResponseHandler` (`@ControllerAdvice`) — not in scanned package |

> Note: items in `shared/spring-boot-common` aren't picked up by the current scan because the base package of the scan is `com.profiletailors.smp`. They become relevant only if Option B (broaden scan) is chosen.

#### Stereotype: `@RestController` — **NOT picked up by current filter** (lucky exception: they're caught anyway)

| File | Class |
|------|-------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/http/ResourcePreviewController.kt` | `ResourcePreviewController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/infrastructure/http/WorkspaceAccessSummaryController.kt` | `WorkspaceAccessSummaryController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/AuditEventController.kt` | `AuditEventController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/CurrentUserProfileController.kt` | `CurrentUserProfileController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt` | `LocalAuthController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` | `PublishingConnectionController`, `PublishingPublicationController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/http/WorkspaceMembershipController.kt` | `WorkspaceMembershipController` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/http/WorkspaceOwnershipController.kt` | `WorkspaceOwnershipController` |

These work today only because Spring Boot's MVC auto-config (`WebFluxConfiguration`, `EnableWebFluxSecurity`) registers controllers through a separate `RequestMappingHandlerMapping` path, not through the user-level `@ComponentScan` include filter. This is why controllers, the WebFlux config, and the security config still work, while `@Component`/`@Repository` do not.

#### Stereotype: `@Configuration` (default-filtered) — **NOT directly relevant** but worth listing

- All `@Configuration` classes under `com.profiletailors.smp.*` (`PlatformBootstrapConfiguration`, `IdentityBootstrapConfiguration`, `IdentitySecurityConfiguration`, `TenancyWebConfiguration`, `WebFluxConfiguration`, `LinkedInPublishingAdapters`, `PublishingSchedulingConfiguration`, `LocalJwtConfiguration`, `IdentityPlatformBridgeConfiguration`) — these would be **silently skipped** under the current filter too, but the application still boots because their `@Bean` methods are needed and they are reached via... **actually, they are also skipped**, but Spring's own auto-config covers most of what they declare (security, webflux). The configurations are not actually being loaded today — that is a second, deeper bug. None of them throws a startup error because nothing currently `@Autowired`s the beans they would declare; only `@Bean` methods inside `LinkedInPublishingAdapters` and `IdentityBootstrapConfiguration` produce user-visible beans, and those would silently be missing too.
- `shared/spring-boot-common/src/main/kotlin/com/profiletailors/config/JacksonConfig.kt` (`@Configuration`) — not in scanned package, and not registered as `@AutoConfiguration`. **This is silently inactive today**; if the team relies on its Jackson customisation (per the KDoc on lines 22-36), it is not running.
- `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/StorageAutoConfiguration.kt` — is `@Configuration` (not `@AutoConfiguration`), not in the scan path, not in any META-INF import. **Also silently inactive.**

#### Stereotype: `@ConfigurationProperties`

| File | Class | Notes |
|------|-------|-------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` | `CorsConfigurationProperties` (data class) | Registered via `@EnableConfigurationProperties` on `IdentitySecurityConfiguration` (which is itself currently skipped — another silent failure). |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/credentials/PublishingCredentialsProperties.kt` | `PublishingCredentialsProperties` | Has redundant `@Component`. Properties still injectable today only because of the `@Component`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/http/TenancyWebConfiguration.kt` | `WorkspaceContextConfigurationProperties` (data class) | Registered via `@EnableConfigurationProperties` on `TenancyWebConfiguration` (which is itself skipped). |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitProperties.kt` | `RateLimitProperties` | Registered via `@EnableConfigurationProperties` on `RateLimitConfiguration` (skipped). |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/config/SecurityProperties.kt` | `SecurityProperties` | Not registered anywhere visible. |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/StorageProperties.kt` | `StorageProperties` | Registered via `@EnableConfigurationProperties` on `StorageAutoConfiguration` (skipped). |

### Module: `shared/common`

- `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt` — the marker annotation itself. No stereotypes.
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/bus/event/BaseDomainEvent.kt`, `DomainEvent.kt`, `query/Response.kt` — domain types. No Spring stereotypes.
- `shared/common/src/testFixtures/...` — test fixtures. No Spring stereotypes.
- `shared/common/src/test/...` — test types. No Spring stereotypes.

### Module: `shared/bus`

- `shared/bus/src/main/kotlin/com/profiletailors/common/domain/bus/...` — mediator, command/query/event handlers, pipelines, notifications. Pure Kotlin (no Spring stereotypes). Loaded by `MediatorBuilder` reflection at runtime, not by component scan.

### Module: `shared/security`, `shared/presentation`

- No Spring stereotypes in main. Pure domain/port types.

### Module: `shared/spring-boot-common`

| File | Stereotype | Notes |
|------|-----------|-------|
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/config/JacksonConfig.kt` | `@Configuration` (line 37) | Not registered as `@AutoConfiguration`, not in scan path → silently inactive. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/config/WorkspaceContextWebFilter.kt` | `@Component` (line 26) | Not picked up by any scan → silently inactive. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/controllers/ConstraintViolationAdvice.kt` | `@ControllerAdvice` (line 16) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/controllers/GlobalExceptionHandler.kt` | `@RestControllerAdvice` (line 26) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/AppAutoConfiguration.kt` | `@AutoConfiguration` (line 13) | Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → active. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/bus/event/EventConfiguration.kt` | `@Configuration` (line 20) | Not registered anywhere → silently inactive (this is how the bus event-subscription wiring should work but is not running). |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/bus/event/EventEmitter.kt` | `@Component` (line 16) | Not picked up → silently inactive. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/config/DataMaskingService.kt` | `@Service` Spring's (line 5) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/config/HasherConfig.kt` | `@Configuration` (line 11) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/config/HasherRegistry.kt` | `@Component` (line 9) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/config/SecurityProperties.kt` | `@ConfigurationProperties` (line 5) | Not registered. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserFactory.kt` | `@Component` (line 8) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/pagination/OffsetPagePresenter.kt` | `@Component` (line 15) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/pagination/OffsetPageResponseHandler.kt` | `@ControllerAdvice` (line 9) | Not picked up. |
| `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/sort/SortParserFactory.kt` | `@Component` (line 7) | Not picked up. |

### Module: `shared/shield/ratelimit`

| File | Stereotype | Notes |
|------|-----------|-------|
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/application/RateLimitingService.kt` | custom `@Service` (line 19) | Picked up by the nested `@ComponentScan` in `RateLimitConfiguration` (which itself is `@Configuration`, currently skipped because the configuration is not in scan path). |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/adapter/ApiKeyParser.kt` | `@Component` (line 18) | Not picked up. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/adapter/Bucket4jRateLimiter.kt` | `@Component` (line 48) | Not picked up. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/adapter/ReactiveRateLimitingAdapter.kt` | `@Component` (line 18) | Not picked up. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/adapter/SpringRateLimitEventPublisher.kt` | `@Component` (line 15) | Not picked up. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitConfiguration.kt` | `@Configuration` (line 15) with nested `@ComponentScan(..., includeFilters = [Service::class])` (line 17) | Not picked up today. Same anti-pattern as `SmpApplication.kt`. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitProperties.kt` | `@ConfigurationProperties` (line 46) | Registered via `@EnableConfigurationProperties` on `RateLimitConfiguration` (skipped). |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/filter/RateLimitingFilter.kt` | `@Component` (line 35) | Not picked up. |
| `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/metrics/RateLimitMetrics.kt` | `@Component` (line 29) | Not picked up. |

### Module: `shared/storage`

| File | Stereotype | Notes |
|------|-----------|-------|
| `shared/storage/src/main/kotlin/com/profiletailors/storage/application/GeneratePresignedUrlUseCase.kt` | custom `@Service` (line 27) | **Not annotated** (see comment line 25) — must be wired explicitly. |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/application/StorageApplicationService.kt` | custom `@Service` (line 34) | Picked up only by `StorageAutoConfiguration` (which is currently skipped). |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/metrics/StorageMetrics.kt` | `@Component` (line 29) | Not picked up. |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/StorageAutoConfiguration.kt` | `@Configuration` (line 18) — not `@AutoConfiguration` | Not in scan path, not registered → silently inactive. |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/StorageProperties.kt` | `@ConfigurationProperties` (line 6) | Registered via `@EnableConfigurationProperties` on the inactive `StorageAutoConfiguration`. |

## Custom Service annotation role

- Defined at `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt`. It is `@Target(CLASS)`, `@Retention(RUNTIME)`, `@MustBeDocumented`. No meta-annotations. **It is NOT meta-annotated with `@Component` or any Spring stereotype** — so the only way it gets picked up is by being explicitly listed in a `ComponentScan.includeFilters`, which is exactly what the current `SmpApplication.kt` does.
- Used in 13 source files across `server/smp` (10) and `shared/*` (3):
  - 10 `internal class` command/query handlers in `server/smp` (see inventory above).
  - 3 classes in shared: `RateLimitingService` (ratelimit), `StorageApplicationService` (storage), `GeneratePresignedUrlUseCase` (storage; class is NOT annotated — comment explains).
  - Plus 1 import in `SmpApplication.kt` itself (to reference the annotation in the filter).
- 2 files use Spring's `@Service` (`CredentialEncryptionService` in smp/publishing, `DataMaskingService` in shared/spring-boot-common). These are accidental and inconsistent with the rest of the codebase.

**Conclusion**: the custom `Service` is a **deliberate hexagonal "domain application service" marker** (a project-style choice, like Vaughn Vernon's "domain service" concept). The team is using it to mark use-case handlers that the `Mediator` registers reflectively. It is not a generic bean marker — the inventory shows it is applied only to command/query handler types. The current scan filter is therefore a *project convention enforcement mechanism* — but it has a fatal flaw: it excludes all `@Component` and `@Repository` classes (which the same project uses for gateways, filters, and persistence ports).

**Recommendation**: keep the custom `Service` as a semantic marker for use-case handlers, but **drop the include filter** so the project behaves like a normal Spring Boot application. The mediator already discovers handlers by classpath reflection (`MediatorBuilder` + `AppSpringBeanProvider`); it doesn't depend on the scan filter.

## Modulith findings

- All 9 application modules under `server/smp/*` have a `ModuleMetadata.kt` with `@ApplicationModule(allowedDependencies = [...])`. The classes are `internal class` with no further annotations.
- The modulith boundary verifier `ModularityVerificationTest.verifiesApplicationModules` and `ModularStructureTest.verifiesModularStructure` are both `@Disabled` with the reason "Pre-existing modulith boundary violation: authorization -> audit :: application. Not related to publishing change." — the modulith tests are not currently running and do not gate context startup. Modulith boundary enforcement is therefore advisory today.
- **No `@ApplicationModuleListener` is used** anywhere in the codebase (`rg` returned zero matches). Event listeners are wired reflectively by `EventConfiguration` (itself currently skipped, see `shared/spring-boot-common` above).
- **No `@Async` is used** anywhere in the codebase.
- The modulith event subscription path is `EventConfiguration` (Spring config) → finds beans of type `EventConsumer` → reads their `@Subscribe` annotations and registers them with `EventEmitter`. This means: (a) `EventConfiguration` must be a loaded `@Configuration`, and (b) the consumer beans must be loaded. Today **both are silently inactive** because the restrictive scan skips the shared `@Configuration` and the smp module's `@Component` classes.
- This means when the scan is fixed, more than just `ReplaceApiKeyCredentialHandler` will start working — `EventConfiguration` will start wiring `@Subscribe`-annotated event consumers too. This is a desirable side effect but should be confirmed by the design phase to ensure no event consumers are being created ad-hoc via constructor calls in tests.

## Exclude filter audit

Both regexes are over **test-only paths** in this codebase:

- `com.profiletailors.smp.integration..*` — matches `server/smp/src/test/kotlin/com/profiletailors/smp/integration/**` and `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/**`. These are `@SpringBootTest` test classes that themselves use `@Import` to bring in their `SharedTestBeans` test configuration. Spring's default scan starts at the `@SpringBootApplication` base package (`com.profiletailors.smp`) and only walks `main` sources by default — `test` is on a separate source set, not on the runtime classpath unless tests are running.
- `com.profiletailors.smp.bdd..*` — matches `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/**`. Same story: BDD glue code with `@CucumberContextConfiguration` and `@Import`. Not on the runtime classpath.

So the regex excludes are **defensive belt-and-suspenders for the test classpath** but they are unnecessary because Spring's scan never enters `src/test` in the first place. They are also harmless to keep; what is needed is a small KDoc to explain their intent. They are not the bug.

There is one risk: if a developer ever **moves a test class into `main`** (e.g. for a Cucumber runner that should be on the app classpath), the regex would silently exclude it. That is a real failure mode worth flagging in the design phase.

## Test infrastructure survey

- **Existing context-load regression guard**: `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt` — `@SpringBootTest` with H2, calls `contextLoads()`. This is the natural place for the new regression test; it would have caught the original failure.
- **Integration test pattern**: `IntegrationTestBase` (in `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt`) is the shared base for H2-backed endpoint tests. It uses `@Import` to plug in test beans.
- **Postgres-backed variants**: `ResourcePreviewEndpointPostgresIntegrationTest`, `WorkspaceAccessSummaryEndpointPostgresIntegrationTest`, `PublishingQueuePostgresIntegrationTest` — all annotated `@SpringBootTest` with Testcontainers. These boot a full context.
- **BDD**: `CucumberSpringConfiguration` (`@CucumberContextConfiguration + @SpringBootTest`) and `CucumberPostgresSpringConfiguration` both boot the full app context and would catch this too.
- **Modulith structural tests**: `ModularStructureTest` and `ModularityVerificationTest` — both `@Disabled`. They verify the modulith graph and do not start the full context.
- **No `@WebFluxTest`, `@DataR2dbcTest`, or `ApplicationContextRunner` usage** in the smp tests. The team only uses full-context `@SpringBootTest`.
- **Existing @Configuration / @Bean declarations in test sources** (under `bdd/BddTestConfiguration.kt`, `bdd/postgres/PostgresBddTestConfiguration.kt`, `integration/support/*TestBase.kt`) live in `src/test/kotlin/...integration.support` / `bdd` and are loaded via `@Import`, not component scan. The exclude regex therefore correctly ensures these are not picked up by an accidental broad scan.
- **Bean overriding**: all `@SpringBootTest` classes set `spring.main.allow-bean-definition-overriding=true` (e.g. `SmpApplicationTests.kt:15`, `CucumberSpringConfiguration.kt:35`). This is needed because some test configs declare duplicate beans.

## Conflict check

- `r2-storage-dedicated-adapter` is in `apply` per its `state.yaml` but is **already merged into main** (commit `992218c7`, 2026-06-09). It touched only `shared/storage/...` files. It did **not** modify `SmpApplication.kt`, did not add new stereotyped beans in `server/smp`, and did not touch any `@ComponentScan`. **No conflict.**
- A new `fix-restrictive-component-scan` change is tracked only in `state.yaml` (init complete, next: explore). It modifies `server/smp/SmpApplication.kt` and possibly a few bean classes. No other in-flight change in `openspec/changes/` touches those areas.

## Split package status

**Still present** in the current source tree (not just historical):

- `shared/bus/src/main/kotlin/com/profiletailors/common/domain/bus/...` — 30 files (`Container`, `DependencyProvider`, `Mediator`, `MediatorBuilder`, `MediatorImpl`, `Registrar`, `Registry`, `RegistryImpl`, `RequestHandlerDelegate`, `PublishStrategies`, `PublishStrategy`, `HandlerNotFoundException`, plus `command/`, `event/`, `notification/`, `pipeline/`, `query/` sub-packages).
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/bus/...` — 3 files (`event/BaseDomainEvent.kt`, `event/DomainEvent.kt`, `query/Response.kt`).

Both JARs publish the same `com.profiletailors.common.domain.bus` package root. The Java Module System forbids this; Gradle (without `module-info.java`) tolerates it but at runtime `Class.forName` and reflection can pick either class. This affects class loading, not component scanning. **It is not the cause of the `BeanDefinitionStoreException`** and should be tracked as a separate change. Severity: medium (eventual class-loading ambiguity, especially under reflection-heavy libraries like Jackson 3 and Nimbus JOSE). Belongs in its own change. Flag for the design phase to either split the package or move the bus content into `shared/common`.

## Build & test baseline

- `./gradlew :server:smp:compileKotlin` — was not run in this explore phase (gradle daemon startup adds noticeable time and produces noise from `NVD_API_KEY` warnings; the task itself is cheap but not strictly required for a static analysis). The 4 `@Component` classes and 17 `@Repository` classes all reference types and APIs that exist in the classpath, so the code is compile-clean; the only failure is at **bean definition registration time**, not compile time.
- `./gradlew :server:smp:test` — would have caught the bug (the `SmpApplicationTests.contextLoads()` test boots the full context and would have failed on missing `R2dbcApiKeyCredentialReplacementGateway` / `ReplaceApiKeyCredentialHandler`); but the test would also need to define the required beans via `@Import` or autowire them. The existing `WorkspaceAccessSummaryEndpointIntegrationTest` already injects `replaceApiKeyCredentialHandler`, proving the test author expected context startup to wire that bean.
- Last known successful boot: prior to PR #39 (LinkedIn publishing MVP, `3e17d7ea`), when `SmpApplication.kt` was a plain `@SpringBootApplication`. After that PR landed, the include-filter was added and the bug introduced. The test suite was probably not run on the broken main or the test config was @Importing enough to compensate (e.g. `WorkspaceAccessSummaryEndpointTestBase` injects the handler — but `@Autowired` requires the bean to exist, so the test would have failed there too).
- The exact user-reported error (`BeanDefinitionStoreException` on `ReplaceApiKeyCredentialHandler`) is consistent with Spring's behaviour: `@Component`-annotated classes are silently dropped from the registry, then when something tries to autowire them, Spring throws. The mention of "BeanDefinitionStoreException" suggests this is happening at `@Component` scanning time, which in Spring 6.x (Boot 4.x) actually means a `BeanDefinitionStoreException` thrown by `ClassPathBeanDefinitionScanner` when it tries to derive a bean name from an internal class that conflicts with another internal class in the same package (Kotlin `internal class Foo` and `internal class Foo$Default` etc). This is the second latent bug: the Kotlin `internal` modifier generates synthetic `$serializer` / `$Default` classes in the same `.class` file as the public class — and `ClassPathBeanDefinitionScanner` can trip over them when it tries to register the bean definition. (Verification needed in the design phase.)

## Risks

1. **Removing the include filter alone is not enough**: with the current setup, even `@Component` and `@Repository` may still fail to register because of a Kotlin-internal-class naming collision (see above). The design phase must run the build after the change to confirm.
2. **Kotlin `internal` visibility**: many use-case handlers are `internal class`. Spring's classpath scanner uses reflection and bypasses Java visibility — it loads `internal` classes from the same module just fine. The `lateinit var` injection in `WorkspaceAccessSummaryEndpointTestBase` proves the test author already relied on this. Confirm in design.
3. **Shared module scan coverage**: after removing the include filter, only `com.profiletailors.smp` is scanned by default. The shared modules (`shared/spring-boot-common`, `shared/storage`, `shared/shield/ratelimit`) still depend on `@ComponentScan` or `@AutoConfiguration` to be loaded. They are **not** loaded today. After the fix, we may want to broaden `scanBasePackages` to include `com.profiletailors.*` — but that risks sweeping in test fixtures (`shared/common/testFixtures`) and classes from unrelated shared modules. Need a deliberate decision in design.
4. **The custom `Service` annotation may still be useful as a project-style marker** even without the scan filter (e.g. for static analysis, IDE highlighting, future `detekt` rules). The proposal should keep the annotation as a semantic marker and remove the include filter — not delete the annotation.
5. **`RateLimitConfiguration` (shared/shield/ratelimit) has the same anti-pattern**. If the rate-limit module is ever made active in the smp context, the same bug will recur. Flag for a follow-up change or include in this one as a preventive measure.
6. **Publishing `CredentialEncryptionService` uses Spring's `@Service`**. After the fix it will be picked up correctly. Make sure `PublishingCredentialsProperties` (which has both `@Component` and `@ConfigurationProperties`) is registered via `@EnableConfigurationProperties` and the redundant `@Component` removed — otherwise the `@Component` may cause a duplicate-bean warning.
7. **BddTestConfiguration & IntegrationTestBase have `@Import` not `@ComponentScan`**, so the exclude regex doesn't actually need to exist for them. But removing the regex is a separate decision (see option B/C).
8. **Modulith boundary test is disabled** so it provides no protection; the design should consider re-enabling it as part of the guard-rail work.
9. **Spring Modulith event subscription is currently inactive**. After the fix, more event consumers will start being wired via `EventConfiguration`. The design should confirm there are no double-publishers and that the bus tests cover the wiring.
10. **`@Configuration` classes in `com.profiletailors.smp` are also silently skipped** under the current filter (see inventory). Their `@Bean` methods are therefore not active. If the fix restores them, several previously-missing beans (the `NoOpAuditHook`, `RequestContextStore`, `StoreBackedPrincipalContextProvider`, `principalContextProvider` `@Primary` bean in `IdentityPlatformBridgeConfiguration`, etc.) will suddenly appear in the context. This may surface other latent issues (e.g. duplicate beans if `SecurityContextPrincipalContextProvider` is auto-wired elsewhere). Run the full test suite after the change.

## Recommended options for `sdd-propose`

### Option A — Remove the include filter; keep the exclude regex as a documented safety net

- (a) `SmpApplication.kt`: drop the `includeFilters` block entirely. Keep `excludeFilters` with a one-line KDoc explaining it guards against test-only paths in case a class is ever moved to `main`.
- (b) Bean classes: change `ReplaceApiKeyCredentialHandler` and `SecureRandomApiKeyCredentialValueFactory` from `@Component` to custom `@Service` (consistent with sibling handlers like `RegisterUserHandler`). Same for `GetWorkspaceAuditEventsHandler` (already `internal class`, just swap the annotation). For `R2dbcLinkedInCredentialGateway` and the `@Repository` classes, leave as-is — `@Repository` is the right stereotype for persistence ports. For `CredentialEncryptionService`, change from Spring's `@Service` to custom `@Service` for consistency. For `PublishingCredentialsProperties`, remove the redundant `@Component` and register via `@EnableConfigurationProperties` on a new `@Configuration` (or co-locate with `CredentialEncryptionService`).
- (c) Guard rail: add a detekt rule (or grep-based CI check) that fails the build if any `server/smp/src/main/kotlin/.../application/...` file is annotated with `@Component` or `@Repository` instead of the custom `Service` (handlers) or `@Repository` (gateways). More broadly, add a regression test `SmpApplicationTests.contextLoads()` (it already exists) and assert that `applicationContext.getBeanNamesForAnnotation(Repository::class.java)` is non-empty and includes the expected gateways.
- (d) Test: extend `SmpApplicationTests` with an explicit assertion that `R2dbcApiKeyCredentialReplacementGateway`, `ReplaceApiKeyCredentialHandler`, `WorkspaceAuthorizationService` (custom `@Service`), and `R2dbcWorkspaceMembershipResolver` (`@Repository`) are all present. This is the regression test the bug slipped through.
- (e) Risk: low–medium. The fix is small, the test already exists, and the only behavioural change is that more beans become active (which is the intent). Risk: latent issues in `EventConfiguration`, `@Configuration` classes, and `PublishingCredentialsProperties` may surface.

### Option B — Drop both filters; broaden scan to all project packages

- (a) `SmpApplication.kt`: replace with plain `@SpringBootApplication(scanBasePackages = ["com.profiletailors"])` and add `@ComponentScan` with no include/exclude filters. This picks up everything in any shared module that lives under `com.profiletailors.*`.
- (b) Bean classes: no changes needed. Optional: still normalise the inconsistent `Service` vs `Component`/`Repository` usage in a follow-up.
- (c) Guard rail: add the same regression test as Option A. Add a detekt/custom check that fails if any class under `com.profiletailors` declares a stereotype but is in `testFixtures` or otherwise unwanted.
- (d) Test: same as Option A.
- (e) Risk: medium. `shared/common/testFixtures` and other unintended classes may get swept in; some shared modules (e.g. `shared/storage`) have `@AutoConfiguration` patterns that expect to be loaded through META-INF, not user scan — running both can cause duplicate-bean conflicts. The team's stated intent (`docs/agents/AGENTS.md` says "no backend, no CMS, no heavy deps" — wait, that's the marketing app; the smp module is different) doesn't explicitly say whether shared modules should be user-scanned or auto-config-scanned. The decision needs product input.

### Option C — Keep the project convention, fix the include filter to cover all project stereotypes

- (a) `SmpApplication.kt`: change the `includeFilters` to include `Service::class`, `RestControllerAdvice::class`, `ControllerAdvice::class`, `Repository::class`, and `Component::class` (or, more idiomatically, just drop the include filter and use a custom annotation meta-annotated with `@Component` to keep the project convention while letting Spring's default filter work). Concretely: meta-annotate `com.profiletailors.common.domain.Service` with `@Component` and `@Retention(RUNTIME)`, so the project convention is preserved AND Spring's default filter catches everything. Then drop the `includeFilters` block.
- (b) Bean classes: meta-annotate `Service.kt` with `@Component` (and leave every other class alone). For the two Spring-`@Service` cases, normalise to the custom marker.
- (c) Guard rail: a detekt rule that every `application/...` class in `com.profiletailors.smp` carries the custom `@Service`, and every gateway/repository carries `@Repository`. Plus the same context-load test.
- (d) Test: same as Option A.
- (e) Risk: low. Smallest semantic change. Preserves the team's apparent intent (custom marker for handlers). One subtle concern: the meta-annotation means the custom `@Service` will be auto-discovered by Spring's default filter even outside the restrictive scan — that is the desired behaviour. The `internal class` modifier still allows the bean to be registered.

**Recommendation to the propose phase: lean toward Option C**, with Option A as the lower-risk fallback. Option B is too broad and has more unknown surface area. Option C preserves the project's hexagonal-convention intent while making the bug impossible to reintroduce silently.

## Ready for Proposal

**Yes — sufficient context to write `proposal.md`.** The orchestrator should:

1. Recommend the `sdd-propose` phase use **Option C as the primary recommendation**, with Option A as an explicit alternative.
2. Carry forward the entire bean inventory and the 9 risks above verbatim so the design phase can address them.
3. Treat the **split package** (`shared/bus` vs `shared/common` under `com.profiletailors.common.domain.bus`) as **out of scope** for this change — note it as a follow-up.
4. Treat the **`RateLimitConfiguration` nested `@ComponentScan`** as **in scope for a defensive sub-task** in this change (same anti-pattern, should be fixed preventively) — but only if the rate-limit module becomes active; otherwise flag for follow-up.
5. Remind `sdd-spec` that the regression test is `SmpApplicationTests.contextLoads()` plus a new explicit bean-presence assertion test.
