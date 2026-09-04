# Delta: Notification Delivery Specification

## ADDED Requirements

### Requirement: Invitation Notification Type

The system MUST support invitation notifications as a distinct notification type within the Notification context.

Invitation notifications:
- Are created in response to InvitationIssued domain events
- Track their own lifecycle independent of the Invitation aggregate
- Are correlated to the invitation via invitationId
- Do not receive raw bearer tokens in their payload

#### Scenario: InvitationIssued triggers notification creation

- GIVEN an InvitationIssued domain event is published
- WHEN the SendInvitationEmailConsumer receives the event
- THEN the consumer MUST create a Notification record
- AND the notification MUST have type INVITATION
- AND the notification MUST have status PENDING
- AND the notification MUST be correlated to the invitationId from the event

### Requirement: Notification Payload for Invitations

The notification payload for invitation notifications MUST contain sufficient information to construct the acceptance email without receiving the raw bearer token.

The invitation notification payload MUST contain:
- invitationId: correlation identifier
- recipientEmail: destination address
- workspaceName: for email personalization
- locale: for template selection
- expiresAt: for display in email content

The invitation notification payload MUST NOT contain:
- rawToken: plaintext bearer token (never in payload)
- tokenHash: hashed bearer token
- constructed acceptUrl: URL must be reconstructed from invitationId

#### Scenario: Notification payload excludes raw token

- GIVEN a notification is created for an invitation
- WHEN the notification payload is serialized or logged
- THEN the payload MUST NOT contain rawToken
- AND the payload MUST NOT contain tokenHash
- AND the payload MUST NOT contain the constructed acceptUrl

#### Scenario: Accept URL reconstructed from invitation ID

- GIVEN a notification consumer needs to render the invitation email
- WHEN constructing the accept URL
- THEN the consumer MUST use the invitationId from the payload
- AND the consumer MUST reconstruct the URL using the platform's configured base URL
- AND the raw token MUST NOT be needed to construct the URL

### Requirement: Notification Correlation with Invitation

The system MUST enable correlation between notifications and their corresponding invitations for future operations such as resend (DALLAY-574).

Correlation MUST be achieved via:
- invitationId stored in the notification payload
- idempotency key: `invitation:{invitationId}:initial`

#### Scenario: Notification is correlated with invitationId

- GIVEN a notification is created for an invitation
- WHEN the notification record is persisted
- THEN the notification payload MUST contain invitationId
- AND queries for notifications by invitationId MUST return the notification

### Requirement: Notification Lifecycle Independence

The notification lifecycle (PENDING → SENT/FAILED) MUST be independent of the invitation lifecycle (ACTIVE/ACCEPTED/EXPIRED/REVOKED).

Notification state transitions MUST NOT trigger invitation state changes.

#### Scenario: Notification delivery failure leaves invitation ACTIVE

- GIVEN an invitation is in ACTIVE state
- WHEN the notification dispatch fails
- THEN the notification status MUST transition to FAILED
- AND the invitation status MUST remain ACTIVE
- AND the invitation MUST NOT receive any delivery status update

#### Scenario: Notification lifecycle is isolated from invitation

- GIVEN an invitation is in ACTIVE state
- WHEN a notification for that invitation is created and dispatched
- THEN the notification lifecycle operates independently
- AND changes to notification status do not affect invitation status
- AND changes to invitation status do not affect notification status

## MODIFIED Requirements

### Requirement: Email Dispatch Failure Handling

The notification consumer MUST handle email dispatch failures gracefully without throwing exceptions to the event bus.

When email dispatch fails:
- The notification status MUST transition to FAILED
- The error message SHOULD be recorded in the notification
- The consumer MUST NOT throw exceptions
- The invitation MUST NOT be notified of the failure

(Previously: InvitationDeliveryAttempted event propagated failure back to invitation context)

#### Scenario: Email dispatch failure updates notification status

- GIVEN a notification is created and email dispatch is attempted
- WHEN the EmailDispatcher returns EmailDispatchResult.Failure
- THEN the notification status MUST transition to FAILED
- AND the error message MUST be recorded
- AND the consumer MUST NOT throw an exception
- AND no cross-context event MUST be published

#### Scenario: Email dispatch success updates notification status

- GIVEN a notification is created and email dispatch is attempted
- WHEN the EmailDispatcher returns EmailDispatchResult.Success
- THEN the notification status MUST transition to SENT
- AND the sentAt timestamp MUST be recorded
- AND no cross-context event MUST be published

## REMOVED Requirements

### Requirement: InvitationDeliveryAttempted event removed

The system MUST NOT publish InvitationDeliveryAttempted events that cross context boundaries.

(Reason: This event existed solely to propagate notification state back to the Invitation context. With deliveryStatus removed from Invitation, this cross-context coupling is eliminated.)
