---
name: unit-test-parameterized
description: Use when migrating legacy parameterized-testing guidance to `spring-boot-testing-core` or general backend unit testing.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Parameterized

This skill is **deprecated** in favor of the consolidated fast-testing guidance.

## Replacement

Use `spring-boot-testing-core` for parameterized tests that support:

- boundary analysis
- DTO validation matrices
- pure function permutations
- mapper and utility scenarios

## Note

This guidance is generic JUnit practice more than a Spring-specific concern, so it now belongs inside
core testing conventions rather than a separate standalone skill.

## New Home

- `spring-boot-testing-core`
