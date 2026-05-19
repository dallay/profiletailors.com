---
name: spring-boot-messaging
description: Use when implementing event-driven or message-based integration in Spring Boot 4, publishing domain events, coordinating Kafka or broker messaging, or applying outbox and idempotency patterns for reliable delivery.
allowed-tools: Read, Write, Edit, Bash
---

# Spring Boot Reactive Messaging

Messaging and event-driven integration patterns for **Spring Boot 4 + Spring Framework 7 + WebFlux +
Kotlin coroutines**.

This skill covers application events, transaction-bound events, asynchronous listeners, reliable
publication patterns, and broker-oriented integration guidance with a reactive-first mindset.

## Official Baseline

From the official Spring Framework documentation:

- `@EventListener` remains the base model for application events
- `@TransactionalEventListener` supports both imperative and reactive transaction models
- for **reactive transactions**, the transaction context must be included in the published event
  source/context model
- `@EventListener @Async` is supported, but has important limitations around exception propagation,
  return-value event publication, and context propagation
- `TransactionalOperator` is the official reactive transaction tool

## What This Skill Owns

- application events inside a Spring Boot service
- transaction-bound event publication patterns
- event-driven side effects and listener design
- outbox and idempotency guidance
- when to use in-process events vs external brokers
- async listener caveats in reactive applications

## What This Skill Does Not Own

Use companion skills instead when the main concern is:

- core WebFlux/controller/persistence rules → `spring-boot`
- resilience/timeouts/retries for downstream HTTP → `spring-boot-resilience`
- API/webhook contract design → `spring-boot-api-standards`
- event and integration verification → `spring-boot-testing-integrations`

## Event Model Selection

### Use in-process application events when:

- the side effect is local to the same service boundary
- you want loose coupling between internal components
- reliability requirements do not require external durable transport by themselves

### Use an external broker or outbox-based integration when:

- another service must consume the event
- durability and replay matter
- you need cross-service decoupling
- delivery cannot depend on the lifecycle of one process only

## Core Rules

- Distinguish **domain events**, **application events**, and **integration events**.
- Do not use in-process Spring events as if they were durable distributed messaging.
- Keep listener work small and explicit.
- Prefer idempotent event handlers.
- For cross-service delivery, outbox + broker is safer than direct hope-driven publishing.
- In reactive applications, do not blindly copy imperative transaction-event patterns.

## `@EventListener` Basics

Use `@EventListener` for in-process event reactions inside a service boundary.

```kotlin
@Component
class WorkspaceNotificationListener {

    @EventListener
    fun handleWorkspaceCreated(event: WorkspaceCreatedEvent) {
        // trigger lightweight local side effect
    }
}
```

### Rules

- Keep listeners focused on one side effect.
- Avoid hidden business orchestration in listeners.
- Prefer explicit event classes over generic maps or string payloads.

## Event Publication by Return Value

Spring supports publishing a new event by returning it from an `@EventListener`.

```kotlin
@EventListener
fun handleWorkspaceCreated(event: WorkspaceCreatedEvent): WorkspaceAuditEvent {
    return WorkspaceAuditEvent(event.workspaceId)
}
```

### Important caveat

This is convenient for simple in-process event chaining, but do **not** use it as a substitute for a
real durable integration flow.

## Transaction-Bound Events

Use `@TransactionalEventListener` when event handling semantics must depend on transaction outcome.

```kotlin
@Component
class WorkspaceCreatedTransactionalListener {

    @TransactionalEventListener
    fun handleWorkspaceCreated(event: WorkspaceCreatedEvent) {
        // runs after commit by default when transaction semantics are available
    }
}
```

### Reactive Transaction Warning

This is where many teams get burned.

According to the official docs:

- `@TransactionalEventListener` supports reactive transactions too
- reactive transactions use **Reactor context**, not thread-local transaction state
- therefore, the transaction context must be available in the published event/source model

### Guidance

- In imperative blocking systems, `@TransactionalEventListener` often feels straightforward.
- In reactive systems, treat it as an advanced feature, not the default hammer.
- If correctness depends on durable publication after commit, strongly consider an explicit outbox
  pattern.

## Reactive Transaction Pattern

For write flows in reactive systems, prefer explicit transaction boundaries with
`TransactionalOperator`.

```kotlin
@Service
class WorkspaceLifecycleService(
    private val repository: WorkspaceRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionalOperator: TransactionalOperator,
) {
    suspend fun createWorkspace(name: String): Workspace =
        requireNotNull(
            transactionalOperator.executeAndAwait {
                val workspace = repository.save(Workspace.create(name))
                eventPublisher.publishEvent(WorkspaceCreatedEvent(workspace.id, workspace))
                workspace
            },
        ) { "Reactive transaction completed without returning a workspace" }
}
```

### Rules

- Make the transaction boundary explicit.
- Keep event publication close to the state change when semantics require it.
- Be very careful assuming post-commit semantics behave the same in imperative and reactive flows.

## Async Event Listeners

Spring supports asynchronous listeners via `@EventListener` + `@Async`.

```kotlin
@Component
class EmailNotificationListener {

    @Async
    @EventListener
    fun handleWorkspaceCreated(event: WorkspaceCreatedEvent) {
        // asynchronous side effect
    }
}
```

### Official caveats

The Spring docs explicitly call out that async listeners:

- do **not** propagate exceptions back to the publisher
- cannot publish a follow-up event by returning a value
- do not automatically propagate ThreadLocals/logging context

### Guidance

- Use async listeners for non-critical side effects that tolerate decoupled execution.
- If tracing/logging context matters, configure context propagation intentionally.
- Do not rely on async listeners for critical state transitions without stronger guarantees.

## Outbox Pattern

When another service must consume the event reliably, prefer an explicit outbox.

### Pattern

1. write domain state
2. write outbox record in the same transaction boundary
3. publish from outbox to broker asynchronously
4. mark outbox record processed

### Why it matters

- avoids dual-write inconsistencies
- works better across service boundaries
- is easier to reason about than pretending in-process events are durable

## Idempotency Rules

Every serious message-driven system needs idempotency.

- consumers should tolerate duplicate delivery
- handlers should detect already-processed messages when correctness requires it
- message keys or event ids should be stable and explicit
- retries without idempotency are just duplicate side effects with better marketing

## Broker Guidance

If using Kafka, RabbitMQ, or another broker:

- separate domain events from integration events
- keep broker payloads stable and versionable
- define retry/dead-letter behavior deliberately
- keep consumer side effects observable and bounded
- document ordering expectations explicitly; do not assume global ordering

## Observability

### Rules

- log publication and consumption with correlation identifiers when possible
- monitor lag/backlog on broker consumers
- instrument outbox processing and repeated failure loops
- if async listeners or multicaster executors cross thread boundaries, configure context propagation
  intentionally

## Decision Guide

| Need                                         | Preferred approach                      |
|----------------------------------------------|-----------------------------------------|
| Local in-process decoupling                  | `@EventListener`                        |
| Transaction-outcome-sensitive local handling | `@TransactionalEventListener` with care |
| Reactive write-flow transaction control      | `TransactionalOperator`                 |
| Cross-service reliable delivery              | Outbox + broker                         |
| Fire-and-forget non-critical side effect     | `@EventListener` + `@Async`             |

## Common Mistakes

- ❌ Treating Spring application events as durable distributed messaging
- ❌ Assuming `@TransactionalEventListener` works identically in imperative and reactive flows
- ❌ Hiding critical business orchestration inside scattered listeners
- ❌ Ignoring duplicate delivery/idempotency
- ❌ Using async listeners without understanding exception/context propagation limits
- ❌ Publishing cross-service integration events directly from volatile in-process logic without an
  outbox or equivalent reliability strategy

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core reactive persistence and transaction boundary rules
- `spring-boot-testing-integrations` — Event-driven and outbox verification patterns
- `spring-boot-resilience` — Retry, timeout, and downstream protection patterns
