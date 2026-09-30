# Resilience Testing Patterns

Test resilience policy at the boundary where it is configured. Profile Tailors uses Kotlin,
coroutines, and WebFlux; examples based on `RestTemplate`, Mockito, blocking persistence, or servlet
tests are not implementation defaults.

## Circuit breakers and retries

- Verify state transitions through the resilience library's public API or a focused integration
  test.
- Assert retry count, fallback result, and final failure classification at the use-case boundary.
- Use a controlled fake or mock for unit tests. Use a local HTTP test server when the behavior
  depends on actual WebClient response handling.
- Keep retry delays and circuit thresholds under the existing configuration contract; do not make
  tests wait on production-duration backoff.

## Coroutine and reactive behavior

- Keep suspend calls and `Flow` non-blocking. Do not wrap each call in `runBlocking`.
- Use coroutine test utilities for suspend behavior and virtual time where the current test stack
  supports it.
- Do not move R2DBC operations onto `Dispatchers.IO`. Isolate genuinely blocking dependencies at
  their boundary and test the adapter's execution policy directly.

## Integration verification

Use the existing `WebTestClient` or HTTP test-server patterns for WebFlux adapters. Read the
module's Gradle version catalog and test fixtures for the supported dependencies and task names.
