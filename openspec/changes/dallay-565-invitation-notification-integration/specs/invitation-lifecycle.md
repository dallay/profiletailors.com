# Delta: Invitation Lifecycle Specification

## MODIFIED Requirements

### Requirement: Invitation Lifecycle States

The WaitlistInvitation aggregate MUST maintain independent lifecycle states that are NOT coupled to notification delivery state.

The invitation lifecycle consists of the following states:
- ACTIVE: Invitation has been issued and is awaiting acceptance
- ACCEPTED: Invitation has been used to complete registration
- EXPIRED: Invitation has passed its expiration timestamp
- REVOKED: Invitation has been manually cancelled by an administrator

(Previously: Invitation lifecycle included deliveryStatus field tracking notification state)

#### Scenario: Invitation transitions to ACTIVE on creation

- GIVEN an administrator initiates invitation for a waitlist entry
- WHEN the invitation is persisted successfully
- THEN the invitation status MUST be ACTIVE
- AND the invitation MUST NOT have a deliveryStatus field

#### Scenario: Invitation remains ACTIVE regardless of notification outcome

- GIVEN an invitation is in ACTIVE state
- WHEN the notification delivery succeeds or fails
- THEN the invitation status MUST remain ACTIVE
- AND the invitation MUST NOT reflect notification delivery state

#### Scenario: Invitation expires after TTL

- GIVEN an invitation is in ACTIVE state
- WHEN the current time exceeds the invitation's expiresAt timestamp
- THEN the invitation status SHOULD transition to EXPIRED
- AND the invitation MUST NOT have a deliveryStatus field

#### Scenario: Invitation is revoked by administrator

- GIVEN an invitation is in ACTIVE state
- WHEN an administrator revokes the invitation
- THEN the invitation status MUST transition to REVOKED
- AND the invitation MUST NOT have a deliveryStatus field

### Requirement: InvitationIssued Domain Event

The system MUST publish an InvitationIssued domain event after successful invitation persistence without exposing the raw bearer token.

The InvitationIssued event payload MUST contain:
- invitationId: unique identifier for the invitation
- waitlistEntryId: reference to the waitlist entry
- recipientEmail: email address of the invitee
- workspaceName: name of the workspace
- locale: locale code for email template rendering
- issuedAt: timestamp of invitation issuance
- expiresAt: timestamp when invitation expires

The InvitationIssued event payload MUST NOT contain:
- rawToken: the plaintext bearer token
- tokenHash: the hashed bearer token
- acceptUrl: constructed accept URL

(Previously: InvitationCreated event contained rawToken in payload)

#### Scenario: Invitation creation publishes token-free event

- GIVEN an administrator initiates invitation for a waitlist entry
- WHEN the invitation is persisted successfully
- THEN the system MUST publish InvitationIssued event
- AND the event MUST contain invitationId
- AND the event MUST contain recipientEmail
- AND the event MUST contain workspaceName
- AND the event MUST contain locale
- AND the event MUST NOT contain rawToken
- AND the event MUST NOT contain tokenHash

#### Scenario: Accept URL is reconstructed from invitation ID

- GIVEN an InvitationIssued event is received by a consumer
- WHEN the consumer needs to construct the accept URL
- THEN the consumer MUST use the invitationId from the event
- AND the consumer MUST reconstruct the accept URL using the platform's known base URL and invitationId
- AND the consumer MUST NOT receive the constructed URL in the event payload

### Requirement: Idempotency for Initial Notification

The system MUST ensure exactly one initial notification is scheduled per invitation, preventing duplicate dispatches from repeated event consumption or replay.

The idempotency key for invitation initial notification MUST be: `invitation:{invitationId}:initial`

#### Scenario: Duplicate InvitationIssued events do not duplicate initial delivery

- GIVEN a notification record exists with idempotency key `invitation:{invitationId}:initial`
- WHEN an InvitationIssued event for the same invitationId is processed
- THEN the consumer MUST skip notification creation
- AND the consumer MUST NOT attempt to dispatch email

#### Scenario: New invitation generates unique idempotency key

- GIVEN a new invitation is created with invitationId
- WHEN the InvitationIssued event is processed
- THEN the notification idempotency key MUST be `invitation:{invitationId}:initial`
- AND the key MUST be unique to this specific invitation

## REMOVED Requirements

### Requirement: InvitationDeliveryStatus field removed

The WaitlistInvitation aggregate MUST NOT contain a deliveryStatus field.

(Reason: Delivery state belongs exclusively to the Notification context. Cross-context coupling violates bounded context boundaries. The Invitation context does not observe notification delivery state.)
