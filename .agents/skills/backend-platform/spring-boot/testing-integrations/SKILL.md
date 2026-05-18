---
name: spring-boot-testing-integrations
description: Use when testing Spring Boot 4 integrations that involve external HTTP services, WireMock, caches, events, schedulers, brokers, or containerized dependencies where focused integration tests provide better confidence than unit tests.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot Testing Integrations

Integration-focused testing patterns for a Kotlin + coroutines + Spring Boot 4 backend.

Use this skill when a unit test is too weak because correctness depends on:

- external HTTP behavior
- cache semantics
- event publication or consumption
- scheduling / asynchronous boundaries
- real infrastructure via Testcontainers or focused Spring context startup

## What Belongs Here

- WireMock-based HTTP client tests
- cache integration and invalidation tests
- event-driven integration tests
- scheduler / async boundary verification
- focused persistence or adapter integration tests
- Testcontainers-backed tests when realism matters

## What Does NOT Belong Here

Use companion skills instead when the main concern is:

- controller/request validation/security at HTTP boundary → `spring-boot-testing-webflux`
- service logic, properties, JSON, mappers → `spring-boot-testing-core`

## Core Rules

- Use the narrowest integration scope that still proves the behavior.
- Prefer focused adapters over full end-to-end tests.
- Keep test style consistent with the Kotlin skill where practical: Kotest is preferred for pure
  Kotlin behavior, while JUnit 5 is acceptable for Spring/Testcontainers/WireMock integration
  setups.
- Use real infrastructure only where contract risk justifies it.
- Keep containers and stubs deterministic.
- In reactive stacks, isolate blocking dependencies explicitly.
- Avoid fake confidence from tests that never verify the actual integration boundary.

## WireMock Pattern

Use WireMock to test outbound HTTP clients without real network dependencies.

```kotlin
class WeatherApiClientTest {

    @JvmField
    @RegisterExtension
    val wireMock: WireMockExtension = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build()

    @Test
    fun `fetches weather from external API`() = runTest {
        wireMock.stubFor(
            get(urlEqualTo("/weather?city=London"))
                .willReturn(
                    okJson("""{"city":"London","temperature":15}"""),
                ),
        )

        val client = WeatherApiClient(
            WebClient.builder().baseUrl(wireMock.runtimeInfo.httpBaseUrl).build(),
        )

        val weather = client.getWeather("London")

        assertThat(weather.city).isEqualTo("London")
        wireMock.verify(getRequestedFor(urlEqualTo("/weather?city=London")))
    }
}
```

### WireMock Rules

- Always use dynamic ports.
- Verify requests, not just responses.
- Test timeout, 4xx, 5xx, and malformed payload paths.
- Prefer `WebClient` in examples and real adapters for this stack.

## Cache Integration Pattern

Caching behavior often depends on proxies, configuration, and invalidation semantics, so prove it with
focused integration tests when needed.

```kotlin
@SpringBootTest
@EnableCaching
class UserCacheIntegrationTest(
    @Autowired private val userService: UserService,
    @MockkBean private val userRepository: UserRepository,
) {
    @Test
    fun `reuses cached value on repeated read`() {
        every { userRepository.findById(any()) } returns UserEntity(...)

        userService.getUserById(1)
        userService.getUserById(1)

        verify(exactly = 1) { userRepository.findById(1) }
    }
}
```

### Cache Rules

- Test hit, miss, and invalidation behavior separately.
- Be explicit about proxy-driven behavior.
- In reactive code, document whether you are caching values or publishers.

## Event and Messaging Pattern

Use focused integration tests to verify event publication, outbox writes, or consumer behavior.

```kotlin
@SpringBootTest
class WorkspaceEventPublishingTest(
    @Autowired private val service: WorkspaceLifecycleService,
    @Autowired private val eventRecorder: TestEventRecorder,
) {
    @Test
    fun `publishes workspace created event`() = runTest {
        service.createWorkspace("Profile Tailors")

        assertThat(eventRecorder.events)
            .anyMatch { it is WorkspaceCreatedEvent }
    }
}
```

### Event Rules

- Verify the observable side effect, not just an internal method call.
- For async/eventual behavior, use deterministic waiting tools instead of `Thread.sleep()`.
- Keep idempotency and duplicate delivery in mind.

## Scheduler and Async Boundary Pattern

If scheduling or asynchronous execution matters, test the boundary and resulting effect.

```kotlin
@SpringBootTest
class SubscriptionSyncJobTest(
    @Autowired private val job: SubscriptionSyncJob,
    @MockkBean private val syncService: SubscriptionSyncService,
) {
    @Test
    fun `runs scheduled synchronization`() = runTest {
        coEvery { syncService.syncPendingSubscriptions() } returns Unit

        job.syncPendingSubscriptions()

        coVerify(exactly = 1) { syncService.syncPendingSubscriptions() }
    }
}
```

### Scheduling Rules

- Prefer invoking the scheduled method directly in tests.
- Test scheduling configuration separately from job logic.
- Avoid clock/time assumptions unless the contract requires them.

## Testcontainers Guidance

Use Testcontainers when the integration contract depends on a real engine.

- SQL adapters with vendor-specific behavior
- Redis cache semantics
- brokers or other infrastructure with real protocol behavior
- cases where mocks would hide correctness risks

Do **not** default to containers for every test.

## Common Mistakes

- ❌ Using full `@SpringBootTest` when a narrower integration test would do
- ❌ Stubbing external APIs without verifying the outgoing request
- ❌ Using `Thread.sleep()` for eventual consistency assertions
- ❌ Treating cache proxies as unit-test-only concerns
- ❌ Testing reactive integrations with hidden blocking code and never noticing
- ❌ Overusing containers where a focused stub is enough

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core reactive infrastructure rules
- `spring-boot-testing-core` — Fast unit and slice-style backend tests
- `spring-boot-testing-webflux` — Reactive HTTP boundary tests
