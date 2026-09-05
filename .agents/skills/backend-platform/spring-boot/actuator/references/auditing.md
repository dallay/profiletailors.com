# Auditing with Spring Boot Actuator

Once Spring Security is in play, Spring Boot Actuator has a flexible audit framework that publishes
events (by default, "authentication success", "failure" and "access denied" exceptions). This
feature can be very useful for reporting and for implementing a lock-out policy based on
authentication failures.

You can enable auditing by providing a bean of type `AuditEventRepository` in your application's
configuration. For convenience, Spring Boot offers an `InMemoryAuditEventRepository`.
`InMemoryAuditEventRepository` has limited capabilities, and we recommend using it only for
development environments. For production environments, consider creating your own alternative
`AuditEventRepository` implementation.

## Basic Audit Configuration

### In-Memory Audit Repository (Development)

```kotlin
@Configuration
class AuditConfiguration {

    @Bean
    fun auditEventRepository(): AuditEventRepository {
        return InMemoryAuditEventRepository()
    }
}
```

### Database Audit Repository (Production)

```kotlin
@Entity
@Table(name = "audit_events")
class PersistentAuditEvent(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "principal", nullable = false)
    var principal: String = "",

    @Column(name = "audit_event_type", nullable = false)
    var auditEventType: String = "",

    @Column(name = "audit_event_date", nullable = false)
    var auditEventDate: Instant = Instant.now(),

    @ElementCollection
    @MapKeyColumn(name = "name")
    @Column(name = "value")
    @CollectionTable(
        name = "audit_event_data",
        joinColumns = [JoinColumn(name = "event_id")]
    )
    var data: MutableMap<String, String> = mutableMapOf()
)

@Repository
class CustomAuditEventRepository(
    private val repository: PersistentAuditEventRepository
) : AuditEventRepository {

    override fun add(event: AuditEvent) {
        val persistentEvent = PersistentAuditEvent(
            principal = event.principal,
            auditEventType = event.type,
            auditEventDate = event.timestamp,
            data = event.data.toMutableMap()
        )
        repository.save(persistentEvent)
    }

    override fun find(principal: String, after: Instant, type: String): List<AuditEvent> {
        val events = repository.findByPrincipalAndAuditEventDateAfterAndAuditEventType(
            principal, after, type
        )
        return events.map { convertToAuditEvent(it) }
    }

    private fun convertToAuditEvent(persistentEvent: PersistentAuditEvent): AuditEvent {
        return AuditEvent(
            persistentEvent.auditEventDate,
            persistentEvent.principal,
            persistentEvent.auditEventType,
            persistentEvent.data
        )
    }
}
```

## Custom Auditing

### Custom Audit Events

You can publish custom audit events using `AuditEventRepository`:

```kotlin
@Service
class UserService(
    private val auditEventRepository: AuditEventRepository,
    private val userRepository: UserRepository
) {

    fun createUser(request: CreateUserRequest): User {
        val user = userRepository.save(request.toUser())

        // Publish audit event
        val data = mutableMapOf(
            "userId" to user.id.toString(),
            "username" to user.username,
            "email" to user.email
        )

        val event = AuditEvent(getCurrentUsername(), "USER_CREATED", data)
        auditEventRepository.add(event)

        return user
    }

    fun deleteUser(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        userRepository.delete(user)

        // Publish audit event
        val data = mutableMapOf(
            "userId" to userId.toString(),
            "username" to user.username
        )

        val event = AuditEvent(getCurrentUsername(), "USER_DELETED", data)
        auditEventRepository.add(event)
    }

    private fun getCurrentUsername(): String {
        val auth = SecurityContextHolder.getContext().authentication
        return auth?.name ?: "system"
    }
}
```

### Custom Audit Event Publisher

```kotlin
@Component
class AuditEventPublisher(
    private val auditEventRepository: AuditEventRepository
) {

    fun publishEvent(type: String, data: Map<String, String>) {
        val principal = getCurrentPrincipal()
        val event = AuditEvent(principal, type, data)
        auditEventRepository.add(event)
    }

    fun publishSecurityEvent(type: String, details: String) {
        val data = mutableMapOf(
            "details" to details,
            "timestamp" to Instant.now().toString(),
            "source" to "security"
        )
        publishEvent(type, data)
    }

    fun publishBusinessEvent(type: String, entityId: String, action: String) {
        val data = mutableMapOf(
            "entityId" to entityId,
            "action" to action,
            "timestamp" to Instant.now().toString()
        )
        publishEvent(type, data)
    }

    private fun getCurrentPrincipal(): String {
        val auth = SecurityContextHolder.getContext().authentication
        return auth?.name ?: "anonymous"
    }
}
```

## Method-Level Auditing

### Using AOP for Automatic Auditing

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Auditable(
    val value: String = "",
    val type: String = "",
    val includeArgs: Boolean = false,
    val includeResult: Boolean = false
)

@Aspect
@Component
class AuditableAspect(
    private val auditEventPublisher: AuditEventPublisher
) {

    @Around("@annotation(auditable)")
    fun auditMethod(joinPoint: ProceedingJoinPoint, auditable: Auditable): Any? {
        val methodName = joinPoint.signature.name
        val className = joinPoint.target.javaClass.simpleName
        val auditType = if (auditable.type.isEmpty()) {
            "$className.$methodName"
        } else {
            auditable.type
        }

        val data = mutableMapOf(
            "method" to methodName,
            "class" to className
        )

        if (auditable.includeArgs) {
            val args = joinPoint.args
            args.forEachIndexed { index, arg ->
                data["arg$index"] = arg.toString()
            }
        }

        return try {
            val result = joinPoint.proceed()

            if (auditable.includeResult && result != null) {
                data["result"] = result.toString()
            }

            data["status"] = "success"
            auditEventPublisher.publishEvent(auditType, data)

            result
        } catch (ex: Exception) {
            data["status"] = "failure"
            data["error"] = ex.message ?: "Unknown error"
            auditEventPublisher.publishEvent(auditType, data)
            throw ex
        }
    }
}
```

### Usage Example

```kotlin
@Service
class OrderService {

    @Auditable(type = "ORDER_CREATED", includeArgs = true)
    fun createOrder(request: CreateOrderRequest): Order {
        // Order creation logic
        return Order()
    }

    @Auditable(type = "ORDER_CANCELLED", includeResult = true)
    fun cancelOrder(orderId: Long): Order {
        // Order cancellation logic
        return cancelledOrder
    }

    @Auditable(type = "PAYMENT_PROCESSED")
    fun processPayment(request: PaymentRequest): PaymentResult {
        // Payment processing logic
        return PaymentResult()
    }
}
```

## Security Audit Events

### Authentication Events

Spring Boot automatically publishes authentication events when using Spring Security:

- `AUTHENTICATION_SUCCESS`
- `AUTHENTICATION_FAILURE`
- `ACCESS_DENIED`

### Custom Security Events

```kotlin
@Component
class SecurityAuditService(
    private val auditEventPublisher: AuditEventPublisher
) {

    @EventListener
    fun handleAuthenticationSuccess(event: AuthenticationSuccessEvent) {
        val data = mutableMapOf(
            "username" to event.authentication.name,
            "authorities" to event.authentication.authorities.toString(),
            "source" to getClientIP()
        )

        auditEventPublisher.publishEvent("AUTHENTICATION_SUCCESS", data)
    }

    @EventListener
    fun handleAuthenticationFailure(event: AbstractAuthenticationFailureEvent) {
        val data = mutableMapOf(
            "username" to event.authentication.name,
            "exception" to event.exception.javaClass.simpleName,
            "message" to (event.exception.message ?: ""),
            "source" to getClientIP()
        )

        auditEventPublisher.publishEvent("AUTHENTICATION_FAILURE", data)
    }

    @EventListener
    fun handleAccessDenied(event: AuthorizationDeniedEvent<*>) {
        val data = mutableMapOf(
            "username" to event.authentication.get().name,
            "resource" to event.authorizationDecision.toString(),
            "source" to getClientIP()
        )

        auditEventPublisher.publishEvent("ACCESS_DENIED", data)
    }

    private fun getClientIP(): String {
        val requestAttributes = RequestContextHolder.getRequestAttributes()
        if (requestAttributes is ServletRequestAttributes) {
            val request = requestAttributes.request
            return request.remoteAddr
        }
        return "unknown"
    }
}
```

### Password Change Auditing

```kotlin
@Service
class PasswordService(
    private val auditEventPublisher: AuditEventPublisher,
    private val passwordEncoder: PasswordEncoder
) {

    fun changePassword(oldPassword: String, newPassword: String) {
        val username = getCurrentUsername()

        try {
            // Validate old password
            if (!isCurrentPassword(oldPassword)) {
                val data = mutableMapOf(
                    "username" to username,
                    "reason" to "invalid_old_password"
                )
                auditEventPublisher.publishEvent("PASSWORD_CHANGE_FAILED", data)
                throw InvalidPasswordException("Invalid old password")
            }

            // Change password
            updatePassword(newPassword)

            // Audit success
            val data = mutableMapOf("username" to username)
            auditEventPublisher.publishEvent("PASSWORD_CHANGED", data)

        } catch (ex: Exception) {
            val data = mutableMapOf(
                "username" to username,
                "error" to (ex.message ?: "")
            )
            auditEventPublisher.publishEvent("PASSWORD_CHANGE_ERROR", data)
            throw ex
        }
    }

    private fun isCurrentPassword(password: String): Boolean {
        // Implementation
        return true
    }

    private fun updatePassword(newPassword: String) {
        // Implementation
    }

    private fun getCurrentUsername(): String {
        val auth = SecurityContextHolder.getContext().authentication
        return auth?.name ?: "anonymous"
    }
}
```

## Audit Events Endpoint

The `/actuator/auditevents` endpoint exposes audit events:

```
GET /actuator/auditevents
GET /actuator/auditevents?principal=user&after=2023-01-01T00:00:00Z&type=USER_CREATED
```

Response format:

```json
{
  "events": [
    {
      "timestamp": "2023-12-01T10:30:00Z",
      "principal": "admin",
      "type": "USER_CREATED",
      "data": {
        "userId": "123",
        "username": "newuser",
        "email": "user@example.com"
      }
    }
  ]
}
```

## Production Configuration

### Secure Audit Endpoint

```kotlin
@Configuration
class AuditSecurityConfig {

    @Bean
    @Order(1)
    fun auditSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(EndpointRequest.to("auditevents"))
            .authorizeHttpRequests { requests ->
                requests.anyRequest().hasRole("AUDITOR")
            }
            .httpBasic(withDefaults())
            .build()
    }
}
```

### Audit Configuration

```yaml
management:
  endpoint:
    auditevents:
      enabled: true
      cache:
        time-to-live: 10s
  endpoints:
    web:
      exposure:
        include: "auditevents"

# Custom audit properties
audit:
  retention-days: 90
  max-events-per-request: 100
  sensitive-data-masking: true
```

## Best Practices

1. **Data Sensitivity**: Never include sensitive data (passwords, tokens) in audit events
2. **Performance**: Consider async processing for high-volume audit events
3. **Retention**: Implement audit data retention policies
4. **Security**: Secure the audit endpoint and audit data storage
5. **Monitoring**: Monitor audit system health and performance
6. **Compliance**: Ensure audit events meet regulatory requirements
7. **Immutability**: Ensure audit events cannot be modified after creation

### Async Audit Processing

```kotlin
@Configuration
@EnableAsync
class AsyncAuditConfiguration {

    @Bean
    fun auditTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("audit-")
        executor.initialize()
        return executor
    }
}

@Service
class AsyncAuditEventRepository(
    private val delegate: AuditEventRepository
) : AuditEventRepository {

    @Async("auditTaskExecutor")
    override fun add(event: AuditEvent) {
        delegate.add(event)
    }

    override fun find(principal: String, after: Instant, type: String): List<AuditEvent> {
        return delegate.find(principal, after, type)
    }
}
```
