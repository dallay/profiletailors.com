# Platform Notifications Specification

## Purpose

Provides administrative visibility and safe operational intervention for notification delivery state. Enables platform operators to query notification history, inspect redacted delivery failures, and retry eligible failed notifications without compromising security tokens or one-time action semantics.

## Requirements

| REQ-ID | Statement | Rationale | Acceptance |
|--------|-----------|-----------|------------|
| REQ-PN-001 | Query notifications by status, channel, template, recipient, and time range | Operators need filtered views to diagnose channel-specific failures and delivery patterns | Query returns paginated notifications matching all supplied filters; omitted filters return all values for that dimension |
| REQ-PN-002 | Redact sensitive payload fields in admin responses | Tokens, passwords, token-bearing links, PII beyond recipient identifier must not leak to operator views | Response payloads strip `token`, `password`, `acceptUrl`, `inviteLink`, `resetLink`, `resetUrl`, `rawToken`, `verificationToken` fields; recipient email/phone retained for correlation |
| REQ-PN-003 | Retry eligible failed notifications through a retry-requested domain event | Password recovery and non-token-bound failures can be safely re-dispatched; avoids duplicate notification engine | Retry persists a new PENDING attempt and publishes `NotificationRetryRequested`; the established email dispatch consumer delivers it and marks it SENT or FAILED; original row unchanged |
| REQ-PN-004 | Block retry for invitation and waitlist notification templates | Invitation tokens are consumed/expired; retry would send invalid tokens or require new token creation (domain concern, not admin concern) | Retry command validates template_id against eligibility whitelist; invitation/waitlist template_ids return 400 with explicit denial reason |
| REQ-PN-005 | Expose delivery state observability | Operators need visibility into PENDING, SENT, FAILED status with error messages | Admin response includes `status`, `error_message`, `channel`, `template_id`, `sent_at`, `created_at` without payload mutation |
| REQ-PN-006 | Enforce platform.notifications.read for query operations | Read-only access must be separated from mutating operations | Query endpoint enforces `platform.notifications.read` permission via `OperatorAccessResolver` |
| REQ-PN-007 | Enforce platform.notifications.manage for retry operations | Retry is a mutating operation requiring higher privilege | Retry endpoint enforces `platform.notifications.manage` permission via `OperatorAccessResolver` |

## Scenarios

### Scenario: Query failed notifications with filters

**REQ-PN-001**

```gherkin
GIVEN platform operator has platform.notifications.read permission
  AND notification table contains FAILED notifications for multiple channels
 WHEN operator queries GET /api/admin/notifications?status=FAILED&channel=EMAIL&page=0&size=20
 THEN response status is 200
  AND response contains paginated list of EMAIL channel FAILED notifications
  AND pagination metadata includes totalElements, totalPages, currentPage
```

### Scenario: Inspect redacted notification payload

**REQ-PN-002**

```gherkin
GIVEN notification 550e8400-e29b-41d4-a716-446655440000 has template PASSWORD_RECOVERY
  AND original payload contains {"email":"user@example.com","token":"secret123","resetUrl":"https://app.profiletailors.com/reset?t=secret123"}
 WHEN operator fetches GET /api/admin/notifications/550e8400-e29b-41d4-a716-446655440000
 THEN response status is 200
  AND response.payload contains {"email":"user@example.com"}
  AND response.payload does NOT contain "token" field
  AND response.payload does NOT contain "resetUrl" field
```

### Scenario: Retry eligible password-recovery notification success

**REQ-PN-003, REQ-PN-004**

```gherkin
GIVEN notification abc-123 has status FAILED, template PASSWORD_RECOVERY, error "SMTP timeout"
  AND operator has platform.notifications.manage permission
 WHEN operator posts POST /api/admin/notifications/abc-123/retry
 THEN response status is 200
  AND NotificationRetryRequested is published with the new attempt id
  AND the dispatch consumer delivers the attempt and marks it SENT or FAILED
  AND new notification row created with the caller idempotency_key or a generated `retry-{id}-{uuid}` key
  AND original notification abc-123 status remains FAILED (append-only)
  AND NOTIFICATION_RETRIED audit event published with outcome SUCCESS
```

### Scenario: Retry invitation template denied

**REQ-PN-004**

```gherkin
GIVEN notification xyz-789 has status FAILED, template WORKSPACE_INVITATION
  AND operator has platform.notifications.manage permission
 WHEN operator posts POST /api/admin/notifications/xyz-789/retry
 THEN response status is 400
  AND response.error contains "Retry not allowed for token-bound invitation templates"
  AND NO new notification created
  AND NOTIFICATION_RETRIED audit event published with outcome REJECTED
```

### Scenario: Query denied without read permission

**REQ-PN-006**

```gherkin
GIVEN operator has role AUDITOR (lacks platform.notifications.read)
 WHEN operator queries GET /api/admin/notifications
 THEN response status is 403
  AND response.error contains "Insufficient permissions"
```

### Scenario: Retry denied without manage permission

**REQ-PN-007**

```gherkin
GIVEN operator has platform.notifications.read (but NOT platform.notifications.manage)
  AND notification abc-123 is eligible for retry
 WHEN operator posts POST /api/admin/notifications/abc-123/retry
 THEN response status is 403
  AND response.error contains "Insufficient permissions"
```

### Scenario: Delivery state observability for pending notification

**REQ-PN-005**

```gherkin
GIVEN notification def-456 has status PENDING, channel SMS, created_at 2026-09-20T15:30:00Z
 WHEN operator fetches GET /api/admin/notifications/def-456
 THEN response status is 200
  AND response.status is "PENDING"
  AND response.channel is "SMS"
  AND response.error_message is null
  AND response.sent_at is null
  AND response.created_at is "2026-09-20T15:30:00Z"
```

## Technical Notes

- **Idempotency Key Strategy**: Retry reuses the caller-supplied idempotency key when present (a repeated key returns 409), otherwise generates `retry-{notificationId}-{uuid}`. A pre-save lookup rejects already-used keys; the UNIQUE constraint remains the backstop against concurrent duplicates. Original row status/error_message never mutated (append-only contract).
- **Eligibility Whitelist**: Implementation maintains exact-match `ELIGIBLE_TEMPLATES = setOf("platform.password-recovery", "platform.password-reset")`; expand cautiously after domain review.
- **Redaction Implementation**: `redactPayload(JsonNode): JsonNode` function mirrors `platform-admin-audit` redaction pattern (case-insensitive field name matching against denylist: `token`, `password`, `acceptUrl`, `rawToken`, `verificationToken`).
- **No Attempt Tracking Schema**: Retry creates new `notifications` row; no `notification_attempts` join table. Prior attempts queryable via `idempotency_key` prefix search if needed.
- **Channel Failure Context**: `error_message` field in `notifications` table already captures provider-specific failure reasons (SMTP timeout, SMS quota, etc.); no new error taxonomy needed.
