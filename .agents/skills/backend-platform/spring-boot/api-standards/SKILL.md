---
name: spring-boot-api-standards
description: Use when designing or reviewing reactive HTTP APIs in Spring Boot 4, including resource URLs, status codes, DTO contracts, validation, pagination, filtering, security headers, and consistent error responses.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot API Standards

Contract and design standards for **Kotlin + coroutines + WebFlux** APIs in Spring Boot 4.

This skill is the companion for **HTTP/API design discipline**. Use it when the question is not only
"how do I wire a controller?" but also "is this API contract well designed, stable, and safe to
consume?"

## What This Skill Owns

- resource-oriented URL design
- HTTP method semantics
- status code policy
- request/response DTO contract rules
- validation expectations at the API boundary
- error response consistency
- pagination, filtering, and sorting guidance
- security and operational headers at the HTTP boundary

## What This Skill Does Not Own

Use companion skills instead when the main concern is:

- core Spring wiring and controller baseline → `spring-boot`
- OpenAPI annotation details and Swagger configuration → `spring-boot-openapi`
- controller slice testing and HTTP contract verification → `spring-boot-testing-webflux`
- authn/authz mechanics → `spring-boot-security`

## URL and Resource Design

### Rules

- Use **plural nouns** for top-level collections.
- Model URLs around **resources**, not actions.
- Keep nesting shallow; use nesting to express ownership, not every relationship.
- Avoid RPC-style endpoints like `/getUsers`, `/createOrder`, `/runReport`.
- Version externally exposed APIs intentionally; do not version internal-only endpoints without a
  reason.

### Good Examples

```text
GET    /api/workspaces
POST   /api/workspaces
GET    /api/workspaces/{workspaceId}
PATCH  /api/workspaces/{workspaceId}
DELETE /api/workspaces/{workspaceId}
GET    /api/workspaces/{workspaceId}/members
POST   /api/workspaces/{workspaceId}/members
```

### Bad Examples

```text
GET  /api/getAllWorkspaces
POST /api/createWorkspace
POST /api/workspaces/{id}/delete
GET  /api/workspace-member-list
```

## HTTP Method Semantics

| Method   | Use for                                               | Notes                                               |
|----------|-------------------------------------------------------|-----------------------------------------------------|
| `GET`    | Read resources                                        | Safe and idempotent                                 |
| `POST`   | Create resources or submit commands with side effects | Usually not idempotent                              |
| `PUT`    | Full replacement                                      | Use only when replacement semantics are real        |
| `PATCH`  | Partial update                                        | Prefer when the API truly supports partial mutation |
| `DELETE` | Delete resources                                      | Should be idempotent from client perspective        |

### Rules

- Do not tunnel updates through `POST` unless the endpoint is explicitly command-like.
- Prefer `PATCH` over fake partial `PUT`.
- Use `POST` for command endpoints only when the contract is not naturally resource CRUD.

## Status Code Policy

| Code  | Meaning                     | Typical Use                                   |
|-------|-----------------------------|-----------------------------------------------|
| `200` | Success with body           | GET, PUT, PATCH                               |
| `201` | Created                     | POST that creates a resource                  |
| `202` | Accepted                    | Async processing started                      |
| `204` | Success without body        | DELETE, some idempotent updates               |
| `400` | Malformed request           | Invalid shape, parse problems                 |
| `401` | Unauthenticated             | Missing/invalid credentials                   |
| `403` | Authenticated but forbidden | Permission denied                             |
| `404` | Resource missing            | Identifier not found                          |
| `409` | State conflict              | Duplicate or invalid state transition         |
| `422` | Semantically invalid input  | Business-rule or semantic validation failures |
| `429` | Too many requests           | Rate limiting                                 |
| `500` | Unexpected server failure   | Unhandled platform errors                     |

### Rules

- Return `201` for creation when a new resource exists afterward.
- Use `202` when work is asynchronous and not completed yet.
- Do not collapse all failures into `400`.
- Reserve `500` for real unexpected failures, not predictable domain outcomes.

## DTO Contract Rules

### Rules

- Always separate API DTOs from domain models and persistence models.
- Use Kotlin `data class` DTOs.
- Keep response contracts stable and explicit.
- Do not leak internal persistence fields, ORM concerns, or audit implementation details unless they
  are part of the contract.
- Keep request DTOs focused on what the client is allowed to send.

### Example

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
    val createdAt: Instant,
)
```

## Validation at the Boundary

### Rules

- Use Jakarta Bean Validation for request shape and basic constraints.
- Keep technical validation at the HTTP boundary.
- Keep business invariants in the domain/application layer.
- Validate query parameters and path variables when needed, not just request bodies.
- Return consistent validation failure shapes.

### Guidance

- `@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@Min`, etc. protect transport input.
- They do **not** replace domain rules.
- Semantic failures that pass shape validation often belong in `409` or `422`, not `400`.

## Error Response Standards

Use centralized mapping and keep the contract predictable.

### Rules

- Use `@RestControllerAdvice` for global HTTP error mapping.
- Prefer `ProblemDetail` as the default error format.
- Keep titles, statuses, and details consistent across the API.
- Document validation and domain failure responses.
- Never expose stack traces or sensitive internals in API responses.

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

## Pagination, Filtering, and Sorting

### Rules

- Paginate large collections.
- Do not assume `Pageable` is the only contract shape.
- Prefer explicit query parameters that you can document and evolve intentionally.
- Be careful with offset pagination for large or highly mutable datasets.
- Consider cursor-based pagination when stability matters.

### Example Query Contract

```text
GET /api/workspaces?limit=20&cursor=opaque-token&sort=createdAt:desc&ownerId=123
```

### Guidance

- Use `limit` with sensible caps.
- Keep filter parameters explicit and named.
- Make sorting fields documented and whitelistable.
- Return metadata that clients can actually use.

## Security and Operational Headers

### Rules

- Configure CORS intentionally; never leave it wide open by accident.
- Return `WWW-Authenticate` or equivalent auth cues where appropriate.
- Consider CSP, `X-Content-Type-Options`, and `X-Frame-Options` at the platform/app boundary.
- Do not leak internal topology, framework versions, or debug-only headers in public APIs.

### Guidance

These headers are often enforced in infrastructure or security config, but API design reviews should
still validate them.

## Review Checklist

Use this when reviewing an API before implementation or merge:

- Are URLs resource-oriented and consistently named?
- Are HTTP methods semantically correct?
- Are DTOs separated from domain and persistence models?
- Are status codes accurate for success and failure paths?
- Are validation and semantic failures distinguished?
- Is the error contract consistent and documented?
- Is pagination/filtering stable and bounded?
- Are security/CORS/header concerns intentionally handled?

## Common Mistakes

- ❌ Action-based URLs instead of resource-based URLs
- ❌ Returning entities directly from controllers
- ❌ Using Java/Lombok DTO patterns in a Kotlin-first stack
- ❌ Using offset pagination blindly for unstable datasets
- ❌ Treating all errors as `400`
- ❌ Leaking internal exception details to clients
- ❌ Mixing API contract decisions with persistence implementation details

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core Spring Boot reactive controller and boundary rules
- `spring-boot-openapi` — OpenAPI and Swagger specifics
- `spring-boot-testing-webflux` — HTTP contract and reactive controller verification
- `spring-boot-security` — Authn/authz implementation concerns
