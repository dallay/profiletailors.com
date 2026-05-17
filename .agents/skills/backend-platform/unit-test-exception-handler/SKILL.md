---
name: unit-test-exception-handler
description: Use when migrating legacy exception-handler testing guidance to `spring-boot-testing-webflux`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Exception Handler

This skill is **deprecated** for the current backend standard.

## Replacement

Use `spring-boot-testing-webflux` for:

- `@RestControllerAdvice` tests
- `ProblemDetail` response verification
- validation error payload checks
- reactive HTTP error mapping tests

## Why

The older skill assumes `MockMvc` and servlet-style controller testing. The current stack is
WebFlux-first and should verify exception mapping with `WebTestClient` and `@WebFluxTest`.

## New Home

- `spring-boot-testing-webflux`
