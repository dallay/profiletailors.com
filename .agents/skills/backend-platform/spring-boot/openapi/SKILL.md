---
name: spring-boot-openapi
description: Use when documenting reactive Spring Boot 4 HTTP APIs with SpringDoc OpenAPI, configuring Swagger UI, annotating endpoints, documenting security schemes, or defining request and response schemas.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot OpenAPI

OpenAPI and SpringDoc guidance for **Spring Boot 4 + WebFlux + Kotlin coroutines**.

This skill documents how to describe reactive HTTP APIs clearly and consistently without coupling the
API contract to persistence or servlet-era assumptions.

## Official Baseline

From the official Spring Boot and Spring Framework docs:

- WebFlux annotated controllers are fully supported in Spring Boot 4
- reactive controllers still use standard annotations like `@RestController`, `@GetMapping`,
  `@RequestParam`, and `@PathVariable`
- `@RequestParam` is the normal WebFlux mechanism for explicit query parameters
- `ProblemDetail` is an official error-response type and should be reflected in API documentation when
  applicable

## What This Skill Owns

- SpringDoc/OpenAPI integration
- schema and endpoint annotations
- security scheme documentation
- documenting reactive controller contracts
- documenting query parameters, pagination, sorting, and filtering contracts
- documenting error responses consistently

## What This Skill Does Not Own

Use companion skills instead when the main concern is:

- HTTP contract design itself → `spring-boot-api-standards`
- core controller and DTO boundaries → `spring-boot`
- reactive HTTP verification → `spring-boot-testing-webflux`
- security implementation details → `spring-boot-security`

## Core Rules

- Document the **public contract**, not implementation details.
- Annotate request/response DTOs, not persistence entities.
- Keep docs aligned with reactive endpoint behavior.
- Document explicit query parameters instead of assuming framework-specific pagination abstractions.
- Document `ProblemDetail` or equivalent error responses when clients depend on them.
- Keep security schemes and auth requirements visible in the spec.

## Dependency Baseline

Use SpringDoc support that is compatible with your Spring Boot 4 version and WebFlux stack.

### Rules

- Verify compatibility with the exact Spring Boot version in use.
- Prefer SpringDoc configuration that works with WebFlux endpoints.
- Do not assume MVC-only examples apply unchanged to reactive APIs.

## Annotated Reactive Endpoint Example

```kotlin
@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController {

    @Operation(summary = "List workspaces")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Workspaces returned"),
            ApiResponse(responseCode = "401", description = "Unauthenticated"),
        ],
    )
    @GetMapping
    suspend fun listWorkspaces(
        @RequestParam(required = false) ownerId: UUID?,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): WorkspaceListResponse = TODO()
}
```

## DTO and Schema Documentation

Use DTOs that represent the API contract and document them explicitly.

```kotlin
@Schema(description = "Request to create a workspace")
data class CreateWorkspaceRequest(
    @field:Schema(description = "Workspace display name", example = "Profile Tailors")
    @field:NotBlank
    val name: String,

    @field:Schema(description = "Owner user identifier")
    @field:NotNull
    val ownerId: UUID,
)
```

### Rules

- Prefer Kotlin `data class` DTOs.
- Use examples where they clarify the contract.
- Avoid documenting fields that are not actually returned or accepted.

## Error Response Documentation

If the API uses `ProblemDetail`, document it consistently.

```kotlin
@Operation(summary = "Find workspace by id")
@ApiResponses(
    value = [
        ApiResponse(responseCode = "200", description = "Workspace found"),
        ApiResponse(responseCode = "404", description = "Workspace not found"),
    ],
)
@GetMapping("/{id}")
suspend fun findById(@PathVariable id: UUID): WorkspaceResponse = TODO()
```

### Rules

- Document validation and not-found responses.
- Keep error documentation consistent with your global exception mapping.
- If using RFC-style errors, make that visible in the API contract.

## Query Parameters, Pagination, and Filtering

Do **not** assume `Pageable` is your default documentation strategy.

### Preferred approach

Document explicit query parameters that reflect the real public contract:

- `limit`
- `cursor`
- `sort`
- `ownerId`
- `status`
- similar business-facing filters

### Example

```kotlin
@Operation(summary = "Search products")
@GetMapping("/search")
suspend fun searchProducts(
    @Parameter(description = "Search text")
    @RequestParam query: String,
    @Parameter(description = "Maximum number of items to return")
    @RequestParam(defaultValue = "20") limit: Int,
    @Parameter(description = "Cursor for next page")
    @RequestParam(required = false) cursor: String?,
    @Parameter(description = "Sort expression, e.g. createdAt:desc")
    @RequestParam(required = false) sort: String?,
): ProductSearchResponse = TODO()
```

### Guidance

- Use cursor-style pagination when stability matters.
- If a project truly exposes `Pageable`-style semantics, document them explicitly and intentionally.
- Do not let Spring Data implementation choices dictate the public contract automatically.

## Security Scheme Documentation

Document bearer or cookie-based auth requirements explicitly.

```kotlin
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
class OpenApiSecurityConfiguration
```

### Rules

- Keep security scheme names stable across the spec.
- Mark public and protected endpoints clearly.
- Document auth requirements at operation level where useful.

## Swagger UI and Spec Exposure

### Rules

- Expose docs intentionally by environment.
- Keep public docs paths stable.
- Do not expose internal-only debug endpoints in public API docs.
- Review CORS and auth behavior around docs endpoints when the API is protected.

## Review Checklist

- Are endpoints documented from controller DTOs, not entities?
- Are query parameters explicit and understandable without framework knowledge?
- Are error responses documented consistently?
- Are security schemes visible and correct?
- Does the spec match reactive endpoint behavior and return shape?
- Are examples realistic and non-sensitive?

## Common Mistakes

- ❌ Documenting persistence entities instead of API DTOs
- ❌ Using `Pageable` as an undocumented implicit contract
- ❌ Copying MVC-centric examples without checking WebFlux behavior
- ❌ Forgetting to document error responses and auth requirements
- ❌ Letting implementation details leak into the OpenAPI schema

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core reactive controller and DTO boundary rules
- `spring-boot-api-standards` — URL, verb, status code, and contract design
- `spring-boot-testing-webflux` — Verifying documented endpoint behavior
- `spring-boot-security` — Authn/authz implementation details
