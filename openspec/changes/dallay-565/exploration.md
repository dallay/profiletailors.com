# Exploration: Expose invitation notification delivery for admin operations while separating Invitation and Notification bounded contexts

## Overview

### Historical Discovery State

At the start of exploration on 2026-09-01, the repository did not contain the prior session's
claimed DALLAY-565 artifacts or partial implementation. The active directory contained only an
untracked `state.yaml`; it had no exploration, proposal, delta specs, design, or tasks. That
historical absence was resolved later in the same change: the proposal, design, delta specs, tasks,
application evidence, QA report, and verification report now exist, and `state.yaml` records
`current_phase: qa-unit-1`.

The invitation email integration that is present was introduced by `09168d86` and later adjusted by invitation-email commits such as `19287316`, `a9055f20`, `5c830d1d`, `d09a5220`, and `ed469186`. The current flow is:

1. `AdminWaitlistController` invokes `InviteWaitlistEntryHandler` inside a controller-level `@Transactional` method.
2. `InviteWaitlistEntryHandler` persists a `WaitlistInvitation`, including `deliveryStatus`, `lastDeliveryAttemptAt`, and `deliveryAttemptCount`, updates the waitlist entry, writes an admin audit event, and publishes `InvitationCreated` with the raw token and accept URL.
3. `SendInvitationEmailConsumer` is registered through the shared `@Subscribe` event emitter. It persists a `Notification`, dispatches the email immediately, updates the notification outcome, and publishes `InvitationDeliveryAttempted`.
4. `UpdateInvitationDeliveryOnNotificationAttempted` consumes that outcome in `platformadmin` and writes the delivery outcome back into the invitation aggregate. The admin invitation and waitlist-detail read models expose those invitation-owned delivery fields.
5. Resend follows the same pattern through `InvitationResent`; it supersedes the existing waitlist invitation and creates a new invitation identifier, so the current idempotency key is per new invitation rather than per stable logical resend chain.

The first-class `platformadmin.domain.Invitation` model already represents invitation validity without delivery fields, but the waitlist invite/resend flow still uses the older `WaitlistInvitation` model. This leaves two invitation representations in the same bounded context. The architecture documentation already names Platformadmin and Notifications as separate bounded contexts (`docs/architecture/c4/03-component.md`, `docs/architecture/c4/04-code.md`), and `server/smp/notifications/ModuleMetadata.kt` documents Notifications as a hybrid context whose reusable domain/application code lives in `shared/notifications`.

The product contract makes the admin surface operator-only and explicitly excludes email sending from the admin SPA (`apps/web/admin/PRODUCT.md`). The backend change should therefore expose reliable operational delivery data through backend contracts, not move provider delivery into the admin frontend. At discovery time, `openspec/specs/email-notifications/spec.md` covered generic event-driven email, idempotency, failures, and visibility, but no invitation-specific delta existed; this change now supplies that delta. `openspec/specs/private-beta-launch-readiness/spec.md` requires invite delivery, operator-visible status, failure handling, and a manual fallback as part of the broader beta gate.

## Changes

### Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt` — owns waitlist invitation creation, raw-token event construction, audit ordering, and the current invitation-owned delivery state.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/ResendWaitlistInvitationHandler.kt` — supersedes the old invitation, creates a new one, and publishes the resend delivery trigger; its logical invitation and idempotency semantics need an explicit decision.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/WaitlistInvitation.kt` and `server/smp/src/main/resources/db/changelog/platform-admin/002-create-waitlist-invitations.yaml` — model and persist delivery state inside the invitation aggregate; removing or retaining these columns is a compatibility decision.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcWaitlistInvitationRepository.kt` — reads and writes the delivery columns and is the persistence boundary that would change if Notifications becomes the owner.
- `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` — currently consumes `@Subscribe` events synchronously, dispatches email, and sends the cross-context delivery-outcome event; it is the primary separation and post-commit seam.
- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationCreated.kt`, `InvitationResent.kt`, and `InvitationDeliveryAttempted.kt` — current event contracts carry or imply raw-token and invitation-delivery coupling; their ownership, payload sensitivity, and replacement compatibility need definition.
- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/InvitationEmail.kt` and `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/persistence/R2dbcNotificationRepository.kt` — render and persist the invitation accept URL, which currently embeds the raw token, and provide the Notifications-owned delivery record/idempotency boundary.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/events/UpdateInvitationDeliveryOnNotificationAttempted.kt` — writes notification outcomes back into Platformadmin and directly embodies the coupling the change is intended to remove.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationController.kt`, `AdminInvitationSummary.kt`, and `R2dbcAdminWaitlistQuery.kt` — current admin API/read models expose `deliveryStatus` and `deliveryAttemptCount` from `waitlist_invitations`; the replacement exposure contract is not specified.
- `server/smp/src/test/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumerTest.kt` — asserts the old `InvitationCreated`/`InvitationResent` consumers and `InvitationDeliveryAttempted` publication, so it will need a contract-level rewrite after the decision is made.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/events/UpdateInvitationDeliveryOnNotificationAttemptedTest.kt` — locks in the current reverse bridge and will become obsolete or change to a Notifications-owned query/read-model test.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandlerTest.kt`, `ResendWaitlistInvitationHandlerTest.kt`, `R2dbcWaitlistInvitationRepositoryPostgresIntegrationTest.kt`, and `PlatformAdminInvitationTransactionPostgresIntegrationTest.kt` — assert raw-token event payloads, `PENDING` invitation delivery fields, delivery-column persistence, lifecycle transitions, and transaction behavior; they do not prove a post-commit invitation-to-notification handoff.
- `server/smp/src/test/kotlin/com/profiletailors/smp/notifications/infrastructure/persistence/R2dbcNotificationRepositoryPostgresTest.kt` and `shared/notifications/src/test/kotlin/com/profiletailors/notifications/domain/InvitationEmailTest.kt` — establish the current Notification idempotency/persistence and invitation-template security behavior, including the fact that the accept URL is retained in the notification payload.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationControllerTest.kt` — covers the admin invitation endpoints but only verifies invitation-summary fields, not delivery state sourced from Notifications.
- `docs/architecture/adr/0001-use-a-modular-monolith-backend.md`, `0002-adhere-to-hexagonal-architecture.md`, `0015-aggregate-root-as-sole-entry-point.md`, `0016-aggregates-communicate-by-identity-only.md`, and `docs/architecture/transaction-policy.md` — require package-isolated contexts, event/API seams, aggregate boundaries, identity-only communication, and explicit reactive transaction handling.
- `openspec/specs/email-notifications/spec.md`, `openspec/specs/private-beta-launch-readiness/spec.md`, `apps/web/PRODUCT.md`, and `apps/web/admin/PRODUCT.md` — current product and capability constraints; no main invitation-specific specification exists.

### Approaches

1. **Narrow event seam with Notifications-owned delivery and an admin read contract** — Keep invitation lifecycle and notification delivery as separate owners. Platformadmin emits a token-safe invitation-issued/resend trigger after a successful transaction; Notifications creates and updates its own delivery record; the admin query composes lifecycle data with a narrowly defined delivery summary through an explicit cross-context contract. Defer generic notification search and retry operations to DALLAY-574.
   - Pros: directly satisfies the requested separation; preserves operator visibility without putting provider behavior in the admin SPA; gives DALLAY-574 a stable seam; limits scope to the invitation integration.
   - Cons: requires a decision about the canonical invitation model, a safe token handoff, post-commit reliability, read-side composition, and migration/backward compatibility for existing `waitlist_invitations` rows.
   - Effort: High

2. **Compatibility bridge with post-commit dispatch while retaining invitation delivery columns** — Change the consumer to run after commit and keep the existing `WaitlistInvitation.deliveryStatus` fields as a transitional read model, while gradually moving operational queries to Notifications.
   - Pros: smallest migration and lower immediate API disruption; existing admin responses and many tests remain usable.
   - Cons: delivery ownership remains duplicated; the reverse outcome bridge continues to couple contexts; stale or divergent status becomes possible; it does not fully meet the issue's explicit “delivery state in Notifications” rule.
   - Effort: Medium

3. **Durable notification outbox/schedule** — Persist a notification scheduling record atomically with invitation issuance, then dispatch it from an after-commit worker/outbox processor. Correlate delivery state to the invitation through an identity-only reference and query it from the admin side.
   - Pros: strongest crash/retry guarantees; durable post-commit handoff; clean ownership and a natural foundation for safe retries.
   - Cons: introduces worker/outbox lifecycle, retry policy, operational controls, and likely overlaps DALLAY-574 and DALLAY-519; substantially larger than the current issue's stated integration seam.
   - Effort: High

### Recommendation

Approach 1 is the selected direction. The resulting proposal and design use the standalone
`Invitation` as the canonical lifecycle model, keep delivery state in Notifications, correlate by
identity, and preserve legacy reads without new legacy writes.

The proposal and design record these decisions:

- New delivery flows use DALLAY-564's standalone `Invitation` and stable identity.
- Admin reads compose lifecycle data with a narrow Notifications summary.
- Durable events and payloads contain identity and operational data only; DALLAY-566 owns the
  unresolved ephemeral token handoff.
- Resends retain the Invitation identity and use a new command key.
- The initial scheduling seam is best-effort `AFTER_COMMIT`; an outbox remains out of scope.
- Notifications owns failure visibility, while generic retry operations remain with DALLAY-574.

## Usage

### Proposal Readiness

Yes — Approach 1 was selected and is represented by `proposal.md`, `design.md`, both delta specs,
and `tasks.md`. Unit 1 contract work has been technically verified with warnings, acceptance remains
`NOT TESTED`, and progression to unit 2 remains blocked on DALLAY-566.

## Troubleshooting

### Risks

- Removing delivery columns from `waitlist_invitations` is a destructive schema and read-contract change unless existing rows, admin responses, and rollback behavior are handled explicitly.
- The current event path carries `rawToken`, and the current notification payload stores a token-bearing accept URL; logs, audit events, persistence, and event serialization need separate security guarantees.
- Publishing through `@Subscribe` currently permits dispatch during the invitation transaction. A naïve annotation change may either send before rollback or lose an event after commit; the dual Spring/EventEmitter publisher makes the listener path non-obvious.
- Notification idempotency is currently keyed by invitation ID, while resend creates a new invitation ID. Changing either side can cause duplicate sends or suppress a legitimate resend.
- The current test suite has strong unit and PostgreSQL coverage for the existing coupling but no invitation/notification Cucumber feature was found, and no test proves that rollback suppresses scheduling while commit enables it.
- DALLAY-565 is blocked in Linear by DALLAY-564 and blocks DALLAY-570 and DALLAY-574; choosing the wrong invitation model or API seam can create rework across those changes.
- The active worktree had an unrelated `settings` branch tip and the untracked DALLAY-565 state
  directory during initial exploration; no unrelated worktree changes were modified.

## References

- [Proposal](proposal.md)
- [Design](design.md)
- [Invitation notification delivery specification](specs/invitation-notification-delivery/spec.md)
- [Change state](state.yaml)
