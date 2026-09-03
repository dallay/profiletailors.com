# Invitation Notification Delivery Specification

## Overview

Integrate DALLAY-564's standalone Invitation with Notifications while isolating validity from delivery.

## Changes

### Requirement: Canonical Invitation and Prerequisite

DALLAY-565 MUST use standalone `Invitation` and its ID for durable correlation. It MUST NOT target
`WaitlistInvitation` and MUST wait for DALLAY-564 to land.

#### Scenario: Canonical flow

- GIVEN DALLAY-564 has landed and an Invitation exists
- WHEN delivery integration handles it
- THEN it MUST use the Invitation ID and no waitlist invitation

#### Scenario: Missing prerequisite

- GIVEN DALLAY-564 has not landed
- WHEN DALLAY-565 is evaluated
- THEN implementation MUST be blocked

### Requirement: Post-Commit Handoff

The initial handoff MUST be attempted only after Invitation persistence commits. `AFTER_COMMIT` is
best effort, not a guarantee of scheduling, dispatch, or inbox delivery; an outbox is out of scope.

#### Scenario: Commit schedules

- GIVEN an Invitation transaction commits successfully
- WHEN the after-commit callback runs
- THEN one initial handoff MUST be attempted

#### Scenario: Rollback suppresses

- GIVEN Invitation persistence rolls back
- WHEN the transaction completes
- THEN no handoff or resulting Notification MUST be created

### Requirement: Notifications Own Delivery State

Notifications MUST own each delivery's status, attempts, and timestamps. Invitation status MUST mean
only ACTIVE, ACCEPTED, EXPIRED, or REVOKED validity; delivery outcomes MUST NOT update it.

#### Scenario: Failure preserves validity

- GIVEN an ACTIVE Invitation has a failed delivery
- WHEN Notifications records the outcome
- THEN delivery MUST be FAILED and Invitation MUST remain ACTIVE

#### Scenario: Multiple deliveries

- GIVEN one Invitation has an initial delivery and a resend
- WHEN both are recorded
- THEN each MUST have independent state and the same Invitation ID

### Requirement: Correlated Admin Read

An authorized backend/admin read MUST compose Invitation data with a narrow Notifications summary
through an explicit read contract keyed by Invitation ID. It MUST support zero or many deliveries,
expose count, latest status, and timestamps, and exclude payloads and sensitive values.

#### Scenario: Independent owners compose

- GIVEN an Invitation and Notifications share an ID
- WHEN an authorized admin read is requested
- THEN lifecycle data MUST come from Invitation and delivery data from Notifications

#### Scenario: No delivery is readable

- GIVEN a lost handoff left no Notification
- WHEN the composed read is requested
- THEN Invitation data MUST remain readable with `InvitationDeliverySummary.EMPTY`

### Requirement: Initial Delivery, Resend, and Idempotency

The same command idempotency key MUST create or reuse exactly one delivery. Each new resend key MUST
add exactly one delivery for the same Invitation and MUST NOT mint a replacement Invitation.

#### Scenario: Initial delivery

- GIVEN a committed Invitation and new key K
- WHEN the initial command is accepted
- THEN exactly one delivery MUST be created

#### Scenario: Duplicate key

- GIVEN key K already created a delivery
- WHEN K is submitted again
- THEN its result MUST be reused and no delivery added

#### Scenario: Resend key

- GIVEN an Invitation already has a delivery
- WHEN a resend with new key R is accepted
- THEN exactly one additional delivery for that Invitation MUST be created

### Requirement: Token-Safe Durable Boundary

Raw tokens and directly recoverable token-bearing values MUST NOT enter durable events, logs, audit
records, metrics, or persistence. Delivery data MUST contain only non-secret correlation and
operational values.

#### Scenario: Durable data is token-free

- GIVEN an approved handoff renders an invitation
- WHEN delivery and observability data is stored
- THEN no raw or recoverable token value MUST be present

### Requirement: DALLAY-566 Integration Boundary

DALLAY-566 owns token transport, envelope, encoding, TTL, validation, recipient binding, and
non-secret correlation. DALLAY-565 MUST consume only required properties and MUST NOT invent or
duplicate that mechanism.

#### Scenario: Handoff contract absent

- GIVEN DALLAY-566 has not defined the handoff properties
- WHEN delivery integration is designed
- THEN implementation MUST stop at the boundary

### Requirement: Safe Failure and Observability

Provider failure MUST record a Notifications failure with time and non-secret reason without failing
or invalidating the committed Invitation. Logs and metrics MUST mask recipients, use low-cardinality
status/error dimensions, contain no token, and make no delivery guarantee; a lost handoff MAY remain
unrecorded.

#### Scenario: Provider failure

- GIVEN a committed delivery fails at the provider
- WHEN the failure is handled
- THEN FAILED and its time MUST be visible in Notifications

### Requirement: Non-Destructive Migration Compatibility

Legacy waitlist rows, columns, and compatible response fields MUST remain available for historical
reads and rollback. New Invitation flows MUST NOT write legacy delivery fields or present them as
current Notifications state; this change authorizes no removal, backfill, redaction, or incompatible
response.

#### Scenario: Historical row remains readable

- GIVEN a legacy waitlist invitation row exists
- WHEN its existing read is requested
- THEN its compatible response MUST remain available without new delivery writes

## Usage

Platformadmin will schedule `InvitationNotificationRequested` after a committed Invitation transaction.
Notifications will consume the event, own delivery records, and expose `InvitationDeliverySummaryReader`
for composed admin reads.

## Troubleshooting

- If the same command key creates duplicate deliveries, verify the unique constraint on
  `platform.invitation:{invitationId}:{commandId}`.
- If delivery state leaks into Invitation validity, check that no reverse event or direct write updates
  the Invitation aggregate.
- DALLAY-566 owns the ephemeral token handoff; do not implement token generation, URL assembly, or
  recipient binding in this change.

## References

- `openspec/changes/dallay-565/design.md`
- `openspec/changes/dallay-565/specs/email-notifications/spec.md`
