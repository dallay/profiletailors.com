# Distributed rate-limit store

## Summary

Add a pluggable Bucket4j store layer so SMP can use local Caffeine buckets in single-instance deployments and Redis-backed distributed buckets when horizontal scaling is enabled.

## Scope

- Introduce a `RateLimitStore` abstraction in the shared rate-limit module.
- Keep the existing Caffeine implementation as the default local backend.
- Add a Redis-backed Bucket4j implementation for shared buckets across replicas.
- Expose configuration for store type, Redis URI, and Redis key prefix.
- Tag metrics with the bucket source (`local` vs `distributed`).
- Add integration coverage for shared buckets across two store instances.
