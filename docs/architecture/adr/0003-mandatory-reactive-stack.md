# ADR-0003: Mandatory Reactive Stack

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Backend (`server/smp`)
- Supersedes: None
- Superseded by: None

## Context
The platform must handle high concurrency, especially in scheduling and real-time social media data streams. Traditional thread-per-request models consume significant resources under high load.

## Decision drivers
- Resource efficiency (non-blocking I/O).
- Concurrency (ability to handle many concurrent requests with minimal threads).
- Technology alignment (Kotlin coroutines provide a superior developer experience for reactive programming).

## Decision
The backend MUST use a fully reactive stack:
- **Web Framework**: Spring WebFlux.
- **Database Driver**: R2DBC (Reactive Relational Database Connectivity).
- **Programming Model**: Kotlin Coroutines (`suspend` functions).

Blocking I/O operations MUST be avoided or carefully isolated on dedicated thread pools (e.g., `Dispatchers.IO`).

## Scope and boundaries
- Applies to all I/O-bound code in `server/smp`.
- All repository methods and use-case handlers MUST be `suspend` functions.

## Alternatives considered
### Spring MVC (Servlet-based)
- Advantages: Simpler programming model, mature ecosystem.
- Disadvantages: Less efficient for high-concurrency I/O.
- Reason rejected: Does not meet future scale targets for scheduled operations.

## Consequences
### Positive
- High throughput and low memory footprint.
- Natural fit for SSE and long-polling features.

### Negative
- Steeper learning curve.
- Difficult to debug (stack traces are less useful).
- Risk of "locking up" the event loop if blocking code is introduced.

### Accepted trade-offs
- Complexity is accepted for operational efficiency.

## Compliance and enforcement
- Code review focus on blocking calls.
- ArchUnit tests can be extended to check for `suspend` usage on specific interfaces.

## Verification
- Build file includes `spring-boot-starter-webflux` and `spring-boot-starter-data-r2dbc`.
- Controller and Repository interfaces use `suspend`.

## Migration or remediation
The codebase is reactive at its core: all domain and application layer methods use `suspend` functions. The infrastructure layer contains necessary `runBlocking` adapters only at framework boundaries where required to bridge synchronous contracts (e.g., `PublishingWorker.kt` for Spring's `TaskScheduler` Runnable interface and `MediaHealthConfiguration.kt` for Spring's `HealthIndicator.health()` synchronous interface).

## Revisit conditions
- Critical library dependencies do not support reactive/coroutines.
- Developer productivity drops significantly due to reactive complexity.
