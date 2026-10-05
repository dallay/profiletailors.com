## Exploration: fix-invitation-email-delivery

### Current State

Direct invitation creation and resend are exposed by `AdminInvitationController` and delegated to
`CreateInvitationHandler` and `ResendInvitationHandler`. The handlers persist the invitation and
publish `InvitationIssued` or `DirectInvitationResent` through `SpringDomainEventPublisher`.

`SpringDomainEventPublisher` synchronously sends each event to Spring's plain
`ApplicationEventPublisher` and the legacy `EventEmitter`. It does not use a reactive-aware
transactional event publisher or attach a reactive transaction context to the event. The controller
methods use `@Transactional`, while the repository policy requires explicit `TransactionalOperator`
boundaries for reactive multi-write operations.

`SendInvitationEmailConsumer` listens to all three invitation event types with
`@TransactionalEventListener(phase = AFTER_COMMIT)`. Once invoked, it builds `InvitationEmail`,
checks `invitation:{id}:initial`, saves a `PENDING` notification, dispatches through
`IdentityEmailDispatcher`, and updates the notification to `SENT` or `FAILED`. Existing tests call
the consumer methods directly and verify annotations; they do not exercise the real Spring event
and reactive transaction wiring.

Production evidence shows two direct invitations persisted successfully, including one
`NEW_WORKSPACE` invitation, but no matching notification rows and no invitation messages in Resend.
This places the observed failure before or during notification creation, not at the provider's final
delivery boundary.

There is also a target-specific defect: creation and resend derive `workspaceName` from nullable
`workspaceId`. `NEW_WORKSPACE` produces an empty name, which `InvitationEmail` rejects. For an
existing workspace the current value is an ID rather than a human-readable workspace name.

The current implementation conflicts with older DALLAY-565 documentation: the event carries an
in-memory raw token and `NotificationPayload` stores the token-bearing `acceptUrl`, while the
canonical invitation specification forbids raw tokens and token-bearing URLs in observable or
durable state. Resend also currently derives the same `:initial` idempotency key as creation.

### Affected Areas

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationController.kt` —
direct create/resend methods establish the current declarative transaction boundary.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandler.kt` —
persists direct invitations and publishes `InvitationIssued` with nullable workspace-name handling.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/ResendInvitationHandler.kt` —
rotates token material and publishes `DirectInvitationResent`.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/bus/SpringDomainEventPublisher.kt` —
event fan-out and exception handling; both channels currently catch and log failures.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` —
post-commit listener, notification persistence, idempotency, and dispatch.

-

`shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/InvitationEmail.kt` —
workspace-name validation, accept URL construction, payload, and idempotency contract.

-

`server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/persistence/R2dbcNotificationRepository.kt` —
notification persistence evidence boundary.

- `server/smp/src/main/kotlin/com/profiletailors/smp/config/PersistenceConfig.kt` and
  `docs/architecture/transaction-policy.md` — authoritative reactive transaction pattern.
-

`shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/bus/event/EventConfiguration.kt`
and `EventEmitter.kt` — working `@Subscribe` comparison path.

-

`server/smp/src/test/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumerTest.kt`
and invitation handler/integration tests — current coverage gaps and regression targets.

- `openspec/changes/dallay-565-invitation-notification-integration/` — notification contract and
  explicitly pending real commit/rollback proof.
- `openspec/changes/dallay-568-direct-invitation-admin-commands/` — active direct invitation owner
  whose scope depends on this delivery seam.
- `openspec/changes/dallay-567-accept-invitations-registration-flow/` — unrelated blocked QA change;
  its artifacts must remain untouched.

### Approaches

1. **Reactive-aware transactional event publication** — preserve the existing `AFTER_COMMIT`
   consumer, but publish with the reactive transaction context and enforce a real
   `TransactionalOperator` boundary.
    - Pros: smallest behavioral change; preserves post-commit semantics and avoids a schema
      migration.
    - Cons: couples the infrastructure seam to Spring reactive event APIs; still loses work after a
      process crash unless a durable queue is added; token and resend contracts remain separate
      decisions.
    - Effort: Medium

2. **Explicit transaction completion followed by publication** — make the composition root own the
   reactive transaction, then publish the event only after the transaction completes successfully.
    - Pros: transaction timing is explicit and easier to test; avoids relying on listener context
      propagation.
    - Cons: requires reshaping handler/composition boundaries and defining failure behavior between
      commit and publication; still does not provide durable retry.
    - Effort: Medium

3. **Transactional notification/outbox record** — persist a notification or outbox entry in the same
   transaction as the invitation, then dispatch it with a worker and retry policy.
    - Pros: survives process crashes, supports observable retries, and gives a durable handoff.
    - Cons: adds schema, worker, operational monitoring, and idempotent provider-delivery design;
      larger than the currently evidenced defect.
    - Effort: High

4. **Reuse the synchronous `@Subscribe` bus** — route invitation events through the already-working
   custom emitter instead of the transactional listener.
    - Pros: minimal framework change and familiar existing path.
    - Cons: sends before commit, can deliver an invitation that later rolls back, and contradicts
      the documented post-commit contract.
    - Effort: Low

### Recommendation

Treat this as a new cross-cutting reliability change rather than modifying DALLAY-567. DALLAY-568
owns the direct endpoints but is already in its apply/QA lifecycle; DALLAY-565 owns the notification
seam but explicitly deferred real commit/rollback verification. A focused owner is therefore needed
for the shared failure without mutating either existing QA artifact.

Before implementation, add a failing full-wiring probe that:

1. publishes through the real `SpringDomainEventPublisher` inside a real reactive transaction;
2. proves no listener, notification row, or provider call occurs before commit;
3. proves commit produces exactly one notification and one provider attempt;
4. proves rollback produces neither;
5. covers both `EXISTING_WORKSPACE` and `NEW_WORKSPACE`; and
6. covers create, resend, duplicate delivery, and failed dispatch.

If that probe confirms missing reactive transaction context, Option 1 is the preferred first slice.
If the product requires crash recovery or guaranteed eventual delivery, escalate to Option 3
instead.
Workspace naming, raw-token handoff, and resend idempotency must be explicit proposal decisions
rather
than inferred from the current implementation.

### Risks

- `@Transactional` on final Kotlin `suspend` controller paths may not establish the reactive context
  required by `@TransactionalEventListener`.
- A missing reactive transaction context can silently discard the listener, matching the
  zero-notification production evidence.
- `NEW_WORKSPACE` currently reaches email construction with a blank workspace name;
  existing-workspace emails use an ID as display text.
- `SpringDomainEventPublisher` catches and logs failures independently, which can hide event-channel
  failure from callers and weaken the documented publish-failure contract.
- Raw token and token-bearing URL handling contradict the canonical invitation security contract and
  needs separate ownership review.
- Creation and resend currently share the `invitation:{id}:initial` idempotency key, so a fixed
  listener may still suppress resend delivery.
- `AFTER_COMMIT` in-process delivery is not crash durable; an outbox is required if eventual
  delivery across process failure is a product requirement.
- Existing DALLAY-565/DALLAY-568 artifacts contain stale or contradictory event and token
  assumptions; downstream proposal/spec work must reconcile them without rewriting historical
  verification evidence.

### Ready for Proposal

Yes — a proposal can be drafted for the focused delivery-reliability change, with the full-wiring
probe
as its first acceptance gate. The proposal must state whether the scope is limited to reactive
post-commit publication or also resolves workspace naming, token handoff, resend idempotency, and
crash recovery.
