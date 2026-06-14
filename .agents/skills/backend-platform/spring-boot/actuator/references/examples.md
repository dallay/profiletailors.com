# Spring Boot Actuator Examples

## Complete Application Example

### Application Configuration

```kotlin
@SpringBootApplication
class MonitoringApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SpringApplication(MonitoringApplication::class.java)
            // Enable startup tracking
            app.setApplicationStartup(BufferingApplicationStartup(2048))
            app.run(*args)
        }
    }

    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config()
                .commonTags("application", "order-service", "environment", "production")
        }
    }
}
```

### Application Properties

```yaml
spring:
  application:
    name: order-service

info:
  app:
    name: ${spring.application.name}
    description: Order Processing Service
    version: "@project.version@"
    encoding: "@project.build.sourceEncoding@"
    java:
      version: "@java.version@"

management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus,startup"
      base-path: "/actuator"

  endpoint:
    health:
      show-details: when-authorized
      show-components: always
      probes:
        enabled: true
      group:
        liveness:
          include: "ping,diskSpace"
        readiness:
          include: "readinessState,db,redis,externalApi"
          show-details: always
      status:
        order: "fatal,down,out-of-service,warning,unknown,up"
        http-mapping:
          down: 503
          fatal: 503
          warning: 500

    info:
      enabled: true

    metrics:
      enabled: true

  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      region: eu-west-1

  info:
    git:
      mode: full
    build:
      enabled: true
```

## Health Indicators Examples

### Database Health Indicator

```kotlin
@Component
class DatabaseHealthIndicator(
    private val dataSource: DataSource
) : HealthIndicator {

    companion object {
        private val log = LoggerFactory.getLogger(DatabaseHealthIndicator::class.java)
    }

    override fun health(): Health {
        return try {
            dataSource.connection.use { connection ->
                val startTime = System.currentTimeMillis()
                val valid = connection.isValid(1000)
                val responseTime = System.currentTimeMillis() - startTime

                if (!valid) {
                    return Health.down()
                        .withDetail("database", "Connection not valid")
                        .build()
                }

                val metaData = connection.metaData

                val builder = Health.up()
                    .withDetail("database", metaData.databaseProductName)
                    .withDetail("version", metaData.databaseProductVersion)
                    .withDetail("responseTime", "${responseTime}ms")

                if (responseTime > 500) {
                    builder.status("WARNING")
                        .withDetail("warning", "Slow database connection")
                }

                builder.build()
            }
        } catch (ex: SQLException) {
            log.error("Database health check failed", ex)
            Health.down()
                .withDetail("error", ex.message)
                .withException(ex)
                .build()
        }
    }
}
```

### External API Health Indicator with Circuit Breaker

```kotlin
@Component
class PaymentGatewayHealthIndicator(
    private val restTemplate: RestTemplate,
    @Qualifier("paymentCircuitBreaker") private val circuitBreaker: CircuitBreaker
) : HealthIndicator {

    override fun health(): Health {
        val state = circuitBreaker.state

        val builder = Health.up()
            .withDetail("circuitBreaker", state.toString())
            .withDetail("service", "Payment Gateway")

        if (state == CircuitBreaker.State.OPEN) {
            return builder
                .down()
                .withDetail("reason", "Circuit breaker is open")
                .build()
        }

        if (state == CircuitBreaker.State.HALF_OPEN) {
            builder.status("WARNING")
                .withDetail("reason", "Circuit breaker is testing")
        }

        return try {
            val startTime = System.currentTimeMillis()
            val response = restTemplate.getForEntity(
                "https://api.payment.com/health",
                Map::class.java
            )
            val responseTime = System.currentTimeMillis() - startTime

            builder
                .withDetail("responseTime", "${responseTime}ms")
                .withDetail("statusCode", response.statusCode.value())
                .build()

        } catch (ex: Exception) {
            builder
                .down()
                .withDetail("error", ex.message)
                .build()
        }
    }
}
```

### Cache Health Indicator

```kotlin
@Component
class CacheHealthIndicator(
    private val cacheManager: CacheManager
) : HealthIndicator {

    override fun health(): Health {
        val cacheNames = cacheManager.cacheNames

        val cacheDetails = mutableMapOf<String, Any>()
        var allHealthy = true

        for (cacheName in cacheNames) {
            val cache = cacheManager.getCache(cacheName)
            if (cache != null) {
                try {
                    // Test cache operations
                    cache.put("health-check", "test")
                    val value = cache.get("health-check", String::class.java)
                    cache.evict("health-check")

                    cacheDetails[cacheName] = "UP"
                } catch (ex: Exception) {
                    cacheDetails[cacheName] = "DOWN: ${ex.message}"
                    allHealthy = false
                }
            }
        }

        val builder = if (allHealthy) Health.up() else Health.down()
        return builder
            .withDetail("caches", cacheDetails)
            .withDetail("totalCaches", cacheNames.size)
            .build()
    }
}
```

### Reactive Health Indicator

```kotlin
@Component
class ReactiveExternalServiceHealthIndicator(
    webClientBuilder: WebClient.Builder
) : ReactiveHealthIndicator {

    private val webClient = webClientBuilder
        .baseUrl("https://api.example.com")
        .build()

    override fun health(): Mono<Health> {
        return webClient
            .get()
            .uri("/health")
            .retrieve()
            .toBodilessEntity()
            .map { response ->
                Health.up()
                    .withDetail("statusCode", response.statusCode.value())
                    .withDetail("service", "External API")
                    .build()
            }
            .timeout(Duration.ofSeconds(2))
            .onErrorResume(TimeoutException::class.java) {
                Mono.just(
                    Health.down()
                        .withDetail("error", "Timeout after 2 seconds")
                        .build()
                )
            }
            .onErrorResume { ex ->
                Mono.just(
                    Health.down()
                        .withDetail("error", ex.message)
                        .build()
                )
            }
    }
}
```

## Custom Endpoints Examples

### Application Statistics Endpoint

```kotlin
@Component
@Endpoint(id = "appstats")
class AppStatisticsEndpoint(
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val meterRegistry: MeterRegistry
) {

    @ReadOperation
    fun getStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        // User statistics
        stats["users"] = mapOf(
            "total" to userRepository.count(),
            "active" to userRepository.countByStatus("ACTIVE"),
            "inactive" to userRepository.countByStatus("INACTIVE")
        )

        // Order statistics
        stats["orders"] = mapOf(
            "total" to orderRepository.count(),
            "pending" to orderRepository.countByStatus("PENDING"),
            "completed" to orderRepository.countByStatus("COMPLETED"),
            "cancelled" to orderRepository.countByStatus("CANCELLED")
        )

        // JVM statistics
        stats["jvm"] = mapOf(
            "memoryUsed" to getMetricValue("jvm.memory.used"),
            "memoryMax" to getMetricValue("jvm.memory.max"),
            "threadCount" to getMetricValue("jvm.threads.live")
        )

        stats["timestamp"] = Instant.now()

        return stats
    }

    @ReadOperation
    fun getStatisticsByType(@Selector type: String): Map<String, Any> {
        return when (type.lowercase()) {
            "users" -> mapOf(
                "total" to userRepository.count(),
                "byStatus" to userRepository.countByStatusGrouped()
            )
            "orders" -> mapOf(
                "total" to orderRepository.count(),
                "byStatus" to orderRepository.countByStatusGrouped()
            )
            else -> mapOf("error" to "Unknown type: $type")
        }
    }

    private fun getMetricValue(meterName: String): Double {
        return meterRegistry.find(meterName)
            .gauge()
            ?.value()
            ?: 0.0
    }
}
```

### Feature Flags Endpoint

```kotlin
@Component
@Endpoint(id = "features")
class FeatureFlagsEndpoint(
    private val eventPublisher: ApplicationEventPublisher
) {

    private val features = ConcurrentHashMap<String, FeatureFlag>()

    init {
        initializeDefaultFeatures()
    }

    private fun initializeDefaultFeatures() {
        features["dark-mode"] = FeatureFlag(true, "Dark mode UI")
        features["new-checkout"] = FeatureFlag(false, "New checkout flow")
        features["ai-recommendations"] = FeatureFlag(false, "AI-powered recommendations")
    }

    @ReadOperation
    fun getAllFeatures(): Map<String, FeatureFlag> {
        return features
    }

    @ReadOperation
    fun getFeature(@Selector name: String): FeatureFlag? {
        return features[name]
    }

    @WriteOperation
    fun updateFeature(
        @Selector name: String,
        enabled: Boolean?,
        description: String?
    ) {
        features.compute(name) { _, existing ->
            val feature = existing ?: FeatureFlag(false, "")

            enabled?.let { feature.enabled = it }
            description?.let { feature.description = it }

            feature
        }

        eventPublisher.publishEvent(FeatureFlagChangedEvent(name, features[name]!!))
    }

    @DeleteOperation
    fun deleteFeature(@Selector name: String) {
        val removed = features.remove(name)
        if (removed != null) {
            eventPublisher.publishEvent(FeatureFlagDeletedEvent(name))
        }
    }

    data class FeatureFlag(
        var enabled: Boolean = false,
        var description: String = "",
        var lastModified: Instant = Instant.now()
    ) {
        fun setEnabled(value: Boolean) {
            enabled = value
            lastModified = Instant.now()
        }
    }
}
```

### Cache Management Endpoint

```kotlin
@Component
@Endpoint(id = "caches")
class CacheManagementEndpoint(
    private val cacheManager: CacheManager
) {

    @ReadOperation
    fun getCaches(): Map<String, Any> {
        val cacheNames = cacheManager.cacheNames

        return mapOf(
            "totalCaches" to cacheNames.size,
            "caches" to cacheNames
        )
    }

    @ReadOperation
    fun getCache(@Selector cacheName: String): Map<String, Any> {
        val cache = cacheManager.getCache(cacheName)
            ?: return mapOf("error" to "Cache not found: $cacheName")

        return mapOf(
            "name" to cacheName,
            "type" to cache.javaClass.simpleName
        )
    }

    @DeleteOperation
    fun clearCache(@Selector cacheName: String) {
        val cache = cacheManager.getCache(cacheName)
        cache?.clear()
    }

    @WriteOperation
    fun clearAllCaches() {
        cacheManager.cacheNames.forEach { name ->
            val cache = cacheManager.getCache(name)
            cache?.clear()
        }
    }
}
```

## Custom Info Contributors

### Detailed Application Info

```kotlin
@Component
class DetailedApplicationInfoContributor(
    private val environment: Environment,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository
) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        // Runtime information
        val runtime = Runtime.getRuntime()
        builder.withDetail(
            "runtime", mapOf(
                "processors" to runtime.availableProcessors(),
                "freeMemory" to runtime.freeMemory(),
                "totalMemory" to runtime.totalMemory(),
                "maxMemory" to runtime.maxMemory(),
                "uptime" to ManagementFactory.getRuntimeMXBean().uptime
            )
        )

        // Active profiles
        builder.withDetail("profiles", environment.activeProfiles.toList())

        // Database statistics
        builder.withDetail(
            "database", mapOf(
                "users" to mapOf(
                    "total" to userRepository.count(),
                    "active" to userRepository.countByStatus("ACTIVE")
                ),
                "orders" to mapOf(
                    "total" to orderRepository.count(),
                    "pending" to orderRepository.countByStatus("PENDING"),
                    "completed" to orderRepository.countByStatus("COMPLETED")
                )
            )
        )

        // Deployment information
        builder.withDetail(
            "deployment", mapOf(
                "environment" to environment.getProperty("app.environment", "unknown"),
                "region" to environment.getProperty("app.region", "unknown"),
                "instance" to getHostname()
            )
        )
    }

    private fun getHostname(): String {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "unknown"
        }
    }
}
```

### Dependency Version Info

```kotlin
@Component
class DependencyVersionInfoContributor : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        val versions = mutableMapOf<String, String>()

        // Spring versions
        versions["spring-boot"] = SpringBootVersion.getVersion() ?: "unknown"
        versions["spring-framework"] = SpringVersion.getVersion()

        // Java version
        versions["java"] = System.getProperty("java.version")
        versions["java-vendor"] = System.getProperty("java.vendor")

        // Other dependencies (if available)
        addVersionIfPresent(versions, "hibernate", "org.hibernate.Version", "getVersionString")
        addVersionIfPresent(
            versions,
            "jackson",
            "com.fasterxml.jackson.core.Version",
            "versionString"
        )

        builder.withDetail("dependencies", versions)
    }

    private fun addVersionIfPresent(
        versions: MutableMap<String, String>,
        key: String,
        className: String,
        methodName: String
    ) {
        try {
            val clazz = Class.forName(className)
            val versionInstance = clazz.getDeclaredConstructor().newInstance()
            val version = clazz.getMethod(methodName).invoke(versionInstance) as String
            versions[key] = version
        } catch (e: Exception) {
            // Dependency not present or version not accessible
        }
    }
}
```

## Metrics Examples

### Service Metrics

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val meterRegistry: MeterRegistry
) {

    private val orderCreatedCounter: Counter
    private val orderFailedCounter: Counter
    private val orderProcessingTimer: Timer
    private val orderAmountSummary: DistributionSummary

    init {
        // Counters
        orderCreatedCounter = Counter.builder("orders.created")
            .description("Total number of orders created")
            .tag("service", "order")
            .register(meterRegistry)

        orderFailedCounter = Counter.builder("orders.failed")
            .description("Total number of failed orders")
            .tag("service", "order")
            .register(meterRegistry)

        // Timer for processing duration
        orderProcessingTimer = Timer.builder("order.processing.time")
            .description("Order processing duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)

        // Distribution summary for order amounts
        orderAmountSummary = DistributionSummary.builder("order.amount")
            .description("Order amount distribution")
            .baseUnit("EUR")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)

        // Gauge for pending orders
        Gauge.builder("orders.pending", orderRepository) { repo ->
            repo.countByStatus("PENDING").toDouble()
        }
            .description("Number of pending orders")
            .register(meterRegistry)
    }

    fun createOrder(request: OrderRequest): Order {
        return orderProcessingTimer.record {
            try {
                val order = processOrder(request)
                orderCreatedCounter.increment()
                orderAmountSummary.record(order.totalAmount)

                // Tag by payment method
                Counter.builder("orders.created.by.payment")
                    .tag("paymentMethod", order.paymentMethod)
                    .register(meterRegistry)
                    .increment()

                order
            } catch (ex: Exception) {
                orderFailedCounter.increment()
                throw ex
            }
        }!!
    }

    private fun processOrder(request: OrderRequest): Order {
        // Implementation
        return Order()
    }
}
```

### Custom Metrics with Tags

```kotlin
@Service
class MetricsService(
    private val registry: MeterRegistry
) {

    fun recordHttpRequest(method: String, endpoint: String, statusCode: Int, duration: Long) {
        Timer.builder("http.requests")
            .tag("method", method)
            .tag("endpoint", endpoint)
            .tag("status", statusCode.toString())
            .register(registry)
            .record(duration, TimeUnit.MILLISECONDS)
    }

    fun recordDatabaseQuery(query: String, duration: Long, success: Boolean) {
        Timer.builder("db.queries")
            .tag("query", query)
            .tag("success", success.toString())
            .register(registry)
            .record(duration, TimeUnit.MILLISECONDS)
    }

    fun trackCacheHit(cacheName: String, hit: Boolean) {
        Counter.builder("cache.operations")
            .tag("cache", cacheName)
            .tag("result", if (hit) "hit" else "miss")
            .register(registry)
            .increment()
    }

    fun recordBusinessMetric(metricName: String, value: Double, tags: Map<String, String>) {
        val builder = DistributionSummary.builder(metricName)
        tags.forEach { (key, value) -> builder.tag(key, value) }
        builder.register(registry).record(value)
    }
}
```

## Security Configuration Examples

### Complete Security Setup

```kotlin
@Configuration
@EnableWebSecurity
class ActuatorSecurityConfiguration {

    @Bean
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests { auth ->
                auth
                    // Public health check (for load balancers)
                    .requestMatchers(EndpointRequest.to(HealthEndpoint::class.java)).permitAll()

                    // Info endpoint for authenticated users
                    .requestMatchers(EndpointRequest.to(InfoEndpoint::class.java)).authenticated()

                    // Read-only metrics for monitoring role
                    .requestMatchers(HttpMethod.GET, "/actuator/metrics/**")
                    .hasAnyRole("MONITOR", "ADMIN")

                    // Prometheus endpoint for monitoring tools
                    .requestMatchers(EndpointRequest.to("prometheus"))
                    .hasRole("MONITOR")

                    // Write operations only for admin
                    .requestMatchers(HttpMethod.POST, "/actuator/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/actuator/**").hasRole("ADMIN")

                    // Everything else requires admin
                    .anyRequest().hasRole("ADMIN")
            }
            .httpBasic(Customizer.withDefaults())
            .build()
    }

    @Bean
    fun actuatorUsers(): UserDetailsService {
        val monitor = User.builder()
            .username("monitor")
            .password("{noop}monitor-password")
            .roles("MONITOR")
            .build()

        val admin = User.builder()
            .username("admin")
            .password("{noop}admin-password")
            .roles("ADMIN", "MONITOR")
            .build()

        return InMemoryUserDetailsManager(monitor, admin)
    }
}
```

### IP-Based Access Control

```kotlin
@Configuration
class IpBasedActuatorSecurity {

    @Bean
    fun actuatorSecurity(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers { request ->
                        isFromAllowedIp(request.remoteAddr)
                    }.permitAll()
                    .anyRequest().denyAll()
            }
            .build()
    }

    private fun isFromAllowedIp(remoteAddr: String): Boolean {
        // Allow localhost and specific IPs
        return remoteAddr == "127.0.0.1" ||
                remoteAddr == "0:0:0:0:0:0:0:1" ||
                remoteAddr.startsWith("10.0.0.")
    }
}
```

## Testing Examples

### Health Indicator Tests

```kotlin
@SpringBootTest
class DatabaseHealthIndicatorTest {

    @Autowired
    private lateinit var healthIndicator: DatabaseHealthIndicator

    @MockBean
    private lateinit var dataSource: DataSource

    @Test
    fun shouldReturnUpWhenDatabaseIsHealthy() {
        val connection = mock<Connection>()
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.isValid(1000)).thenReturn(true)

        val metaData = mock<DatabaseMetaData>()
        whenever(connection.metaData).thenReturn(metaData)
        whenever(metaData.databaseProductName).thenReturn("PostgreSQL")

        val health = healthIndicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details).containsKey("database")
    }

    @Test
    fun shouldReturnDownWhenDatabaseConnectionFails() {
        whenever(dataSource.connection).thenThrow(SQLException("Connection failed"))

        val health = healthIndicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details).containsKey("error")
    }
}
```

### Endpoint Tests

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ActuatorEndpointIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun healthEndpointShouldBeAccessible() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun metricsEndpointShouldListAvailableMetrics() {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.names").isArray)
            .andExpect(jsonPath("$.names[*]", hasItem("jvm.memory.used")))
    }

    @Test
    fun customEndpointShouldWork() {
        mockMvc.perform(get("/actuator/features"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun securedEndpointShouldRequireAuthentication() {
        mockMvc.perform(
            post("/actuator/features/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")
        )
            .andExpect(status().isOk)
    }
}
```

## Kubernetes Integration Example

### Deployment with Probes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: order-service:1.0.0
          ports:
            - containerPort: 8080
              name: http
            - containerPort: 8081
              name: management

          env:
            - name: MANAGEMENT_SERVER_PORT
              value: "8081"
            - name: MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE
              value: "health,info,prometheus"

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: management
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: management
            initialDelaySeconds: 30
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3

          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  selector:
    app: order-service
  ports:
    - name: http
      port: 8080
      targetPort: http
    - name: management
      port: 8081
      targetPort: management
```

### ServiceMonitor for Prometheus Operator

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: order-service-metrics
  labels:
    app: order-service
spec:
  selector:
    matchLabels:
      app: order-service
  endpoints:
    - port: management
      path: /actuator/prometheus
      interval: 30s
      scrapeTimeout: 10s
```
