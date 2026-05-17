---
name: unit-test-wiremock-rest-api
description: Use when migrating legacy WireMock API testing guidance to `spring-boot-testing-integrations`.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Deprecated: Unit Test WireMock REST API

This skill is **deprecated** and has been absorbed into `spring-boot-testing-integrations`.

## Replacement

Use `spring-boot-testing-integrations` for:

- outbound HTTP client tests with WireMock
- request verification
- timeout and failure-path simulation
- adapter-level integration tests with `WebClient`

## Migration Guidance

- Prefer `WebClient` examples for this stack
- Always use dynamic ports
- Verify outgoing request shape, not only returned data

## New Home

- `spring-boot-testing-integrations`
