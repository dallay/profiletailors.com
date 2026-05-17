---
name: spring-boot-crud-patterns
description: Use when migrating older CRUD guidance to `spring-boot-data-jpa-legacy` or planning a future reactive CRUD companion for Spring Boot 4.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Deprecated: Spring Boot CRUD Patterns

This skill is **deprecated** for the current backend standard.

## Why Deprecated

The older CRUD guidance is built around assumptions that are not canonical for this backend stack:

- Spring Boot 3.x wording
- Java-first examples
- Spring Data JPA as the default persistence model
- `@DataJpaTest` as baseline persistence verification
- imperative `@Transactional` service patterns

The canonical backend baseline is now:

- Spring Boot 4
- Kotlin
- coroutines
- WebFlux
- reactive-first persistence boundaries

## Use These Instead

- `spring-boot` — reactive controller, DTO, transaction, and persistence-boundary rules
- `spring-boot-data-jpa-legacy` — legacy blocking/JPA persistence guidance
- `spring-boot-api-standards` — resource URLs, verbs, DTO contracts, status codes, pagination

## Future Direction

If the team wants a CRUD-focused helper again, it should be reintroduced later as a **reactive**
companion aligned with:

- Kotlin data classes
- `suspend` / `Flow`
- reactive persistence adapters
- `ProblemDetail`
- WebFlux-first HTTP contracts
