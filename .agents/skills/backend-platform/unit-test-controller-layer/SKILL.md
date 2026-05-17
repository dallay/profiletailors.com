---
name: unit-test-controller-layer
description: Use when migrating legacy controller-layer testing guidance to `spring-boot-testing-webflux`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Controller Layer

This skill is **deprecated** for the current backend standard.

## Replacement

Use `spring-boot-testing-webflux` instead.

## Why

This older skill is centered on:

- `MockMvc`
- `@WebMvcTest`
- servlet-style controller assumptions

The current backend standard is:

- Spring Boot 4
- Kotlin
- coroutines
- WebFlux
- `WebTestClient`

## Migration Guidance

- Replace `MockMvc` with `WebTestClient`
- Replace `@WebMvcTest` with `@WebFluxTest`
- Keep repository and persistence concerns out of controller slices
- Test `ProblemDetail`, validation, security allow/deny, and response contracts from reactive HTTP boundaries

## New Home

- `spring-boot-testing-webflux`
