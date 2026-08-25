# Shared Spring Boot Common Module (`shared:spring-boot-common`)

Spring Boot 4 integration layer for Profile Tailors shared Kotlin libraries, offering global RFC 9457 ProblemDetail exception handlers, WebFilter workspace context resolvers, Jackson Kotlin serialization, reactive base repositories, and HasherRegistry beans.

## Role in the platform

Serves as the Spring WebFlux adapter bridge for Profile Tailors shared modules and the `server/smp` monolith. It translates domain exceptions (`EntityNotFoundException`, `BusinessRuleValidationException`) into RFC 9457 `ProblemDetail` HTTP error responses, configures reactive workspace context filters (`X-Workspace-Id`), and provides Spring-managed event emitters.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines & Flow
- **Framework & Libraries**: Spring Boot 4.0, Spring WebFlux, Jackson Kotlin, Spring Data R2DBC
- **Testing**: JUnit 5, AssertJ, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:spring-boot-common`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:spring-boot-common:test
```

### Environment variables

No environment variables required for unit testing.

## Project structure

```text
shared/spring-boot-common/
├── src/main/kotlin/com/profiletailors/springboot/
│   ├── web/        # ApiController, GlobalExceptionHandler, ProblemDetail builders
│   ├── filter/     # WorkspaceContextWebFilter (X-Workspace-Id resolver)
│   ├── event/      # EventEmitter Spring application event publisher
│   ├── json/       # Jackson ObjectMapper custom serializers
│   └── config/     # Spring Auto-Configuration
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:spring-boot-common:test
```

## API / Public interface

Main types in package `com.profiletailors.springboot`:

- `ApiController`: Base controller providing standardized HTTP response envelopes and error mapping.
- `GlobalExceptionHandler`: Exception handler producing RFC 9457 `ProblemDetail` payloads.
- `WorkspaceContextWebFilter`: Reactive WebFilter extracting `X-Workspace-Id` into `ResourceContext`.
- `EventEmitter`: Spring application event publisher adapter for `EventPublisher`.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.spring.boot.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
