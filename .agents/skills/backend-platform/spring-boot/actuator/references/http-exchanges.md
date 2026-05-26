# HTTP Exchanges

You can enable recording of HTTP exchanges by providing a bean of type `HttpExchangeRepository` in
your application's configuration. For convenience, Spring Boot offers
`InMemoryHttpExchangeRepository`, which, by default, stores the last 100 request-response exchanges.
`InMemoryHttpExchangeRepository` is limited compared to tracing solutions, and we recommend using it
only for development environments. For production environments, we recommend using a
production-ready tracing or observability solution, such as Zipkin or OpenTelemetry. Alternatively,
you can create your own `HttpExchangeRepository`.

You can use the `httpexchanges` endpoint to obtain information about the request-response exchanges
that are stored in the `HttpExchangeRepository`.

## Basic Configuration

### In-Memory Repository (Development)

```kotlin
@Configuration
class HttpExchangesConfiguration {

    @Bean
    fun httpExchangeRepository(): InMemoryHttpExchangeRepository {
        return InMemoryHttpExchangeRepository()
    }
}
```

### Custom Repository Size

```kotlin
@Configuration
class HttpExchangesConfiguration {

    @Bean
    fun httpExchangeRepository(): InMemoryHttpExchangeRepository {
        return InMemoryHttpExchangeRepository(1000) // Store last 1000 exchanges
    }
}
```

## Custom HTTP Exchange Recording

To customize the items that are included in each recorded exchange, use the
`management.httpexchanges.recording.include` configuration property:

```yaml
management:
  httpexchanges:
    recording:
      include:
        - request-headers
        - response-headers
        - cookie-headers
        - authorization-header
        - principal
        - remote-address
        - session-id
        - time-taken
```

Available options:

- `request-headers`: Include request headers
- `response-headers`: Include response headers
- `cookie-headers`: Include cookie headers
- `authorization-header`: Include authorization header
- `principal`: Include principal information
- `remote-address`: Include remote address
- `session-id`: Include session ID
- `time-taken`: Include request processing time

## Custom HTTP Exchange Repository

### Database-backed Repository

```kotlin
@Entity
@Table(name = "http_exchanges")
class HttpExchangeEntity(
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(name = "timestamp")
    var timestamp: Instant? = null,
    
    @Column(name = "method")
    var method: String? = null,
    
    @Column(name = "uri", length = 2000)
    var uri: String? = null,
    
    @Column(name = "status")
    var status: Int? = null,
    
    @Column(name = "time_taken")
    var timeTaken: Long? = null,
    
    @Column(name = "principal")
    var principal: String? = null,
    
    @Column(name = "remote_address")
    var remoteAddress: String? = null,
    
    @Column(name = "session_id")
    var sessionId: String? = null,
    
    @Lob
    @Column(name = "request_headers")
    var requestHeaders: String? = null,
    
    @Lob
    @Column(name = "response_headers")
    var responseHeaders: String? = null
)

@Repository
interface HttpExchangeEntityRepository : JpaRepository<HttpExchangeEntity, Long> {
    
    fun findTop100ByOrderByTimestampDesc(): List<HttpExchangeEntity>
    
    @Modifying
    @Query("DELETE FROM HttpExchangeEntity h WHERE h.timestamp < :cutoff")
    fun deleteOlderThan(@Param("cutoff") cutoff: Instant)
}

@Component
class DatabaseHttpExchangeRepository(
    private val repository: HttpExchangeEntityRepository
) : HttpExchangeRepository {

    private val objectMapper = ObjectMapper()

    override fun findAll(): List<HttpExchange> {
        return repository.findTop100ByOrderByTimestampDesc()
            .map { toHttpExchange(it) }
    }

    override fun add(httpExchange: HttpExchange) {
        val entity = toEntity(httpExchange)
        repository.save(entity)
    }

    private fun toEntity(exchange: HttpExchange): HttpExchangeEntity {
        val entity = HttpExchangeEntity()
        entity.timestamp = exchange.timestamp
        
        val request = exchange.request
        entity.method = request.method
        entity.uri = request.uri.toString()
        entity.principal = exchange.principal?.name
        entity.remoteAddress = request.remoteAddress
        
        exchange.response?.let { response ->
            entity.status = response.status
        }
        
        entity.timeTaken = exchange.timeTaken?.toMillis()
        
        try {
            entity.requestHeaders = objectMapper.writeValueAsString(request.headers)
            exchange.response?.let { response ->
                entity.responseHeaders = objectMapper.writeValueAsString(response.headers)
            }
        } catch (e: Exception) {
            // Handle serialization error
        }
        
        return entity
    }

    private fun toHttpExchange(entity: HttpExchangeEntity): HttpExchange {
        // PSEUDOCODE: Conversion from entity to HttpExchange
        // This is complex due to HttpExchange being immutable
        // Real implementation requires using HttpExchange.Builder or reflection
        // See Spring Boot Actuator documentation for complete implementation
        TODO("Conversion requires custom builder - see Spring Boot Actuator docs")
    }

    @Scheduled(fixedRate = 3600000) // Clean up every hour
    fun cleanup() {
        val cutoff = Instant.now().minus(Duration.ofDays(7))
        repository.deleteOlderThan(cutoff)
    }
}
```

### Filtered HTTP Exchange Repository

```kotlin
@Component
class FilteredHttpExchangeRepository implements HttpExchangeRepository {

    private val delegate: HttpExchangeRepository
    private final Set<String> excludePaths;
    private final Set<String> excludeUserAgents;

    public FilteredHttpExchangeRepository(HttpExchangeRepository delegate) {
        this.delegate = delegate;
        this.excludePaths = setOf("/actuator/health", "/actuator/metrics", "/favicon.ico");
        this.excludeUserAgents = setOf("kube-probe", "ELB-HealthChecker");
    }

    @Override
    public List<HttpExchange> findAll() {
        return delegate.findAll();
    }

    @Override
    fun add(HttpExchange httpExchange): void {
        if (shouldRecord(httpExchange)) {
            delegate.add(httpExchange);
        }
    }

    private fun shouldRecord(HttpExchange exchange): boolean {
        String path = exchange.getRequest().getUri().getPath();
        
        // Skip health check and monitoring endpoints
        if (excludePaths.contains(path)) {
            return false;
        }
        
        // Skip requests from monitoring tools
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        if (userAgent != null && excludeUserAgents.stream().anyMatch(userAgent::contains)) {
            return false;
        }
        
        // Skip successful static resource requests
        if (path.startsWith("/static/") || path.startsWith("/css/") || path.startsWith("/js/")) {
            return exchange.getResponse() == null || exchange.getResponse().getStatus() >= 400;
        }
        
        return true;
    }
}
```

## Async HTTP Exchange Recording

### Async Repository Wrapper

```kotlin
@Component
class AsyncHttpExchangeRepository implements HttpExchangeRepository {

    private val delegate: HttpExchangeRepository
    private val taskExecutor: TaskExecutor

    public AsyncHttpExchangeRepository(HttpExchangeRepository delegate, 
                                     @Qualifier("httpExchangeTaskExecutor") TaskExecutor taskExecutor) {
        this.delegate = delegate;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public List<HttpExchange> findAll() {
        return delegate.findAll();
    }

    @Override
    fun add(HttpExchange httpExchange): void {
        taskExecutor.execute(() -> {
            try {
                delegate.add(httpExchange);
            } catch (Exception e) {
                // Log error but don't let it affect the main request
                log.error("Failed to record HTTP exchange", e);
            }
        });
    }
}

@Configuration
class HttpExchangeTaskExecutorConfiguration {

    @Bean("httpExchangeTaskExecutor")
    fun httpExchangeTaskExecutor(): TaskExecutor {
        ThreadPoolTaskExecutor executor = ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("http-exchange-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }
}
```

## HTTP Exchanges Endpoint

### Accessing HTTP Exchanges

```
GET /actuator/httpexchanges
```

Response format:

```json
{
  "exchanges": [
    {
      "timestamp": "2023-12-01T10:30:00.123Z",
      "request": {
        "method": "GET",
        "uri": "http://localhost:8080/api/users/123",
        "headers": {
          "accept": ["application/json"],
          "user-agent": ["Mozilla/5.0..."]
        },
        "remoteAddress": "192.168.1.100"
      },
      "response": {
        "status": 200,
        "headers": {
          "content-type": ["application/json"],
          "content-length": ["256"]
        }
      },
      "principal": {
        "name": "john.doe"
      },
      "session": {
        "id": "JSESSIONID123"
      },
      "timeTaken": "PT0.025S"
    }
  ]
}
```

### Securing the Endpoint

```kotlin
@Configuration
class HttpExchangesSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain httpExchangesSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .requestMatcher(EndpointRequest.to("httpexchanges"))
            .authorizeHttpRequests(requests -> 
                requests.anyRequest().hasRole("ADMIN"))
            .httpBasic(withDefaults())
            .build();
    }
}
```

## Custom HTTP Exchange Information

### Including Custom Data

```kotlin
@Component
class CustomHttpExchangeRepository implements HttpExchangeRepository {

    private val delegate: InMemoryHttpExchangeRepository

    public CustomHttpExchangeRepository() {
        this.delegate = InMemoryHttpExchangeRepository();
    }

    @Override
    public List<HttpExchange> findAll() {
        return delegate.findAll();
    }

    @Override
    fun add(HttpExchange httpExchange): void {
        HttpExchange enrichedExchange = enrichExchange(httpExchange);
        delegate.add(enrichedExchange);
    }

    private fun enrichExchange(HttpExchange original): HttpExchange {
        // Add custom information to the exchange
        // Note: HttpExchange is immutable, so we need to create a wrapper
        // or use reflection to modify internal state
        
        // For demonstration, we'll just add it normally
        // In practice, you might need to create a custom implementation
        return original;
    }
}

@Component
class HttpExchangeEnricher {

    fun enrich(HttpServletRequest request, HttpServletResponse response): void {
        // Add custom attributes that can be picked up by the repository
        request.setAttribute("custom.trace.id", getTraceId());
        request.setAttribute("custom.user.role", getUserRole());
        request.setAttribute("custom.api.version", getApiVersion(request));
    }

    private fun getTraceId(): String {
        // Get from tracing context
        return "trace-123";
    }

    private fun getUserRole(): String {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getAuthorities().toString() : "anonymous";
    }

    private fun getApiVersion(HttpServletRequest request): String {
        return request.getHeader("API-Version");
    }
}
```

## Performance Considerations

### Configuration for Production

```yaml
management:
  httpexchanges:
    recording:
      include:
        - time-taken
        - principal
        - remote-address
      # Exclude detailed headers to reduce memory usage
      exclude:
        - request-headers
        - response-headers
  endpoint:
    httpexchanges:
      enabled: false  # Disable in production for security
```

### Custom Sampling

```kotlin
@Component
class SamplingHttpExchangeRepository implements HttpExchangeRepository {

    private val delegate: HttpExchangeRepository
    private final Random random = Random();
    private val samplingRate: double

    public SamplingHttpExchangeRepository(HttpExchangeRepository delegate,
                                        @Value("${app.http-exchanges.sampling-rate:0.1}") double samplingRate) {
        this.delegate = delegate;
        this.samplingRate = samplingRate;
    }

    @Override
    public List<HttpExchange> findAll() {
        return delegate.findAll();
    }

    @Override
    fun add(HttpExchange httpExchange): void {
        if (random.nextDouble() < samplingRate) {
            delegate.add(httpExchange);
        }
    }
}
```

## Best Practices

1. **Production Use**: Disable HTTP exchanges endpoint in production or secure it properly
2. **Memory Management**: Use limited-size repositories to prevent memory leaks
3. **Sensitive Data**: Be careful not to log sensitive information in headers
4. **Performance**: Consider async recording for high-throughput applications
5. **Sampling**: Use sampling in production to reduce overhead
6. **Retention**: Implement cleanup policies for stored exchanges
7. **Security**: Ensure recorded data doesn't contain credentials or tokens

### Production Configuration Example

```yaml
management:
  endpoint:
    httpexchanges:
      enabled: false  # Disabled in production
  httpexchanges:
    recording:
      include:
        - time-taken
        - principal
        - remote-address
      exclude:
        - authorization-header
        - cookie-headers
        - request-headers
        - response-headers

logging:
  level:
    org.springframework.boot.actuate.web.exchanges: WARN
```