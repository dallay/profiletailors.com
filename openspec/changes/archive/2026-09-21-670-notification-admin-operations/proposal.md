# Proposal: Notification Admin Queries and Safe Retry Operations

## Intent

Provide platform administrators operational visibility into notification delivery state without
building a duplicate notification management system. Enable safe retry of failed notifications where
technically appropriate (password recovery, non-token-bound failures) while explicitly blocking
unsafe retry scenarios (invitation tokens, consumed/expired one-time actions).

**Problem**: Operators cannot inspect notification delivery failures, diagnose channel issues, or
retry eligible failed notifications. Current retry mechanisms (`ResendInvitationHandler`,
`ResendWaitlistInvitationHandler`) create NEW tokens and NEW notification rows—not true retry. No
query interface exists over the `notifications` table.

## Scope

### In Scope

- Admin query port: list/filter notifications by channel, template_id, status, recipient, time
  ranges
- R2DBC adapter: paginated queries with filtering, read-only access to notifications table
- `GET /api/admin/notifications` endpoint: paginated response with redacted payloads
- Payload redaction: strip sensitive fields (tokens, passwords, PII beyond recipient identifier)
  from admin responses
- Retry command: `RetryNotificationCommand` for FAILED notifications without security-token
  semantics
- Retry handler: re-dispatch eligible notifications (password recovery class) through existing
  `NotificationService`
- Authorization: `platform.notifications.read` (query), `platform.notifications.manage` (retry)
  permissions via `OperatorAccessResolver`
- Audit: `NOTIFICATION_RETRIED` event with operator_id, notification_id, prior status/error
- BDD scenarios: `platformadmin/notifications.feature` covering list, filter, redaction, safe retry,
  unsafe retry denial

### Out of Scope

- Retry for invitation-bound or token-bound notifications (handled by domain-specific resend
  commands that mint fresh tokens)
- Replacing existing `ResendInvitationHandler` / `ResendWaitlistInvitationHandler` (they remain
  as-is for security-token workflows)
- Attempt-tracking schema (deferred; requires explicit ADR for normalization vs. JSONB metadata
  tradeoff)
- Blind resend without eligibility check (all retries validate state before dispatch)
- Payload content mutation or template override (retry uses original template_id and payload
  structure)
- Real-time notification stream or webhook subscriptions

## Capabilities

> **Contract for sdd-spec**: Existing capabilities modified, no new top-level capabilities
> introduced.

### New Capabilities

<!-- None — notification visibility and retry are extensions of existing platform-admin operational capabilities -->

### Modified Capabilities

- `platform-admin-audit`: Add `NOTIFICATION_RETRIED` event type with redacted metadata
- `admin-authorization`: Extend permission model with `platform.notifications.read` and
  `platform.notifications.manage`

## Approach

### Query Layer

Use existing notifications table as append-only event log. Query port returns:

-
`NotificationRecord(id, channel, recipient, template_id, status, sent_at, failed_at, error_message, redacted_payload)`
- Pagination via limit/offset (precedent: `PagedResult<T>` from #672/#668)
- Filtering: channel (EMAIL), status (PENDING/SENT/FAILED), template_id, recipient pattern, time
  range
- Redaction: `RedactSensitivePayload(payload: JSONB) -> JSONB` removes `token`, `password`,
  `acceptUrl`, `rawToken`, PII fields; retains structural keys for debugging

### Retry Strategy

**Core tradeoff**: Retry as re-dispatch vs. delegation to existing resend handlers.

**Decision**: Re-dispatch through `NotificationService` for eligible classes only.

- **Eligible**: Password recovery notifications (stateless, idempotent link generation per retry)
- **Ineligible**: Invitations (token consumed/expired check lives in domain layer, not notification
  layer; resend = new token via `ResendInvitationCommand`)
- **Eligibility check**: `template_id IN ('password-recovery', 'password-reset-confirmation')` AND
  `status = FAILED` AND `failed_at > NOW() - INTERVAL '7 days'`
- **Retry semantics**: Update existing row `status = PENDING`, clear `failed_at`/`error_message`,
  dispatch via `EmailNotificationPort`. On success: `status = SENT`, `sent_at = NOW()`. On failure:
  restore `status = FAILED`, append error.

**Alternative considered and rejected**: Generic retry for all templates. Rejected because
invitation/waitlist tokens have single-use semantics enforced outside notifications domain—retry
without domain validation risks sending expired/invalid tokens.

### Observability

- Metrics: `notification.query.latency`, `notification.retry.attempted`,
  `notification.retry.ineligible`
- Log context: operator_id, notification_id, template_id, prior_status on retry

## Affected Areas

| Area                                                   | Impact   | Description                                                      |
|--------------------------------------------------------|----------|------------------------------------------------------------------|
| `server/smp/.../notifications/application`             | New      | `GetNotificationsQuery`, `RetryNotificationCommand`, handlers    |
| `server/smp/.../notifications/domain`                  | Modified | Add `NotificationQueryPort`, eligibility rules in domain service |
| `server/smp/.../notifications/infrastructure`          | New      | `R2dbcNotificationQueryAdapter`, redaction logic                 |
| `server/smp/.../admin/infrastructure`                  | New      | `AdminNotificationsController` with `/api/admin/notifications`   |
| `server/smp/.../platformadmin/application`             | Modified | Extend `OperatorAccessResolver` with notification permissions    |
| `server/smp/.../platformadmin/domain`                  | Modified | Add `NOTIFICATION_RETRIED` audit event type                      |
| `server/smp/src/test/resources/features/platformadmin` | New      | `notifications.feature` BDD scenarios                            |

## Risks

| Risk                                                                         | Likelihood | Mitigation                                                                                                                                                                                |
|------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Admin accidentally retries invitation with expired token, sends invalid link | Medium     | Eligibility check blocks invitation templates; BDD scenario validates rejection with clear error message                                                                                  |
| Payload redaction incomplete, exposes token in admin UI                      | Low        | Whitelist safe fields (recipient, template_id, status) + explicit deny list (token, password, acceptUrl, rawToken); redaction logic unit-tested + BDD scenario validates sanitized output |
| Retry loop: operator retries, fails, retries same notification repeatedly    | Low        | Audit log captures each retry with operator_id; future: rate limit per notification_id (deferred to observability metrics review)                                                         |
| Query performance degrades with large notifications table                    | Medium     | Index on `(status, template_id, failed_at)` for admin queries; pagination enforced (max 100 per page); monitoring `notification.query.latency`                                            |

## Rollback Plan

1. Remove admin authorization rules for `platform.notifications.*` permissions via migration
   rollback
2. Drop `AdminNotificationsController` endpoint (no external consumers outside admin UI)
3. Revert `NOTIFICATION_RETRIED` audit event registration
4. Archive BDD scenarios under `@wip` tag to preserve test coverage for future retry work

No data migration required—notifications table remains append-only and unchanged.

## Dependencies

- Existing notifications table schema (id, idempotency_key UNIQUE, channel, recipient, template_id,
  payload JSONB, status, sent_at, failed_at, error_message)
- `OperatorAccessResolver` and admin authorization framework from platform-admin-audit capability
- `NotificationService` for retry dispatch (already wired for EMAIL channel)

## Success Criteria

- [ ] Admin can list notifications filtered by channel/template/status with pagination
- [ ] Sensitive payload fields (token, password, acceptUrl, rawToken) are redacted in admin
  responses
- [ ] Admin can retry FAILED password-recovery notifications; retry updates existing row and
  re-dispatches
- [ ] Retry command rejects invitation/waitlist templates with clear "unsafe retry" error message
- [ ] `NOTIFICATION_RETRIED` audit events logged with operator_id, notification_id, prior_status
- [ ] BDD scenarios pass: list, filter, redaction, safe retry, unsafe retry denial
- [ ] Query latency < 500ms p95 for paginated requests (100 rows)
