# ADR 0015: Distributed RateLimit Store for SMP Horizontal Scaling

- **Status**: Accepted
- **Date**: 2026-07-31
- **Deciders**: Platform Architecture, SMP Team
- **Related**: DALLAY-512, reusable-lead-capture-waitlist

## Context

`shared/shield/ratelimit` currently supports Bucket4j with local in-process Caffeine bucket storage.
This is correct for `0.1.0` production topology (single SMP replica on a single VPS).

Before horizontal scaling (multiple SMP replicas receiving the same public traffic), local bucket
state no longer enforces a global per-IP/per-waitlist limit because each replica keeps its own
bucket map.

## Decision

We introduce a pluggable `RateLimitStore` abstraction in `shared/shield/ratelimit` with:

- `LocalCaffeineRateLimitStore` for single-instance, test, and development usage.
- `RedisBucket4jRateLimitStore` as the first distributed backend for shared bucket state.

SMP wires store selection via runtime configuration (no code changes required):

- `application.rate-limit.store.distributed-enabled` (default `false`)
- `application.rate-limit.store.type` (`LOCAL` or `REDIS`; `HAZELCAST` reserved)
- `application.rate-limit.store.redis.uri`
- `application.rate-limit.store.redis.key-prefix`

## Spike Outcome: Redis vs Hazelcast

### Redis (selected first)

Pros:

- Widely available managed offering in cloud/VPS environments.
- Strong operational familiarity for counters/ephemeral state.
- Bucket4j native Redis backend support with CAS semantics.
- Good fit for cross-replica shared rate-limit state.

Cons:

- Additional infrastructure component when moving beyond single replica.
- Network hop increases latency vs in-process buckets.

### Hazelcast (fallback candidate)

Pros:

- Native distributed data-grid model with in-memory speed.
- Useful when a Hazelcast cluster already exists for other workloads.

Cons:

- Higher operational complexity if introduced only for rate limiting.
- Extra cluster lifecycle and membership concerns.

Decision rationale:

- Redis is first because it minimizes adoption risk and operational overhead in the expected
  scaling path.
- Hazelcast remains a valid fallback if platform constraints favor it.

## Consequences

Positive:

- Rate limits can be enforced consistently across SMP replicas once distributed mode is enabled.
- Existing single-instance behavior remains unchanged by default.
- Metrics now include bucket source (`local` vs `distributed`) without removing legacy metrics.

Neutral/Negative:

- Operators must provision and monitor Redis before enabling distributed mode.
- Trusted-proxy client IP resolution remains a separate concern and must be solved independently.

## Trigger for Mandatory Enablement

Distributed mode becomes a release blocker before either:

- Deploying more than one SMP replica, or
- Any topology where multiple SMP instances receive the same public traffic.

Until that trigger, local Caffeine remains the valid `0.1.0` default.
