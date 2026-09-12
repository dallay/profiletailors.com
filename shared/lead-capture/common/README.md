# Shared Lead Capture Common Module (`shared:lead-capture:common`)

Domain primitives and interfaces for lead capture, waitlist entries, and prospect acquisition across Profile Tailors backend modules.

## Role in the platform

Defines core lead acquisition abstractions used by waitlist management services (`shared/lead-capture/waitlist`), notification workflows (`shared/notifications`), and administrative endpoints (`server/smp`). It establishes lead capture invariants independently from persistence or HTTP delivery frameworks.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4
- **Testing**: JUnit 5, Kotest, ArchUnit

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:lead-capture:common`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:lead-capture:common:test
```

### Environment variables

No environment variables required.

## Project structure

```text
shared/lead-capture/common/
├── src/main/kotlin/com/profiletailors/leadcapture/
│   └── common/   # Core lead capture domain models and contracts
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:lead-capture:common:test
```

## API / Public interface

Exported package `com.profiletailors.leadcapture.common` provides core domain models and validation interfaces for prospect signups and early access tracking.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../../LICENSE) for details.

---
Back to [Root README](../../../README.md)
