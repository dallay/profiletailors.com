---
name: spring-boot-saga-pattern
description: Use when coordinating a multi-step business process across independently committed services or providers and designing recovery behavior.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
metadata:
  category: backend-platform
  family: spring-boot
  source: local
  version: 2026-09-30
---
# Saga Pattern

Use a saga only when a business operation crosses transaction boundaries that cannot share one
atomic database transaction. A saga coordinates separately committed steps and defines how the
workflow reaches a valid outcome after a partial failure. It does not provide rollback equivalent
to a database transaction.

## Before choosing a saga

- Map the business steps, ownership boundaries, durable state, and failure outcomes.
- Identify which effects can be compensated, which are irreversible, and what an operator must do
  when automatic recovery cannot finish.
- Prefer one local transaction when all work belongs to one service and one database boundary.
- Compare choreography and orchestration using the workflow's visibility, coupling, and recovery
  needs. Do not add a broker or saga framework solely to avoid designing transaction boundaries.

## Implementation boundaries

- Keep business transitions and recovery decisions in the domain/application layers. Define ports
  in Domain and implement provider, broker, and persistence adapters in Infrastructure.
- Persist workflow state so process restarts do not lose progress. Use idempotency keys for commands
  and messages that may be delivered more than once.
- Use an outbox or the repository's existing reliable publication boundary when a committed state
  transition must produce a message.
- Make each compensation explicit and safe to retry. A compensation is a new business action, not
  a time reversal; retain evidence of both the original action and its recovery.
- Model pending, completed, retryable failure, compensating, and manual-intervention states only
  where the business needs them.
- Keep Spring, broker, and persistence types in Infrastructure. Application services use the
  framework-free `com.profiletailors.common.domain.Service` marker where discovery is needed.

## Coroutines and persistence

Use the repository's coroutine and R2DBC patterns for reactive services. Keep the database update
and outbox write inside the adapter's supported transaction boundary. Do not wrap broker sends,
provider calls, or R2DBC operations in blocking transactions and assume that this makes them atomic.

## Testing

- Test domain transition and compensation decisions without Spring or broker infrastructure.
- Test application orchestration through domain ports, including duplicate commands and retryable
  failures.
- Use the existing integration fixtures to verify database/outbox atomicity, message delivery, and
  recovery after a process restart.
- Follow the module's JUnit 5, Kotest, and MockK conventions. Read the version catalog before
  adding a broker or saga dependency.

The `references/` directory contains broader saga material. For any concrete technology example,
the repository's Kotlin, WebFlux, coroutine, R2DBC, and hexagonal architecture contracts take
precedence.
