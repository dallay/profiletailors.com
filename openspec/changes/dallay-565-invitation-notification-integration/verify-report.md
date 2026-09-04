# Verification Report: Integrate invitation creation with notification delivery

## Status

PASS WITH WARNINGS

## Evidence

| Check | Result |
|---|---|
| `./gradlew :server:smp:compileKotlin --no-daemon --console=plain` | PASS — `BUILD SUCCESSFUL` |
| `./gradlew :server:smp:compileTestKotlin --no-daemon --console=plain` | PASS — `BUILD SUCCESSFUL` |
| Focused SMP tests for `SendInvitationEmailConsumer`, `InvitationIssued`, `InviteWaitlistEntryHandler`, and `ResendWaitlistInvitationHandler` | PASS — `BUILD SUCCESSFUL` |
| `./gradlew :shared:notifications:test --tests '*InvitationEmailTest*' --no-daemon --console=plain` | PASS — `BUILD SUCCESSFUL` |
| `./gradlew :server:smp:detekt --no-daemon --console=plain` | PASS — `BUILD SUCCESSFUL` |
| `./gradlew :server:smp:test --tests '*PlatformAdminInvitationTransactionPostgresIntegrationTest*' --no-daemon --console=plain` | PASS — `BUILD SUCCESSFUL` |
| `./gradlew :server:smp:test --tests '*Invitation*' --no-daemon --console=plain` | NOT PASS — two unrelated PostgreSQL repository tests could not connect to a stopped Testcontainers instance after the targeted integration run |
| AFTER_COMMIT integration test | PARTIAL — listener annotation is verified by a focused test; a real commit/rollback integration test remains pending |

## Requirement Traceability

- Invitation creation publishes `InvitationIssued`: verified by `InviteWaitlistEntryHandlerTest`.
- Consumer subscribes with `@TransactionalEventListener(phase = AFTER_COMMIT)`: verified by source inspection and a focused annotation test in `SendInvitationEmailConsumerTest`.
- Notification creation, dispatch success, dispatch failure, idempotency, and URL-template usage: verified by `SendInvitationEmailConsumerTest`.
- Raw token is excluded from `NotificationPayload`: verified by `SendInvitationEmailConsumerTest`.
- Invitation lifecycle delivery-state removal: NOT IMPLEMENTED in this slice. `deliveryStatus` and `InvitationDeliveryAttempted` remain for the planned Phase 3 change.

## Warnings and Blockers

1. The current `InvitationIssued` event still carries an in-memory `rawToken` field. This is needed by the existing in-process delivery path, but it is not a genuinely token-free event contract. The field must be replaced with an explicit secure handoff before claiming the full DALLAY-565 security requirement.
2. A dedicated production integration test that exercises a real commit/rollback boundary is still not present; the focused suite verifies the listener annotation and dispatch behavior.
3. Phase 3 intentionally remains pending: invitation `deliveryStatus`, reverse delivery event, persistence migration, and related tests have not been removed.
4. Full invitation-suite execution was not clean because two PostgreSQL repository tests failed to connect to a stopped Testcontainers port; this is environment evidence, not a product failure diagnosis.

## Recommended Next Action

Do not archive this change yet. Correct the token handoff, URL-template ownership, and exact idempotency contract; then add and run a dedicated post-commit integration test before starting the Phase 3 removal slice.
