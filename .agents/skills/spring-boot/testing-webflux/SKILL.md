---
name: spring-boot-testing-webflux
description: Use when testing reactive Spring Boot 4 HTTP adapters, `@RestController` endpoints, `@RestControllerAdvice`, validation errors, security constraints, or response contracts with WebFlux.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot Testing WebFlux

Testing patterns for **reactive HTTP boundaries** in a Kotlin + coroutines + WebFlux backend.

This skill replaces MVC-first guidance such as `MockMvc` and `@WebMvcTest` with the correct default
for this stack:

- `@WebFluxTest`
- `WebTestClient`
- coroutine-aware endpoint tests
- stable error-contract verification
- reactive security verification

## What Belongs Here

- controller slice tests
- request/response DTO validation at the HTTP boundary
- `@RestControllerAdvice` and `ProblemDetail` tests
- status code and header verification
- security rules on endpoints
- JSON response contract tests for reactive endpoints

## What Does NOT Belong Here

Use companion skills instead when the main concern is:

- business logic without HTTP → `spring-boot-testing-core`
- WireMock, Testcontainers, cache integration, external clients, messaging →
  `spring-boot-testing-integrations`

## Core Rules

- Use `@WebFluxTest` as the default controller slice.
- Use `WebTestClient`, not `MockMvc`.
- Keep test style consistent with the Kotlin skill where practical: Kotest is fine, but JUnit 5 is
  acceptable for Spring slice tests that already rely on standard Spring test integration.
- Mock application handlers / services, not the controller itself.
- Assert both success and failure mappings.
- Verify validation errors and error payload structure.
- For security, verify allow and deny cases explicitly.
- Keep persistence out of controller slice tests.

## Controller Slice Pattern

```kotlin
@WebFluxTest(WorkspaceController::class)
class WorkspaceControllerTest(
    @Autowired private val webTestClient: WebTestClient,
) {
    @MockkBean
    lateinit var createWorkspace: CreateWorkspaceCommandHandler

    @MockkBean
    lateinit var findWorkspace: FindWorkspaceQueryHandler

    @Test
    fun `returns workspace by id`() {
        val id = UUID.randomUUID()
        coEvery { findWorkspace.handle(FindWorkspaceQuery(id)) } returns workspaceProjection(id)

        webTestClient.get()
            .uri("/api/workspaces/{id}", id)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
            .jsonPath("$.name").isEqualTo("Profile Tailors")
    }
}
```

### Controller Rules

- Test route mapping, validation, status codes, and response body shape.
- Do not test repository behavior here.
- Do not use full application startup unless the slice is insufficient.

## Validation Error Pattern

```kotlin
@Test
fun `returns 400 when request body is invalid`() {
    webTestClient.post()
        .uri("/api/workspaces")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            mapOf(
                "name" to "",
                "ownerId" to UUID.randomUUID().toString(),
            ),
        )
        .exchange()
        .expectStatus().isBadRequest
}
```

### Validation Rules

- Test malformed JSON separately from semantically invalid input.
- Keep the contract stable enough that clients can rely on it.
- If using `ProblemDetail`, assert its fields consistently.

## Exception Handler Pattern

Use controller slice tests to verify global mapping behavior.

```kotlin
@WebFluxTest(controllers = [WorkspaceController::class])
@Import(GlobalExceptionHandler::class)
class GlobalExceptionHandlerTest(
    @Autowired private val webTestClient: WebTestClient,
) {
    @MockkBean
    lateinit var findWorkspace: FindWorkspaceQueryHandler

    @Test
    fun `maps not found to problem detail`() {
        val id = UUID.randomUUID()
        coEvery { findWorkspace.handle(FindWorkspaceQuery(id)) } throws WorkspaceNotFoundException(id)

        webTestClient.get()
            .uri("/api/workspaces/{id}", id)
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.title").isEqualTo("Workspace Not Found")
    }
}
```

## Reactive Security Pattern

Use Spring Security test support with WebFlux slices when endpoint access rules matter.

```kotlin
@WebFluxTest(AdminController::class)
@Import(SecurityConfiguration::class)
class AdminControllerSecurityTest(
    @Autowired private val webTestClient: WebTestClient,
) {
    @Test
    fun `rejects anonymous access`() {
        webTestClient.get()
            .uri("/api/admin/reports")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
```

### Security Rules

- Test anonymous, authenticated-but-forbidden, and authorized paths.
- Keep method security tests and HTTP security tests distinct when possible.
- Prefer reactive security configuration over servlet assumptions.

## Streaming Endpoint Pattern

When the contract is a stream, verify it as a stream.

```kotlin
@Test
fun `streams workspace summaries`() {
    every { workspaceQueryService.listAll() } returns flowOf(
        WorkspaceSummaryResponse("a"),
        WorkspaceSummaryResponse("b"),
    )

    webTestClient.get()
        .uri("/api/workspaces")
        .accept(MediaType.APPLICATION_NDJSON)
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
}
```

## Common Mistakes

- ❌ Using `MockMvc` in a WebFlux-first codebase
- ❌ Pulling repositories into controller tests
- ❌ Testing every endpoint with `@SpringBootTest`
- ❌ Asserting only status code and ignoring response body contract
- ❌ Treating security success as enough without deny-path tests
- ❌ Reusing servlet security patterns in reactive tests

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core reactive API and error-handling rules
- `spring-boot-testing-core` — Fast service, mapper, config, and JSON tests
- `spring-boot-testing-integrations` — External client, cache, messaging, and container-based tests
