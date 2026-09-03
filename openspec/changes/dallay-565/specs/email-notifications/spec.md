# Delta for Email Notifications

## Overview

This delta updates the email-notifications capability so invitation triggers come only from the
approved post-commit handoff, never persist raw token material, and keep Notifications as the owner
of invitation delivery state.

## Changes

### Requirement: Domain Event Consumer for Email Notifications

The system MUST consume domain events to trigger email notifications. The system MUST implement the
`EventConsumer` interface and handle consumption failures gracefully. It MUST consume `UserRegistered`
and dispatch its verification email as before.
Invitation triggers MUST come only from the approved post-commit handoff, use the command idempotency
key for deduplication, and never persist, log, audit, or measure raw token material. Notifications
MUST own invitation delivery state.

(Previously: The consumer handled generic domain events and verification email idempotency, but did
not define invitation-specific post-commit timing, delivery ownership, or token-safe boundaries.)

#### Scenario: UserRegistered event triggers verification email

- GIVEN a `UserRegistered` domain event is published
- WHEN the verification consumer receives it
- THEN it MUST extract the email, generate the verification token, and send via SMTP
- AND it MUST log the dispatch without secret material

#### Scenario: Event consumer handles email sending failure

- GIVEN a `UserRegistered` event is received
- AND email sending fails
- WHEN the consumer processes the event
- THEN it MUST log the failure
- AND it MUST NOT throw the provider failure to the caller
- AND the system MUST allow manual resend through its existing flow

#### Scenario: Event consumer validates event data

- GIVEN a `UserRegistered` event is received
- WHEN the consumer validates its payload
- THEN it MUST verify that the email field exists and has a valid format
- AND it MUST reject malformed events with safe logging

#### Scenario: Event consumer is idempotent

- GIVEN the same `UserRegistered` event is received multiple times
- WHEN the consumer processes the duplicates
- THEN it MUST NOT send multiple verification emails
- AND it MUST use an idempotency key
- AND it MUST log duplicate detection

#### Scenario: Invitation trigger is post-commit

- GIVEN a standalone Invitation transaction commits
- WHEN the approved invitation trigger reaches the consumer
- THEN the consumer MUST create or reuse one Notifications delivery for its command key
- AND it MUST NOT run as a consequence of a rolled-back transaction

#### Scenario: Invitation trigger is token-safe

- GIVEN an invitation delivery is handled through DALLAY-566's approved ephemeral boundary
- WHEN the consumer persists delivery state or emits observability output
- THEN it MUST retain only non-secret correlation and operational data
- AND it MUST NOT emit a reverse delivery-state event to Invitation

## Usage

Consumers will implement `EventConsumer` and handle `UserRegistered` for verification emails as before.
Invitation-specific consumption will use `InvitationNotificationRequested` from the approved post-commit
handoff; retries for transient failures remain deferred to a future implementation.

## Troubleshooting

- If invitation emails are not sent, verify the post-commit handoff published
  `InvitationNotificationRequested` and that the consumer owns the command-key idempotency.
- Durable retry, outbox, and manual resend UI are out of scope for this delta.

## References

- `openspec/changes/dallay-565/specs/invitation-notification-delivery/spec.md`
- `openspec/changes/dallay-565/design.md`
