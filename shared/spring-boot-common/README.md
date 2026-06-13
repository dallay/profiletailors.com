# shared:spring-boot-common

Spring Boot 4 integration layer for the Profile Tailors backend. Bridges the framework-agnostic shared modules to Spring WebFlux — exception handlers, request filters, pagination presenters, repository base classes, and auto-configuration.

## Overview

This module is the **Spring adapter** for the shared kernel. It provides:

- Global exception handling with RFC 9457 `ProblemDetail`
- Workspace context resolution via `WebFilter`
- Jackson serialization configuration
- PageResponse HTTP serialization (offset and cursor)
- Reactive repository base classes with criteria support
- EventEmitter for Spring-managed event publishing
- HasherRegistry with property-driven configuration

## Key Types

### HTTP & Controllers

| Type | Purpose |
|------|---------|
| `ApiController` | Base class for all REST controllers — consistent envelope, error handling |
| `ConstraintViolationAdvice` | Jakarta validation → `ProblemDetail` (400) |
| `GlobalExceptionHandler` | Entity not found (404), illegal argument (400), generic (500) |
| `ProblemDetailFactory` | RFC 9457 ProblemDetail builder |

### Request Context

| Type | Purpose |
|------|---------|
| `WorkspaceContextWebFilter` | Extracts `X-Workspace-Id` header → reactive context |
| `WorkspaceContextHolder` | Reactor `Context` accessor for workspace + principal |

### Pagination

| Type | Purpose |
|------|---------|
| `OffsetPagePresenter` | `OffsetPageResponse` → HTTP headers + JSON body |
| `OffsetPageResponseHandler` | `@ControllerAdvice` for automatic wrapping |
| `CursorApiResponse` / `OffsetApiResponse` | Response DTOs with CORS-safe headers |

### Events

| Type | Purpose |
|------|---------|
| `EventEmitter` | Spring bean for publishing domain events via the shared bus |

### Repository

| Type | Purpose |
|------|---------|
| `ReactiveSearchRepository<T>` | Reactive R2DBC repository with criteria-based search |
| `R2DBCCriteriaParser` | `Criteria` tree → R2DBC `Statement` predicates |

### Security

| Type | Purpose |
|------|---------|
| `HasherRegistry` | Named hasher lookup (sha256, hmac) |
| `SecurityProperties` | `profiletailors.security.*` configuration properties |
| `DataMaskingService` / `LogMasker` | Sensitive data masking for audit logs |

## Usage

Spring Boot auto-configuration is enabled via `spring.factories`. Include as a dependency and configure:

```yaml
profiletailors:
  security:
    hashers:
      sha256: com.profiletailors.common.infrastructure.security.Sha256Hasher
      hmac: com.profiletailors.common.infrastructure.security.HmacHasher
```

```kotlin
@ApiController
class PostsController(private val emitter: EventEmitter) {

    @PostMapping
    suspend fun create(@RequestBody @Valid command: CreatePost): Post {
        emitter.publish(PostCreatedEvent(command.id))
        return mediator.send(command)
    }
}
```

## Dependencies

- `shared:common` (api) — domain primitives
- `shared:bus` (api) — event types and mediator
- `shared:security` (api) — hasher interfaces
- `shared:presentation` (api) — PageResponse, Criteria

## Related

- [shared:bus](../bus/README.md) — event bus types
- [shared:presentation](../presentation/README.md) — PageResponse DTOs
- [shared:security](../security/README.md) — hasher interfaces
- [shared:shield:ratelimit](../shield/ratelimit/README.md) — depends on this module for Spring integration
