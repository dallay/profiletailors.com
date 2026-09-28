# Spring AI MCP Server — Testing Guide

Testing strategies for the Spring AI MCP server in the reactive SMP backend: unit
tests with Kotest + MockK, integration tests with `@AutoConfigureWebTestClient`,
Testcontainers + R2DBC, security tests with reactive `WebTestClient`, and slice
tests with `@WebFluxTest`.

## Unit Testing Tools

```kotlin
class DatabaseToolsTest(
    val databaseTools: DatabaseTools = mockk(),
    val databaseClient: DatabaseClient = mockk(),
) : StringSpec({
    "executes SELECT queries and returns the rows" {
        val query = "SELECT * FROM users WHERE id = :id"
        val params: Map<String, Any> = mapOf("id" to 1)
        val rows = listOf(mapOf("id" to 1, "name" to "John"))

        coEvery { databaseClient.sql(query).bind("id", 1).fetch().all().toList() } returns rows.toFlux()

        val result = runBlocking { databaseTools.executeQuery(query, params) }
        result shouldBe rows
    }

    "rejects non-SELECT queries before reaching the database" {
        val query = "DROP TABLE users"

        shouldThrow<IllegalArgumentException> {
            runBlocking { databaseTools.executeQuery(query, emptyMap()) }
        }.message shouldBe "Only SELECT queries are allowed"
    }

    "returns the table schema for the given name" {
        val tableName = "users"
        val columns = listOf(
            mapOf("column_name" to "id", "data_type" to "integer"),
            mapOf("column_name" to "name", "data_type" to "varchar"),
        )

        coEvery { databaseClient.sql(any<String>()).bind("table", tableName).fetch().all().toList() } returns columns.toFlux()

        val schema = runBlocking { databaseTools.getTableSchema(tableName) }
        schema.tableName shouldBe tableName
        schema.columns shouldBe columns
    }
})
```

## Integration Testing

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class McpServerIntegrationTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    "POST /mcp/tools/executeQuery returns the tool payload" {
        val response = webTestClient.post().uri("/mcp/tools/executeQuery")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("query" to "SELECT * FROM users", "params" to emptyMap<String, Any>()))
            .exchange()
            .expectStatus().isOk
            .expectBody<List<Map<String, Any>>>()
            .returnResult()
            .responseBody!!

        response.first()["id"] shouldBe 1
    }

    "GET /mcp/tools lists the registered tools" {
        webTestClient.get().uri("/mcp/tools").exchange()
            .expectStatus().isOk
            .expectBody<Map<String, Any>>()
            .value { it["tools"] shouldBe listOf<Any>() }
    }

    "GET /actuator/health/mcp returns UP" {
        webTestClient.get().uri("/actuator/health/mcp").exchange()
            .expectStatus().isOk
            .expectBody<Map<String, Any>>()
            .value { it["status"] shouldBe "UP" }
    }
})
```

## Integration Testing with Testcontainers

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
@Testcontainers
class McpServerDatabaseIntegrationTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username") { postgres.username }
            registry.add("spring.r2dbc.password") { postgres.password }
        }
    }

    "executes the SELECT against the real PostgreSQL" {
        webTestClient.post().uri("/mcp/tools/executeQuery")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("query" to "SELECT current_database(), current_user"))
            .exchange()
            .expectStatus().isOk
            .expectBody<Map<String, Any>>()
            .value {
                it["success"] shouldBe true
                (it["data"] as List<*>).first()["current_database"] shouldBe "testdb"
            }
    }
})
```

## Slice Test with `@WebFluxTest`

```kotlin
@WebFluxTest(McpController::class)
class McpControllerSliceTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    val toolRegistry = mockk<ToolRegistry>()

    beforeTest {
        clearMocks(toolRegistry, answers = true)
        every { toolRegistry.listTools() } returns listOf(
            Tool.builder().name("tool1").description("Tool 1").build(),
            Tool.builder().name("tool2").description("Tool 2").build(),
        )
    }

    "lists the registered tools" {
        webTestClient.get().uri("/mcp/tools").exchange()
            .expectStatus().isOk
            .expectBody<Map<String, Any>>()
            .value {
                val tools = it["tools"] as List<Map<String, Any>>
                tools.shouldHaveSize(2)
                tools[0]["name"] shouldBe "tool1"
            }
    }
})
```

## Testing Tool Validation

```kotlin
class ToolValidationTest : StringSpec({
    val validator = DefaultToolValidator(
        McpServerProperties().apply {
            tools.validation.maxArgumentsSize = 1_000
        },
    )

    "accepts arguments within the size limit" {
        val tool = Tool.builder().name("testTool").build()
        val args: Map<String, Any> = mapOf("param1" to "value1", "param2" to 123)

        shouldNotThrowAny { validator.validateArguments(tool, args) }
    }

    "rejects oversized arguments" {
        val tool = Tool.builder().name("testTool").build()
        val args: Map<String, Any> = mapOf("largeParam" to "x".repeat(2_000))

        shouldThrow<ValidationException> { validator.validateArguments(tool, args) }
            .message shouldContain "Arguments too large"
    }
})
```

## Security Testing

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class McpSecurityTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    val userToken = TestJwtIssuer().issueUserToken(TestUsers.default())
    val adminToken = TestJwtIssuer().issueAdminToken(TestUsers.default())

    "regular tools accept USER tokens" {
        webTestClient.get().uri("/mcp/tools/getWeather")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken")
            .exchange()
            .expectStatus().isOk
    }

    "admin tools reject USER tokens" {
        webTestClient.get().uri("/mcp/tools/admin/deleteData")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken")
            .exchange()
            .expectStatus().isForbidden
    }

    "admin tools accept ADMIN tokens" {
        webTestClient.get().uri("/mcp/tools/admin/deleteData")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            .exchange()
            .expectStatus().isOk
    }
})
```

## Configuration Properties Testing

```kotlin
@SpringBootTest
@EnableConfigurationProperties(McpServerProperties::class)
class McpPropertiesTest(
    private val properties: McpServerProperties,
) : StringSpec({
    "default values match the documented contract" {
        properties.server.name shouldBe "spring-ai-mcp-server"
        properties.transport.type shouldBe TransportType.STDIO
        properties.security.isEnabled shouldBe false
    }
})
```
