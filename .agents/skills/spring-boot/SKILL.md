---
name: spring-boot
description: >
  Use when implementing Spring Boot 4 adapters, reactive HTTP endpoints, persistence integrations,
  configuration, transaction boundaries, security baselines, or infrastructure tests in a Kotlin +
  coroutines + WebFlux hexagonal backend.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  author: profiletailors
  version: "5.0"
---

# Spring Boot Skill

Implementation patterns for the **Spring Boot 4 infrastructure layer** in a **Kotlin + coroutines +
WebFlux** hexagonal architecture.

This is the **core backend skill** for the reactive stack. Use it for bean wiring, HTTP adapters,
configuration, reactive persistence boundaries, transaction demarcation, API error handling, and
infrastructure tests without violating domain/application boundaries.

> For feature modeling, use cases, ports/adapters rules, and domain boundaries, use the
> [hexagonal-architecture skill](../hexagonal-architecture/SKILL.md).

## Stack Baseline

These defaults are canonical unless a companion skill explicitly documents an exception:

- **Language:** Kotlin
- **Spring:** Spring Boot 4
- **Web:** Spring WebFlux
- **Concurrency:** Kotlin coroutines
- **Streaming:** `Flow<T>` when streaming is part of the contract
- **HTTP client:** `WebClient` or Spring HTTP interfaces, never `RestTemplate`
- **SQL persistence default:** reactive-first (`R2DBC` or equivalent reactive adapter)
- **Security baseline:** reactive Spring Security (`SecurityWebFilterChain`)
- **Testing baseline:** `WebTestClient`, slice tests, and focused integration tests

## Relationship to Hexagonal Architecture

Use this skill for:

- Spring bean wiring
- reactive HTTP adapters (`@RestController`, DTOs, validation)
- reactive persistence adapters and mappings
- configuration, profiles, and conditional beans
- transaction boundaries resolved from infrastructure
- exception-to-HTTP mapping
- infrastructure testing
- baseline observability and security guidance

Do **not** use this skill to define domain rules, aggregate behavior, or application business logic.

## Non-Negotiable Boundaries

- Domain is pure: no Spring annotations, no persistence annotations, no framework types.
- Application is framework-agnostic: no Spring stereotypes, no Spring transaction annotations, no
  transport-specific types.
- Infrastructure implements ports and hosts all Spring-specific code.
- Controllers translate HTTP requests/responses into application commands/queries.
- Persistence adapters translate storage models into domain models.
- Never expose persistence entities directly through the HTTP layer.
- Never move business rules into controllers, repository adapters, configuration classes, or event
  listeners.
- Transactional behavior belongs to infrastructure wiring, not to application classes.

## Layer Mapping

| Layer          | Responsibility                | Spring Allowed? | Typical Elements                                             |
|----------------|-------------------------------|----------------:|--------------------------------------------------------------|
| Domain         | Business rules and invariants |              No | Entities, Value Objects, Domain Events, Repository Ports     |
| Application    | Use-case orchestration        |              No | Commands, Queries, Handlers, Application Services            |
| Infrastructure | Framework integration         |             Yes | Controllers, Config, Adapters, Repositories, Event Listeners |

## Local Architectural Markers

When application classes must be discovered or selected by infrastructure, use **local markers**
instead of Spring stereotypes.

```kotlin
package com.profiletailors.common.application

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class ApplicationService
```

Rules:

- `ApplicationService` is a local architectural marker, not a framework annotation.
- Use it for readability and architecture enforcement, not as an excuse for hidden framework magic.
- Prefer explicit infrastructure `@Configuration` + `@Bean` wiring even when the marker is present.
- Application code must not import Spring directly just to become a bean.

## Bean Wiring and Dependency Injection

### Preferred Rules

- Prefer constructor injection.
- Avoid field injection.
- Use `@Configuration` + `@Bean` to wire application services and handlers.
- Use stereotypes (`@RestController`, `@Repository`, `@Component`) only for clearly infrastructural
  adapters.
- Use `@Primary`, `@Qualifier`, `@ConditionalOnProperty`, and `@Profile` when multiple adapter
  implementations exist.
- Keep optional collaborators explicit with `ObjectProvider`, conditional beans, or no-op
  implementations.
- If an application class needs too many collaborators, fix the design before adding more wiring.

### Application Service Example

```kotlin
@ApplicationService
class CreateWorkspaceCommandHandler(
    private val creator: WorkspaceCreator,
) {
    suspend fun handle(command: CreateWorkspaceCommand): WorkspaceId =
        creator.create(
            name = WorkspaceName(command.name),
            ownerId = UserId(command.ownerId),
        )
}
```

### Explicit Bean Wiring

```kotlin
@Configuration(proxyBeanMethods = false)
class WorkspaceConfiguration {

    @Bean
    fun workspaceCreator(
        repository: WorkspaceRepository,
        finderRepository: WorkspaceFinderRepository,
        eventPublisher: DomainEventPublisher,
    ) = WorkspaceCreator(repository, finderRepository, eventPublisher)

    @Bean
    fun createWorkspaceCommandHandler(
        creator: WorkspaceCreator,
    ) = CreateWorkspaceCommandHandler(creator)
}
```

### Optional Dependency Strategy

```kotlin
@Component
class ReportNotifier(
    notificationGatewayProvider: ObjectProvider<NotificationGateway>,
) {
    private val notificationGateway =
        notificationGatewayProvider.getIfAvailable(NotificationGateway::noOp)

    suspend fun notify(message: String) {
        notificationGateway.send(message)
    }
}
```

## Package Structure

Prefer **package by feature** (vertical slicing) over package by technical layer.

### Rules

- Group controllers, handlers, ports, adapters, and DTOs around a business capability.
- Keep each feature readable without navigating the entire codebase.
- Use subpackages only when they clarify boundaries, not as ceremony.
- Let hexagonal boundaries exist **inside** each feature package.
- Avoid giant global `controller/`, `service/`, `repository/`, or `dto/` folders.

### Example

```text
com.profiletailors.workspace/
  domain/
    Workspace.kt
    WorkspaceId.kt
    WorkspaceRepository.kt
  application/
    create/
      CreateWorkspaceCommand.kt
      CreateWorkspaceCommandHandler.kt
      WorkspaceCreator.kt
    find/
      FindWorkspaceQuery.kt
      FindWorkspaceQueryHandler.kt
      WorkspaceFinder.kt
  infrastructure/
    http/
      WorkspaceController.kt
      WorkspaceResponse.kt
    persistence/
      WorkspaceEntity.kt
      WorkspaceMapper.kt
      WorkspaceR2dbcRepository.kt
      WorkspaceStoreR2dbcAdapter.kt
    configuration/
      WorkspaceConfiguration.kt

com.profiletailors.billing/
  domain/
  application/
  infrastructure/
```

This keeps the code aligned with bounded contexts, preserves hexagonal boundaries, and avoids giant
cross-feature technical folders.

## HTTP Adapter Patterns

Reactive controllers receive transport input, delegate to the application layer, and map results
into
HTTP responses.

### Core Rules

- Default to **WebFlux + coroutines**.
- Use `suspend fun` for request/response endpoints.
- Use `Flow<T>` only when the API contract is truly streaming.
- No business logic in controllers.
- Request/response DTOs belong to infrastructure.
- Validate request DTOs at the HTTP boundary.
- Map domain/application results into explicit HTTP status codes.
- Document public endpoints with OpenAPI annotations when the project exposes an API contract.
- Avoid returning persistence entities or framework exceptions directly.

### Controller Example

```kotlin
@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController(
    private val createWorkspace: CreateWorkspaceCommandHandler,
    private val findWorkspace: FindWorkspaceQueryHandler,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(
        @Valid @RequestBody request: CreateWorkspaceRequest,
    ): WorkspaceIdResponse {
        val id = createWorkspace.handle(
            CreateWorkspaceCommand(
                name = request.name,
                ownerId = request.ownerId,
            ),
        )
        return WorkspaceIdResponse(id.value)
    }

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: UUID): WorkspaceResponse =
        findWorkspace.handle(FindWorkspaceQuery(id)).toResponse()
}
```

### Streaming Example

```kotlin
@GetMapping(produces = [MediaType.APPLICATION_NDJSON_VALUE])
fun list(): Flow<WorkspaceSummaryResponse> = workspaceQueryService.listAll()
```

### Request/Response DTOs

```kotlin
data class CreateWorkspaceRequest(
    @field:NotBlank
    val name: String,
    @field:NotNull
    val ownerId: UUID,
)

data class WorkspaceResponse(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val memberCount: Int,
    val createdAt: Instant,
)
```

### Status Code Guidelines

| Code  | Usage                                          |
|-------|------------------------------------------------|
| `200` | Successful GET/PUT/PATCH                       |
| `201` | Resource created                               |
| `204` | Successful DELETE with no body                 |
| `400` | Malformed input or invalid request shape       |
| `404` | Resource not found                             |
| `409` | Conflict with current state                    |
| `422` | Semantic validation or business rule violation |
| `500` | Unexpected server error                        |

## Validation

Validation at the HTTP boundary protects the adapter. Domain invariants protect the business model.

### Rules

- Use Bean Validation for request shape and basic input constraints.
- Keep business invariants inside value objects and domain entities.
- Use custom validators for technical input contracts when needed.
- Do not assume `@NotBlank` replaces domain rules.

### Request Validation Example

```kotlin
data class CreateOrderRequest(
    @field:NotNull
    val customerId: UUID,

    @field:NotEmpty
    val items: List<@Valid OrderItemRequest>,
)

data class OrderItemRequest(
    @field:NotNull
    val productId: UUID,

    @field:Min(1)
    val quantity: Int,
)
```

## Exception Handling and HTTP Mapping

Domain and application errors should be mapped centrally into HTTP-friendly responses.

### Rules

- Domain exceptions stay framework-agnostic.
- Controllers should not duplicate `try/catch` mapping logic.
- Use `@RestControllerAdvice` to translate exceptions into consistent API responses.
- Prefer `ProblemDetail` / RFC 7807-compatible responses.
- Keep error contracts stable and documented.

### Example

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WorkspaceNotFoundException::class)
    fun handleNotFound(ex: WorkspaceNotFoundException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.message ?: "Workspace not found",
        ).apply {
            title = "Workspace Not Found"
        }

        return ResponseEntity.status(problem.status).body(problem)
    }
}
```

## Persistence Adapter Patterns

Persistence adapters implement domain ports. They map storage models to domain models and keep
storage-specific concerns out of domain/application.

### Shared Rules

- Storage models are not domain models.
- Spring Data repositories and database clients are infrastructure details.
- Adapters implement domain repository ports.
- Mapping must be explicit.
- Do not place business rules inside repository adapters.
- For SQL, the default path is **reactive persistence**.

### Reactive SQL Example

```kotlin
@Repository
class WorkspaceStoreR2dbcAdapter(
    private val repository: WorkspaceR2dbcRepository,
    private val mapper: WorkspaceMapper,
) : WorkspaceRepository {

    override suspend fun save(workspace: Workspace): Workspace {
        val saved = repository.save(mapper.toEntity(workspace)).awaitSingle()
        return mapper.toDomain(saved)
    }

    override suspend fun findById(id: WorkspaceId): Workspace? =
        repository.findById(id.value).awaitSingleOrNull()?.let(mapper::toDomain)

    override suspend fun delete(id: WorkspaceId) {
        repository.deleteById(id.value).awaitFirstOrNull()
    }
}

interface WorkspaceR2dbcRepository : ReactiveCrudRepository<WorkspaceEntity, UUID>
```

### Mapper Strategy

Prefer manual mappers first. Use generated mapping only when complexity clearly justifies it.

```kotlin
@Component
class WorkspaceMapper {
    fun toDomain(entity: WorkspaceEntity): Workspace = Workspace(
        id = WorkspaceId(requireNotNull(entity.id)),
        name = WorkspaceName(entity.name),
        ownerId = UserId(entity.ownerId),
        createdAt = entity.createdAt,
    )

    fun toEntity(domain: Workspace): WorkspaceEntity = WorkspaceEntity(
        id = domain.id.value,
        name = domain.name.value,
        ownerId = domain.ownerId.value,
        createdAt = domain.createdAt,
    )
}
```

### Reactive Persistence Baseline

- **Default for this stack:** R2DBC or another reactive persistence adapter.
- Blocking persistence is outside the default path for this platform.
- If an exceptional compatibility adapter is unavoidable, document it explicitly and isolate it as a
  blocking boundary.
- Do not mix reactive HTTP flows with hidden blocking persistence calls.

## Transaction Management

Transactions belong to use-case execution boundaries, not to the domain model and not to
application annotations.

### Rules

- Define one transaction per write use case when consistency requires it.
- Keep domain and application logic transaction-agnostic.
- Resolve transactions from infrastructure wiring, not from application classes.
- Reactive transaction management has different semantics than imperative blocking transactions; do
  not copy patterns blindly.
- Prefer `TransactionalOperator` for reactive transaction boundaries.

### Reactive Infrastructure Boundary Example

```kotlin
@Component
class TransactionalSubscriberStore(
    private val repository: SubscriberStore,
    private val transactionalOperator: TransactionalOperator,
) {
    suspend fun save(subscriber: Subscriber): Subscriber =
        requireNotNull(
            transactionalOperator.executeAndAwait {
                repository.save(subscriber)
            },
        ) { "Reactive transaction completed without returning a saved subscriber" }
}
```

### Guidance

- Do not annotate application handlers or services with Spring `@Transactional`.
- Be explicit about where the transaction starts and ends.
- If you have to keep a blocking persistence adapter, isolate it clearly and do not pretend it is
  reactive.

## Configuration Properties

Use typed configuration instead of scattering property lookups across services.

```kotlin
@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(
    val host: String = "smtp.example.com",
    val port: Int = 587,
    val ssl: Boolean = false,
)
```

Rules:

- Prefer `@ConfigurationProperties` over repeated `@Value` injection.
- Keep configuration immutable when possible.
- Validate critical configuration early.
- Enable properties in configuration, not in business code.

## Security Baseline

This core skill only defines the **baseline**. Detailed auth flows belong in the companion security
skill.

### Rules

- For reactive applications, use `SecurityWebFilterChain`, not servlet filter chains.
- Do not use `OncePerRequestFilter` as the default JWT pattern in WebFlux applications.
- Keep authentication/authorization concerns in infrastructure.
- Use method security sparingly and intentionally.
- Treat token revocation, refresh flows, and OAuth integration as specialized concerns.

### Minimal Reactive Security Example

```kotlin
@Configuration
@EnableWebFluxSecurity
class SecurityConfiguration {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange {
                it.pathMatchers("/actuator/health").permitAll()
                it.anyExchange().authenticated()
            }
            .oauth2ResourceServer { it.jwt() }
            .build()
}
```

See the future companion security skill for JWT-specific guidance.

## HTTP Clients

### Rules

- Use `WebClient` for explicit reactive clients.
- Use Spring HTTP interfaces when they improve clarity and fit the version in use.
- Do not introduce `RestTemplate` in this stack.
- Keep client DTOs and error mapping in infrastructure.

### Declarative HTTP Interface Example

```kotlin
@HttpExchange(url = "https://api.example.com", accept = ["application/json"])
interface TodoClient {
    @GetExchange("/todos/{id}")
    suspend fun getTodoById(@PathVariable id: Long): Todo?
}
```

## OpenAPI / API Documentation Baseline

Document public HTTP contracts at the controller boundary, never from persistence models.

### Rules

- Annotate public endpoints with summary, responses, and request semantics.
- Keep examples aligned with request/response DTOs.
- Document error responses for validation and domain failures.
- Keep OpenAPI specifics in the companion OpenAPI skill.

## Testing Matrix

Choose the narrowest test that gives confidence.

### Quick Testing Baseline

| Need to prove                      | Preferred tool                            |
|------------------------------------|-------------------------------------------|
| Service / use-case behavior        | Plain unit test + mocks                   |
| Reactive controller behavior       | `@WebFluxTest` + `WebTestClient`          |
| JSON contract                      | `@JsonTest`                               |
| `@ConfigurationProperties` binding | `ApplicationContextRunner`                |
| External HTTP integration          | WireMock / focused integration test       |
| Persistence adapter realism        | Focused integration test / Testcontainers |

| Layer / Adapter        | Preferred Test Type        | Notes                                         |
|------------------------|----------------------------|-----------------------------------------------|
| Domain                 | Pure unit tests            | No Spring context                             |
| Application            | Pure unit tests            | Mock/fake ports                               |
| HTTP Adapter (WebFlux) | `@WebFluxTest`             | Controller + validation + error mapping       |
| JSON                   | `@JsonTest`                | Serialization contract                        |
| Config Properties      | `ApplicationContextRunner` | Binding and validation                        |
| Persistence Adapter    | Integration test           | Verify mapping and repository behavior        |
| End-to-End             | `@SpringBootTest`          | Full wiring only when narrower tests are weak |

### WebFlux Controller Slice Example

```kotlin
@WebFluxTest(WorkspaceController::class)
class WorkspaceControllerTest(
    @Autowired private val webTestClient: WebTestClient,
) {
    @MockkBean
    lateinit var findWorkspace: FindWorkspaceQueryHandler
}
```

### Testing Rules

- Do not use `@SpringBootTest` for everything.
- Test application services without Spring.
- Use `WebTestClient`, not `MockMvc`, as the default web testing tool.
- Use focused integration tests for persistence and external adapters.
- Use Testcontainers when realism matters.
- Verify both happy path and failure mapping at the HTTP boundary.

## Observability Baseline

- Expose health/readiness/liveness endpoints through Actuator when the application runs in managed
  environments.
- Avoid blocking health indicators in reactive applications.
- Keep tracing/metrics propagation compatible with Reactor/coroutines context.
- When adding Micrometer instrumentation to coroutine-based code, preserve context propagation and
  avoid dropping trace/observation state by jumping into unmanaged threads.
- Put detailed observability setup in the companion Actuator/observability skill.

### Practical Note

```kotlin
suspend fun findWorkspace(id: UUID): WorkspaceResponse {
    // Micrometer + Reactor/coroutines context should flow through this suspend chain naturally.
    return workspaceService.findById(id)
}
```

If a metric, trace, or observation disappears across async boundaries, verify the coroutine /
Reactor
handoff before blaming the monitoring backend.

## Common Mistakes

- ❌ Putting Spring annotations in domain/application classes
- ❌ Annotating application handlers with `@Transactional`
- ❌ Returning persistence entities from controllers
- ❌ Using `MockMvc` as the default web test tool in WebFlux apps
- ❌ Introducing `RestTemplate` in a reactive stack
- ❌ Mixing blocking persistence calls into reactive request flows without explicit isolation
- ❌ Treating blocking adapters and reactive adapters as interchangeable defaults
- ❌ Putting business rules into validators, controllers, or repository adapters

## Related Skills

- [hexagonal-architecture](../hexagonal-architecture/SKILL.md)
- `spring-boot-openapi`
- `spring-boot-security`
- `spring-boot-testing-webflux`
- `spring-boot-testing-core`
- `spring-boot-testing-integrations`
- `spring-boot-project-bootstrap`

## References

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring Framework WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Data R2DBC](https://spring.io/projects/spring-data-r2dbc)
- [Kotlin Coroutines Reactor](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-reactor/)
