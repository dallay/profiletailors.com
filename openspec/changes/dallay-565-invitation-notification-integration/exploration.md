# Exploration: Backend Integration — Invitations and Notifications

## Current State

### Invitation Creation Flow (Existing)

The invitation creation process lives in the platform-admin bounded context:

- `InviteWaitlistEntryHandler` creates a `WaitlistInvitation` with `deliveryStatus = PENDING`
- After persisting the invitation, it publishes an `InvitationCreated` domain event with the raw bearer token
- The handler is called from `AdminInvitationController.invite()` which is annotated with `@Transactional`
- The raw token is generated, hashed, and the hash is persisted; the plaintext token is passed in the event payload

File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt`

```kotlin
val invitation = invitationRepository.save(
    WaitlistInvitation(
        id = WaitlistInvitationId.generate(),
        waitlistEntryId = command.waitlistEntryId,
        tokenHash = tokenHash,
        status = WaitlistInvitationStatus.ACTIVE,
        issuedAt = now,
        expiresAt = now + invitationTtl,
        createdBy = command.operatorPrincipalId,
        deliveryStatus = InvitationDeliveryStatus.PENDING,
    ),
)

eventPublisher.publish(
    InvitationCreated(
        invitationId = invitation.id.value,
        waitlistEntryId = command.waitlistEntryId,
        operatorPrincipalId = command.operatorPrincipalId,
        recipient = context.recipientEmail,
        workspaceName = context.workspaceName,
        acceptUrl = acceptUrlTemplate.build(rawToken),
        locale = context.locale,
        rawToken = rawToken,
    ),
)
```

### Notification Delivery Flow (Existing)

The notification delivery process lives in the notifications bounded context:

- `SendInvitationEmailConsumer` subscribes to `InvitationCreated` events
- It creates a `Notification` record with `status = PENDING` and an idempotency key derived from `invitationId`
- The idempotency check prevents duplicate sends: if a notification with the same key exists, the consumer skips dispatch
- After calling `EmailDispatcher.dispatch()`, it publishes `InvitationDeliveryAttempted` with the delivery result
- The raw token is used to render the email and never persisted in the notification record

File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt`

The consumer enforces idempotency and owns the notification lifecycle:
- Creates notification with `PENDING` status
- Dispatches email via `IdentityEmailDispatcher`
- Updates notification to `SENT` or `FAILED` based on dispatch result
- Publishes `InvitationDeliveryAttempted` for the platform-admin context

### Platform-Admin Delivery Status Update (Existing)

The platform-admin context updates the invitation's `deliveryStatus` by consuming `InvitationDeliveryAttempted`:

File: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/events/UpdateInvitationDeliveryOnNotificationAttempted.kt`

This consumer updates the invitation's `deliveryStatus` to `SENT` or `FAILED` based on the notification attempt result.

### Event Infrastructure

Event publishing uses Spring's `ApplicationEventPublisher` and a custom `EventEmitter`:

File: `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/bus/SpringDomainEventPublisher.kt`

The `SpringDomainEventPublisher` fans events to:
1. Spring's `ApplicationEventPublisher` for `@EventListener` methods
2. The application `EventEmitter` for `@Subscribe`-annotated consumers like `SendInvitationEmailConsumer`

### Transaction Boundary Analysis

Controllers are annotated with `@Transactional`:
- `AdminInvitationController.invite()` has `@Transactional`
- The handler `InviteWaitlistEntryHandler.handle()` runs inside this transaction
- `invitationRepository.save()` and `eventPublisher.publish()` both execute within the same transaction boundary

Spring's `ApplicationEventPublisher` provides **AFTER_COMMIT** semantics by default for transactional event listeners, but the current code does NOT use `@TransactionalEventListener`. The `EventEmitter` and `@Subscribe` consumers receive events synchronously during transaction commit, NOT after commit.

This means:
- If `eventPublisher.publish()` throws, the invitation save rolls back (good)
- If the event consumer throws, the invitation save rolls back (risky)
- If the consumer succeeds but commit fails, the notification may be sent but invitation not persisted (data inconsistency)
- There is NO outbox pattern or durable event queue

### Token Flow and Security

Current token handling:
- Raw token generated in `InviteWaitlistEntryHandler`
- Token hash persisted in `WaitlistInvitation.tokenHash`
- Raw token passed in `InvitationCreated` event payload
- `SendInvitationEmailConsumer` receives the raw token, renders the email, and drops it
- Raw token is NOT persisted in `Notification` or any audit log
- The `InvitationCreated` event serialization must ensure the token is excluded from any persisted event payload

Risk: If the event bus persists events for replay/audit, the raw token could be exposed. The domain event must override `toPayload()` to exclude `rawToken`.

File: `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationCreated.kt`

The event already documents that `rawToken` MUST NOT be persisted in `BaseDomainEvent.toPayload()` or audit/log output.

### Existing Notification Infrastructure

Notification entity:
- Immutable once persisted
- Status: `PENDING` → `SENT` or `FAILED`
- Idempotency key prevents duplicate sends
- Supports correlation via payload (e.g., `invitationId` can be stored in `NotificationPayload`)
- `sentAt`, `failedAt`, `errorMessage` track delivery outcome

File: `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/Notification.kt`

EmailDispatcher:
- Returns `EmailDispatchResult.Success` or `EmailDispatchResult.Failure`
- Does NOT throw on dispatch failure
- The consumer updates notification status based on the result

File: `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/IdentityEmailDispatcher.kt`

### Existing Test Coverage

Invitation tests:
- `InviteWaitlistEntryHandlerTest.kt` — unit tests for invitation creation logic
- `SendInvitationEmailConsumerTest.kt` — unit tests for notification consumer
- BDD tests for invitation flow exist in `server/smp/src/test/resources/features/`

Notification tests:
- `IdentityEmailDispatcherTest.kt` — unit tests for email dispatcher
- `SendWelcomeEmailConsumerTest.kt` — similar pattern for welcome emails

## Affected Areas

### Files to Modify

None. The integration already exists and is functional. The exploration reveals that:

1. `InviteWaitlistEntryHandler` already publishes `InvitationCreated`
2. `SendInvitationEmailConsumer` already creates notifications and dispatches emails
3. `UpdateInvitationDeliveryOnNotificationAttempted` already updates invitation delivery status
4. Token flow is already secured (raw token in event, hash in DB, not in notification)

### Files to Review for Transaction Safety

- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/bus/SpringDomainEventPublisher.kt` — does NOT guarantee post-commit handoff
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationController.kt` — transactional boundary
- `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` — idempotency and failure handling
- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationCreated.kt` — token exclusion from serialization

## Approaches

### Option 1: No Implementation Needed (Current State)

The integration already exists. DALLAY-565 asks for backend integration, which is already implemented:
- Invitation persistence triggers notification creation via `InvitationCreated` event
- Notification delivery updates invitation status via `InvitationDeliveryAttempted` event
- Token handling is secure (hash persisted, plaintext in event only, not in notification)
- Idempotency prevents duplicate sends
- EmailDispatcher failure leaves invitation ACTIVE and notification FAILED

**Pros:**
- Zero implementation effort
- Already tested and functional
- Follows existing patterns

**Cons:**
- Transaction boundary is risky (no post-commit guarantee)
- Event consumer failure rolls back invitation save
- No durable event queue or outbox pattern

**Effort:** None

### Option 2: Add Transactional Event Listener (Post-Commit Guarantee)

Refactor `SendInvitationEmailConsumer` to use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` instead of `@Subscribe`:

1. Change `SendInvitationEmailConsumer` to listen to Spring events with `@TransactionalEventListener`
2. Remove `@Subscribe` and `EventConsumer` inheritance
3. Ensure `SpringDomainEventPublisher` publishes to both Spring and `EventEmitter` for backward compatibility
4. Event is only delivered after invitation persistence commits

**Pros:**
- Guarantees post-commit handoff
- Consumer failure does NOT roll back invitation save
- Notification sent only if invitation persisted

**Cons:**
- If consumer crashes before notification created, invitation is PENDING forever (no retry)
- Requires refactor of consumer infrastructure
- Other consumers may need similar treatment

**Effort:** Medium

### Option 3: Implement Outbox Pattern (Durable Event Queue)

Add a transactional outbox table for domain events:

1. Create `DomainEventOutbox` table
2. `InviteWaitlistEntryHandler` writes `InvitationCreated` to outbox within transaction
3. Background worker polls outbox and publishes events to `EventEmitter`
4. Mark events as published after successful delivery
5. Retry failed events with exponential backoff

**Pros:**
- Guarantees at-least-once delivery
- Survives process crashes
- Supports event replay and debugging
- Industry-standard pattern for event-driven systems

**Cons:**
- Significant implementation effort
- Requires new infrastructure (outbox table, worker, monitoring)
- Adds complexity to event bus
- May require schema migration and deployment coordination

**Effort:** High

### Option 4: Document Transaction Risk and Monitor (Minimal Change)

Accept the current transaction boundary risk and add monitoring:

1. Document the transaction risk in ADR or technical debt register
2. Add metrics for notification delivery failures
3. Add alerting for stuck invitations (PENDING > threshold)
4. Add manual admin tool to retry failed notifications (future work in DALLAY-574)

**Pros:**
- No code changes required
- Focuses on observability and operational response
- Acknowledges risk without over-engineering

**Cons:**
- Does not eliminate transaction risk
- Relies on manual intervention for stuck invitations
- Data inconsistency still possible under edge cases

**Effort:** Low

## Recommendation

**Option 1: No Implementation Needed** is the correct approach for DALLAY-565.

The backend integration between invitations and notifications already exists and is functional:
- Invitation creation publishes `InvitationCreated` event
- Notification consumer creates notification record and dispatches email
- Delivery status updates invitation via `InvitationDeliveryAttempted` event
- Token handling is secure (hash persisted, plaintext in event only)
- Idempotency prevents duplicate sends
- Dispatcher failure leaves invitation ACTIVE and notification FAILED

The transaction boundary risk (no post-commit guarantee) is a real concern, but it is NOT a blocker for DALLAY-565. The scope of DALLAY-565 is to implement the integration, which already exists. Improving the transaction safety belongs to a separate architectural improvement task.

If transaction safety must be addressed, **Option 2: Add Transactional Event Listener** is the smallest safe implementation. It requires refactoring `SendInvitationEmailConsumer` to use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` instead of `@Subscribe`, guaranteeing post-commit handoff without requiring outbox infrastructure.

**Option 3: Implement Outbox Pattern** is the gold-standard solution but is significant over-engineering for this change. It should be considered only if the application adopts outbox as a cross-cutting architectural decision.

**Option 4: Document Transaction Risk** is a fallback if no code changes are permitted, but it does not eliminate the risk.

## Risks

### Transaction Boundary Risk (High Impact, Low Probability)

The event is published synchronously within the transaction. If:
- The consumer throws an exception, the invitation save rolls back
- The consumer succeeds but commit fails, the notification may be sent but invitation not persisted

This violates the requirement that dispatcher failure must leave invitation ACTIVE and notification FAILED.

**Mitigation:**
- Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` (Option 2)
- Implement outbox pattern (Option 3)
- Accept risk and add monitoring (Option 4)

### Token Exposure Risk (High Impact, Low Probability)

If the event bus persists `InvitationCreated` events for replay/audit, the raw token could be exposed in logs, metrics, or audit events.

**Mitigation:**
- Verify `InvitationCreated.toPayload()` excludes `rawToken` (already documented in event docstring)
- Add test to verify token is never serialized
- Review event bus persistence configuration

### Idempotency Key Collision (Low Impact, Very Low Probability)

The idempotency key is derived from `invitationId`. If two notifications for the same invitation are created (e.g., via resend), the second one is skipped.

**Mitigation:**
- This is by design for initial invite
- Resend uses `InvitationResent` event with different idempotency key
- No action needed for DALLAY-565

## Ready for Proposal

**No.** DALLAY-565 may be a misunderstanding of the current state. The backend integration already exists and is functional. Before proposing implementation, confirm with the product owner whether:

1. DALLAY-565 is asking to implement something new, or
2. DALLAY-565 is asking to verify/test the existing integration, or
3. DALLAY-565 is asking to improve the transaction safety of the existing integration

If the goal is transaction safety, propose Option 2 (transactional event listener) as the smallest safe implementation. If the goal is to verify the existing integration, propose creating BDD scenarios that exercise the invitation-notification flow end-to-end.

If the goal is truly net-new implementation and the existing integration is not what was intended, clarify the desired behavior before proposing a design.
