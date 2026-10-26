---
name: spring-boot
description: >-
  Implement or review Spring Boot infrastructure in Profile Tailors' Kotlin, WebFlux, and coroutine
  backend. Use for HTTP adapters, bean discovery, R2DBC, transactions, configuration, security, or
  infrastructure tests; use companion skills for specialized APIs and testing.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  category: backend-platform
  family: spring-boot
  source: local
  version: 2026-09-28
---

# Spring Boot Infrastructure

This is the entry point for Spring Boot changes in the Kotlin backend. Read the target bounded
context's implementation first and follow its established adapter and wiring patterns. Do not
copy examples from this skill when the local implementation provides a more specific contract.

## Boundaries

- Use `hexagonal-architecture` for domain/application/infrastructure placement and dependency
  direction; use `ddd-architecture` for domain model contracts.
- Keep Spring, WebFlux, persistence, and provider concerns in infrastructure. Domain and
  application code remain framework-agnostic.
- Application service discovery uses the project's
  `com.profiletailors.common.domain.Service` marker and the configured application scan. Check
  `SmpApplication.kt` and the target module before changing discovery or wiring.
- HTTP controllers translate transport input/output and delegate behavior. Persistence adapters
  map storage records to internal models. Do not expose persistence entities through HTTP.
- Treat transactions as infrastructure boundaries. Follow the established R2DBC policy and keep
  transaction scope aligned with the use case that needs atomicity; do not add Spring transaction
  annotations to application classes.

## Implementing an adapter

1. Find a nearby adapter in the same bounded context and read its configuration, DTOs, mapping, and
   tests.
2. Keep transport and storage types at their boundary. Add a port only if an inner layer needs an
   external capability or the boundary has a clear testing or substitution benefit.
3. Use coroutines for request/response work and `Flow` when streaming is part of the contract.
   Preserve cancellation and avoid blocking calls in reactive request flows.
4. Map expected application/domain failures at the HTTP boundary using the local
   `ProblemDetail`/RFC 9457 conventions. Keep error responses stable and avoid duplicating mappings
   in controllers.
5. Add the narrowest test that exercises the changed boundary; use existing fixtures and package
   scripts rather than inventing a new test setup.

## Stack defaults

- HTTP: Spring WebFlux and the coroutine patterns already used in the target context.
- SQL: R2DBC/reactive persistence is the default. Do not introduce hidden blocking access in a
  reactive flow. Isolate and document an unavoidable blocking integration at its boundary.
- HTTP clients: use the existing `WebClient` or Spring HTTP interface pattern. Do not introduce
  `RestTemplate`.
- Security: use the reactive security configuration for this application; consult
  `spring-boot-security` before changing authentication or authorization.
- Configuration: prefer typed configuration properties for grouped settings and validate required
  values at startup.
- API documentation: follow the public controller contract and use `spring-boot-openapi` for
  OpenAPI-specific changes.

These are defaults for the current backend. Check Gradle's version catalog and the target module
before relying on a library API or introducing dependencies.

## Testing selection

Choose a test that proves the changed boundary without loading unrelated infrastructure:

| Change | Start with |
| --- | --- |
| Domain or application behavior | Plain unit test with a fake or mock port |
| WebFlux request, validation, or error mapping | `WebTestClient` slice test |
| R2DBC mapping or persistence behavior | Focused integration test using existing database fixtures |
| Configuration binding | Existing configuration test pattern |
| Cross-adapter wiring | Full application test only when narrower tests cannot prove it |

Use `spring-boot-testing-core`, `spring-boot-testing-webflux`, and
`spring-boot-testing-integrations` for details. Do not default every test to `@SpringBootTest`.

## Repository examples

Read these before creating a parallel convention:

- `shared/spring-boot-common/` for shared Spring Boot infrastructure.
- `server/smp/src/main/kotlin/com/profiletailors/smp/ideas/infrastructure/http/IdeasController.kt`
  and `IdeasProblemDetailsHandler.kt` for a WebFlux endpoint and problem details.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt`
  for the existing reactive transaction runner.
- `server/smp/src/main/kotlin/com/profiletailors/smp/hashtags/infrastructure/http/HashtagsProblemDetailsHandler.kt`
  for focused HTTP error mapping.

## Companion skills

- `spring-boot-api-standards` for API conventions.
- `spring-boot-security` for authentication and authorization.
- `spring-boot-openapi` for OpenAPI.
- `spring-boot-resilience` and `spring-boot-messaging` for resilience and messaging.
- `spring-boot-actuator` for Actuator and observability.
- `spring-boot-testing-core`, `spring-boot-testing-webflux`, and
  `spring-boot-testing-integrations` for testing.
- `spring-boot-project-bootstrap` only when the task actually bootstraps a Spring Boot project.

## Verify

Read the repository recipes with `just -l`, then run the narrowest relevant check for the changed
module. Do not report a check as passing unless it was run.
