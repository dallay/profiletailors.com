# Shared Notifications Module (`shared:notifications`)

Domain abstractions and notification dispatching services for email alerts, transactional messaging, and waitlist confirmation notifications across Profile Tailors backend services.

## Role in the platform

Coordinates notification dispatch for backend operations. It depends on `:shared:common`, `:shared:bus`, `:shared:lead-capture:common`, and `:shared:lead-capture:waitlist`. It handles transactional email delivery and event-driven notifications triggered by user registration, post scheduling, and waitlist signups.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines
- **Logging**: SLF4J
- **Testing**: JUnit 5, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:notifications`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:notifications:test
```

### Environment variables

No specific environment variables required for unit testing.

## Project structure

```text
shared/notifications/
├── src/main/kotlin/com/profiletailors/notifications/ # Notification models and handlers
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:notifications:test
```

## API / Public interface

Exported package `com.profiletailors.notifications` provides notification command handlers, template abstractions, and dispatch services.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
