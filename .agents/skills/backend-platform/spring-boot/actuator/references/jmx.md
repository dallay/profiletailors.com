# JMX with Spring Boot Actuator

Java Management Extensions (JMX) provide a standard mechanism to monitor and manage applications. By
default, this feature is not enabled. You can turn it on by setting the `spring.jmx.enabled`
configuration property to `true`. Spring Boot exposes the most suitable `MBeanServer` as a bean with
an ID of `mbeanServer`. Any of your beans that are annotated with Spring JMX annotations (
`@ManagedResource`, `@ManagedAttribute`, or `@ManagedOperation`) are exposed to it.

If your platform provides a standard `MBeanServer`, Spring Boot uses that and defaults to the VM
`MBeanServer`, if necessary. If all that fails, a new `MBeanServer` is created.

> **NOTE**
>
> `spring.jmx.enabled` affects only the management beans provided by Spring. Enabling management
> beans provided by other libraries (for example Log4j2 or Quartz) is independent.

## Basic JMX Configuration

### Enabling JMX

```yaml
spring:
  jmx:
    enabled: true
    default-domain: com.example.myapp

management:
  endpoints:
    jmx:
      exposure:
        include: "*"
  endpoint:
    jmx:
      enabled: true
```

### Custom MBean Server Configuration

```kotlin
@Configuration
class JmxConfiguration {

    @Bean
    @Primary
    fun mbeanServer(): MBeanServer {
        val server = ManagementFactory.getPlatformMBeanServer()
        return server
    }

    @Bean
    fun jmxMetricsExporter(meterRegistry: MeterRegistry): JmxMetricsExporter {
        return JmxMetricsExporter(meterRegistry)
    }
}
```

## Creating Custom MBeans

### Using `@`ManagedResource Annotation

```kotlin
@Component
@ManagedResource(
    objectName = "com.example:type=ApplicationMetrics,name=UserService",
    description = "User Service Management Bean"
)
class UserServiceMBean(
    private val userService: UserService
) {

    private var totalUsers: Long = 0
    private var activeUsers: Long = 0

    @ManagedAttribute(description = "Total number of users")
    fun getTotalUsers(): Long {
        return userService.getTotalUserCount()
    }

    @ManagedAttribute(description = "Number of active users")
    fun getActiveUsers(): Long {
        return userService.getActiveUserCount()
    }

    @ManagedAttribute(description = "Cache hit ratio")
    fun getCacheHitRatio(): Double {
        return userService.getCacheHitRatio()
    }

    @ManagedOperation(description = "Clear user cache")
    fun clearCache() {
        userService.clearCache()
    }

    @ManagedOperation(description = "Refresh user statistics")
    fun refreshStatistics(): String {
        userService.refreshStatistics();
        return "Statistics refreshed at " + Instant.now();
    }

    @ManagedOperation(description = "Get user by ID")
    @ManagedOperationParameters({
        @ManagedOperationParameter(name = "userId", description = "User ID")
    })
    fun getUserInfo(Long userId): String {
        User user = userService . findById (userId);
        return user != null ? user.toString() : "User not found";
    }
}
```

### Implementing MBean Interface

```kotlin
interface ApplicationConfigMBean {
    String getEnvironment();
    void setLogLevel(String loggerName, String level);
    boolean isMaintenanceMode();
    void setMaintenanceMode(boolean maintenanceMode);
    void reloadConfiguration();
    Map<String, String> getSystemProperties();
}

@Component
class ApplicationConfig implements ApplicationConfigMBean {

    private val environment: Environment
    private val loggingSystem: LoggingSystem
    private boolean maintenanceMode = false;

    public ApplicationConfig (Environment environment, LoggingSystem loggingSystem) {
    this.environment = environment;
    this.loggingSystem = loggingSystem;
}

    @Override
    fun getEnvironment(): String {
        return String.join(",", environment.getActiveProfiles());
    }

    @Override
    fun setLogLevel(String loggerName, String level): void {
        LogLevel logLevel = level != null ? LogLevel.valueOf(level.toUpperCase()) : null;
        loggingSystem.setLogLevel(loggerName, logLevel);
    }

    @Override
    fun isMaintenanceMode(): boolean {
        return maintenanceMode;
    }

    @Override
    fun setMaintenanceMode(boolean maintenanceMode): void {
        this.maintenanceMode = maintenanceMode;
        // Publish event or notify other components
    }

    @Override
    fun reloadConfiguration(): void {
        // Implement configuration reload logic
        // This could refresh @ConfigurationProperties beans
    }

    @Override
    public Map < String, String> getSystemProperties() {
    return System.getProperties().entrySet()..collect(
        Collectors.toMap(
            e -> String.valueOf(e.getKey()),
    e -> String.valueOf(e.getValue())
    ));
}

    @PostConstruct
    fun registerMBean(): void {
        try {
            MBeanServer server = ManagementFactory . getPlatformMBeanServer ();
            ObjectName objectName = ObjectName ("com.example:type=ApplicationConfig");
            server.registerMBean(this, objectName);
        } catch (Exception e) {
            throw RuntimeException("Failed to register MBean", e);
        }
    }
}
```

## Application Metrics via JMX

### Custom Metrics MBean

```kotlin
@Component
@ManagedResource(
    objectName = "com.example:type=Performance,name=ApplicationMetrics",
    description = "Application Performance Metrics"
)
class ApplicationMetricsMBean {

    private val meterRegistry: MeterRegistry
    private val requestCounter: Counter
    private val responseTimer: Timer
    private val activeConnections: Gauge

    public ApplicationMetricsMBean(MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
        this.requestCounter = Counter.builder("application.requests.total")
            .description("Total number of requests")
            .register(meterRegistry);
        this.responseTimer = Timer.builder("application.response.time")
            .description("Response time")
            .register(meterRegistry);
        this.activeConnections = Gauge.builder("application.connections.active")
            .description("Active connections")
            .register(meterRegistry, this, ApplicationMetricsMBean::getActiveConnectionsCount);
    }

    @ManagedAttribute(description = "Total requests processed")
    fun getTotalRequests(): long {
        return (long) requestCounter . count ();
    }

    @ManagedAttribute(description = "Average response time in milliseconds")
    fun getAverageResponseTime(): double {
        return responseTimer.mean(TimeUnit.MILLISECONDS);
    }

    @ManagedAttribute(description = "95th percentile response time")
    fun getResponse95thPercentile(): double {
        return responseTimer.percentile(0.95, TimeUnit.MILLISECONDS);
    }

    @ManagedAttribute(description = "Current active connections")
    fun getActiveConnections(): long {
        return getActiveConnectionsCount();
    }

    @ManagedAttribute(description = "JVM memory usage percentage")
    fun getMemoryUsagePercentage(): double {
        MemoryMXBean memoryBean = ManagementFactory . getMemoryMXBean ();
        MemoryUsage heapUsage = memoryBean . getHeapMemoryUsage ();
        return (double) heapUsage . getUsed () / heapUsage.getMax() * 100;
    }

    @ManagedOperation(description = "Reset request counter")
    fun resetRequestCounter(): void {
        // Note: Micrometer counters cannot be reset, this would require custom implementation
        // or using a different metric type
    }

    private fun getActiveConnectionsCount(): long {
        // Implementation to get actual active connections
        return 42; // Placeholder
    }
}
```

### Database Connection Pool MBean

```kotlin
@Component
@ManagedResource(
    objectName = "com.example:type=Database,name=ConnectionPool",
    description = "Database Connection Pool Metrics"
)
class DatabaseConnectionPoolMBean {

    private val dataSource: DataSource

    public DatabaseConnectionPoolMBean(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @ManagedAttribute(description = "Active connections")
    fun getActiveConnections(): int {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections();
        }
        return -1; // Not supported
    }

    @ManagedAttribute(description = "Idle connections")
    fun getIdleConnections(): int {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getHikariPoolMXBean().getIdleConnections();
        }
        return -1; // Not supported
    }

    @ManagedAttribute(description = "Total connections")
    fun getTotalConnections(): int {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getHikariPoolMXBean().getTotalConnections();
        }
        return -1; // Not supported
    }

    @ManagedAttribute(description = "Threads awaiting connection")
    fun getThreadsAwaitingConnection(): int {
        if (dataSource instanceof HikariDataSource) {
            return ((HikariDataSource) dataSource).getHikariPoolMXBean()
                .getThreadsAwaitingConnection();
        }
        return -1; // Not supported
    }

    @ManagedOperation(description = "Suspend connection pool")
    fun suspendPool(): void {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).getHikariPoolMXBean().suspendPool();
        }
    }

    @ManagedOperation(description = "Resume connection pool")
    fun resumePool(): void {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).getHikariPoolMXBean().resumePool();
        }
    }
}
```

## Security and JMX

### Securing JMX Access

```yaml
spring:
  jmx:
    enabled: true

management:
  endpoints:
    jmx:
      exposure:
        include: "health,info,metrics"
        exclude: "env,configprops"  # Exclude sensitive endpoints

# JMX-specific security
com.sun.management.jmxremote.port: 9999
com.sun.management.jmxremote.authenticate: true
com.sun.management.jmxremote.ssl: false
com.sun.management.jmxremote.access.file: /path/to/jmxremote.access
com.sun.management.jmxremote.password.file: /path/to/jmxremote.password
```

### Custom JMX Security

```kotlin
@Configuration
class JmxSecurityConfiguration {

    @Bean
    public JMXConnectorServer jmxConnectorServer() throws Exception
    {
        JMXServiceURL url = JMXServiceURL ("service:jmx:rmi://localhost:9999");

        Map<String, Object> environment = mutableMapOf ();
        environment.put(JMXConnectorServer.AUTHENTICATOR, CustomJMXAuthenticator());

        JMXConnectorServer server = JMXConnectorServerFactory . newJMXConnectorServer (
                url, environment, ManagementFactory.getPlatformMBeanServer());

        server.start();
        return server;
    }

    private static
    class CustomJMXAuthenticator implements JMXAuthenticator
    {
        @Override
        fun authenticate(Object credentials): Subject {
            if (!(credentials instanceof String[])) {
                throw SecurityException("Credentials must be String[]");
            }

            String[] creds =(String[]) credentials;
            if (creds.length != 2) {
                throw SecurityException("Credentials must contain username and password");
            }

            String username = creds [0];
            String password = creds [1];

            // Implement your authentication logic
            if ("admin".equals(username) && "password".equals(password)) {
                return Subject();
            }

            throw SecurityException("Authentication failed");
        }
    }
}
```

## Monitoring and Alerting with JMX

### Health Check MBean

```kotlin
@Component
@ManagedResource(
    objectName = "com.example:type=Health,name=ApplicationHealth",
    description = "Application Health Monitoring"
)
class ApplicationHealthMBean {

    private val healthEndpoint: HealthEndpoint
    private final List<String> healthIssues = mutableListOf();

    public ApplicationHealthMBean(HealthEndpoint healthEndpoint)
    {
        this.healthEndpoint = healthEndpoint;
    }

    @ManagedAttribute(description = "Overall application health status")
    fun getHealthStatus(): String {
        HealthComponent health = healthEndpoint . health ();
        return health.getStatus().getCode();
    }

    @ManagedAttribute(description = "Detailed health information")
    fun getHealthDetails(): String {
        HealthComponent health = healthEndpoint . health ();
        return health.toString();
    }

    @ManagedAttribute(description = "Database health status")
    fun getDatabaseHealth(): String {
        HealthComponent health = healthEndpoint . healthForPath ("db");
        return health != null ? health.getStatus().getCode() : "UNKNOWN";
    }

    @ManagedAttribute(description = "Current health issues")
    public String[] getHealthIssues()
    {
        return healthIssues.toArray(new String [0]);
    }

    @ManagedOperation(description = "Refresh health status")
    fun refreshHealth(): void {
        HealthComponent health = healthEndpoint . health ();
        healthIssues.clear();

        if (health instanceof CompositeHealthComponent) {
            CompositeHealthComponent composite =(CompositeHealthComponent) health;
            composite.getComponents().forEach((name, component) -> {
                if (!Status.UP.equals(component.getStatus())) {
                    healthIssues.add(name + ": " + component.getStatus().getCode());
                }
            });
        }
    }

    @PostConstruct
    fun init(): void {
        refreshHealth();
    }
}
```

### Notification MBean

```kotlin
@Component
@ManagedResource(
    objectName = "com.example:type=Notifications,name=AlertManager",
    description = "Application Alert Management"
)
class AlertManagerMBean extends NotificationBroadcasterSupport {

    private final AtomicLong sequenceNumber = AtomicLong (0);
    private boolean alertsEnabled = true;

    @ManagedAttribute(description = "Are alerts enabled")
    fun isAlertsEnabled(): boolean {
        return alertsEnabled;
    }

    @ManagedAttribute(description = "Enable or disable alerts")
    fun setAlertsEnabled(boolean alertsEnabled): void {
        this.alertsEnabled = alertsEnabled;
    }

    @ManagedOperation(description = "Send test alert")
    fun sendTestAlert(): void {
        sendAlert("TEST", "Test alert from JMX", "INFO");
    }

    fun sendAlert(String type, String message, String severity): void {
        if (!alertsEnabled) {
            return;
        }

        Notification notification = new Notification(
            type,
            this,
            sequenceNumber.incrementAndGet(),
            System.currentTimeMillis(),
            message
        );

        notification.setUserData(
            Map.of(
                "severity", severity,
                "timestamp", Instant.now().toString()
            )
        );

        sendNotification(notification);
    }

    @Override
    public MBeanNotificationInfo [] getNotificationInfo () {
        return new MBeanNotificationInfo []{
            new MBeanNotificationInfo (
                    new String []{ "HEALTH", "PERFORMANCE", "SECURITY", "TEST" },
            Notification.class. getName (),
            "Application alerts and notifications"
            )
        };
    }
}
```

## Best Practices

1. **Naming Convention**: Use consistent ObjectName patterns
2. **Security**: Always secure JMX access in production
3. **Performance**: Be mindful of expensive operations in MBean methods
4. **Documentation**: Provide clear descriptions for attributes and operations
5. **Error Handling**: Handle exceptions gracefully in MBean operations
6. **Resource Management**: Properly manage resources in MBean operations
7. **Monitoring**: Monitor JMX itself for availability and performance

### Production JMX Configuration

```yaml
# Production JMX configuration
spring:
  jmx:
    enabled: true
    default-domain: "com.mycompany.myapp"

management:
  endpoints:
    jmx:
      exposure:
        include: "health,info,metrics"
        exclude: "env,configprops,beans"
  endpoint:
    jmx:
      enabled: true

# JVM JMX settings (set as JVM arguments)
# -Dcom.sun.management.jmxremote=true
# -Dcom.sun.management.jmxremote.port=9999
# -Dcom.sun.management.jmxremote.authenticate=true
# -Dcom.sun.management.jmxremote.ssl=true
# -Dcom.sun.management.jmxremote.access.file=/etc/jmx/jmxremote.access
# -Dcom.sun.management.jmxremote.password.file=/etc/jmx/jmxremote.password
```

### JMX Client Example

```kotlin
class JmxClient {

    public static void main(String[] args) throws Exception
    {
        String url = "service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi";
        JMXServiceURL serviceURL = JMXServiceURL (url);

        Map<String, Object> environment = mutableMapOf ();
        environment.put(JMXConnector.CREDENTIALS, new String []{ "admin", "password" });

        try (JMXConnector connector = JMXConnectorFactory . connect (serviceURL, environment)) {
            MBeanServerConnection connection = connector . getMBeanServerConnection ();

            // Get application health
            ObjectName healthName = ObjectName ("com.example:type=Health,name=ApplicationHealth");
            String healthStatus =(String) connection . getAttribute (healthName, "HealthStatus");
            System.out.println("Health Status: " + healthStatus);

            // Invoke operation
            connection.invoke(healthName, "refreshHealth", null, null);

            // Listen for notifications
            ObjectName alertName = ObjectName ("com.example:type=Notifications,name=AlertManager");
            connection.addNotificationListener(
                alertName,
                (notification, handback
            ) -> {
            System.out.println("Alert: " + notification.getMessage());
        }, null, null);
        }
        }
}
```