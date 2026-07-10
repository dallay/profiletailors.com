# ADR-0004: Implement CQRS via Mediator

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Backend (`server/smp`)
- Supersedes: None
- Superseded by: None
- Related:
    - C4: [Code Diagram](../c4/04-code.md)

## Context

Standard Service-oriented architectures often lead to bloated service classes that violate the
Single Responsibility Principle.

## Decision drivers

- Maintainability (one class per use case).
- Decoupling (entry points don't depend on specific service implementations).
- Extensibility (easier to add cross-cutting behaviors via mediator pipelines).

## Decision

The backend MUST implement the CQRS pattern using a Mediator:

- **Commands**: Represent intent to change state.
- **Queries**: Represent intent to read state.
- **Handlers**: Single-responsibility classes that process one Command or Query.
- **Mediator**: Dispatches requests to their corresponding handlers.

A "relaxed" CQRS approach is permitted where Commands MAY return data (using `CommandWithResult`) to
simplify frontend integration and reduce round-trips.

## Scope and boundaries

- All public API interactions in the `application` layer MUST use the Mediator.

## Alternatives considered

### Direct Service Injection

- Description: Controllers inject multiple service interfaces.
- Disadvantages: Leads to large constructors and tight coupling.
- Reason rejected: Less scalable as the number of use cases grows.

## Consequences

### Positive

- Clearly defined use cases.
- Easy to audit and log every action through the mediator.
- Handlers are easy to unit test in isolation.

### Negative

- More files (one for command, one for handler).
- Slight indirection in the codebase.

### Accepted trade-offs

- Increased file count is accepted for improved modularity and SRP.

## Compliance and enforcement

Enforced via naming conventions and dependency rules:

- **Naming conventions**: Commands use the `Command` suffix; Queries use the `Query` suffix;
  Handlers use the `Handler` suffix.
- **Handler interfaces**: All command handlers MUST implement `CommandHandler<TCommand>` or
  `CommandWithResultHandler<TCommand, TResult>`; all query handlers MUST implement
  `QueryHandler<TQuery, TResult>`.
- **Dependency injection**: Controllers MUST only inject the `Mediator` and MUST NOT directly inject
  service or handler classes. All use-case execution flows through `mediator.send(command)` or
  `mediator.send(query)`.

## Verification

- Usage of `mediator.send(command)` in Controllers.
- Handlers located in the `application` layer.

## Migration or remediation

Codebase is already following this pattern.

## Revisit conditions

- The indirection of the mediator becomes a barrier to understanding the system flow.
