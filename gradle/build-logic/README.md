# Profile Tailors Gradle Build Logic

Centralized Gradle convention plugins providing reusable build configuration, dependency management, code quality rules, security scanning, and licence reporting across Kotlin subprojects.

## Role in the platform

Encapsulates standard Gradle build logic for the entire Profile Tailors backend and shared modules (`server/smp`, `shared/*`). It defines custom Gradle convention plugins (`com.profiletailors.kotlin.library`, `com.profiletailors.spring.boot.library`, `com.profiletailors.spring.boot.application`, `com.profiletailors.security.owasp`, `com.profiletailors.spotless`, `com.profiletailors.legal.licence-report`), eliminating build script duplication and enforcing repository-wide Kotlin/Spring standards.

## Tech stack

- **Runtime & Language**: Java 25, Kotlin 2.4, Gradle Kotlin DSL
- **Plugins & Libraries**: Spring Boot 4.0 Gradle Plugin, Detekt 1.23, OWASP Dependency Check, Spotless, Kover

## Getting started

### Prerequisites

- Java JDK `>= 25`
- Gradle wrapper (`./gradlew` on Linux/macOS, `gradlew.bat` on Windows)

### Installation

Included automatically when building Kotlin projects from the monorepo root:

```bash
./gradlew tasks
```

### Running locally

Test build logic convention plugins:

```bash
./gradlew :gradle:build-logic:test
```

### Environment variables

No specific environment variables required.

## Project structure

```text
gradle/build-logic/
├── src/
│   ├── main/kotlin/com/profiletailors/buildlogic/
│   │   ├── formatting/   # Spotless code formatting plugin
│   │   ├── legal/        # Licence report plugin
│   │   ├── library/      # Pure Kotlin library convention plugin
│   │   ├── security/     # OWASP dependency check plugin
│   │   └── springboot/   # Spring Boot application/library convention plugins
│   └── test/             # JUnit 5 build logic tests
├── build.gradle.kts      # Plugin definitions and dependencies
└── settings.gradle.kts   # Build logic project settings
```

## Testing

Run JUnit 5 unit tests for build plugins:

```bash
./gradlew :gradle:build-logic:test
```

## API / Public interface

Exposes standard Gradle plugin IDs for inclusion in subproject `build.gradle.kts` files:

- `com.profiletailors.kotlin.library` — Standard Kotlin library setup (Detekt, Kover, Spotless)
- `com.profiletailors.spring.boot.library` — Spring Boot library setup
- `com.profiletailors.spring.boot.application` — Executable Spring Boot service setup
- `com.profiletailors.security.owasp` — OWASP vulnerability scanner
- `com.profiletailors.spotless` — Spotless code formatting
- `com.profiletailors.legal.licence-report` — Open source licence reporting

## Configuration

- `build.gradle.kts`: Declares convention plugin IDs and implementation classes.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
