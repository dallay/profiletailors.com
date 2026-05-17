---
name: unit-test-config-properties
description: Use when migrating legacy configuration-properties testing guidance to `spring-boot-testing-core`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Config Properties

This skill is **deprecated** and has been absorbed into `spring-boot-testing-core`.

## Replacement

Use `spring-boot-testing-core` for:

- `@ConfigurationProperties` binding tests
- `ApplicationContextRunner` patterns
- configuration validation tests
- default-value and type-conversion verification

## Migration Guidance

- Prefer `ApplicationContextRunner` for focused property tests
- Test prefix binding, defaults, validation, and type conversion explicitly
- Avoid booting the full application for simple property binding checks

## New Home

- `spring-boot-testing-core`
