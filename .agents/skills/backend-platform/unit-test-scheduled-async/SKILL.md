---
name: unit-test-scheduled-async
description: Use when migrating legacy scheduled and async testing guidance to `spring-boot-testing-integrations`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Scheduled Async

This skill is **deprecated** and has been absorbed into `spring-boot-testing-integrations`.

## Replacement

Use `spring-boot-testing-integrations` for:

- scheduler boundary verification
- background task checks
- async side-effect tests
- focused integration tests for timing-sensitive behavior

## Why

The older guidance leans on `CompletableFuture` and async patterns that are not the primary model for
this Kotlin + coroutines + WebFlux stack.

## New Home

- `spring-boot-testing-integrations`
