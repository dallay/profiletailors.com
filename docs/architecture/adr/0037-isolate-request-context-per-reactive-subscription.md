# ADR-0037: Isolate Request Context Per Reactive Subscription

- Status: Accepted
- Date: 2026-11-10
- Decision owners: Approved by the user
- Scope: `server/smp`, `shared/security`
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `.agents/sdd/changes/keycloak-human-authentication/`
  - C4: None
  - Issues/PRs: None

## Context

The SMP backend exposes principal, resource, and request-path context through a shared mutable
`InMemoryRequestContextStore`. Concurrent WebFlux requests can overwrite that state while another
request is still executing. The existing workspace-filter regression test demonstrates the
observable risk when it overlaps downstream work.

The backend uses WebFlux and Kotlin coroutines. The request values must follow the reactive
subscription and its coroutine bridge without adding mutable process-wide or singleton request
state.

## Decision drivers

- Isolate request values between concurrent subscriptions.
- Preserve structured concurrency and cancellation.
- Keep shared domain contracts framework-free and preserve hexagonal dependency direction.
- Verify behavior through downstream providers rather than post-request store inspection.

## Decision

1. Store principal, resource, and request-path values in Reactor Context scoped to each request
   subscription.
2. Resolve those values from coroutines through the existing Reactor/coroutine context bridge.
3. Keep provider contracts and consumers compatible with coroutine-context lookup; do not preserve
   synchronous internal store APIs solely for tests.
4. Do not use `ThreadLocal`, `runBlocking`, mutable singleton request state, or test serialization to
   implement isolation.
5. Test concurrent requests with distinct principal, workspace, and path values, and verify that
   request context is absent outside the completed subscription.
6. Keep Reactor-specific implementation in infrastructure and shared security contracts free of
   Reactor dependencies.

## Scope and boundaries

The platform infrastructure filters populate subscription context. Provider adapters expose the
values through the existing application contracts. Domain and shared security modules do not
depend on Reactor or Spring. Existing application use cases continue to depend on providers, not
infrastructure adapters.

## Alternatives considered

### Keep a mutable singleton store

- Description: Continue setting and clearing fields on one application-wide store.
- Advantages: No provider or consumer migration.
- Disadvantages: Concurrent requests can observe each other's values.
- Reason rejected: It does not satisfy request isolation.

### Use thread-local state

- Description: Associate request values with the executing thread.
- Advantages: Synchronous access is simple.
- Disadvantages: Reactive execution can change threads and coroutine execution can resume on a
  different thread.
- Reason rejected: Thread identity is not the request subscription boundary.

### Serialize request processing

- Description: Prevent overlap while mutable state is active.
- Advantages: Retains synchronous store access.
- Disadvantages: Removes concurrency and hides rather than fixes shared-state ownership.
- Reason rejected: It is not an acceptable production or test bypass.

## Consequences

### Positive

- Request values follow their reactive subscription across asynchronous execution.
- Concurrency tests can assert the provider contract used by downstream code.

### Negative

- Provider contracts and synchronous consumers that read request state require migration.
- Tests must establish Reactor context when invoking coroutine-backed providers.

### Risks

- Any remaining direct mutable-store consumer can retain the original race.
- The migration is incomplete until principal, resource, and path providers and all consumers are
  verified.

### Accepted trade-offs

- Provider access may require coroutine suspension where request context is read.

## Compliance and enforcement

Concurrent downstream provider tests cover request isolation. Backend compilation, tests, Detekt,
architecture checks, and the fast BDD suite verify the migrated contracts and consumers.

## Verification

- Concurrent workspace, principal, and request-path provider tests pass with distinct values per
  subscription.
- Missing-context provider tests preserve the existing null and required-context exception behavior.
- The mutable `RequestContextStore` and its singleton implementation have been removed from SMP.
- Focused provider/filter tests and SMP Detekt pass. `just backend-check` passed after the scoped
  Spotless corrections. `just backend-bdd-fast` completed 334 scenarios with four remaining
  failures; the two waitlist consent failures no longer reproduced. The remaining failures are
  tracked separately and are not yet attributed to request-context propagation.

## Migration or remediation

The migration is implemented. Principal, workspace/resource, and request-path filters publish
subscription-scoped values. Providers read those values through the coroutine bridge. Their
consumers use suspend access, and the mutable store and implementation have been removed.

## Follow-up actions

- [x] Complete provider and consumer migration for principal, resource, and request path.
- [x] Add concurrent regressions for principal and request path.
- [ ] Diagnose the four remaining BDD failures independently and rerun `just backend-bdd-fast`.

## Revisit conditions

Revisit this decision only if the backend replaces the WebFlux/coroutine request execution model or
the Reactor/coroutine context bridge no longer propagates subscription context.
