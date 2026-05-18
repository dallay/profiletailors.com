---
name: spring-boot-testing-core
description: Use when writing fast Spring Boot 4 tests for application services, configuration properties, JSON serialization, validation, mappers, or other logic that should be verified without a full Spring context.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot Testing Core

Testing patterns for the **fastest** layer of a Kotlin + coroutines + Spring Boot 4 backend.

Use this skill for logic that should be proven without HTTP, brokers, or full application startup.
The default mindset is:

- prefer **plain unit tests** first
- use the **smallest Spring slice** only when framework wiring is the actual thing under test
- keep tests deterministic, fast, and explicit

## What Belongs Here

- application service / use-case tests
- domain-adjacent service tests with mocked ports
- `@ConfigurationProperties` binding and validation tests
- `@JsonTest` serialization and deserialization tests
- bean validation tests for DTOs and configuration
- mapper / converter tests
- parameterized tests and boundary-condition tests
- utility methods that support backend behavior

## What Does NOT Belong Here

Use companion skills instead when the main concern is:

- **reactive controllers / HTTP contracts** → `spring-boot-testing-webflux`
- **WireMock, Testcontainers, cache integration, events, scheduling** →
  `spring-boot-testing-integrations`
- **full-stack system verification** → narrow `@SpringBootTest` only when smaller tests are
  insufficient

## Test Pyramid Rules

| Layer                    | Default Tooling                                      | Goal                                  |
|--------------------------|------------------------------------------------------|---------------------------------------|
| Application services     | Kotest or JUnit 5 + MockK                            | Business orchestration correctness    |
| Config properties        | `ApplicationContextRunner`                           | Binding + validation correctness      |
| JSON                     | `@JsonTest`                                          | Serialization contract correctness    |
| Bean validation          | Jakarta Validator / focused context                  | Constraint behavior correctness       |
| Mappers / converters     | Plain unit tests                                     | Deterministic mapping correctness     |

## Core Rules

- Prefer **plain constructor-based tests** for services.
- Use **Kotest by default** for pure Kotlin tests when the codebase follows the Kotlin skill.
- JUnit 5 remains acceptable for Spring-specific test slices or when existing backend tests already
  standardize on it.
- Mock only direct collaborators.
- Do not start Spring just to test branching logic.
- Use real DTOs, value objects, and command/query objects where possible.
- Test one behavior per test.
- Verify both result and critical collaborator interaction.
- Prefer coroutine-aware mocking and assertions for `suspend` functions.
- Avoid `Thread.sleep()` in unit tests.

## Service Test Pattern

Use this for application services, orchestrators, and adapters whose behavior depends on ports.

```kotlin
@ExtendWith(MockKExtension::class)
class CreateWorkspaceCommandHandlerTest {

    @MockK
    lateinit var creator: WorkspaceCreator

    @InjectMockKs
    lateinit var handler: CreateWorkspaceCommandHandler

    @Test
    fun `creates workspace from command`() = runTest {
        val expectedId = WorkspaceId(UUID.randomUUID())
        coEvery { creator.create(any(), any()) } returns expectedId

        val result = handler.handle(
            CreateWorkspaceCommand(
                name = "Profile Tailors",
                ownerId = UUID.randomUUID(),
            ),
        )

        assertThat(result).isEqualTo(expectedId)
        coVerify(exactly = 1) { creator.create(any(), any()) }
    }
}
```

### Service Rules

- Test allow/deny, found/not-found, success/failure paths.
- If a service has too many mocks, fix the design.
- Do not mock value objects or DTOs.
- Prefer `runTest` for coroutine-based collaborators.

## Configuration Properties Pattern

Use `ApplicationContextRunner` to validate binding without booting the whole application.

```kotlin
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val issuer: String = "profile-tailors",
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
)

class SecurityPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)
        .withPropertyValues(
            "app.security.issuer=profile-tailors-api",
            "app.security.access-token-ttl=30m",
        )

    @Test
    fun `binds security properties`() {
        contextRunner.run { context ->
            val props = context.getBean(SecurityProperties::class.java)
            assertThat(props.issuer).isEqualTo("profile-tailors-api")
            assertThat(props.accessTokenTtl).isEqualTo(Duration.ofMinutes(30))
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties::class)
private class TestConfig
```

### Configuration Rules

- Test prefix binding explicitly.
- Test default values explicitly.
- Test validation failures explicitly.
- Test type conversions (`Duration`, collections, maps, booleans).

## JSON Serialization Pattern

Use `@JsonTest` when the JSON contract itself matters.

```kotlin
@JsonTest
class WorkspaceResponseJsonTest(
    @Autowired private val json: JacksonTester<WorkspaceResponse>,
) {
    @Test
    fun `serializes expected fields`() {
        val content = json.write(
            WorkspaceResponse(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                name = "Profile Tailors",
                ownerId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                memberCount = 3,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )

        assertThat(content).extractingJsonPathStringValue("$.name")
            .isEqualTo("Profile Tailors")
    }
}
```

### JSON Rules

- Verify serialized field names, null handling, and time formats.
- Test custom serializers and deserializers when present.
- Use DTO-focused tests, not entity-focused tests.

## Validation and Mapper Patterns

Use direct validator or plain unit tests when possible.

```kotlin
class CreateWorkspaceRequestValidationTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `rejects blank workspace name`() {
        val violations = validator.validate(
            CreateWorkspaceRequest(
                name = "",
                ownerId = UUID.randomUUID(),
            ),
        )

        assertThat(violations).isNotEmpty
    }
}
```

```kotlin
class WorkspaceMapperTest {
    private val mapper = WorkspaceMapper()

    @Test
    fun `maps entity to domain`() {
        val entity = WorkspaceEntity(
            id = UUID.randomUUID(),
            name = "Profile Tailors",
            ownerId = UUID.randomUUID(),
            createdAt = Instant.now(),
        )

        val domain = mapper.toDomain(entity)

        assertThat(domain.name.value).isEqualTo("Profile Tailors")
    }
}
```

## Common Mistakes

- ❌ Using `@SpringBootTest` for pure service logic
- ❌ Mocking DTOs, commands, or value objects
- ❌ Mixing many unrelated assertions into one test
- ❌ Hiding coroutine behavior behind blocking helpers
- ❌ Testing framework internals instead of business behavior
- ❌ Using blocking-persistence-specific slices as the default baseline in a reactive stack

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core Spring Boot reactive architecture rules
- `spring-boot-testing-webflux` — Reactive HTTP and controller testing
- `spring-boot-testing-integrations` — External integration, cache, event, and container-based tests
