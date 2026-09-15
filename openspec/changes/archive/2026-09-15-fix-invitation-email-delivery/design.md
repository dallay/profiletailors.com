# Design: Reliable Direct Invitation Email Delivery

## Technical Approach

Keep invitation mutation in `platformadmin` and delivery in `notifications`. Create and resend handlers own an explicit `AtomicTransactionRunner` (`TransactionalOperator`) boundary. Inside the transaction: target validation, required workspace-name lookup, invitation mutation, audit write, and transactional event registration. Any failure rolls back all writes and registration. After commit, `SendInvitationEmailConsumer` receives the event through `@TransactionalEventListener(AFTER_COMMIT)` with fallback disabled, creates the notification, and attempts the provider. Provider failure never rolls back the invitation.

## Architecture Decisions

### Decision: Scoped reactive event publication

**Choice**: Add `InvitationEventPublisher` to `platformadmin` application contracts and `SpringTransactionalInvitationEventPublisher` to platformadmin infrastructure. The adapter uses Spring `TransactionalEventPublisher.publishEvent(Function<TransactionContext, ApplicationEvent>)`, publishes the invitation event with its reactive transaction context, and awaits the returned `Mono`.

**Alternatives rejected**: The global `SpringDomainEventPublisher` and synchronous `EventEmitter` could change unrelated consumers or publish before commit. Explicit post-commit publication has a commit-to-publish gap. An outbox is durable but outside this change's scope.

**Rationale**: The application depends on a port, infrastructure owns Spring, and event registration remains part of the same reactive transaction.

### Decision: Ownership and transaction boundary

`platformadmin.domain` owns the invitation aggregate and direct events. `platformadmin.application` owns handlers and ports (`InvitationEventPublisher`, the existing tenancy workspace-name read port, audit, and repositories). `platformadmin.infrastructure` owns R2DBC/Spring adapters and composition-root wiring. `notifications.infrastructure` owns the after-commit consumer, notification persistence, and email dispatch. Dependencies remain `domain <- application <- infrastructure`; no handler calls a notification adapter directly.

The controller is not transactional. A publisher failure rolls back invitation mutation, audit, and event registration. DALLAY-568's no-rollback guarantee applies only after commit: provider failure records `FAILED`, leaves the invitation `ACTIVE`, and never reopens the invitation transaction.

### Decision: Target-aware context and API errors

Within the operation, `EXISTING_WORKSPACE` calls `WorkspaceNameReader.findName(workspaceId)`. A null result raises the platform-admin workspace-not-found error and maps to HTTP `404 Not Found`; it aborts before commit and creates no invitation, audit, event, notification, or provider call. The email receives the resolved `workspaces.name`, never the ID or blank text. `NEW_WORKSPACE` keeps `workspaceId` absent and uses exactly: “You’ve been invited to create a new Profile Tailors workspace.” Existing invitation validation/error mappings remain unchanged.

## Data Flow

```text
Handler → AtomicTransactionRunner
  ├─ workspace lookup → invitation mutation → audit write
  └─ InvitationEventPublisher → TransactionalEventPublisher
             └─ commit → AFTER_COMMIT consumer → PENDING → provider → SENT/FAILED
```

Initial delivery uses `invitation:{invitationId}:initial`. Each intentional resend receives a new `deliveryId` and uses `invitation:{invitationId}:resend:{deliveryId}`. Unique-key handling makes replay idempotent, including `FAILED`: reuse the existing notification and do not call the provider. A later intentional resend gets a new key and a new attempt. There is no automatic retry for the same failed key; operational retry is an intentional resend.

The raw token remains transient handler → event → consumer data only. It must never be persisted, logged, audited, or included in metrics. DALLAY-566 owns the token-safe follow-up.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/{CreateInvitationHandler,ResendInvitationHandler}.kt` | Modify | Run the complete flow atomically; resolve labels and register events inside the transaction. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/InvitationEventPublisher.kt` | Create | Application port for transactional invitation event registration. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/events/SpringTransactionalInvitationEventPublisher.kt` | Create | Spring reactive publisher adapter. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/{http/AdminInvitationController.kt,http/AdminProblemDetailsHandler.kt,PlatformAdminBootstrapConfiguration.kt}` | Modify | Remove controller transaction annotations, map workspace-not-found to 404, and wire ports. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` and `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/InvitationEmail.kt` | Modify | Consume committed direct events and render target-aware copy. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/PlatformAdminInvitationTransactionPostgresIntegrationTest.kt` | Modify | Full-wiring commit, rollback, 404, idempotency, and provider-failure regression matrix. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumerTest.kt` | Modify | Verify copy, keys, replay, and `FAILED` behavior. |

## Testing Strategy

Use deterministic PostgreSQL/R2DBC tests with the real transaction runner, publisher adapter, event listener, notification repository, and a recording/failing email dispatcher. Cover committed create/resend, publisher-failure rollback, unresolved-workspace 404 with no writes, provider failure with `ACTIVE` invitation plus `FAILED` notification, replay without duplicate/provider call, and distinct initial/resend keys. Keep handler, controller, consumer, and architecture tests aligned with these contracts.

## Migration / Rollout

No schema migration, outbox, feature flag, or waitlist-flow change is required. Review `docs/architecture/transaction-policy.md`; no ADR change is required because this applies the accepted reactive transaction policy without changing the global publisher. Update only this change's OpenSpec artifacts; do not modify DALLAY-567 QA artifacts. Operationally monitor committed notification counts, `SENT`/`FAILED` transitions, provider attempts, and 404 workspace lookup failures without exposing tokens or full email addresses.

## Open Questions

None.
