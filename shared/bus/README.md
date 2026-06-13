# shared:bus

In-process CQRS mediator and event bus for the Profile Tailors backend. Provides command, query, notification, and event dispatching with pipeline behaviors — all within a single process.

## Overview

This module implements the **Mediator pattern** for internal communication between bounded contexts. It is framework-agnostic (pure Kotlin, no Spring dependencies) and serves as the backbone for all command/query dispatch in the application layer.

## Key Types

| Package | Type | Purpose |
|---------|------|---------|
| `bus.command` | `Command`, `CommandHandler`, `CommandProvider` | Command dispatch — mutate state |
| `bus.query` | `Query`, `QueryHandler`, `QueryProvider` | Query dispatch — read state |
| `bus.event` | `EventPublisher`, `EventMultiplexer`, `EventConsumer`, `EventFilter`, `Subscribe` | In-process event pub/sub |
| `bus.notification` | `Notification`, `NotificationHandler`, `NotificationProvider` | Fire-and-forget notifications |
| `bus.pipeline` | `PipelineBehavior`, `PipelineProvider` | Middleware chain (logging, validation, metrics) |
| root | `Mediator`, `MediatorBuilder`, `Registry`, `Registrar` | Bootstrap and dispatch |

## Usage

```kotlin
// 1. Define a command
data class CreatePost(val title: String) : Command<Post>

// 2. Implement handler
class CreatePostHandler : CommandHandler<CreatePost, Post> {
    override suspend fun handle(command: CreatePost): Post { ... }
}

// 3. Register and dispatch
val mediator = MediatorBuilder()
    .registerCommandHandler<CreatePost, Post> { CreatePostHandler() }
    .build()

val post = mediator.send(CreatePost("Hello"))
```

For events:
```kotlin
@Subscribe
class PostCreatedHandler : EventConsumer<PostCreatedEvent> {
    override suspend fun consume(event: PostCreatedEvent) { ... }
}
```

## Dependencies

- `shared:common` (api) — domain primitives

## Related

- [shared:spring-boot-common](../spring-boot-common/README.md) — Spring auto-configuration for EventEmitter and mediator wiring
- [shared:presentation](../presentation/README.md) — PageResponse DTOs used by query handlers
