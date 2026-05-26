---
name: spring-boot-data-neo4j-reactive
description: Use when integrating Neo4j into a Spring Boot 4 backend with graph models, Cypher queries, reactive repositories, relationship mapping, or graph-focused testing patterns.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Data Neo4j Integration Patterns

## Overview

Provides Spring Data Neo4j integration patterns for Spring Boot applications. Covers node entity
mapping with `@Node` and `@Relationship`, repository configuration (imperative and reactive), custom
Cypher queries with `@Query`, and integration testing with embedded Neo4j databases.

## When to Use

Use this skill when working with:

- Graph databases and Neo4j integration in Spring Boot
- Node entities, relationships, and Cypher queries
- Spring Data Neo4j repositories (imperative or reactive)
- Neo4j testing with embedded databases

## Instructions

### 1. Set Up Spring Data Neo4j

**Add the dependency:**

Maven:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-neo4j</artifactId>
</dependency>
```

Gradle:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-neo4j'
```

**Configure connection in application.properties:**

```properties
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=secret
```

**Configure Cypher-DSL dialect (recommended):**

```kotlin
@Configuration
class Neo4jConfig {
    @Bean
    fun cypherDslConfiguration(): Configuration =
        Configuration.newConfig()
            .withDialect(Dialect.NEO4J_5)
            .build()
}
```

> **Validation Checkpoint**: Run `MATCH (n) RETURN count(n)` via cypher-shell to verify the
> connection works before proceeding.

### 2. Define Node Entities

1. **Use `@`Node annotation** to mark entity classes
2. **Choose ID strategy:**
    - Business key as `@`Id (immutable, natural identifier)
    - Generated `@`Id `@`GeneratedValue (Neo4j internal ID)
3. **Define relationships** with `@`Relationship annotation
4. **Keep entities immutable** with final fields
5. **Use `@`Property** for custom property names

> **Validation Checkpoint**: If entity save fails, check for constraint violations—duplicate IDs
> violate uniqueness constraints.

### 3. Create Repositories

1. **Extend repository interface:**
    - `Neo4jRepository<Entity, ID>` for imperative operations
    - `ReactiveNeo4jRepository<Entity, ID>` for reactive operations
2. **Use query derivation** for simple queries
3. **Apply `@`Query annotation** for complex Cypher queries
4. **Use `$`paramName syntax** for parameters

> **Validation Checkpoint**: Test repository with `findAll()` first—if empty, verify the Neo4j
> instance is running and credentials are correct.

### 4. Test Your Implementation

1. **Use `@`DataNeo4jTest** for repository testing with test slicing
2. **Set up Neo4j Harness** with embedded database and fixtures
3. **Provide test data** via `withFixture()` Cypher queries
4. **Clean up test data** between tests

> **Validation Checkpoint**: If tests fail with "Connection refused", ensure the embedded Neo4j
> started successfully in `@BeforeAll`.

## Basic Entity Mapping

### Node Entity with Business Key

```kotlin
@Node("Movie")
data class MovieEntity(
    @Id
    val title: String,  // Business key as ID

    @Property("tagline")
    val description: String,

    val year: Int?,

    @Relationship(type = "ACTED_IN", direction = Direction.INCOMING)
    val actorsAndRoles: MutableList<Roles> = mutableListOf(),

    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    val directors: MutableList<PersonEntity> = mutableListOf()
)
```

### Node Entity with Generated ID

```kotlin
@Node("Movie")
data class MovieEntity(
    @Id @GeneratedValue
    val id: Long? = null,  // Never set manually

    val title: String,

    @Property("tagline")
    val description: String
) {
    // Copy method for immutability with generated IDs
    fun withId(newId: Long): MovieEntity =
        if (id != null && id == newId) this
        else copy(id = newId)
}
```

## Repository Patterns

### Basic Repository Interface

```kotlin
@Repository
interface MovieRepository : Neo4jRepository<MovieEntity, String> {

    // Query derivation from method name
    fun findOneByTitle(title: String): MovieEntity?

    fun findAllByYear(year: Int): List<MovieEntity>

    fun findByYearBetween(startYear: Int, endYear: Int): List<MovieEntity>
}
```

### Reactive Repository

```kotlin
@Repository
interface MovieRepository : ReactiveNeo4jRepository<MovieEntity, String> {

    fun findOneByTitle(title: String): Mono<MovieEntity>

    fun findAllByYear(year: Int): Flux<MovieEntity>
}
```

**Imperative vs Reactive:**

- Use `Neo4jRepository` for blocking, imperative operations
- Use `ReactiveNeo4jRepository` for non-blocking, reactive operations
- **Do not mix imperative and reactive in the same application**
- Reactive requires Neo4j 4+ on the database side

## Custom Queries with `@`Query

```kotlin
@Repository
interface AuthorRepository : Neo4jRepository<Author, Long> {

    @Query("""
        MATCH (b:Book)-[:WRITTEN_BY]->(a:Author)
        WHERE a.name = ${'$'}name AND b.year > ${'$'}year
        RETURN b
    """)
    fun findBooksAfterYear(
        @Param("name") name: String,
        @Param("year") year: Int
    ): List<Book>

    @Query("""
        MATCH (b:Book)-[:WRITTEN_BY]->(a:Author)
        WHERE a.name = ${'$'}name
        RETURN b ORDER BY b.year DESC
    """)
    fun findBooksByAuthorOrderByYearDesc(@Param("name") name: String): List<Book>
}
```

**Custom Query Best Practices:**

- Use `$parameterName` for parameter placeholders
- Use `@Param` annotation when parameter name differs from method parameter
- MATCH specifies node patterns and relationships
- WHERE filters results
- RETURN defines what to return

## Testing Strategies

### Neo4j Harness for Integration Testing

**Test Configuration:**

```kotlin
@DataNeo4jTest
class BookRepositoryIntegrationTest {

    companion object {
        private lateinit var embeddedServer: Neo4j

        @JvmStatic
        @BeforeAll
        fun initializeNeo4j() {
            embeddedServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()  // No HTTP access needed
                .withFixture("""
                    CREATE (b:Book {isbn: '978-0547928210',
                    name: 'The Fellowship of the Ring', year: 1954})
                    -[:WRITTEN_BY]->(a:Author {id: 1, name: 'J. R. R. Tolkien'})
                    CREATE (b2:Book {isbn: '978-0547928203',
                    name: 'The Two Towers', year: 1956})
                    -[:WRITTEN_BY]->(a)
                """.trimIndent())
                .build()
        }

        @JvmStatic
        @AfterAll
        fun stopNeo4j() {
            embeddedServer.close()
        }

        @JvmStatic
        @DynamicPropertySource
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.neo4j.uri") { embeddedServer.boltURI() }
            registry.add("spring.neo4j.authentication.username") { "neo4j" }
            registry.add("spring.neo4j.authentication.password") { "null" }
        }
    }

    @Autowired
    private lateinit var bookRepository: BookRepository

    @Test
    fun `given book exists when find one by title then book is returned`() {
        val book = bookRepository.findOneByTitle("The Fellowship of the Ring")
        assertThat(book?.isbn).isEqualTo("978-0547928210")
    }
}
```

## Examples

### Example 1: Saving and Retrieving Entities

**Input:**

```kotlin
val movie = MovieEntity("The Matrix", "Welcome to the Real World", 1999)
movieRepository.save(movie)

val found = movieRepository.findOneByTitle("The Matrix")
```

**Output:**

```kotlin
MovieEntity(
    title = "The Matrix",
    description = "Welcome to the Real World",
    year = 1999,
    actorsAndRoles = mutableListOf(),
    directors = mutableListOf()
)
```

### Example 2: Custom Cypher Query

**Input:**

```kotlin
val books = authorRepository.findBooksAfterYear("J.R.R. Tolkien", 1950)
```

**Output:**

```kotlin
listOf(
    Book(isbn = "978-0547928210", name = "The Fellowship of the Ring", year = 1954),
    Book(isbn = "978-0547928203", name = "The Two Towers", year = 1956),
    Book(isbn = "978-0547928227", name = "The Return of the King", year = 1957)
)
```

### Example 3: Relationship Traversal

**Input:**

```kotlin
@Query("""
    MATCH (m:Movie)<-[:ACTED_IN]-(a:Person)
    WHERE m.title = ${'$'}title
    RETURN a.name as actorName
""")
fun findActorsByMovieTitle(@Param("title") title: String): List<String>

val actors = movieRepository.findActorsByMovieTitle("The Matrix")
```

**Output:**

```kotlin
listOf("Keanu Reeves", "Laurence Fishburne", "Carrie-Anne Moss", "Hugo Weaving")
```

---

Progress from basic to advanced examples covering complete movie database, social network patterns,
e-commerce product catalogs, custom queries, and reactive operations.

See [examples](./references/examples.md) for comprehensive code examples.

## Best Practices

### Entity Design

- Use immutable entities with final fields
- Choose between business keys (`@`Id) or generated IDs (`@`Id `@`GeneratedValue)
- Keep entities focused on graph structure, not business logic
- Use proper relationship directions (INCOMING, OUTGOING, UNDIRECTED)

### Repository Design

- Extend `Neo4jRepository` for imperative or `ReactiveNeo4jRepository` for reactive
- Use query derivation for simple queries
- Write custom `@`Query for complex graph patterns
- Don't mix imperative and reactive in same application

### Configuration

- Always configure Cypher-DSL dialect explicitly
- Use environment-specific properties for credentials
- Never hardcode credentials in source code
- Configure connection pooling based on load

### Testing

- Use Neo4j Harness for integration tests
- Provide test data via `withFixture()` Cypher queries
- Use `@DataNeo4jTest` for test slicing
- Test both successful and edge-case scenarios

### Architecture

- Use constructor injection exclusively
- Separate domain entities from DTOs
- Follow feature-based package structure
- Keep domain layer framework-agnostic

### Security

- Use Spring Boot property overrides for credentials
- Configure proper authentication and authorization
- Validate input parameters in service layer
- Use parameterized queries to prevent Cypher injection

## Constraints and Warnings

- Do not mix imperative and reactive repositories in the same application.
- Neo4j transactions are required for write operations; ensure `@Transactional` is properly
  configured.
- Be cautious with deep relationship traversal as it can cause performance issues.
- Large result sets should be paginated to avoid memory problems.
- Cypher queries are case-sensitive; ensure consistent casing in property names.
- Immutable entities require proper wither methods for generated IDs.
- Relationships in Spring Data Neo4j are not lazy-loaded by default; consider projection for large
  graphs.
- The Neo4j Java driver is not compatible with reactive streams; use the reactive driver for
  reactive operations.

## Troubleshooting

| Problem                                    | Cause                           | Solution                                              |
|--------------------------------------------|---------------------------------|-------------------------------------------------------|
| `Connection refused` on localhost:7687     | Neo4j server not running        | Start Neo4j or use embedded Neo4j for tests           |
| `Authentication failed`                    | Wrong credentials               | Check `spring.neo4j.authentication.username/password` |
| Entity not saved / `MATCH` returns nothing | Transaction not committed       | Add `@Transactional` or verify auto-commit settings   |
| `ConstraintViolationException` on save     | Duplicate `@Id` value           | Ensure IDs are unique or use `@GeneratedValue`        |
| Relationships missing in results           | Wrong `@Relationship` direction | Check `Direction.INCOMING/OUTGOING/UNDIRECTED`        |
| `@Query` returns wrong data                | Cypher parameter syntax         | Use `$paramName` not `$ {paramName}`                  |
| Test fails with `@DataNeo4jTest`           | Embedded Neo4j not started      | Ensure `@BeforeAll` starts Neo4j before tests         |

## References

For detailed documentation including complete API reference, Cypher query patterns, and
configuration options:

- [Annotations Reference](./references/reference.md#annotations-reference)
- [Cypher Query Language](./references/reference.md#cypher-query-language)
- [Configuration Properties](./references/reference.md#configuration-properties)
- [Repository Methods](./references/reference.md#repository-methods)
- [Projections and DTOs](./references/reference.md#projections-and-dtos)
- [Transaction Management](./references/reference.md#transaction-management)
- [Performance Tuning](./references/reference.md#performance-tuning)

### External Resources

- [Spring Data Neo4j Official Documentation](https://docs.spring.io/spring-data/neo4j/reference/)
- [Neo4j Developer Guide](https://neo4j.com/developer/)
- [Spring Data Commons Documentation](https://docs.spring.io/spring-data/commons/reference/)
