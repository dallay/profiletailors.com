---
name: unit-test-security-authorization
description: Use when migrating legacy authorization testing guidance to `spring-boot-testing-webflux` or reactive security-focused tests.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test Security Authorization

This skill is **deprecated** and its guidance should move to the reactive testing stack.

## Replacement

Use `spring-boot-testing-webflux` for endpoint-level security tests and the `spring-boot-security`
companion skill for security design rules.

## Migration Guidance

- Test anonymous, forbidden, and authorized HTTP paths explicitly
- Prefer reactive security assumptions over servlet-centric ones
- Keep method-security checks separate from HTTP boundary checks when helpful

## New Homes

- `spring-boot-testing-webflux`
- `spring-boot-security`
