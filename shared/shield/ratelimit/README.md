# shared:shield:ratelimit

Rate limiting infrastructure for the Profile Tailors API. Implements token-bucket rate limiting with Bucket4j and Caffeine caching, integrated into Spring WebFlux via a reactive `WebFilter`.

## Overview

Provides configurable rate limiting per API key or IP address using the token-bucket algorithm. Emits domain events when limits are exceeded and exposes Micrometer metrics for monitoring.

## Key Types

| Type | Purpose |
|------|---------|
| `RateLimiter` | Domain interface — `allowRequest(key)` returns `RateLimitResult` |
| `RateLimitResult` | Result with allowed/denied + remaining tokens + reset time |
| `RateLimitStrategy` | Strategy definition (capacity, refill rate, refill period) |
| `Bucket4jRateLimiter` | Bucket4j-based implementation with Caffeine cache |
| `RateLimitingFilter` | Spring `WebFilter` — intercepts requests and enforces limits |
| `RateLimitingService` | Application service — coordinates rate limiting logic |
| `RateLimitProperties` | Configuration properties (`profiletailors.ratelimit.*`) |
| `RateLimitConfiguration` | Spring auto-configuration |

### Events

| Type | Purpose |
|------|---------|
| `RateLimitExceededEvent` | Published via shared bus when rate limit is exceeded |

## Usage

```yaml
profiletailors:
  ratelimit:
    default-strategy:
      capacity: 100
      refill-rate: 10
      refill-period: SECONDS
    enabled: true
```

```kotlin
// Programmatic check
val result = rateLimiter.allowRequest("api-key-123")
if (result.isAllowed) {
    // process request
} else {
    // return 429 Too Many Requests
}
```

## Dependencies

- `shared:common` — domain primitives
- `shared:bus` — event publishing
- `shared:spring-boot-common` — Spring integration, EventEmitter

## Related

- [shared:spring-boot-common](../../spring-boot-common/README.md) — base Spring integration
- [shared:bus](../../bus/README.md) — event types for RateLimitExceededEvent
