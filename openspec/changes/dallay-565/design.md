# Design: Invitation Notification Delivery for Admin Operations

## Overview

DALLAY-564 lands first and owns new `Invitation` lifecycle writes. DALLAY-565 connects that aggregate
to Notifications through a token-free request after the explicit R2DBC transaction commits. Notifications
owns delivery records; Platformadmin composes operator reads from Invitation and a narrow Notifications
summary. No aggregate instance crosses the boundary; correlation is by `InvitationId`.

## Changes

### Architecture Decisions

| Decision | Choice | Alternatives rejected | Rationale |
|---|---|---|---|
| Canonical model | DALLAY-564 `Invitation`; resend reuses its ID | `WaitlistInvitation`; replacement ID per resend | Validity stays independent of transport and downstream work gets stable correlation. |
| Commit seam | Invoke the scheduler after `AtomicTransactionRunner` returns successfully | Publish inside transaction; outbox now | Rollbacks cannot schedule; the process-crash gap is explicit and an outbox is not claimed. |
| Delivery owner | Notifications owns status, timestamps, errors, and delivery count | Reverse outcome bridge; duplicate Invitation fields | One owner prevents divergent operational state. |
| Idempotency | `platform.invitation:{invitationId}:{commandId}` with a database uniqueness constraint | Invitation-only key | Repeating one command reuses one delivery; a new resend command adds one delivery for the same Invitation. |
| Admin read | Explicit Notifications summary port keyed by ID | Cross-context table join; generic notification admin API | Preserves hexagonal boundaries and keeps DALLAY-574 out of scope. |

### Event, Command, and Token Boundary

Platformadmin receives a validated `commandId` from the admin `Idempotency-Key` contract and exposes
`InvitationNotificationScheduler`. Its adapter publishes token-free `InvitationNotificationRequested`
through the existing `EventPublisher<DomainEvent>` after commit. Notifications maps it to an internal
command, claims the key through `NotificationRepository`, dispatches via `EmailDispatcher`, and records
`SENT` or `FAILED`. Remove the old reverse outcome event and bridge.

The request contains only `invitationId`, `commandId`, and `INITIAL`/`RESEND`. It contains no
recipient, workspace name, locale, Invitation object, token hash, raw token, or token-bearing URL.
Notifications resolves recipient, workspace, and locale from the owning context using `invitationId`.
Persisted `Notification.payload` contains safe metadata only; `InvitationEmail` must not persist a
token-bearing link.

Chosen DALLAY-566 boundary: raw token material may exist only in an approved ephemeral handoff immediately
before rendering and `EmailDispatcher.dispatch`, then is discarded. DALLAY-566 owns generation, rotation,
TTL, validation, recipient binding, URL assembly, envelope, and encoding. This design invents none of them.

### Data Flow

```text
DALLAY-564 command -> AtomicTransactionRunner -> commit
  -> after-commit scheduler -> token-free request event
  -> Notifications command -> unique pending Notification
  -> DALLAY-566 ephemeral handoff -> EmailDispatcher -> SENT/FAILED

Admin read -> Invitation reader + Notifications summary port -> compatible response
```

### Interfaces / Contracts

```kotlin
data class InvitationNotificationRequested(
    val invitationId: UUID,
    val commandId: String,
    val kind: InvitationDeliveryKind,
)

interface InvitationDeliverySummaryReader {
    suspend fun summarize(invitationId: UUID): InvitationDeliverySummary
}
```

`InvitationDeliverySummary` exposes zero-or-many count, latest status, and latest created/sent/failed
timestamps; it never exposes payload or provider content. Duplicate commands return the existing record
without another provider call. A new resend key adds one delivery and does not mint an Invitation.

### File Changes

| File | Action | Description |
|---|---|---|
| `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationNotificationRequested.kt` | Create | Token-free integration event and delivery kind. |
| `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/Notification.kt`, `NotificationRepository.kt`, `InvitationEmail.kt` | Modify | Add Invitation/command correlation and safe ephemeral rendering semantics. |
| `shared/notifications/src/main/kotlin/com/profiletailors/notifications/application/ports/InvitationDeliverySummaryReader.kt` | Create | Notifications-owned read contract. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` | Modify | Consume the new request; remove raw-token and reverse-update behavior. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/persistence/R2dbcNotificationRepository.kt`, `NotificationsSchemaInitializer.kt` | Modify | Store correlation fields and indexed summary data using existing startup DDL. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt`, `ResendWaitlistInvitationHandler.kt`, `AdminCommands.kt` | Modify | Use DALLAY-564 Invitation, command IDs, and post-commit scheduling; never write delivery fields. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcAdminInvitationQuery.kt`, `AdminInvitationSummary.kt`, `AdminInvitationController.kt` | Modify | Compose canonical lifecycle data with Notifications and preserve compatible legacy reads. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/events/UpdateInvitationDeliveryOnNotificationAttempted.kt` and `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/{InvitationCreated,InvitationResent,InvitationDeliveryAttempted}.kt` | Delete/replace | Remove reverse ownership and raw-token event contracts. |

### Testing Strategy

Unit tests cover Notification transitions, same-key reuse, distinct resend keys, active Invitation after
failure, and token-free serialization. PostgreSQL tests cover commit versus rollback, unique-key races,
zero/many summaries, and legacy reads. WebFlux/security tests cover permissions, compatible responses,
masked logs, and token absence. Cucumber `@smoke`/`@fast` scenarios cover initial delivery, provider
failure independence, duplicate commands, and resend multiplicity. Existing architecture and Modulith
tests remain the boundary checks.

### Migration / Rollout

DALLAY-564 owns the `invitations` Liquibase schema. Extend existing Notifications startup DDL only for
correlation columns/indexes; do not claim a migration system or outbox. Retain `waitlist_invitations`, its
delivery columns, and historical `R2dbcAdminWaitlistQuery` reads. New flows write neither legacy fields
nor rows. No backfill, redaction, or destructive removal is authorized. DALLAY-519 remains separate.

## Usage

The admin invitation list/detail endpoints consume `InvitationDeliverySummaryReader` by Invitation ID
and compose it with DALLAY-564 Invitation lifecycle data. Notifications consumers use
`InvitationNotificationRequested` to create or update a delivery record keyed by `commandId`.

## Troubleshooting

- DALLAY-566 must approve the concrete ephemeral token handoff before implementation crosses it.
- Confirm whether the admin response exposes all latest timestamps or only the summary contract.
- A lost after-commit callback is an explicit best-effort gap; durable outbox is deferred to future work.

## References

- `openspec/changes/dallay-565/specs/invitation-notification-delivery/spec.md`
- `openspec/changes/dallay-565/specs/email-notifications/spec.md`
- ADR-0002 (hexagonal architecture) and ADR-0016 (identity-only aggregate communication)
