---
name: spring-boot-rest-api-standards
description: Use when migrating older Spring Boot REST API guidance to the companion `spring-boot-api-standards` skill.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Deprecated: Spring Boot REST API Standards

This skill is **deprecated** for the current backend standard.

Its useful guidance now belongs primarily to `spring-boot-api-standards`, with supporting concerns
split across the reactive Spring Boot ecosystem.

## Use These Instead

- `spring-boot-api-standards` — URL design, verbs, status codes, DTO contracts, validation,
  pagination, security headers, and consistent error responses
- `spring-boot` — reactive controller baseline, DTO boundaries, `ProblemDetail`, and infrastructure
  rules
- `spring-boot-openapi` — OpenAPI and Swagger specifics
- `spring-boot-testing-webflux` — HTTP contract verification and reactive controller tests

## Why Deprecated

The older skill mixed valuable API design guidance with assumptions that do not fit the canonical
backend stack, including:

- Java/Lombok DTO conventions instead of Kotlin-first DTOs
- MVC/servlet-centric examples
- blocking/JPA-friendly patterns
- `Pageable` as the default pagination mindset
- imperative transaction assumptions inside API guidance

The new companion structure keeps API contract standards while aligning with:

- Spring Boot 4
- Kotlin
- coroutines
- WebFlux
- reactive boundary design
