---
name: spring-boot-test-patterns
description: Use when migrating older Spring Boot testing guidance to the consolidated `spring-boot/testing-core`, `spring-boot/testing-webflux`, and `spring-boot/testing-integrations` skills.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Deprecated: Spring Boot Testing Patterns

This skill has been **superseded** by the testing family under `spring-boot/`.

## Use These Instead

- `spring-boot-testing-core` — service tests, config properties, JSON, validation, mappers
- `spring-boot-testing-webflux` — `@WebFluxTest`, `WebTestClient`, `ProblemDetail`, reactive security
- `spring-boot-testing-integrations` — WireMock, cache integration, events, scheduling, focused container-based tests

## Migration Mapping

| Old concern | New skill |
|---|---|
| Unit/service tests | `spring-boot-testing-core` |
| MVC/WebFlux controller tests | `spring-boot-testing-webflux` |
| Full integration / containers | `spring-boot-testing-integrations` |
| WireMock / external API tests | `spring-boot-testing-integrations` |
| Cache behavior | `spring-boot-testing-integrations` |

## Why Deprecated

This older skill mixed:

- MVC and WebFlux testing
- JPA-first and reactive-first guidance
- too many testing layers in one place

The new structure is aligned with the official backend baseline:

- Kotlin
- coroutines
- Spring Boot 4
- WebFlux
- focused testing by boundary
