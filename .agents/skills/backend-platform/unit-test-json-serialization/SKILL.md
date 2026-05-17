---
name: unit-test-json-serialization
description: Use when migrating legacy JSON serialization testing guidance to `spring-boot-testing-core`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test JSON Serialization

This skill is **deprecated** and has been absorbed into `spring-boot-testing-core`.

## Replacement

Use `spring-boot-testing-core` for:

- `@JsonTest`
- DTO serialization/deserialization verification
- custom serializer/deserializer checks
- date/time and field-name contract assertions

## Migration Guidance

- Test DTO contracts, not persistence entities
- Keep JSON tests focused on the API payload shape
- Use `@JsonTest` when the Jackson contract matters

## New Home

- `spring-boot-testing-core`
