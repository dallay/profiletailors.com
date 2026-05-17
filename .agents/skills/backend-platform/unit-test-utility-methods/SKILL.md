---
name: unit-test-utility-methods
description: Use when migrating legacy utility-method testing guidance to `spring-boot-testing-core` or general backend unit testing.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Utility Methods

This skill is **deprecated** in favor of the consolidated testing baseline.

## Replacement

Use `spring-boot-testing-core` for:

- utility and helper tests
- pure-function verification
- null/empty edge cases
- formatter and conversion checks

## Note

This guidance is generic unit-testing practice rather than a Spring-specific concern, so it now fits
better inside the general fast-test baseline.

## New Home

- `spring-boot-testing-core`
