# Shared Security Module (`shared:security`)

Framework-agnostic security primitives, password hashing contracts, principal context abstractions, and multi-tenant resource resolution interfaces for the Profile Tailors backend.

## Role in the platform

Defines core security interfaces used across all bounded contexts in `server/smp` and shared Kotlin libraries. It establishes the `Hasher` interface (SHA-256 and HMAC-SHA256 implementations), `PrincipalContext`, and `ResourceContext` for workspace authorization without depending on Spring Security or WebFlux.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4
- **Testing**: JUnit 5, AssertJ, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:security`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:security:test
```

### Environment variables

No environment variables required.

## Project structure

```text
shared/security/
├── src/main/kotlin/com/profiletailors/security/
│   ├── hashing/  # Hasher interface, Sha256Hasher, HmacHasher
│   └── context/  # PrincipalContext, ResourceContext, AuthenticatedPrincipal
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:security:test
```

## API / Public interface

Main types in package `com.profiletailors.security`:

- `Hasher`: Functional interface for secure hashing (`hash(input)`).
- `Sha256Hasher`: Standard SHA-256 implementation.
- `HmacHasher`: HMAC-SHA256 implementation requiring non-blank secret key.
- `PrincipalContext`: Thread-local / reactive context accessor for the current `AuthenticatedPrincipal`.
- `ResourceContext`: Type-safe accessor for current `WorkspaceId`.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
