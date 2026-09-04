# Design: Integrate invitation creation with notification delivery

## Technical Approach

Surgical refactor to eliminate architectural coupling between Invitation and Notification contexts. The invitation aggregate tracks its own lifecycle (ACTIVE/ACCEPTED/EXPIRED/REVOKED); the notification aggregate tracks delivery lifecycle (PENDING/SENT/FAILED) independently. Communication flows one-way through domain events: platform-admin publishes `InvitationIssued`; notifications consumes it; no reverse channel.

This maps to the proposal's "establish clean boundary" by removing `deliveryStatus` from `WaitlistInvitation`, replacing `InvitationCreated` with `InvitationIssued` (without raw token), eliminating `InvitationDeliveryAttempted` event, and ensuring post-commit event publishing.

## Architecture Decisions

### Decision: Replace InvitationCreated with InvitationIssued

**Choice**: New event `InvitationIssued` containing `invitationId: UUID`, `recipientEmail: String`, `workspaceName: String`, `locale: String?` only.

**Alternatives considered**:
- Keep `InvitationCreated` name — rejected, name reflects persistence detail not domain action
- Pass full accept URL in event — rejected, forces invitation context to know notification transport details
- Pass raw token in event payload — rejected, security risk and unnecessary coupling

**Rationale**: Accept URL is notification concern. Notification infrastructure reconstructs URL from invitation ID using injected `AcceptUrlTemplate` (already exists, used by resend flow). Raw token never leaves handler method scope. Event name `Issued` reflects domain vocabulary from waitlist context.

### Decision: Remove deliveryStatus from WaitlistInvitation

**Choice**: Delete fields `deliveryStatus: InvitationDeliveryStatus`, `lastDeliveryAttemptAt: Instant?`, `deliveryAttemptCount: Int` from aggregate.

**Alternatives considered**:
- Keep fields but mark deprecated — rejected, leaves architectural violation in place
- Add separate read-model projection — rejected, overkill for this use case

**Rationale**: Invitation lifecycle (issued → active → accepted/expired/revoked) is independent of delivery lifecycle (pending → sent/failed). Platform-admin context cannot query notification delivery status; if needed in future, use explicit query to notifications context. Database migration removes columns; existing tests that assert on `deliveryStatus` are updated to remove those assertions.

### Decision: Remove InvitationDeliveryAttempted event

**Choice**: Delete event entirely. Remove `UpdateInvitationDeliveryOnNotificationAttempted` consumer.

**Alternatives considered**:
- Keep event but make platform-admin consumer no-op — rejected, leaves dead coupling code
- Publish event for observability only — rejected, use structured logs/metrics instead

**Rationale**: This event exists solely to update invitation's `deliveryStatus` field. Removing that field eliminates the need for this event. Notifications context logs dispatch outcome; platform-admin context doesn't need it.

### Decision: Post-commit event publishing

**Choice**: Mark `InviteWaitlistEntryHandler` event publishing with `@TransactionalEventListener(phase = AFTER_COMMIT)` on a private suspend method that wraps `eventPublisher.publish()`.

**Alternatives considered**:
- Use Spring's `@TransactionalEventPublisher` — rejected, requires additional infrastructure changes
- Publish synchronously before transaction commit — rejected, violates reliability requirement

**Rationale**: Ensures event only published if invitation persists successfully. If consumer fails, invitation remains ACTIVE and resend can retry without duplicate. Existing Spring infrastructure supports `@TransactionalEventListener`; minimal code change.

### Decision: Token delivery mechanism

**Choice**: `InvitationIssued` event carries only invitation ID. `SendInvitationEmailConsumer` uses `AcceptUrlTemplate` to reconstruct URL with token embedded. Token retrieval uses invitation ID to fetch tokenHash, then... **BLOCKER: This doesn't work.** The consumer has no access to the raw token; only the hash is persisted. 

**Revised choice**: `InvitationIssued` event includes `invitationToken: String` field containing the raw token. This field is NOT serialized in `toPayload()` override (security); it exists only in memory during event dispatch. Consumer receives token, builds accept URL, dispatches email, and drops token.

**Alternatives considered**:
- Store token temporarily in Redis/cache — rejected, adds infrastructure dependency and complexity
- Pass token in separate secure channel — rejected, over-engineered for in-process event bus

**Rationale**: Raw token must reach email dispatcher to build accept URL. In-memory event dispatch is secure: token exists in heap during handler → event bus → consumer flow, never hits disk/log/network unless explicitly serialized. `BaseDomainEvent.toPayload()` override excludes `invitationToken` field from serialization, so audit trail and event store (if added) never see it.

### Decision: Idempotency key format

**Choice**: `"invitation:${invitationId}"` stored in `Notification.idempotencyKey`. SendInvitationEmailConsumer checks for existing notification with this key before creating new one.

**Alternatives considered**:
- Use `event.eventId` as idempotency key — rejected, resend uses different event instance with different eventId
- Hash(invitationId + attempt count) — rejected, resend needs to supersede, not accumulate

**Rationale**: Invitation ID is stable across create + resend. Resend flow supersedes previous invitation (marks it SUPERSEDED) and issues new invitation with new ID, so new idempotency key. Consumer's existing check `notificationRepository.findByIdempotencyKey()` prevents duplicate dispatch if event bus retries.

### Decision: Notification correlation without exposing token

**Choice**: `Notification.payload` contains `invitationId: UUID` only. No tokenHash, no acceptUrl, no raw token.

**Alternatives considered**:
- Store tokenHash in notification payload — rejected, still couples contexts
- Store full accept URL in payload — rejected, exposes token in persistent storage

**Rationale**: Notification context needs correlation ID to answer "which invitation does this delivery attempt belong to?" but doesn't need token itself. `invitationId` is sufficient. Platform-admin context can independently query invitation by ID if needed (though after this refactor, it won't need delivery status).

### Decision: Security — token never persisted in notification context

**Choice**: `BaseDomainEvent.toPayload()` override in `InvitationIssued` excludes `invitationToken` from map. Consumer uses token in-memory only, never passes to repository.

**Alternatives considered**:
- Redact token in logs only — rejected, insufficient, payload serialization also hits audit trail
- Encrypt token in event payload — rejected, adds key management complexity for temporary data

**Rationale**: Defense in depth. Even if event serialization logic changes or new observer added, token never escapes memory. Logs: SLF4J structured logging already configured to exclude fields matching `*token*`, `*secret*`, `*password*` patterns. Metrics: no token-containing fields emitted. Audit: `toPayload()` override ensures audit trail clean.

## Data Flow

Current (before):
```
InviteWaitlistEntryHandler (TRANSACTIONAL)
  ├─> save WaitlistInvitation (deliveryStatus=PENDING)
  ├─> publish InvitationCreated (rawToken in payload)
  │     ↓
  │   SendInvitationEmailConsumer
  │     ├─> create Notification (PENDING)
  │     ├─> dispatch email via EmailDispatcher
  │     ├─> update Notification (SENT/FAILED)
  │     └─> publish InvitationDeliveryAttempted
  │           ↓
  │         UpdateInvitationDeliveryOnNotificationAttempted
  │           └─> update WaitlistInvitation.deliveryStatus (SENT/FAILED) ← CYCLE
  └─> commit transaction
```

Target (after):
```
InviteWaitlistEntryHandler (TRANSACTIONAL)
  ├─> save WaitlistInvitation (no deliveryStatus)
  ├─> commit transaction
  └─> [AFTER_COMMIT] publish InvitationIssued (invitationToken in-memory only)
        ↓
      SendInvitationEmailConsumer
        ├─> check idempotency ("invitation:${invitationId}")
        ├─> create Notification (PENDING, payload={invitationId})
        ├─> build accept URL from invitationToken + AcceptUrlTemplate
        ├─> dispatch email via EmailDispatcher
        ├─> update Notification (SENT/FAILED)
        └─> drop invitationToken (never persisted)
                                         ← no reverse event, no cycle
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `shared/notifications/.../InvitationIssued.kt` | Create | New event replacing InvitationCreated; fields: invitationId, recipientEmail, workspaceName, locale, invitationToken (not serialized) |
| `shared/notifications/.../InvitationCreated.kt` | Delete | Removed; replaced by InvitationIssued |
| `shared/notifications/.../InvitationDeliveryAttempted.kt` | Delete | Removed; no reverse coupling needed |
| `server/smp/.../WaitlistInvitation.kt` | Modify | Remove deliveryStatus, lastDeliveryAttemptAt, deliveryAttemptCount fields |
| `server/smp/.../InviteWaitlistEntryHandler.kt` | Modify | Publish InvitationIssued instead of InvitationCreated; add @TransactionalEventListener wrapper for post-commit; remove deliveryStatus initialization |
| `server/smp/.../ResendWaitlistInvitationHandler.kt` | Modify | Publish InvitationIssued for resend; same event structure as create |
| `server/smp/.../SendInvitationEmailConsumer.kt` | Modify | Consume InvitationIssued; use invitationToken to build accept URL; store invitationId in Notification.payload; existing idempotency check unchanged |
| `server/smp/.../UpdateInvitationDeliveryOnNotificationAttempted.kt` | Delete | Removed; no deliveryStatus to update |
| `server/smp/.../R2dbcWaitlistInvitationRepository.kt` | Modify | Remove deliveryStatus columns from queries and mappings |
| `server/smp/.../resources/db/migration/V027__remove_invitation_delivery_status.sql` | Create | DROP COLUMN for deliveryStatus, lastDeliveryAttemptAt, deliveryAttemptCount |
| `server/smp/.../AdminInvitationController.kt` | Modify | Remove deliveryStatus from response DTO if exposed |
| `server/smp/.../InviteWaitlistEntryHandlerTest.kt` | Modify | Remove assertions on deliveryStatus; verify InvitationIssued published instead of InvitationCreated |
| `server/smp/.../ResendWaitlistInvitationHandlerTest.kt` | Modify | Same as InviteWaitlistEntryHandlerTest for resend flow |
| `server/smp/.../SendInvitationEmailConsumerTest.kt` | Modify | Test with InvitationIssued event; verify accept URL built correctly from token; verify invitationId in notification payload |
| `server/smp/.../UpdateInvitationDeliveryOnNotificationAttemptedTest.kt` | Delete | Test for deleted consumer |

## Interfaces / Contracts

### Before: InvitationCreated (Kotlin)
```kotlin
data class InvitationCreated(
    val invitationId: UUID,
    val waitlistEntryId: String,
    val operatorPrincipalId: UUID,
    val recipient: String,
    val workspaceName: String,
    val acceptUrl: String,          // ← notification concern, remove
    val locale: String?,
    val rawToken: String,            // ← security risk, remove from payload
) : BaseDomainEvent()
```

### After: InvitationIssued (Kotlin)
```kotlin
data class InvitationIssued(
    val invitationId: UUID,
    val recipientEmail: String,      // renamed from recipient
    val workspaceName: String,
    val locale: String?,
    val invitationToken: String,     // in-memory only, not in toPayload()
) : BaseDomainEvent() {
    override fun toPayload(): Map<String, Any?> = mapOf(
        "invitationId" to invitationId,
        "recipientEmail" to recipientEmail,
        "workspaceName" to workspaceName,
        "locale" to locale,
        // invitationToken intentionally excluded
    )
}
```

### Before: WaitlistInvitation (Kotlin)
```kotlin
data class WaitlistInvitation(
    // ... other fields ...
    val deliveryStatus: InvitationDeliveryStatus,  // ← remove
    val lastDeliveryAttemptAt: Instant? = null,    // ← remove
    val deliveryAttemptCount: Int = 0,             // ← remove
)
```

### After: WaitlistInvitation (Kotlin)
```kotlin
data class WaitlistInvitation(
    val id: WaitlistInvitationId,
    val waitlistEntryId: String,
    val tokenHash: String,
    val status: WaitlistInvitationStatus,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val revokedBy: UUID? = null,
    val createdBy: UUID,
    val version: Long = 0,
)
// deliveryStatus, lastDeliveryAttemptAt, deliveryAttemptCount removed
```

### Before: SendInvitationEmailConsumer (Kotlin)
```kotlin
@Subscribe(InvitationCreated::class)
override suspend fun consume(event: InvitationCreated) {
    val acceptUrl = event.acceptUrl  // uses pre-built URL
    // ...
    eventPublisher.publish(InvitationDeliveryAttempted(...))  // ← publishes reverse event
}
```

### After: SendInvitationEmailConsumer (Kotlin)
```kotlin
@Subscribe(InvitationIssued::class)
override suspend fun consume(event: InvitationIssued) {
    val acceptUrl = acceptUrlTemplate.build(event.invitationToken)  // builds URL from token
    val notification = Notification(
        // ...
        payload = mapOf("invitationId" to event.invitationId),  // correlation only
    )
    // ... dispatch email ...
    // NO reverse event published
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | WaitlistInvitation aggregate without deliveryStatus | Verify revoke(), supersede(), accept() methods work unchanged; verify no deliveryStatus in constructor |
| Unit | InvitationIssued event serialization | Verify toPayload() excludes invitationToken; verify in-memory event contains token |
| Unit | InviteWaitlistEntryHandler event publishing | Mock EventPublisher; verify InvitationIssued published with correct fields; verify rawToken passed in event.invitationToken |
| Integration | InviteWaitlistEntryHandler post-commit publishing | Real transaction boundary; verify event only published after commit; verify rollback prevents event |
| Integration | SendInvitationEmailConsumer with InvitationIssued | Verify consumer builds accept URL correctly from token; verify idempotency check prevents duplicate; verify Notification.payload contains only invitationId |
| Integration | Resend flow with InvitationIssued | Verify ResendWaitlistInvitationHandler publishes InvitationIssued; verify consumer handles resend same as create |
| Security regression | Token never in Notification persistence | Verify NotificationRepository.save() does not receive token in any field; verify Notification.payload does not contain token/tokenHash/acceptUrl |
| Security regression | Token never in logs/audit | Verify SLF4J logs for "Dispatched invitation email" contain invitationId but not token; verify BaseDomainEvent.toPayload() audit trail clean |
| BDD | Cucumber invitation scenarios without deliveryStatus | Update feature files to remove deliveryStatus assertions; verify "Operator invites waitlist entry" scenario passes; verify "Operator resends invitation" scenario passes |

## Migration / Rollout

### Database Migration
Execute `V027__remove_invitation_delivery_status.sql`:
```sql
ALTER TABLE waitlist_invitations DROP COLUMN delivery_status;
ALTER TABLE waitlist_invitations DROP COLUMN last_delivery_attempt_at;
ALTER TABLE waitlist_invitations DROP COLUMN delivery_attempt_count;
```

**Rollback**: Re-add columns with default values; existing invitations have no delivery status history (acceptable, this is a refactor not a feature change).

### Deployment
Single-phase deployment safe because:
- New event `InvitationIssued` replaces `InvitationCreated` atomically
- No old consumers of `InvitationCreated` remain after deployment
- `InvitationDeliveryAttempted` consumer removed in same deployment
- Database migration runs before application starts

**Risk**: If deployment fails mid-rollout and some pods have new code while others have old code, invitation emails won't dispatch (old consumer waiting for `InvitationCreated`, new handler publishing `InvitationIssued`). Mitigation: Standard rollback procedure reverts code + database.

### Feature Flags
Not needed; this is a backend refactor with no user-facing behavior change. Invitation emails continue to dispatch with same UX.

## Open Questions

- [ ] RESOLVED: How to pass raw token to consumer? — Use `invitationToken` field in event, exclude from serialization in `toPayload()` override.
- [ ] RESOLVED: What if consumer needs to correlate notification to invitation later? — Store `invitationId` in `Notification.payload`.
- [ ] Should we add structured logging for invitation issuance separate from notification dispatch? — Out of scope; existing logs sufficient.
- [ ] Should we add metrics for invitation issuance rate? — Out of scope; can be added separately.
