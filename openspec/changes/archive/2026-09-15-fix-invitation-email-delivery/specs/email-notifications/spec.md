# Delta for Email Notifications

## ADDED Requirements

### Requirement: Committed direct events create one delivery

The invitation email consumer MUST consume `InvitationIssued` and `DirectInvitationResent` only
after
the enclosing reactive R2DBC transaction commits. It MUST render the target-specific copy defined by
the
invitations delta. For a committed event it MUST persist one `PENDING` notification and make one
provider
attempt. A rolled-back transaction MUST create neither a notification nor a provider call. Provider
delivery is best effort; durable crash recovery is not part of this delta.

#### Scenario: Committed create or resend dispatches once

- GIVEN a direct invitation create or resend commits
- WHEN its corresponding event is consumed
- THEN one invitation notification is recorded as `PENDING`
- AND the provider is attempted once

#### Scenario: Rollback suppresses dispatch

- GIVEN a direct create or resend transaction rolls back
- WHEN transaction completion is observed
- THEN no invitation notification exists and the provider is not called

### Requirement: Initial and resend deliveries have separate identities

Initial delivery MUST use `invitation:{invitationId}:initial`. Each intentional direct resend MUST
receive
a distinct internal `deliveryId` and use `invitation:{invitationId}:resend:{deliveryId}`. A replay
of the
same event and key MUST reuse the existing notification without inserting another record or calling
the
provider again, including when that record is `FAILED`. A later intentional resend MUST receive a
new
delivery identity and MUST NOT be suppressed by the initial key or an earlier resend key.

#### Scenario: Initial event replay is safe

- GIVEN the initial notification key already exists in any status
- WHEN the same `InvitationIssued` event is replayed
- THEN the existing delivery is reused
- AND no second provider attempt is made

#### Scenario: Resend replay differs from a new resend

- GIVEN a resend event has delivery identity `D1`
- WHEN that event is replayed and then a separate resend is accepted
- THEN `D1` creates or reuses only `invitation:{invitationId}:resend:D1`
- AND the separate resend uses a different key and creates one additional delivery

### Requirement: Provider outcomes own notification status

Notifications MUST own invitation delivery state independently of invitation validity. A new record
MUST
start `PENDING`; provider success MUST update it to `SENT` with `sentAt`; provider failure MUST
update it
to `FAILED` with `failedAt` and an error message. The consumer MUST handle a provider failure
without
throwing it back to the event publisher, and the committed invitation MUST remain semantically
`ACTIVE`
unless an independent lifecycle operation changes it.

#### Scenario: Provider success

- GIVEN a pending invitation notification
- WHEN the provider returns success
- THEN the notification is `SENT` and has a send timestamp
- AND the invitation lifecycle is unchanged

#### Scenario: Provider failure

- GIVEN a pending invitation notification for a committed invitation
- WHEN the provider returns failure
- THEN the notification is `FAILED` with failure time and reason
- AND the committed invitation remains valid

### Requirement: Temporary raw-token handoff is non-canonical

Until DALLAY-566 supplies the token-safe replacement, the existing handler-to-event-to-consumer
raw-token
handoff MAY remain solely to render the accept URL in memory. This is a documented temporary
contradiction
of the canonical `invitations` security requirements: current invitation events carry `rawToken`,
and the
current notification payload carries a token-bearing `acceptUrl`, while canonical behavior forbids
raw or
recoverable token values in observable or durable state. This delta MUST NOT be read as a security
relaxation or as authorization to add new token surfaces. DALLAY-566 owns generation, rotation, TTL,
validation, recipient binding, URL assembly, encoding, and the token-safe handoff that removes this
exception.

#### Scenario: Temporary exception remains visible

- GIVEN DALLAY-566’s replacement handoff is not yet implemented
- WHEN direct invitation delivery is restored
- THEN the existing raw-token path is explicitly marked temporary and non-canonical
- AND no HTTP response, audit record, log, metric, or new durable field exposes the token
- AND the DALLAY-566 follow-up remains required

## REMOVED Requirements

(None.)
