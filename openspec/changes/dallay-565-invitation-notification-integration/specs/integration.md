# Delta: Invitation-Notification Integration Specification

## Purpose

This specification documents the integration seam between the Platform-Admin bounded context (Invitation lifecycle) and the Notifications bounded context (Notification delivery). It establishes the correct architectural boundaries ensuring each context maintains its own state independently.

## Integration Architecture

### Context Boundaries

```
┌─────────────────────────────────────────────────────────────────┐
│                    Platform-Admin Context                         │
│                                                                  │
│  ┌──────────────────┐    InvitationIssued    ┌──────────────┐ │
│  │ InviteWaitlist   │ ─────────────────────── │ Notification │ │
│  │ EntryHandler     │    (token-free event)   │ Context      │ │
│  └────────┬─────────┘                         │              │ │
│           │                                    │              │ │
│           │ persists                           │              │ │
│           ▼                                    │              │ │
│  ┌──────────────────┐                         │              │ │
│  │ Waitlist         │                         │              │ │
│  │ Invitation       │ NO COUPLING ◄────────── │              │ │
│  │ (ACTIVE/ACCEPTED │                         │              │ │
│  │ /EXPIRED/REVOKED)│                         │              │ │
│  └──────────────────┘                         └──────┬───────┘ │
│                                                      │          │
│                                                      │ owns     │
│                                                      ▼          │
│                                             ┌──────────────────┐ │
│                                             │ Notification     │ │
│                                             │ (PENDING/SENT/   │ │
│                                             │ FAILED)          │ │
│                                             └──────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Event Flow: Invitation Issuance to Notification Dispatch

The following sequence describes the correct event flow:

1. **Invitation Creation** (Platform-Admin Context)
   - Administrator initiates invitation
   - Handler creates WaitlistInvitation with ACTIVE status
   - Handler publishes InvitationIssued event (token-free)
   - Transaction commits successfully

2. **Event Consumption** (Notifications Context)
   - SendInvitationEmailConsumer receives InvitationIssued
   - Consumer checks idempotency: `invitation:{invitationId}:initial`
   - If new: creates Notification with PENDING status
   - Consumer reconstructs accept URL from invitationId
   - Consumer dispatches email via EmailDispatcher

3. **Notification Update** (Notifications Context)
   - EmailDispatcher returns result
   - Consumer updates Notification to SENT or FAILED
   - No cross-context event published

4. **Invitation Lifecycle** (Platform-Admin Context)
   - Invitation state evolves independently (ACTIVE → ACCEPTED/EXPIRED/REVOKED)
   - Invitation never observes notification state

## ADDED Requirements

### Requirement: Post-Commit Event Publishing Guarantee

The InvitationIssued event MUST be published only after the transaction that persisted the invitation commits successfully.

Event publishing MUST use one of:
- `@TransactionalEventListener(phase = AFTER_COMMIT)`
- Outbox pattern with async publishing
- Transactional outbox table

#### Scenario: Event published after successful commit

- GIVEN an invitation is created and persisted
- WHEN the transaction commits successfully
- THEN the InvitationIssued event MUST be published
- AND the notification consumer receives the event only after commit

#### Scenario: Failed transaction does not publish event

- GIVEN an invitation creation transaction fails to commit
- WHEN the transaction is rolled back
- THEN no InvitationIssued event MUST be published
- AND no notification MUST be created

### Requirement: SendInvitationEmailConsumer Behavior

The SendInvitationEmailConsumer MUST consume InvitationIssued events and manage the notification lifecycle independently.

The consumer MUST:
- Create Notification with type INVITATION and status PENDING
- Use idempotency key: `invitation:{invitationId}:initial`
- Reconstruct accept URL from invitationId
- Update Notification to SENT on success
- Update Notification to FAILED on failure
- NOT update any Invitation state
- NOT publish cross-context events

#### Scenario: Consumer creates notification on InvitationIssued

- GIVEN SendInvitationEmailConsumer receives InvitationIssued
- WHEN no existing notification with idempotency key exists
- THEN the consumer MUST create Notification with status PENDING
- AND the consumer MUST dispatch email
- AND the consumer MUST update notification to SENT or FAILED

#### Scenario: Consumer skips duplicate event

- GIVEN SendInvitationEmailConsumer receives InvitationIssued
- WHEN a notification with idempotency key already exists
- THEN the consumer MUST skip processing
- AND the consumer MUST NOT dispatch email

### Requirement: Clean Context Seam

The integration between Platform-Admin and Notifications contexts MUST follow clean seam principles:

- Platform-Admin context publishes domain events
- Notifications context consumes events and owns notification state
- No bidirectional coupling
- No cross-context state updates

#### Scenario: No cross-context state coupling

- GIVEN a notification is created for an invitation
- WHEN any notification lifecycle event occurs (creation, success, failure)
- THEN no Invitation state update MUST occur
- AND no event MUST flow from Notification context to Platform-Admin context

## MODIFIED Requirements

### Requirement: Token Security Throughout Pipeline

The raw bearer token MUST be excluded from all observable state, logs, events, and payloads throughout the invitation-to-notification pipeline.

The token flow MUST be:
1. Generated in InviteWaitlistEntryHandler
2. Hashed and hash persisted in WaitlistInvitation.tokenHash
3. Used to build acceptUrl for email template rendering
4. Dropped immediately after email dispatch preparation
5. NEVER present in InvitationIssued event payload
6. NEVER present in Notification payload
7. NEVER logged

(Previously: rawToken was passed in InvitationCreated event payload)

#### Scenario: Raw token excluded from InvitationIssued event

- GIVEN an invitation is created with a raw token
- WHEN the InvitationIssued event is serialized
- THEN the rawToken field MUST NOT be present in the event payload
- AND the tokenHash MUST NOT be present in the event payload

#### Scenario: Raw token excluded from notification

- GIVEN a notification is created for an invitation
- WHEN the notification is persisted
- THEN the rawToken MUST NOT be stored in the notification
- AND the tokenHash MUST NOT be stored in the notification
- AND the acceptUrl constructed from token MUST NOT be stored

#### Scenario: Raw token excluded from logs

- GIVEN an invitation is created and notification is dispatched
- WHEN any log entry is written
- THEN no log entry MUST contain the rawToken
- AND no log entry MUST contain the tokenHash
