# Delta for Platform Admin Audit

## ADDED Requirements

### Requirement: NOTIFICATION_RETRIED audit event type

The `AdminAuditEventType` enum MUST include `NOTIFICATION_RETRIED` value.

`NOTIFICATION_RETRIED` events MUST be published when an operator invokes retry on a failed notification, regardless of retry outcome (success, rejected eligibility, dispatch failure).

`NOTIFICATION_RETRIED` event structure:

- `eventType`: `NOTIFICATION_RETRIED`
- `operatorId`: UUID of operator who invoked retry
- `targetType`: `NOTIFICATION`
- `targetId`: UUID of the notification being retried (original notification ID, not new retry attempt ID)
- `metadata`: map containing:
  - `notificationId`: same as `targetId` (for consistency with other event types)
  - `channel`: notification channel (EMAIL, SMS, PUSH, WEBHOOK)
  - `templateId`: template identifier (PASSWORD_RECOVERY, WORKSPACE_INVITATION, etc.)
  - `retryOutcome`: outcome of retry operation (SUCCESS, REJECTED, DISPATCH_FAILED)
  - `priorStatus`: notification status before retry attempt (typically FAILED)
  - `priorError`: error_message from original notification (if present, redacted per existing redaction rules)

Metadata redaction MUST apply existing `redact()` function; if `priorError` contains denylisted substrings (password, token, secret, etc.), value MUST be `[REDACTED]`.

#### Scenario: Retry success audited with full context

```gherkin
GIVEN notification abc-123 has status FAILED, channel EMAIL, template PASSWORD_RECOVERY, error "SMTP timeout"
  AND operator op-456 has platform.notifications.manage permission
 WHEN operator posts POST /api/admin/notifications/abc-123/retry
  AND retry dispatch succeeds (new notification created)
 THEN AdminAuditEvent is published with:
  | Field | Value |
  | eventType | NOTIFICATION_RETRIED |
  | operatorId | op-456 |
  | targetType | NOTIFICATION |
  | targetId | abc-123 |
  | metadata.notificationId | abc-123 |
  | metadata.channel | EMAIL |
  | metadata.templateId | PASSWORD_RECOVERY |
  | metadata.retryOutcome | SUCCESS |
  | metadata.priorStatus | FAILED |
  | metadata.priorError | SMTP timeout |
```

#### Scenario: Retry rejected eligibility audited

```gherkin
GIVEN notification xyz-789 has status FAILED, template WORKSPACE_INVITATION
  AND operator op-999 attempts retry
 WHEN retry eligibility check fails (invitation template not whitelisted)
 THEN AdminAuditEvent is published with:
  | Field | Value |
  | eventType | NOTIFICATION_RETRIED |
  | operatorId | op-999 |
  | targetType | NOTIFICATION |
  | targetId | xyz-789 |
  | metadata.retryOutcome | REJECTED |
  | metadata.templateId | WORKSPACE_INVITATION |
```

#### Scenario: Redacted priorError in audit event

```gherkin
GIVEN notification def-456 has error_message "Failed to send email: authentication token expired"
 WHEN operator retries notification def-456
 THEN NOTIFICATION_RETRIED event metadata.priorError is "[REDACTED]" (contains "token")
```

## MODIFIED Requirements

### Requirement: AdminAuditEventType registry

(Previously: Did not include NOTIFICATION_RETRIED)

The `AdminAuditEventType` enum MUST include these event types:

- `WORKSPACE_INVITATION_CREATED`
- `WORKSPACE_INVITATION_RESENT`
- `WORKSPACE_INVITATION_REVOKED`
- `WAITLIST_ENTRY_INVITED`
- `WAITLIST_ENTRY_CANCELLED`
- `USER_ACCOUNT_DISABLED`
- `USER_ACCOUNT_ENABLED`
- `USER_SESSIONS_REVOKED`
- `PLATFORM_OPERATOR_ASSIGNED`
- `PLATFORM_OPERATOR_REVOKED`
- `REGISTRATION_MODE_CHANGED`
- **`NOTIFICATION_RETRIED`** (new)

#### Scenario: Event type registry includes NOTIFICATION_RETRIED

```gherkin
GIVEN AdminAuditEventType enum is loaded
 WHEN system initializes event type registry
 THEN registry contains NOTIFICATION_RETRIED value
  AND NOTIFICATION_RETRIED can be persisted and queried
```

## Technical Notes

- **Outcome Semantics**:
  - `SUCCESS`: Retry dispatched successfully via `NotificationService.notify()`, new notification row created.
  - `REJECTED`: Eligibility check failed (template not whitelisted, status not FAILED, etc.); no dispatch attempted.
  - `DISPATCH_FAILED`: Eligibility passed but `NotificationService.notify()` threw exception (rare; indicates infrastructure failure).
  
- **TargetType Consistency**: `NOTIFICATION` target type aligns with domain boundary; existing audit events use `WORKSPACE_INVITATION`, `WAITLIST_ENTRY`, `USER`, `OPERATOR`, `CONFIGURATION` — `NOTIFICATION` follows same pattern.

- **Redaction Inheritance**: No new redaction rules needed; `priorError` field processed by existing `redact()` function with established denylist (password, secret, token, key, credential, authorization, bearer, accessurl, reseturl, verificationtoken, rawtoken).

- **Audit Query Impact**: Existing `GET /api/admin/audit` endpoint automatically supports `?eventType=NOTIFICATION_RETRIED` filtering without modification (event type is indexed column).

- **No Payload Duplication**: Audit event metadata does NOT duplicate full notification payload; only correlation IDs (notificationId, channel, templateId) and retry-specific context (outcome, priorStatus, priorError) included.
