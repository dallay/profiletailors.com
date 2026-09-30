---
name: kotlin
description: Use when working with Kotlin source, coroutines, domain models, or Kotlin tests in Profile Tailors.
license: Apache-2.0
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  category: languages-typing
  family: kotlin
  source: local
  version: 2026-09-30
---
# Kotlin Skill

## Overview

Write idiomatic, type-safe Kotlin that follows the repository's architecture and current nearby
patterns. For backend changes, read `.agents/AGENTS.md`, the relevant ADRs, and
`.agents/skills/hexagonal-architecture/SKILL.md`. For dependency versions, use
`gradle/libs.versions.toml` and the module build configuration.

## Changes

### Types and models

- Prefer immutable `val` properties and data classes for value-shaped data. Use a regular class
  when identity, controlled mutation, or invariant protection requires it.
- Represent domain concepts with validated value objects when they have domain meaning or
  construction rules. Mark new value objects with the repository's `@ValueObject` marker.
- Use sealed hierarchies when the set of states is closed and callers should handle every case.
- Prefer explicit types and exhaustive `when` expressions over unchecked casts or reflection.
- Avoid `!!`. Use smart casts, a checked Elvis branch, or `requireNotNull` with a useful failure
  message.
- Use imports for qualified names. Do not add KDoc or explanatory code comments; express contracts
  through names, types, structure, and tests.

```kotlin
import com.profiletailors.common.domain.ValueObject
import java.util.UUID

@JvmInline
@ValueObject
value class WorkspaceId(val value: UUID)

@ValueObject
data class WorkspaceName(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

fun displayName(name: String?): String = name ?: "Unknown"
```

### Backend boundaries

- Keep domain code framework-free. Keep application use cases framework-agnostic and dependent on
  domain-defined ports. Infrastructure implements those ports and owns Spring, WebFlux, R2DBC, and
  external-provider code.
- Use `com.profiletailors.common.domain.Service` for application service discovery. Do not use
  Spring stereotypes in domain or application code.
- Follow the existing CQRS and package-by-feature patterns. Do not add layers or generic utilities
  without a stable responsibility.
- Keep persistence entities, transport DTOs, and provider schemas separate from domain models.
- Put invariants in domain types and policies, not controllers, mappers, or persistence adapters.

## Usage

### Coroutines and Flow

- Use `suspend` functions for asynchronous request/response work and `Flow` when streaming is part
  of the contract.
- Preserve structured concurrency and cancellation. Avoid `GlobalScope` and unowned coroutine
  scopes.
- R2DBC and WebFlux work is already non-blocking. Do not wrap repositories in
  `withContext(Dispatchers.IO)` or use `flowOn(Dispatchers.IO)` as a generic rule.
- If an unavoidable third-party API performs blocking I/O, isolate that boundary and use an
  appropriate dispatcher there. Do not move coroutine-native work to a blocking dispatcher.
- Prefer repository-defined exception and result contracts. Use `Result<T>` only when it makes an
  expected failure part of the function contract; do not wrap domain exceptions merely to avoid
  throwing them.

### Tests

- Follow the module's existing JUnit 5 and Kotest conventions. Use MockK for test doubles and
  coroutine-aware APIs for suspend functions.
- Use `runTest` for coroutine unit tests and the existing integration fixtures for database,
  WebFlux, or provider behavior.
- Assert observable behavior and domain invariants. Keep persistence and transport details out of
  pure domain tests.
- Read the module's `build.gradle.kts` and `gradle/libs.versions.toml` before adding test
  dependencies or relying on a framework version.

## Troubleshooting

Use the configured Kotlin formatter, compiler, Detekt, and architecture rules. Fix the underlying
finding rather than adding a suppression or weakening configuration. Run checks through the
repository recipes listed by `just -l`.

## References

- `.agents/AGENTS.md`
- `.agents/skills/hexagonal-architecture/SKILL.md`
- `gradle/libs.versions.toml`
