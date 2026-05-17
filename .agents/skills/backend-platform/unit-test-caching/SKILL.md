---
name: unit-test-caching
description: Use when migrating legacy cache-testing guidance to `spring-boot-testing-integrations`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Caching

This skill is **deprecated** and has been absorbed into `spring-boot-testing-integrations`.

## Replacement

Use `spring-boot-testing-integrations` for:

- cache hit/miss verification
- invalidation behavior
- proxy-aware cache tests
- focused cache integration checks

## Migration Guidance

- Distinguish unit-level decision tests from proxy-driven integration behavior
- In reactive applications, document whether you cache values or publishers
- Prefer focused integration tests when cache semantics depend on Spring wiring

## New Home

- `spring-boot-testing-integrations`
