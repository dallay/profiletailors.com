---
name: wiremock-standalone-docker
description: Use when migrating standalone WireMock Docker setup guidance to the Spring Boot integration-testing ecosystem.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Deprecated: WireMock Standalone Docker Skill

This skill is **deprecated** as a standalone backend-platform skill.

## Replacement

Use `spring-boot-testing-integrations` for WireMock-based integration testing patterns, and keep
Docker-specific setup as supporting material within the Spring Boot testing ecosystem.

## Why Deprecated

The current backend organization groups Spring Boot integration testing concerns under the
`spring-boot/` tree. WireMock Docker setup is useful, but it should no longer stand apart from the
main integration-testing guidance.

## New Home

- `spring-boot-testing-integrations`
