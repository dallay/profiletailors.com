# Shared Presentation Module (`shared:presentation`)

Framework-agnostic presentation DTOs, pagination wrappers, opaque cursor encoders, sorting primitives, and API response envelopes for the Profile Tailors backend.

## Role in the platform

Provides domain-level response structures used by application-layer query handlers and REST controllers in `server/smp`. It enables offset-based and cursor-based pagination (`PageResponse`, `OffsetPageResponse`, `CursorPageResponse`) and sorting without leaking HTTP or framework dependencies into core domain logic.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4
- **Testing**: JUnit 5, AssertJ, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:presentation`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:presentation:test
```

### Environment variables

No environment variables required.

## Project structure

```text
shared/presentation/
├── src/main/kotlin/com/profiletailors/presentation/
│   ├── pagination/ # PageResponse, CursorEncoder, TimestampCursor, OffsetPageResponse
│   └── filter/     # Filter and sort request criteria primitives
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:presentation:test
```

## API / Public interface

Main types in package `com.profiletailors.presentation`:

- `PageResponse<T>`: Unified response envelope for paginated collections.
- `OffsetPageResponse<T>`: Page number and page size envelope.
- `CursorPageResponse<T>`: Opaque cursor-based envelope for infinite scrolling.
- `CursorEncoder`: Utilities to encode and decode base64 opaque cursors.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
