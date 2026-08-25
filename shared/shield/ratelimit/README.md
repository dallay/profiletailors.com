# Shared Shield Rate Limit Module (`shared:shield:ratelimit`)

Token-bucket rate limiting infrastructure using Bucket4j, Caffeine in-memory caching, Spring WebFlux reactive filters, Micrometer metrics, and rate-limiting domain event publishing.

## Role in the platform

Protects Profile Tailors API endpoints against brute-force attacks, abuse, and resource exhaustion. Integrated into `server/smp` via a reactive Spring `WebFilter`, it evaluates incoming request IP addresses or API tokens against configurable Bucket4j token-bucket strategies.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines
- **Framework & Libraries**: Spring WebFlux, Bucket4j 8.10, Caffeine 3.1, Micrometer
- **Testing**: JUnit 5, AssertJ, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:shield:ratelimit`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:shield:ratelimit:test
```

### Environment variables

| Variable | Required | Description | Default |
| --- | --- | --- | --- |
| `SMP_PLATFORM_RATE_LIMIT_ENABLED` | No | Master toggle to enable rate limiting filter | `false` |

## Project structure

```text
shared/shield/ratelimit/
├── src/main/kotlin/com/profiletailors/shield/ratelimit/
│   ├── config/     # Spring Auto-Configuration and RateLimitProperties
│   ├── domain/     # RateLimiter interface, RateLimitResult, RateLimitStrategy
│   ├── event/      # RateLimitExceededEvent
│   ├── filter/     # Reactive WebFilter interceptor
│   └── service/    # Bucket4jRateLimiter implementation
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:shield:ratelimit:test
```

## API / Public interface

Main types in package `com.profiletailors.shield.ratelimit`:

- `RateLimiter`: Domain contract (`allowRequest(key) -> RateLimitResult`).
- `Bucket4jRateLimiter`: Bucket4j and Caffeine implementation.
- `RateLimitingFilter`: Reactive `WebFilter` for WebFlux request interception.
- `RateLimitExceededEvent`: Domain event emitted when request limit is breached.

## Configuration

Configuration properties (`application.yaml`):

```yaml
profiletailors:
  ratelimit:
    enabled: false
    capacity: 100
    refill-rate: 10
    refill-period: 1s
```

## Contributing

Please review the [Root CONTRIBUTING.md](../../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../../LICENSE) for details.

---
Back to [Root README](../../../README.md)
