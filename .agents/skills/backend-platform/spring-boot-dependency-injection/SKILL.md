---
name: spring-boot-dependency-injection
description: Use when migrating older Spring Boot dependency-injection guidance to the consolidated `spring-boot` core skill.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Deprecated: Spring Boot Dependency Injection

This skill is **deprecated** for the current backend standard.

Its guidance has been absorbed into the `spring-boot` core skill.

## Use This Instead

- `spring-boot` — canonical bean wiring, constructor injection, optional dependency, and boundary rules

## What Moved

The following concerns now live in `spring-boot`:

- constructor-first dependency injection
- avoiding field injection
- `@Configuration` + `@Bean` wiring
- `@Primary`, `@Qualifier`, `@Profile`, and `@ConditionalOnProperty`
- optional dependencies with `ObjectProvider` or no-op implementations
- keeping wiring in infrastructure, not business code

## Why Deprecated

The dependency-injection rules are now part of the core backend baseline. Keeping a separate active
skill would duplicate guidance that belongs to the main Spring Boot infrastructure skill.
