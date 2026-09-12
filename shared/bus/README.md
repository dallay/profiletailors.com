# Shared Bus Module (`shared:bus`)

Framework-agnostic, in-process CQRS mediator, command/query dispatcher, pipeline behavior middleware chain, and event bus implementation for the Profile Tailors backend.

## Role in the platform

Provides the Mediator pattern implementation for internal communication between bounded contexts in `server/smp` and shared backend modules. It enables loose coupling by dispatching commands, queries, notifications, and domain events within a single process without framework dependencies.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines
- **Testing**: JUnit 5, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:bus`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:bus:test
```

### Environment variables

No environment variables required.

## Project structure

```text
shared/bus/
├── src/main/kotlin/com/profiletailors/bus/
│   ├── command/      # Command, CommandHandler, CommandProvider
│   ├── event/        # EventPublisher, EventConsumer, Subscribe, EventMultiplexer
│   ├── notification/ # Notification, NotificationHandler, NotificationProvider
│   ├── pipeline/     # PipelineBehavior middleware chain
│   ├── query/        # Query, QueryHandler, QueryProvider
│   └── Mediator.kt   # Mediator interface and builder registry
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:bus:test
```

## API / Public interface

Main types in package `com.profiletailors.bus`:

- `Mediator`: Central dispatch interface for `send(command)`, `query(query)`, and `publish(event)`.
- `Command<R>` / `CommandHandler<C, R>`: Mutation contract and handler.
- `Query<R>` / `QueryHandler<Q, R>`: Read query contract and handler.
- `PipelineBehavior<C, R>`: Pipeline interceptor for cross-cutting validation, logging, and metrics.

## Configuration

- `build.gradle.kts`: Configured via `com.profiletailors.kotlin.library` convention plugin.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
