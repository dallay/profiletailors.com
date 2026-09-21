# Delta for Invitations

## ADDED Requirements

### Requirement: Direct invitation transactions are atomic

`CreateInvitationHandler` and `ResendInvitationHandler` MUST own an `AtomicTransactionRunner`
boundary. Inside one reactive R2DBC transaction each handler MUST validate the operator, resolve the
target, mutate the invitation, write the administrative audit event, and register the domain event
through `InvitationEventPublisher`. Any failure before commit MUST roll back invitation, audit, and
event registration together. `AdminInvitationController` direct create and direct resend MUST NOT be
transactional. Telemetry recording MUST happen only after the transaction succeeds.

#### Scenario: Committed create persists invitation and audit with registered event

- GIVEN a direct invitation create with a resolvable target
- WHEN the handler transaction commits
- THEN one `ACTIVE` invitation exists
- AND one `INVITATION_CREATED` audit event exists
- AND one `InvitationIssued` event is registered for after-commit delivery

#### Scenario: Publisher failure leaves no invitation or audit

- GIVEN a direct create where event publication fails inside the transaction
- WHEN the handler propagates that failure
- THEN no invitation row exists for the requested email
- AND no `INVITATION_CREATED` audit event exists
- AND no notification exists and the provider is not called

### Requirement: Target-aware invitation context with 404 workspace semantics

For `EXISTING_WORKSPACE` the handler MUST resolve the human label through the narrow tenancy
workspace-name port (`workspaces.name` where `ACTIVE`). A null lookup MUST raise the platform-admin
workspace-not-found error, map to HTTP `404 Not Found` with code `WORKSPACE_NOT_FOUND`, abort before
commit, and create no invitation, audit, event, notification, or provider call. The email MUST
receive
the resolved name, never the workspace ID or blank text. For `NEW_WORKSPACE` the handler MUST keep
`workspaceId` absent and use exactly: “You’ve been invited to create a new Profile Tailors
workspace.”

#### Scenario: Unknown workspace returns 404 with no writes

- GIVEN a direct create for `EXISTING_WORKSPACE` with an unknown workspace ID
- WHEN the handler resolves the target
- THEN HTTP `404` with `WORKSPACE_NOT_FOUND` is returned
- AND no invitation, audit, event, notification, or provider call is created

#### Scenario: New-workspace create uses the canonical copy

- GIVEN a direct create for `NEW_WORKSPACE`
- WHEN the handler resolves the target
- THEN no workspace lookup is performed
- AND the event carries `NEW_WORKSPACE` with the exact canonical workspace copy

### Requirement: Delivery identity originates in handlers

Initial direct create MUST publish `InvitationIssued` with `deliveryId = null`, which the consumer
renders as `invitation:{invitationId}:initial`. Each intentional direct resend MUST mint a new
random
`deliveryId` and publish `DirectInvitationResent` with that identity, which the consumer renders as
`invitation:{invitationId}:resend:{deliveryId}`. Two intentional resends of the same invitation MUST
carry distinct delivery identities.

#### Scenario: Initial create carries no delivery identity

- GIVEN a direct create commits
- WHEN its `InvitationIssued` event is inspected
- THEN `deliveryId` is null
- AND the delivery key is `invitation:{invitationId}:initial`

#### Scenario: Two resends carry distinct delivery identities

- GIVEN one active direct invitation
- WHEN it is resent twice
- THEN both `DirectInvitationResent` events carry non-null delivery identities
- AND the two delivery identities differ

## REMOVED Requirements

(None.)
