# Design: Notification Admin Queries and Safe Retry Operations

## Technical Approach

Implement administrative notification visibility and safe retry through hexagonal architecture layers:

- **Domain**: extend `Notification` aggregate with retry eligibility rules (template-based whitelist)
- **Application**: new query port `NotificationAdminQuery` for read-only paginated access; new command `RetryNotificationCommand` with eligibility gate; handlers orchestrate existing `NotificationService` for retry dispatch
- **Infrastructure**: R2DBC adapter for paginated queries with filters; Spring WebFlux controller for `GET /admin/notifications` and `POST /admin/notifications/{id}/retry`; redaction utility; authorization via `OperatorAccessResolver`; audit via `AdminAuditPublisher`

Maps to proposal scope: query port + adapter (REQ-PN-001), redaction (REQ-PN-002), retry through existing service (REQ-PN-003), eligibility gate (REQ-PN-004), authorization (REQ-PN-006/007), audit (REQ-PN-005), observability (REQ-PN-008).

References specs: `platform-notifications`, `admin-authorization` delta (permissions), `platform-admin-audit` delta (`NOTIFICATION_RETRIED` event).

## Architecture Decisions

### Decision: Query Port in Application Layer

**Choice**: Define `NotificationAdminQuery` interface in `platformadmin.application.query` with paginated result contract

**Alternatives considered**:
- Repository extension in notifications domain (violates bounded context isolation — platformadmin should not extend notifications internals)
- Direct R2DBC calls in controller (violates hexagonal architecture — no domain/application mediation)

**Rationale**: Application port keeps notifications domain unchanged while allowing platformadmin bounded context to query through a stable contract. Adapter implements pagination and filtering without coupling domain model to infrastructure concerns. Follows existing `ConfigurationAdminQuery` precedent from #672.

### Decision: Retry via Existing NotificationService

**Choice**: `RetryNotificationHandler` calls `NotificationService.notify()` with original recipient/template/channel plus idempotency key suffix `-retry-{UUID}`

**Alternatives considered**:
- Direct row update to PENDING + re-queue (bypasses business rules, idempotency, and existing notification pipeline)
- New `RetryNotificationService` (duplicate notification engine)
- Resend handlers `ResendInvitationHandler` (creates NEW tokens — not true retry, violates one-time action semantics)

**Rationale**: Reuses domain notification creation logic, idempotency guarantees, and dispatch pipeline. Suffix prevents idempotency collision with original notification. Keeps domain boundary intact — retry is infrastructure orchestration, not domain behavior. Password recovery notifications have no token expiry semantics; retry with same payload is safe. Invitation retry blocked at eligibility gate per REQ-PN-004.

### Decision: Template-Based Eligibility Whitelist

**Choice**: `NotificationRetryEligibility` domain value object with hardcoded `TemplateId` whitelist (`PASSWORD_RECOVERY` only in initial implementation)

**Alternatives considered**:
- Per-notification `retryable` boolean column (schema change, requires migration, couples retry policy to data model)
- Configuration-driven whitelist (over-engineering for single known-safe template)
- No eligibility check (unsafe — invitation retry would send invalid tokens)

**Rationale**: Domain rule expressible without schema change. Whitelist approach allows future expansion (e.g., `WORKSPACE_WELCOME` if deemed safe) without data migration. Invitation/waitlist templates explicitly denied per proposal out-of-scope. Fails fast at handler boundary before touching `NotificationService`.

### Decision: Payload Redaction Utility in Infrastructure

**Choice**: `NotificationMetadataRedactor` utility class with field denylist (`token`, `rawToken`, `acceptUrl`, `password`, `verificationToken`) applied to `payload` JSONB before response serialization

**Alternatives considered**:
- Database view with redaction (couples redaction to schema, not reusable across admin surfaces)
- Controller-level manual field removal (error-prone, not testable in isolation)
- Domain-level redaction method (domain should not know about admin visibility rules)

**Rationale**: Infrastructure concern — admin visibility policy is not domain invariant. Utility is testable in isolation, reusable for future admin surfaces (e.g., audit event payloads). Whitelist approach (keep `recipient`, `channel`, `templateId`, `status`, timestamps) simpler than denylist for large payloads. Follows `RedactSensitiveMetadata` precedent from audit implementation.

### Decision: No Separate Retry Attempts Table

**Choice**: Each retry creates a new notification row with suffixed idempotency key; original row unchanged

**Alternatives considered**:
- `notification_retry_attempts` table with foreign key to `notifications.id` (schema change, query complexity, unclear bounded context ownership)
- In-place row update with `retry_count` column (loses original failure state, complicates audit trail)

**Rationale**: Append-only notifications table design preserved. Original failure preserved for audit/analysis. Retry attempt becomes first-class notification with own `sent_at`/`failed_at`/`error_message`. Idempotency suffix prevents collision. Query adapter can correlate by prefix if needed (not required for MVP).

## Data Flow

### Query Flow

```
Controller (GET /api/admin/notifications)
    │
    ├─→ OperatorAccessResolver.require(platform.notifications.read)
    │
    ├─→ QueryNotificationsHandler
    │       │
    │       ├─→ NotificationAdminQuery.findNotifications(filters, page, size)
    │       │       │
    │       │       └─→ R2dbcNotificationAdminQueryAdapter
    │       │               │
    │       │               └─→ SELECT * FROM notifications WHERE ... LIMIT/OFFSET
    │       │
    │       └─→ NotificationMetadataRedactor.redact(notifications)
    │
    └─→ PagedResult<RedactedNotificationDto>
```

### Retry Flow

```
Controller (POST /api/admin/notifications/{id}/retry)
    │
    ├─→ OperatorAccessResolver.require(platform.notifications.manage)
    │
    ├─→ NotificationAdminIdempotencyService.getOrCreate(idempotencyKey)
    │
    ├─→ RetryNotificationHandler
    │       │
    │       ├─→ NotificationAdminQuery.findById(notificationId)
    │       │       │
    │       │       └─→ R2dbcNotificationAdminQueryAdapter
    │       │
    │       ├─→ NotificationRetryEligibility.isEligible(templateId)
    │       │       │
    │       │       └─→ [REJECT if not in whitelist]
    │       │
    │       ├─→ NotificationService.notify(
    │       │       recipient, template, payload,
    │       │       idempotencyKey = originalKey + "-retry-{UUID}"
    │       │   )
    │       │
    │       └─→ AdminAuditPublisher.publish(NOTIFICATION_RETRIED, metadata)
    │
    └─→ 200 OK
```

## Component Design

### Application Layer

#### NotificationAdminQuery Port

```kotlin
// platformadmin/application/query/NotificationAdminQuery.kt
interface NotificationAdminQuery {
    suspend fun findNotifications(
        filters: NotificationFilters,
        page: Int,
        size: Int
    ): PagedResult<NotificationView>

    suspend fun findById(id: UUID): NotificationView?
}

data class NotificationFilters(
    val status: NotificationStatus? = null,
    val channel: NotificationChannel? = null,
    val templateId: String? = null,
    val recipient: String? = null,
    val failedAfter: Instant? = null,
    val failedBefore: Instant? = null
)

data class NotificationView(
    val id: UUID,
    val channel: NotificationChannel,
    val recipient: String,
    val templateId: String,
    val payload: Map<String, Any>,
    val status: NotificationStatus,
    val sentAt: Instant?,
    val failedAt: Instant?,
    val errorMessage: String?
)
```

#### Handlers

```kotlin
// platformadmin/application/command/RetryNotificationHandler.kt
@Service
class RetryNotificationHandler(
    private val notificationAdminQuery: NotificationAdminQuery,
    private val notificationService: NotificationService,
    private val auditPublisher: AdminAuditPublisher,
    private val idempotencyService: NotificationAdminIdempotencyService
) : com.profiletailors.common.domain.Service {

    suspend fun handle(command: RetryNotificationCommand): RetryNotificationResult {
        val notification = notificationAdminQuery.findById(command.notificationId)
            ?: throw NotificationNotFoundException(command.notificationId)

        if (!NotificationRetryEligibility.isEligible(notification.templateId)) {
            return RetryNotificationResult.Rejected(
                reason = "Template ${notification.templateId} is not eligible for retry"
            )
        }

        val retryKey = "${notification.idempotencyKey}-retry-${UUID.randomUUID()}"

        try {
            notificationService.notify(
                recipient = notification.recipient,
                templateId = notification.templateId,
                channel = notification.channel,
                payload = notification.payload,
                idempotencyKey = retryKey
            )

            auditPublisher.publish(
                AdminAuditEvent(
                    eventType = AdminAuditEventType.NOTIFICATION_RETRIED,
                    operatorId = command.operatorId,
                    targetType = "NOTIFICATION",
                    targetId = command.notificationId.toString(),
                    metadata = mapOf(
                        "notificationId" to command.notificationId.toString(),
                        "channel" to notification.channel.name,
                        "templateId" to notification.templateId,
                        "retryOutcome" to "SUCCESS",
                        "priorStatus" to notification.status.name,
                        "priorError" to notification.errorMessage.orEmpty()
                    )
                )
            )

            return RetryNotificationResult.Success
        } catch (e: Exception) {
            auditPublisher.publish(/* DISPATCH_FAILED outcome */)
            throw RetryDispatchException(command.notificationId, e)
        }
    }
}

// platformadmin/application/query/QueryNotificationsHandler.kt
@Service
class QueryNotificationsHandler(
    private val notificationAdminQuery: NotificationAdminQuery,
    private val redactor: NotificationMetadataRedactor
) : com.profiletailors.common.domain.Service {

    suspend fun handle(query: QueryNotificationsQuery): PagedResult<RedactedNotificationDto> {
        val pagedResult = notificationAdminQuery.findNotifications(
            filters = query.filters,
            page = query.page,
            size = query.size
        )

        return pagedResult.map { notification ->
            RedactedNotificationDto(
                id = notification.id,
                channel = notification.channel,
                recipient = notification.recipient,
                templateId = notification.templateId,
                payload = redactor.redact(notification.payload),
                status = notification.status,
                sentAt = notification.sentAt,
                failedAt = notification.failedAt,
                errorMessage = notification.errorMessage
            )
        }
    }
}
```

#### Domain Value Object

```kotlin
// platformadmin/domain/NotificationRetryEligibility.kt
object NotificationRetryEligibility {
    private val ELIGIBLE_TEMPLATES = setOf(
        "PASSWORD_RECOVERY"
    )

    fun isEligible(templateId: String): Boolean =
        templateId in ELIGIBLE_TEMPLATES
}
```

### Infrastructure Layer

#### R2DBC Query Adapter

```kotlin
// platformadmin/infrastructure/adapter/R2dbcNotificationAdminQueryAdapter.kt
@Component
class R2dbcNotificationAdminQueryAdapter(
    private val databaseClient: DatabaseClient
) : NotificationAdminQuery {

    override suspend fun findNotifications(
        filters: NotificationFilters,
        page: Int,
        size: Int
    ): PagedResult<NotificationView> = coroutineScope {
        val whereClause = buildWhereClause(filters)
        val params = buildParams(filters)

        val countSql = "SELECT COUNT(*) FROM notifications $whereClause"
        val querySql = """
            SELECT id, channel, recipient, template_id, payload, status, 
                   sent_at, failed_at, error_message
            FROM notifications
            $whereClause
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        val total = databaseClient.sql(countSql)
            .bindParams(params)
            .map { row -> row.get(0, Long::class.java) ?: 0L }
            .awaitSingle()

        val items = databaseClient.sql(querySql)
            .bind("limit", size)
            .bind("offset", page * size)
            .bindParams(params)
            .map { row -> row.toNotificationView() }
            .flow()
            .toList()

        PagedResult(
            items = items,
            page = page,
            size = size,
            total = total
        )
    }

    private fun buildWhereClause(filters: NotificationFilters): String {
        val clauses = mutableListOf<String>()
        if (filters.status != null) clauses += "status = :status"
        if (filters.channel != null) clauses += "channel = :channel"
        if (filters.templateId != null) clauses += "template_id = :templateId"
        if (filters.recipient != null) clauses += "recipient = :recipient"
        if (filters.failedAfter != null) clauses += "failed_at >= :failedAfter"
        if (filters.failedBefore != null) clauses += "failed_at <= :failedBefore"

        return if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
    }

    override suspend fun findById(id: UUID): NotificationView? {
        return databaseClient.sql(
            "SELECT * FROM notifications WHERE id = :id"
        )
            .bind("id", id)
            .map { it.toNotificationView() }
            .awaitOneOrNull()
    }
}
```

#### Redaction Utility

```kotlin
// platformadmin/infrastructure/security/NotificationMetadataRedactor.kt
@Component
class NotificationMetadataRedactor {

    private val SENSITIVE_KEYS = setOf(
        "token", "rawToken", "acceptUrl", "password",
        "verificationToken", "resetToken", "invitationToken"
    )

    fun redact(payload: Map<String, Any>): Map<String, Any> {
        return payload.filterKeys { key ->
            key !in SENSITIVE_KEYS
        }
    }
}
```

#### Controller

```kotlin
// platformadmin/infrastructure/http/AdminNotificationController.kt
@RestController
@RequestMapping("/api/admin/notifications")
class AdminNotificationController(
    private val queryHandler: QueryNotificationsHandler,
    private val retryHandler: RetryNotificationHandler,
    private val accessResolver: OperatorAccessResolver
) {

    @GetMapping
    @RequiresPlatformPermission("platform.notifications.read")
    suspend fun queryNotifications(
        @RequestParam(required = false) status: NotificationStatus?,
        @RequestParam(required = false) channel: NotificationChannel?,
        @RequestParam(required = false) templateId: String?,
        @RequestParam(required = false) recipient: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: OperatorPrincipal
    ): ResponseEntity<PagedResult<RedactedNotificationDto>> {
        accessResolver.require(principal, "platform.notifications.read")

        val result = queryHandler.handle(
            QueryNotificationsQuery(
                filters = NotificationFilters(status, channel, templateId, recipient),
                page = page,
                size = size
            )
        )

        return ResponseEntity.ok()
            .header("Accept", "application/vnd.api.v1+json")
            .body(result)
    }

    @PostMapping("/{id}/retry")
    @RequiresPlatformPermission("platform.notifications.manage")
    suspend fun retryNotification(
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        principal: OperatorPrincipal
    ): ResponseEntity<RetryResponseDto> {
        accessResolver.require(principal, "platform.notifications.manage")

        val result = retryHandler.handle(
            RetryNotificationCommand(
                notificationId = id,
                operatorId = principal.operatorId,
                idempotencyKey = idempotencyKey
            )
        )

        return when (result) {
            is RetryNotificationResult.Success -> ResponseEntity.ok(
                RetryResponseDto(status = "SUCCESS")
            )
            is RetryNotificationResult.Rejected -> ResponseEntity.badRequest()
                .body(RetryResponseDto(status = "REJECTED", reason = result.reason))
        }
    }
}
```

#### Idempotency Service

```kotlin
// platformadmin/infrastructure/idempotency/NotificationAdminIdempotencyService.kt
@Component
class NotificationAdminIdempotencyService(
    private val databaseClient: DatabaseClient
) {
    suspend fun getOrCreate(key: String): IdempotencyRecord {
        // Pattern from ConfigurationIdempotencyService (#672)
        // Check platform_admin_operations table for key
        // If exists, return existing
        // Else insert and return new
    }
}
```

#### Navigation Registry Update

```kotlin
// platformadmin/infrastructure/navigation/PlatformAdminNavigationRegistry.kt
// Add entry:
NavigationItem(
    id = "notifications",
    label = "Notifications",
    path = "/admin/notifications",
    requiredPermission = "platform.notifications.read",
    category = NavCategory.PLATFORM
)
```

#### Permission Mirror in auth.store

```sql
-- migrations/admin-permissions-notifications.sql
INSERT INTO platform_permissions (permission_key, description, scope) VALUES
    ('platform.notifications.read', 'Read notification delivery logs and status', 'PLATFORM'),
    ('platform.notifications.manage', 'Retry failed notifications', 'PLATFORM')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role, permission_key) VALUES
    ('OWNER', 'platform.notifications.read'),
    ('OPERATOR', 'platform.notifications.read'),
    ('AUDITOR', 'platform.notifications.read'),
    ('OWNER', 'platform.notifications.manage'),
    ('OPERATOR', 'platform.notifications.manage')
ON CONFLICT (role, permission_key) DO NOTHING;
```

### BDD Layer

#### Feature File

```gherkin
# server/smp/src/test/resources/features/platformadmin/notifications.feature
@platform-admin @platform-notifications @fast
Feature: Platform Notification Administration

  Background:
    Given the notification admin API is available
      And operator "op-owner-123" has role OWNER
      And operator "op-auditor-456" has role AUDITOR
      And operator "op-viewer-789" has role VIEWER

  Scenario: Query notifications with filters
    Given notification "notif-001" exists with status FAILED, channel EMAIL, template PASSWORD_RECOVERY
      And notification "notif-002" exists with status SENT, channel SMS, template WORKSPACE_INVITATION
    When operator "op-owner-123" queries GET /api/admin/notifications?status=FAILED&channel=EMAIL
    Then response status is 200
      And response.items contains notification "notif-001"
      And response.items does not contain notification "notif-002"

  Scenario: Redacted payload excludes sensitive fields
    Given notification "notif-003" has payload with keys: recipient, token, acceptUrl
    When operator "op-owner-123" queries GET /api/admin/notifications/notif-003
    Then response status is 200
      And response.payload contains key "recipient"
      And response.payload does not contain keys: token, acceptUrl

  Scenario: Retry eligible notification succeeds
    Given notification "notif-004" has status FAILED, template PASSWORD_RECOVERY
    When operator "op-owner-123" posts POST /api/admin/notifications/notif-004/retry
      And Idempotency-Key header is "retry-key-001"
    Then response status is 200
      And audit event NOTIFICATION_RETRIED is published with operatorId "op-owner-123"

  Scenario: Retry ineligible notification denied
    Given notification "notif-005" has status FAILED, template WORKSPACE_INVITATION
    When operator "op-owner-123" posts POST /api/admin/notifications/notif-005/retry
    Then response status is 400
      And response.error contains "not eligible for retry"

  Scenario: AUDITOR can query but not retry
    Given notification "notif-006" has status FAILED
    When operator "op-auditor-456" queries GET /api/admin/notifications
    Then response status is 200
    When operator "op-auditor-456" posts POST /api/admin/notifications/notif-006/retry
    Then response status is 403

  Scenario: VIEWER cannot query notifications
    When operator "op-viewer-789" queries GET /api/admin/notifications
    Then response status is 403
      And response.error contains "Insufficient permissions"
```

#### Step Definitions

```kotlin
// server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/platformadmin/NotificationAdminBddSteps.kt
@Given("notification {string} exists with status {status}, channel {channel}, template {string}")
fun notificationExists(id: String, status: NotificationStatus, channel: NotificationChannel, template: String) {
    bddDatabaseSupport.insertNotification(
        id = UUID.fromString(id),
        status = status,
        channel = channel,
        templateId = template,
        recipient = "test@example.com",
        payload = mapOf("recipient" to "test@example.com")
    )
}

@When("operator {string} queries GET /api/admin/notifications")
fun operatorQueriesNotifications(operatorId: String) {
    lastResponse = webTestClient.get()
        .uri("/api/admin/notifications")
        .header("Authorization", "Bearer ${operatorToken(operatorId)}")
        .header("Accept", "application/vnd.api.v1+json")
        .exchange()
}

@When("operator {string} posts POST /api/admin/notifications/{string}/retry")
fun operatorRetriesNotification(operatorId: String, notificationId: String) {
    lastResponse = webTestClient.post()
        .uri("/api/admin/notifications/$notificationId/retry")
        .header("Authorization", "Bearer ${operatorToken(operatorId)}")
        .header("Idempotency-Key", "retry-key-${UUID.randomUUID()}")
        .header("Accept", "application/vnd.api.v1+json")
        .exchange()
}

// Reuse PlatformAdminBddSteps for role setup and permission checks
```

## Data Model

Uses existing `notifications` table (no schema changes):

```sql
-- Existing table (unchanged)
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    channel VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    template_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP,
    failed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

Retry creates new row:
- Original: `id=notif-001, idempotency_key=original-key, status=FAILED`
- Retry: `id=notif-retry-001, idempotency_key=original-key-retry-{UUID}, status=PENDING`

## API Contract

### GET /api/admin/notifications

**Request**:
```http
GET /api/admin/notifications?status=FAILED&channel=EMAIL&page=0&size=20
Accept: application/vnd.api.v1+json
Authorization: Bearer <token>
```

**Response 200**:
```json
{
  "items": [
    {
      "id": "abc-123",
      "channel": "EMAIL",
      "recipient": "user@example.com",
      "templateId": "PASSWORD_RECOVERY",
      "payload": {
        "recipient": "user@example.com"
      },
      "status": "FAILED",
      "sentAt": null,
      "failedAt": "2026-09-20T10:30:00Z",
      "errorMessage": "SMTP timeout"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

**Response 403**: Missing `platform.notifications.read`

### POST /api/admin/notifications/{id}/retry

**Request**:
```http
POST /api/admin/notifications/abc-123/retry
Accept: application/vnd.api.v1+json
Authorization: Bearer <token>
Idempotency-Key: retry-key-001
```

**Response 200**:
```json
{
  "status": "SUCCESS"
}
```

**Response 400**: Eligibility denied
```json
{
  "status": "REJECTED",
  "reason": "Template WORKSPACE_INVITATION is not eligible for retry"
}
```

**Response 403**: Missing `platform.notifications.manage`

**Response 404**: Notification not found

## Security

### Authorization

- `OperatorAccessResolver.require()` enforces permissions at handler entry
- `@RequiresPlatformPermission` annotation on controller methods (declarative)
- Permission registry mirrors `auth.store` role-permission mappings
- Roles: OWNER/OPERATOR/AUDITOR for read; OWNER/OPERATOR only for retry

### Redaction

- `NotificationMetadataRedactor` strips sensitive keys before DTO serialization
- Whitelist approach: keep `recipient`, `channel`, `templateId`, `status`, timestamps
- Denylist: `token`, `rawToken`, `acceptUrl`, `password`, `verificationToken`
- Audit event `priorError` applies existing `redact()` function for error messages

### Eligibility Gate

- `NotificationRetryEligibility.isEligible()` blocks unsafe templates
- Whitelist: `PASSWORD_RECOVERY` only (no token expiry, idempotent retry safe)
- Denied: `WORKSPACE_INVITATION`, `WAITLIST_INVITATION` (token-bound, one-time action semantics)
- Returns 400 with explicit reason before touching `NotificationService`

## Testing Strategy

### Unit Tests

| Component | Test Focus | Approach |
|---|---|---|
| `NotificationRetryEligibility` | Whitelist membership, denial reason | Pure object tests |
| `NotificationMetadataRedactor` | Sensitive key removal, whitelist preservation | Map input/output assertions |
| `QueryNotificationsHandler` | Filter delegation, redaction application | Fake `NotificationAdminQuery`, verify redactor call |
| `RetryNotificationHandler` | Eligibility check, service dispatch, audit publish | Mock `NotificationService` + `AdminAuditPublisher`, verify calls |

### Integration Tests

| Component | Test Focus | Approach |
|---|---|---|
| `R2dbcNotificationAdminQueryAdapter` | SQL WHERE clause construction, pagination, filtering | Testcontainers PostgreSQL, seed rows, verify query results |
| `NotificationMetadataRedactor` | JSONB payload redaction | Insert notification with sensitive payload, query via adapter, assert redacted |
| `AdminNotificationController` | Authorization enforcement, DTO serialization, error handling | `WebTestClient`, mock handlers, verify 403/400/200 responses |

### BDD Tests

| Feature | Coverage | Tags |
|---|---|---|
| `platformadmin/notifications.feature` | Query filters, redaction, retry success/denial, role authorization | `@platform-admin @platform-notifications @fast` |

Scenarios:
1. Query with status/channel/template filters returns matching notifications
2. Redacted payload excludes token/acceptUrl, includes recipient
3. Retry PASSWORD_RECOVERY succeeds, publishes NOTIFICATION_RETRIED
4. Retry WORKSPACE_INVITATION denied with 400 + reason
5. AUDITOR can query (200) but cannot retry (403)
6. VIEWER cannot query (403)
7. Retry idempotency: duplicate Idempotency-Key returns same result
8. Observability: retry failure publishes audit event with DISPATCH_FAILED outcome

### Security Tests

- Unauthorized operator (no role) → 403 on query + retry
- VIEWER role → 403 on query (lacks `platform.notifications.read`)
- AUDITOR role → 200 on query, 403 on retry (lacks `platform.notifications.manage`)
- Payload redaction: insert notification with `token` field, query returns payload without `token`

## Dependencies

| Dependency | Usage | Location |
|---|---|---|
| `NotificationService` | Retry dispatch through existing pipeline | `notifications.application.NotificationService` |
| `AdminAuditPublisher` | Publish `NOTIFICATION_RETRIED` events | `platformadmin.application.audit.AdminAuditPublisher` |
| `OperatorAccessResolver` | Permission enforcement | `platformadmin.infrastructure.security.OperatorAccessResolver` |
| `BddDatabaseSupport` | BDD test fixture data | `test/bdd/support/BddDatabaseSupport.kt` |
| `PlatformAdminBddSteps` | Role/permission setup in BDD | `test/bdd/glue/platformadmin/PlatformAdminBddSteps.kt` |
| Spring WebFlux | Reactive HTTP controllers | `spring-boot-starter-webflux` |
| R2DBC | Reactive database queries | `spring-boot-starter-data-r2dbc` |

## Risks and Tradeoffs

### Risks

1. **Retry idempotency collision**: If retry suffix generation duplicates (UUID collision), idempotency constraint fails. **Mitigation**: UUID collision probability negligible; `NotificationService` handles idempotency violation gracefully (returns existing notification).

2. **Redaction incompleteness**: Future template payloads may introduce new sensitive keys not in denylist. **Mitigation**: Whitelist approach preferred for high-security contexts; consider schema validation at template registration to enforce known payload shape.

3. **Eligibility whitelist drift**: Domain changes (e.g., password reset adds token expiry) may invalidate retry safety without code update. **Mitigation**: Document eligibility assumptions in `NotificationRetryEligibility`; add domain test asserting `PASSWORD_RECOVERY` has no token expiry.

4. **Query performance**: No index on `template_id`, `status`, `failed_at` columns. **Mitigation**: Add composite index `(status, failed_at DESC)` for common operator query (failed notifications by recency) in separate performance optimization change.

### Tradeoffs

1. **No attempt correlation**: Retry creates independent notification row; operators cannot trace retry lineage without idempotency key prefix parsing. **Accepted**: MVP does not require retry history; future enhancement can add `original_notification_id` foreign key if needed.

2. **Single-template whitelist**: Only `PASSWORD_RECOVERY` eligible; operators cannot retry `WORKSPACE_WELCOME` or similar non-token templates. **Accepted**: Conservative safety default; expand whitelist after domain review confirms safety.

3. **In-memory redaction**: Redaction applied after query fetch, not at SQL layer. **Accepted**: Simpler implementation, testable in isolation; payload size small (< 10KB), negligible memory overhead.

4. **No retry throttling**: Operator can retry same notification multiple times in quick succession. **Accepted**: `Idempotency-Key` header + `NotificationAdminIdempotencyService` prevents duplicate dispatch; rate limiting deferred to future observability-driven tuning.
