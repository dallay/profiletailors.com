## Exploration: Expose invitation notification delivery for admin operations while separating Invitation and Notification bounded contexts

### Current State

The repository does not contain the prior session's claimed DALLAY-565 artifacts or partial implementation. The active directory `openspec/changes/dallay-565/` contains only the untracked `state.yaml`; it has no `exploration.md`, proposal, delta specs, design, or tasks. `git ls-tree -r HEAD -- openspec/changes/dallay-565` and the all-branch history for that path are empty, and there is no current or historical `InvitationIssued` source file. The memory references to `openspec/changes/dallay-565-invitation-notification-integration/` and `InvitationIssued` therefore describe work that is not present in this worktree or reachable git history. The only uncommitted repository item is the existing untracked DALLAY-565 state directory; it is preserved.

The invitation email integration that is present was introduced by `09168d86` and later adjusted by invitation-email commits such as `19287316`, `a9055f20`, `5c830d1d`, `d09a5220`, and `ed469186`. The current flow is:

1. `AdminWaitlistController` invokes `InviteWaitlistEntryHandler` inside a controller-level `@Transactional` method.
2. `InviteWaitlistEntryHandler` persists a `WaitlistInvitation`, including `deliveryStatus`, `lastDeliveryAttemptAt`, and `deliveryAttemptCount`, updates the waitlist entry, writes an admin audit event, and publishes `InvitationCreated` with the raw token and accept URL.
3. `SendInvitationEmailConsumer` is registered through the shared `@Subscribe` event emitter. It persists a `Notification`, dispatches the email immediately, updates the notification outcome, and publishes `InvitationDeliveryAttempted`.
4. `UpdateInvitationDeliveryOnNotificationAttempted` consumes that outcome in `platformadmin` and writes the delivery outcome back into the invitation aggregate. The admin invitation and waitlist-detail read models expose those invitation-owned delivery fields.
5. Resend follows the same pattern through `InvitationResent`; it supersedes the existing waitlist invitation and creates a new invitation identifier, so the current idempotency key is per new invitation rather than per stable logical resend chain.

The first-class `platformadmin.domain.Invitation` model already represents invitation validity without delivery fields, but the waitlist invite/resend flow still uses the older `WaitlistInvitation` model. This leaves two invitation representations in the same bounded context. The architecture documentation already names Platformadmin and Notifications as separate bounded contexts (`docs/architecture/c4/03-component.md`, `docs/architecture/c4/04-code.md`), and `server/smp/notifications/ModuleMetadata.kt` documents Notifications as a hybrid context whose reusable domain/application code lives in `shared/notifications`.

The product contract makes the admin surface operator-only and explicitly excludes email sending from the admin SPA (`apps/web/admin/PRODUCT.md`). The backend change should therefore expose reliable operational delivery data through backend contracts, not move provider delivery into the admin frontend. `openspec/specs/email-notifications/spec.md` covers generic event-driven email, idempotency, failures, and visibility, but no invitation-specific delta spec exists. `openspec/specs/private-beta-launch-readiness/spec.md` requires invite delivery, operator-visible status, failure handling, and a manual fallback as part of the broader beta gate.

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

Use Approach 1 as the candidate direction, but do not create a proposal until the contract is clarified. The current implementation has enough evidence to identify the architectural defect, but not enough product detail to define a safe migration: the admin API already exposes delivery fields while the issue says delivery belongs to Notifications, and the code has both a first-class `Invitation` aggregate and a legacy `WaitlistInvitation` flow.

The next phase needs explicit answers to these questions:

- Is DALLAY-565 limited to the existing waitlist invitation flow, or must it first switch the flow to the first-class `Invitation` model from DALLAY-564?
- What exactly must admin operations receive: the existing invitation summary fields, a joined delivery summary, a separate notification identifier, or only a query seam for DALLAY-574? Which status values and timestamps are contractual?
- May the raw token or token-bearing accept URL be retained in a persisted notification payload? Current `InvitationEmailTest` protects against a separate raw-token field, but `R2dbcNotificationRepository` still persists the accept URL in JSONB.
- Does resend create a new invitation identity, as the current handler does, or reuse one logical invitation identity? This determines correlation and idempotency semantics.
- Is “after successful persistence” a hard post-commit guarantee, and is an in-memory Spring transaction listener acceptable, or is durable outbox behavior required? The current event publisher fans out to both Spring and the legacy event emitter, while `docs/architecture/transaction-policy.md` requires explicit reactive transaction handling.
- Which component owns failure visibility, manual fallback, and any retry authority between DALLAY-565 and the blocked DALLAY-574 work?

### Risks

- Removing delivery columns from `waitlist_invitations` is a destructive schema and read-contract change unless existing rows, admin responses, and rollback behavior are handled explicitly.
- The current event path carries `rawToken`, and the current notification payload stores a token-bearing accept URL; logs, audit events, persistence, and event serialization need separate security guarantees.
- Publishing through `@Subscribe` currently permits dispatch during the invitation transaction. A naïve annotation change may either send before rollback or lose an event after commit; the dual Spring/EventEmitter publisher makes the listener path non-obvious.
- Notification idempotency is currently keyed by invitation ID, while resend creates a new invitation ID. Changing either side can cause duplicate sends or suppress a legitimate resend.
- The current test suite has strong unit and PostgreSQL coverage for the existing coupling but no invitation/notification Cucumber feature was found, and no test proves that rollback suppresses scheduling while commit enables it.
- DALLAY-565 is blocked in Linear by DALLAY-564 and blocks DALLAY-570 and DALLAY-574; choosing the wrong invitation model or API seam can create rework across those changes.
- The active worktree has an unrelated `settings` branch tip and the untracked DALLAY-565 state directory; no production code or unrelated worktree changes were modified during this exploration.

### Ready for Proposal

No — clarification is required before `sdd-propose`. The architectural candidate is clear, but canonical invitation ownership, admin delivery contract, token persistence, resend identity/idempotency, and post-commit durability are unresolved. Once those decisions are recorded, the orchestrator can safely run the proposal phase; until then, do not infer the missing proposal/design/implementation from prior session memory.
