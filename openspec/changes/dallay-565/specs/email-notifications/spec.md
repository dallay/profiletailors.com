# Delta for Email Notifications

## Overview

This delta narrows invitation email triggers to an identity-only, post-commit handoff while keeping
Notifications responsible for delivery state and token-safe persistence.

## Changes

**MODIFIED Requirements**

### Requirement: Domain Event Consumer for Email Notifications

The system MUST consume domain events to trigger email notifications. The system MUST implement the
`EventConsumer` interface, handle consumption failures gracefully, and support retry logic for
transient failures. It MUST consume `UserRegistered` and dispatch its verification email as before.
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
- AND it MUST retry transient failures
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

Use these scenarios to verify event-consumer behavior, transient retries, post-commit timing,
idempotency, delivery ownership, and token-safe persistence.

## Troubleshooting

DALLAY-566 remains the hard gate for the ephemeral token handoff. A missing handoff contract blocks
invitation delivery implementation but does not weaken the mandatory transient-retry requirement.

## References

- [DALLAY-565 design](../../design.md)
- [Invitation notification delivery delta](../invitation-notification-delivery/spec.md)
- [Email notifications source specification](../../../../specs/email-notifications/spec.md)
