# Delta for Lead Capture Waitlist

## MVP Decision: Distributed Rate-Limit Window — DEFERRED OUT OF MVP

The MVP MUST NOT introduce Redis, another distributed bucket store, or any other distributed
rate-limit implementation. The accepted MVP behavior is:

- The shared rate-limit adapter uses a bounded, per-JVM Caffeine cache for Bucket4j buckets.
- SMP's waitlist limiter defaults to disabled through
  `SMP_WAITLIST_RATE_LIMIT_ENABLED:false` in `server/smp/src/main/resources/application.yaml`.
- An operator MAY explicitly enable the waitlist limiter, but that opt-in is per instance and
  MUST NOT be treated as safe distributed enforcement.
- Multi-replica waitlist rate-limit enablement remains deferred until DALLAY-512 (distributed
  bucket backend) and DALLAY-513 (trusted proxy / client-identity handling) are resolved.

This decision accepts bounded per-instance protection for the MVP and deliberately removes the
unimplementable two-replica test from the current change. It does not claim that a per-JVM bucket
is globally enforced across replicas.

## Follow-up: Distributed Rate-Limit Window — NOT AN MVP REQUIREMENT

The following requirement and scenarios are preserved for a future change after DALLAY-512 and
DALLAY-513 are resolved. They MUST NOT be used as current MVP acceptance criteria.

### Requirement: Distributed Rate-Limit Window (Follow-up)

The waitlist rate limit MUST be enforced by a distributed bucket shared across application
replicas, not a per-instance in-memory counter. A Bucket4j window backed by a shared store MUST be
used so that consumption on one replica reduces capacity visible to another. Two concurrently
running replicas MUST observe the same window state.

#### Scenario: Burst on one replica is visible to another

- GIVEN two Spring Boot replicas share the same distributed rate-limit window
- WHEN replica A consumes the remaining quota with a burst of requests
- THEN replica B MUST reject subsequent requests with 429
- AND the rejection MUST reflect the shared window, not replica-local state

#### Scenario: Limit resets on the shared window

- GIVEN the shared window is exhausted
- WHEN the configured window period elapses
- THEN capacity MUST be replenished
- AND both replicas MUST accept new requests again

## Follow-up TDD Requirement

The follow-up scenarios MUST have failing-first coverage in a future change. The removed
`WaitlistDistributedRateLimitE2ETest` was not MVP acceptance evidence because the current
implementation has no distributed store. A replacement two-replica Testcontainers test MAY be
added only after the distributed backend and trusted client-identity requirements are implemented.
