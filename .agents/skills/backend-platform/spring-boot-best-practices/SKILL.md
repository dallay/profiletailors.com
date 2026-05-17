---
name: spring-boot-best-practices
description: Use when migrating older Spring Boot best-practice guidance to the consolidated `spring-boot` core skill.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  author: danvega (adapted by profiletailors)
  version: "2.0"
---

# Deprecated: Spring Boot & Kotlin Best Practices

This skill is **deprecated** for the current backend standard.

Its useful guidance has been absorbed into the `spring-boot` core skill.

## Use This Instead

- `spring-boot` — Kotlin + coroutines + WebFlux + Spring Boot 4 baseline

## What Moved

The following concerns now live in `spring-boot`:

- constructor injection and wiring rules
- controller design with `suspend` functions and `Flow`
- DTO boundaries and not exposing persistence entities
- `ProblemDetail` and centralized exception handling
- quick testing baseline
- `@ConfigurationProperties`
- package-by-feature / vertical slicing
- HTTP client guidance (`WebClient`, `@HttpExchange`)
- observability baseline with Reactor/coroutines context awareness

## Why Deprecated

Keeping both skills active would duplicate the same backend guidance and increase the risk of drift.
The `spring-boot` skill is now the canonical source of truth for implementation practices in this
reactive stack.
