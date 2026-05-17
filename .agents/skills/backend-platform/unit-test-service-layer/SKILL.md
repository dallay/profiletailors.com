---
name: unit-test-service-layer
description: Use when migrating legacy service-layer unit testing guidance to `spring-boot-testing-core`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Service Layer

This skill is **deprecated** and its useful guidance now belongs to `spring-boot-testing-core`.

## Replacement

Use `spring-boot-testing-core` for:

- application service tests
- use-case orchestration tests
- mocked port / collaborator verification
- coroutine-aware service testing

## Migration Guidance

- Prefer constructor-based tests with mocked collaborators
- Use coroutine-aware testing for `suspend` functions
- Do not start Spring for pure service logic
- Test behavior, not framework wiring

## New Home

- `spring-boot-testing-core`
