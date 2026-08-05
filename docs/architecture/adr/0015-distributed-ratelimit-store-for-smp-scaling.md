# ADR-0015: Distributed Rate-Limit Store for SMP Scaling

- Status: Accepted
- Date: 2026-07-31

## Context

The shared rate-limiting module now supports a pluggable Bucket4j backing store. The default runtime remains a local Caffeine-backed implementation for single-instance and development environments, but the system also supports a Redis-backed distributed backend for horizontally scaled SMP deployments.

Without a distributed store, each replica maintains its own in-process bucket state. That causes inconsistent enforcement once traffic is routed across multiple SMP instances.

## Decision

We will support two bucket-store modes:

- Local mode (`LOCAL`): use the existing Caffeine-backed in-process store. This remains the default and is suitable for single-instance deployments and local development.
- Distributed mode (`REDIS`): use a Redis-backed Bucket4j proxy manager so buckets are shared across replicas.

Configuration is externalized under the application rate-limit properties:

- `application.rate-limit.store.type` (`LOCAL` or `REDIS`)
- `application.rate-limit.store.redis.uri`
- `application.rate-limit.store.redis.key-prefix`

The limiter implementation will delegate bucket resolution to the selected store while preserving the same public contract for the application layer.

## Consequences

### Positive

- Rate limits can be enforced consistently across multiple SMP replicas.
- The local implementation remains simple and fast for development and single-instance deployments.
- Metrics can distinguish whether a bucket came from the local or distributed backend.
- The store abstraction makes future backends (Hazelcast, DynamoDB, etc.) practical without changing the limiter contract.

### Negative

- Distributed mode adds Redis operational complexity and a new external dependency.
- Shared buckets require Redis availability and appropriate capacity planning.
- The Redis-backed implementation should be used only when cross-replica synchronization is required.

## Implementation Notes

- The implementation is wired through the shared rate-limit module under `shared/shield/ratelimit`.
- The limiter now routes bucket resolution through a `RateLimitStore` abstraction.
- Metrics tags include `bucket_source` with values `local` or `distributed`.
- A Redis integration test exercises the shared-bucket behavior when Docker is available.
