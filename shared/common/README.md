# Shared Common Module (`shared:common`)

Framework-agnostic Kotlin domain primitives, immutable value objects, error hierarchies, domain event interfaces, and common model abstractions used across all Profile Tailors backend modules.

## Role in the platform

Forms the foundation of the shared kernel for the Kotlin backend. It is imported by `server/smp`, `shared/bus`, `shared/security`, `shared/storage`, and other shared Kotlin libraries. It defines universal domain abstractions (`Email`, `Username`, `WorkspaceId`, `AggregateRoot`, `ValueObject`, `DomainEvent`) without relying on Spring or external frameworks.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines
- **JSON**: Jackson Kotlin Module
- **Testing**: JUnit 5, AssertJ, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:common`.

### Running locally

Compile and run tests:

```bash
./gradlew :shared:common:test
```

### Environment variables

No environment variables required.

## Project structure

```text
shared/common/
├── src/main/kotlin/com/profiletailors/common/
│   ├── domain/
│   │   ├── bus/           # Domain event and query response contracts
│   │   ├── context/       # Principal and tenant context primitives
│   │   ├── error/         # Domain exception types (EntityNotFoundException, AggregateException)
│   │   ├── model/         # Base entities, WorkspaceId, Language, AuditableEntity
│   │   └── vo/            # Immutable value objects (Email, Username, Name, Credential, IpHash)
│   └── util/              # Pure utility functions and system environment wrappers
└── build.gradle.kts
```

## Testing

Run JUnit 5 unit tests:

```bash
./gradlew :shared:common:test
```

## API / Public interface

Key exported types in package `com.profiletailors.common`:

- `@ValueObject`, `ValueObject`: Marker annotation and base interface for immutable value objects.
- `Email`, `Username`, `FirstName`, `LastName`, `Name`: Domain value objects with strict invariant validation.
- `WorkspaceId`: Strongly-typed UUID wrapper for multi-tenant isolation.
- `AggregateRoot`: Marker annotation for DDD aggregate roots.
- `DomainEvent`: Common domain event interface.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
