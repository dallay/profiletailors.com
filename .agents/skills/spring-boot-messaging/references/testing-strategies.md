# Messaging Test Strategies

## Overview

Use Kotlin and the test stack already declared by the module. In Profile Tailors, domain and
application behavior uses the existing Kotest/JUnit 5 conventions with MockK; messaging adapters
are tested at their infrastructure boundary. Do not copy Mockito, Java, JDBC, or servlet examples
as the repository baseline.

## Changes

### Domain event behavior

Test event creation and aggregate state with ordinary domain tests. Assert the event type and the
business facts it carries. Keep persistence and broker types out of domain tests.

### Handler behavior

Mock the handler's domain/application ports with MockK. Verify the meaningful effect and returned
result, not internal call order that is not part of the contract. Use coroutine-aware MockK APIs
for suspend functions.

## Usage

### Broker adapter behavior

Test message encoding, routing keys, headers, retry classification, and error handling at the
adapter boundary. Prefer a broker test container or the repository's existing integration fixture
for protocol behavior. Keep transport DTOs and broker configuration in infrastructure.

### Outbox behavior

Verify that a business transition and its outbox record are committed atomically by the chosen
adapter. Test duplicate delivery and retry behavior with the same idempotency assumptions as the
production consumer. Do not simulate an outbox with in-memory state when transaction semantics are
the behavior under test.

## Troubleshooting

### Reactive and coroutine behavior

Keep suspend functions and `Flow` non-blocking throughout the tested path. Do not wrap each call in
`runBlocking` or move R2DBC work to `Dispatchers.IO`. For truly blocking external libraries, isolate
the blocking boundary and test that boundary explicitly.

## References

### Test sources

Use `just -l` and the module's `build.gradle.kts` to find the available test tasks, dependency
versions, and integration tags. Do not add a second test framework to a module without a clear
project need.
