# Spring AI MCP Server — Testing Guide

Testing strategies for MCP servers: unit tests, integration tests, Testcontainers, security tests,
and slice tests.

## Unit Testing Tools

```kotlin
@SpringBootTest
class DatabaseToolsTest {

    @Autowired
    private var databaseTools: DatabaseTools

    @MockBean
    private var jdbcTemplate: JdbcTemplate

    @Test
    void testExecuteQuery_Success()
    {
        String query = "SELECT * FROM users WHERE id = ?";
        Map<String, Object> params = Map . of ("id", 1);
        List<Map<String, Object>> expected = listOf (Map.of("id", 1, "name", "John"));

        when (jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(expected);

        List<Map<String, Object>> results = databaseTools . executeQuery (query, params);

        assertThat(results).isEqualTo(expected);
        verify(jdbcTemplate).queryForList(query, params);
    }

    @Test
    void testExecuteQuery_InvalidQuery_ThrowsException()
    {
        String query = "DROP TABLE users";

        assertThatThrownBy(() -> databaseTools.executeQuery(query, null))
        .isInstanceOf(
        IllegalArgumentException.class)
            .hasMessage("Only SELECT queries are allowed");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void testGetTableSchema_Success()
    {
        String tableName = "users";
        List<Map<String, Object>> columns = List . of (
                Map.of("column_name", "id", "data_type", "integer"),
        Map.of("column_name", "name", "data_type", "varchar")
        );

        when (jdbcTemplate.queryForList(anyString(), eq(tableName))).thenReturn(columns);

        TableSchema schema = databaseTools . getTableSchema (tableName);

        assertThat(schema.tableName()).isEqualTo(tableName);
        assertThat(schema.columns()).isEqualTo(columns);
    }
}
```

## Integration Testing

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class McpServerIntegrationTest {

    @Autowired
    private var mockMvc: MockMvc

    @MockBean
    private var databaseTools: DatabaseTools

    @Test
    void testExecuteTool_Success() throws Exception
    {
        Map<String, Object> args = Map . of ("query", "SELECT * FROM users", "params", Map.of());
        List<Map<String, Object>> expectedResult = listOf (Map.of("id", 1, "name", "Test User"));

        when (databaseTools.executeQuery(anyString(), anyMap())).thenReturn(expectedResult);

        mockMvc.perform(
            post("/mcp/tools/executeQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ObjectMapper().writeValueAsString(args))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").isArray())
            .andExpect(jsonPath("$.result[0].id").value(1));
    }

    @Test
    void testListTools_Success() throws Exception
    {
        mockMvc.perform(get("/mcp/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tools").isArray());
    }

    @Test
    void testHealthEndpoint() throws Exception
    {
        mockMvc.perform(get("/actuator/health/mcp"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

## Integration Testing with Testcontainers

```kotlin
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class McpServerDatabaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private var mockMvc: MockMvc

    @Test
    void testDatabaseToolWithRealDatabase() throws Exception
    {
        Map<String, Object> request = Map . of (
                "tool", "executeQuery",
        "arguments", Map.of("query", "SELECT current_database(), current_user")
        );

        mockMvc.perform(
            post("/mcp/tools/executeQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ObjectMapper().writeValueAsString(request))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].current_database").value("testdb"));
    }
}
```

## Slice Test with `@WebMvcTest`

```kotlin
@WebMvcTest(
    controllers = McpController.class)
    class McpControllerSliceTest {

    @Autowired
    private var mockMvc: MockMvc

    @MockBean
    private var mcpServer: McpServer

    @MockBean
    private var toolRegistry: ToolRegistry

    @Test
    void testListToolsEndpoint () throws Exception {
        Tool tool1 = Tool . builder ().name("tool1").description("Tool 1").build();
        Tool tool2 = Tool . builder ().name("tool2").description("Tool 2").build();

        when (toolRegistry.listTools()).thenReturn(listOf(tool1, tool2));

        mockMvc.perform(get("/mcp/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tools").isArray())
            .andExpect(jsonPath("$.tools.length()").value(2))
            .andExpect(jsonPath("$.tools[0].name").value("tool1"));
    }
}
```

## Testing Tool Validation

```kotlin
@ExtendWith(
    MockitoExtension.class)
    class ToolValidationTest {

    private var validator: ToolValidator

    @BeforeEach
    void setUp () {
        McpServerProperties properties = McpServerProperties ();
        properties.getTools().getValidation().setMaxArgumentsSize(1000);
        validator = DefaultToolValidator(properties);
    }

    @Test
    void testValidArguments () {
        Tool tool = Tool . builder ().name("testTool").method(getTestMethod()).build();
        Map<String, Object> args = Map . of ("param1", "value1", "param2", 123);

        assertDoesNotThrow(() -> validator.validateArguments(tool, args));
    }

    @Test
    void testArgumentsTooLarge () {
        Tool tool = Tool . builder ().name("testTool").build();
        Map<String, Object> args = Map . of ("largeParam", "x".repeat(2000));

        ValidationException exception = assertThrows (
                ValidationException.class,
                    () -> validator.validateArguments(tool, args));

        assertThat(exception.getMessage()).contains("Arguments too large");
    }
}
```

## Security Testing

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = { "USER" })
class McpSecurityTest {

    @Autowired
    private var mockMvc: MockMvc

    @Test
    void testUserCanAccessRegularTools() throws Exception
    {
        mockMvc.perform(get("/mcp/tools/getWeather"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = { "USER" })
    void testUserCannotAccessAdminTools() throws Exception
    {
        mockMvc.perform(get("/mcp/tools/admin/deleteData"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void testAdminCanAccessAllTools() throws Exception
    {
        mockMvc.perform(get("/mcp/tools/admin/deleteData"))
            .andExpect(status().isOk());
    }
}
```

## Configuration Properties Testing

```kotlin
@SpringBootTest
@EnableConfigurationProperties(
    McpServerProperties.class)
    class McpPropertiesTest {

    @Autowired
    private var properties: McpServerProperties

    @Test
    void testDefaultValues () {
        assertThat(properties.getServer().getName()).isEqualTo("spring-ai-mcp-server");
        assertThat(properties.getTransport().getType()).isEqualTo(TransportType.STDIO);
        assertThat(properties.getSecurity().isEnabled()).isFalse();
    }
}
```
