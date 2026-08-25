# Shared Lead Capture Waitlist Module (`shared:lead-capture:waitlist`)

Waitlist domain logic, signup processing, early-access invitation management, and waitlist entry validation for Profile Tailors.

## Role in the platform

Implements the waitlist domain context. It depends on `:shared:lead-capture:common` and provides use cases for registering waitlist prospects, checking duplicate entries, managing invite status, and searching waitlist records for back-office administration in `server/smp` and `apps/web/admin`.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines
- **Testing**: JUnit 5, Kotest, ArchUnit, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:lead-capture:waitlist`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:lead-capture:waitlist:test
```

### Environment variables

No environment variables required.

## Project structure

```text
shared/lead-capture/waitlist/
├── src/main/kotlin/com/profiletailors/leadcapture/
│   └── waitlist/ # Waitlist domain models, services, and validation rules
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:lead-capture:waitlist:test
```

## API / Public interface

Exported package `com.profiletailors.leadcapture.waitlist` provides waitlist registration domain services, validation logic, and query interfaces.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../../LICENSE) for details.

---
Back to [Root README](../../../README.md)
