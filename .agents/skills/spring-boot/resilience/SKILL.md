---
name: spring-boot-resilience
description: Use when implementing reactive fault-tolerance patterns in Spring Boot 4 with native Spring Framework 7 resilience features or Resilience4j, including retries, concurrency limiting, circuit breakers, rate limiting, timeouts, and fallbacks for downstream calls.
allowed-tools: Read, Write, Edit, Bash
---

# Spring Boot Reactive Resilience

Resilience patterns for **Spring Boot 4 + Spring Framework 7 + WebFlux + Kotlin coroutines**.

This skill covers the official resilience model now available in Spring Framework 7, plus the cases
where Resilience4j still makes sense for more advanced control.

## Official Baseline

The official documentation confirms that Spring Framework 7 includes native resilience support:

- `@Retryable` is built in
- `@ConcurrencyLimit` is built in
- both can be enabled via `@EnableResilientMethods`
- `@Retryable` applies to reactive return types and decorates the Reactor pipeline

For reactive HTTP clients in Spring Boot 4, the official client baseline is:

- `WebClient`
- Boot-managed `WebClient.Builder`
- Boot HTTP client properties for shared timeout/connect configuration

## What This Skill Owns

- native Spring Framework 7 resilience annotations
- reactive retry guidance
- concurrency limiting guidance
- when to use native resilience vs Resilience4j
- timeout, fallback, and downstream-protection patterns
- observability expectations for resilient remote calls

## What This Skill Does Not Own

Use companion skills instead when the main concern is:

- HTTP contract/status code design → `spring-boot-api-standards`
- core WebFlux controller/persistence boundaries → `spring-boot`
- reactive security → `spring-boot-security`
- endpoint verification and integration tests → `spring-boot-testing-webflux` /
  `spring-boot-testing-integrations`

## Core Rules

- In reactive apps, use `WebClient`, not `RestTemplate`.
- Prefer the **native** Spring Framework 7 resilience annotations for simple retry and concurrency
  limiting.
- Use Resilience4j when you need advanced circuit breakers, bulkheads, rate limiting, or time
  limiters beyond the native baseline.
- Apply resilience where external boundaries fail: remote HTTP clients, brokers, SMTP, storage
  adapters, and long-latency dependencies.
- Retry only transient failures.
- Do not retry predictable business failures.
- Make fallback behavior explicit and safe.

## Enabling Native Resilience

Use `@EnableResilientMethods` in configuration to activate Spring's native resilience annotations.

```kotlin
@Configuration
@EnableResilientMethods
class ResilienceConfiguration
```

## Native `@Retryable`

Use native `@Retryable` for straightforward retry behavior.

### Reactive Example

```kotlin
@Service
class InventoryClient(
    private val webClientBuilder: WebClient.Builder,
) {
    private val webClient = webClientBuilder
        .baseUrl("https://inventory-service.example")
        .build()

    @Retryable(
        includes = [InventoryServiceException::class, WebClientResponseException.ServiceUnavailable::class],
        maxRetries = 4,
        delay = 200,
        multiplier = 2.0,
        maxDelay = 2000,
    )
    fun checkStock(productId: UUID, quantity: Int): Mono<StockResponse> =
        webClient.get()
            .uri("/api/stock/{productId}?qty={quantity}", productId, quantity)
            .retrieve()
            .onStatus({ it.is5xxServerError }) {
                Mono.error(InventoryServiceException("Inventory temporarily unavailable"))
            }
            .bodyToMono(StockResponse::class.java)
}
```

### Coroutine-Friendly Guidance

If your public API is `suspend`, keep the remote client resilience at the reactive boundary and
adapt
at the edge when needed.

```kotlin
suspend fun checkStockAwait(productId: UUID, quantity: Int): StockResponse =
    checkStock(productId, quantity).awaitSingle()
```

### Retry Rules

- Retry 5xx, timeouts, and transport instability.
- Do **not** retry validation errors, auth failures, or domain conflicts by default.
- Use exponential backoff for unstable downstreams.
- Add jitter when retry storms are a risk.

## Native `@ConcurrencyLimit`

Use `@ConcurrencyLimit` to cap concurrent executions of a method when downstream pressure or shared
resource exhaustion is the main risk.

```kotlin
@Service
class NotificationService {

    @ConcurrencyLimit(10)
    fun sendNotification(request: NotificationRequest): Mono<Void> =
        Mono.fromRunnable {
            // downstream side effect
        }
}
```

### Concurrency Rules

- Use it to protect fragile downstreams or expensive operations.
- Keep the limit small enough to reduce overload, but not so small that it becomes accidental
  self-denial of service.
- Prefer this for simple concurrency throttling.

## When to Use Resilience4j Instead

Native Spring resilience is strong for retry and concurrency limiting, but Resilience4j still
matters
when you need:

- `@CircuitBreaker`
- `@RateLimiter`
- `@Bulkhead`
- `@TimeLimiter`
- richer stateful breaker behavior and operational tuning

### Circuit Breaker Example

```kotlin
@Service
class PaymentClient(
    private val webClientBuilder: WebClient.Builder,
) {
    private val webClient = webClientBuilder
        .baseUrl("https://payment-gateway.example")
        .build()

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "paymentFallback")
    fun charge(request: PaymentRequest): Mono<PaymentResponse> =
        webClient.post()
            .uri("/api/charge")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentResponse::class.java)

    fun paymentFallback(request: PaymentRequest, ex: Throwable): Mono<PaymentResponse> =
        Mono.just(
            PaymentResponse(
                orderId = request.orderId,
                status = "PENDING_RETRY",
                message = "Payment temporarily unavailable",
            ),
        )
}
```

### Rate Limiter Example

```kotlin
@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productQueryService: ProductQueryService,
) {
    @GetMapping("/search")
    @RateLimiter(name = "productSearch")
    suspend fun search(@RequestParam query: String): ProductSearchResponse =
        productQueryService.search(query)
}
```

## Timeouts

Use layered timeout strategy instead of one giant hammer.

### Prefer these layers

1. Boot HTTP client configuration (`connect-timeout`, `read-timeout`)
2. downstream-specific `WebClient` config where needed
3. Resilience time limits when business semantics require it

### Boot-level HTTP client properties

```yaml
spring:
  http:
    clients:
      connect-timeout: 1s
      read-timeout: 2s
```

### Rules

- Keep timeouts explicit.
- Do not let remote calls inherit unbounded waits.
- Tune timeout values per dependency criticality and normal latency.

## Fallback Strategy

Fallbacks must preserve correctness, not just silence errors.

### Good fallback use cases

- cached or stale-but-safe reads
- accepted-for-later processing states
- explicit degraded-mode responses

### Bad fallback use cases

- inventing successful writes that never happened
- hiding data inconsistency
- swallowing security or financial integrity failures

## Observability

Resilience without observability is theater.

### Rules

- emit metrics for retries, failures, breaker state changes, and rate limiting when supported
- propagate observations through `WebClient`
- distinguish business failures from transport failures in logs/metrics
- avoid noisy logs on every retry attempt unless debugging a real issue

### Practical Guidance

- Spring Boot 4 provides observation integration for `WebClient`
- Actuator + Micrometer should surface resilience behavior where possible
- alert on persistent open circuits or repeated fallback use, not only raw exceptions

## Native vs Resilience4j Decision Guide

| Need                      | Preferred tool                 |
|---------------------------|--------------------------------|
| Simple retry              | native `@Retryable`            |
| Simple concurrency cap    | native `@ConcurrencyLimit`     |
| Stateful circuit breaker  | Resilience4j `@CircuitBreaker` |
| Request rate limiting     | Resilience4j `@RateLimiter`    |
| Advanced isolation model  | Resilience4j `@Bulkhead`       |
| Declarative timeout layer | Resilience4j `@TimeLimiter`    |

## Testing Guidance

- Verify retryable methods fail and retry only on intended exceptions.
- Verify fallback methods preserve API semantics.
- Use WireMock or focused integration tests for unstable downstream behavior.
- Test that concurrency limiting or rate limiting produces clear degraded outcomes.
- Keep resilience tests close to the adapter/client boundary.

## Common Mistakes

- ❌ Using `RestTemplate` in a reactive stack
- ❌ Treating `CompletableFuture` as the default resilience model in WebFlux
- ❌ Retrying business conflicts, auth failures, or invalid requests
- ❌ Adding fallbacks that lie about success
- ❌ Using advanced Resilience4j patterns when native Spring features are enough
- ❌ Ignoring timeout configuration and relying only on retries

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core reactive WebFlux and infrastructure baseline
- `spring-boot-testing-integrations` — WireMock, downstream-failure, and adapter integration tests
- `spring-boot-actuator` — Metrics, health, and production observability
