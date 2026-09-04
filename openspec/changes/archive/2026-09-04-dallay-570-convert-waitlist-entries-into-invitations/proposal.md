# Proposal: DALLAY-570 — Convert Waitlist Entries into Invitations

> **This proposal describes the corrected architecture. Previous version described a broken model.**
> **Review date**: 2026-09-03

## Problem Statement

`InviteWaitlistEntryHandler` creates `WaitlistInvitation` (stored in `waitlist_invitations` table).
`AcceptInvitationHandler` (and the registration gateway `InvitationRegistrationGatewayAdapter`) look up
`Invitation` (stored in `invitations` table). These are two different tables, so acceptance always
fails — the token is never found.

Additionally, requiring `workspaceId` on every `Invitation` is architecturally wrong for the waitlist
private-beta case: the user has no workspace at invite time. The workspace must be provisioned as part of
acceptance.

## Root Cause

Two aggregates (`Invitation`, `WaitlistInvitation`) for one semantic concept (an invitation to join
Profile Tailors). The canonical acceptance path reads `Invitation`; the waitlist creation path writes
`WaitlistInvitation`.

## Solution: InvitationTarget with Shared Orchestration

### 1. InvitationTarget — Two Onboarding Paths

Replace the mandatory `workspaceId` field with an `InvitationTarget` enum and nullable `workspaceId`:

```kotlin
@ValueObject
enum class InvitationTarget {
    EXISTING_WORKSPACE,
    NEW_WORKSPACE,
}

data class Invitation(
    val id: InvitationId,
    val source: InvitationSource,
    val sourceReferenceId: String?,
    val target: InvitationTarget,
    val workspaceId: String?,     // nullable: null for NEW_WORKSPACE while ACTIVE
    val invitedEmailNormalized: String,
    val tokenHash: String,
    val status: InvitationStatus,
    // ...
)
```

**Lifecycle-aware invariants:**

```kotlin
when (target) {
    InvitationTarget.EXISTING_WORKSPACE ->
        require(!workspaceId.isNullOrBlank())  // always required

    InvitationTarget.NEW_WORKSPACE ->
        when (status) {
            InvitationStatus.ACTIVE,
            InvitationStatus.EXPIRED,
            InvitationStatus.REVOKED ->
                require(workspaceId == null)  // workspace not yet provisioned
            InvitationStatus.ACCEPTED ->
                require(!workspaceId.isNullOrBlank())  // provisioned on accept
        }
}
```

The `accept()` method takes the resolved workspace ID as a parameter for `NEW_WORKSPACE`:

```kotlin
fun accept(at: Instant, principalId: String, resolvedWorkspaceId: String? = null): Invitation {
    require(isActive(at))
    val resolvedWsId = when (target) {
        InvitationTarget.EXISTING_WORKSPACE -> workspaceId
        InvitationTarget.NEW_WORKSPACE -> resolvedWorkspaceId
    }
    require(!resolvedWsId.isNullOrBlank()) { "NEW_WORKSPACE acceptance requires resolved workspace ID" }
    return copy(
        status = InvitationStatus.ACCEPTED,
        acceptedAt = at,
        acceptedPrincipalId = principalId,
        workspaceId = resolvedWsId,
        version = version + 1,
    )
}
```

**Why this model:**
- `source` = why/came from (WAITLIST vs DIRECT) — immutable
- `target` = what happens on accept (join existing workspace vs provision new one)
- Separation of concerns is good DDD

### 2. Shared Orchestration: InvitationActivationCoordinator

Both acceptance entry points must use the same orchestration:

| Entry point | Triggered by |
|---|---|
| `AcceptInvitationHandler` | Authenticated user clicks email link |
| `InvitationRegistrationGatewayAdapter` | New user completes registration form |

Neither should contain branching logic for `EXISTING_WORKSPACE` vs `NEW_WORKSPACE` directly.
Both delegate to `InvitationActivationCoordinator`:

```kotlin
class InvitationActivationCoordinator(
    private val invitationRepository: InvitationRepository,
    private val membershipProvisioner: WorkspaceMembershipProvisioner,
    private val workspaceProvisioningService: WorkspaceProvisioningService,
    private val waitlistEntryAdmin: WaitlistEntryAdmin,
    private val clock: Clock,
) {
    suspend fun activate(
        invitation: Invitation,
        principalId: String,
        displayName: String?,
    ): Invitation {
        val accepted: Invitation
        when (invitation.target) {
            InvitationTarget.EXISTING_WORKSPACE -> {
                membershipProvisioner.reconcile(invitation.workspaceId, principalId)
                accepted = invitation.accept(clock.instant(), principalId)
            }
            InvitationTarget.NEW_WORKSPACE -> {
                val provisioned = workspaceProvisioningService.provisionDefaultWorkspace(
                    principalId,
                    displayName ?: principalId,
                )
                if (invitation.source == InvitationSource.WAITLIST) {
                    val entry = waitlistEntryAdmin.findById(invitation.sourceReferenceId)
                        ?: throw IllegalStateException("Waitlist entry not found: ${invitation.sourceReferenceId}")
                    entry.convert(clock.instant())
                    waitlistEntryAdmin.save(entry)
                }
                accepted = invitation.accept(clock.instant(), principalId, provisioned.workspaceId)
            }
        }
        if (!invitationRepository.updateIfVersionMatches(accepted)) {
            throw OptimisticLockException("Invitation was modified concurrently")
        }
        return accepted
    }
}
```

**Transaction boundary**: The atomic transaction is managed by the caller
(`AcceptInvitationHandler` or `InvitationRegistrationGatewayAdapter`). Both use
`AtomicTransactionRunner` for registration.

### 3. Token/Notification Ownership (DALLAY-565/566)

DALLAY-565 defines post-commit token-free handoff:

```kotlin
InvitationNotificationRequested(
    invitationId,
    commandId,
    kind,
)
```

DALLAY-566 owns ephemeral raw-token generation and delivery handoff immediately before
rendering/dispatch.

**DALLAY-570 does NOT publish raw token in any event.** `InviteWaitlistEntryHandler` publishes
`InvitationIssued` only for internal audit; it does NOT carry the raw token. Notification delivery
goes through the DALLAY-565/566 contract.

### 4. Resend Is Not a New Invitation (DALLAY-565 Contract)

DALLAY-565 explicitly defines resend semantics: **resend reuses the same InvitationId** and creates
a new delivery command/record. It does NOT create a replacement Invitation.

Therefore DALLAY-570 does NOT model `SUPERSEDED` in the canonical `Invitation` aggregate.
If an entry is already `INVITED` with an active `Invitation`:
- **Option A**: Reject duplicate invite creation (current behavior in some paths)
- **Option B**: Route through explicit resend via DALLAY-565 contract

DALLAY-570 does NOT create a new `Invitation` on re-invite. The existing active
`Invitation` is used; a new notification delivery is issued.

### 5. WaitlistInvitation Is Legacy-Only

`WaitlistInvitation` and `WaitlistInvitationRepository` are **legacy compatibility models only**.
They MUST NOT be used for new waitlist invitation flows.

New flows use:
- `Invitation` with `source=WAITLIST`, `target=NEW_WORKSPACE` for invitation lifecycle
- Notifications for delivery lifecycle
- `WaitlistInvitation` only for historical records created before this migration

New code MUST NOT create or update `WaitlistInvitation` rows.

### 6. Database Migration

Current `invitations` table:
```yaml
workspace_id:
  type: varchar(64)
  nullable: false
  foreignKeyName: fk_invitations_workspace
  references: workspaces(id)
```

Required migration (additive, backwards-compatible):
```sql
-- 1. Allow workspace_id to be nullable
ALTER TABLE invitations
    ALTER COLUMN workspace_id DROP NOT NULL;

-- 2. Add target column with safe default
ALTER TABLE invitations
    ADD COLUMN target VARCHAR(32) NOT NULL DEFAULT 'EXISTING_WORKSPACE';

-- 3. Protect against duplicate ACTIVE NEW_WORKSPACE invitations per waitlist entry
CREATE UNIQUE INDEX uq_invitations_waitlist_active_source
    ON invitations (source_reference_id)
    WHERE status = 'ACTIVE'
      AND source = 'WAITLIST';

-- 4. Add lifecycle-aware check constraint
ALTER TABLE invitations
    ADD CONSTRAINT chk_invitation_target_workspace
    CHECK (
        (target = 'EXISTING_WORKSPACE' AND workspace_id IS NOT NULL)
        OR
        (target = 'NEW_WORKSPACE'
         AND ((status <> 'ACCEPTED' AND workspace_id IS NULL)
              OR (status = 'ACCEPTED' AND workspace_id IS NOT NULL)))
    );
```

**Rollback**: `ALTER TABLE invitations DROP COLUMN target`, then re-add NOT NULL.
Safe only before any `NEW_WORKSPACE` invitations with `workspace_id = NULL` exist.
After rollout, active `NEW_WORKSPACE` invitations with `NULL` workspace_id must first
be revoked/deleted before restoring NOT NULL.

## What This Proposal Does NOT Cover

- Resend/revoke for waitlist invitations (handled by DALLAY-565 notification delivery contract)
- Migration of existing `WaitlistInvitation` records (out of scope — legacy table stays)
- `ResendWaitlistInvitationHandler` refactoring (future work)
- Workspace name display for NEW_WORKSPACE notifications (DALLAY-566 responsibility)

## Open Questions (Resolved)

| Question | Resolution |
|---|---|
| Where does workspaceId come from for waitlist? | It doesn't exist yet. `NEW_WORKSPACE` provisions it on acceptance. |
| Which handler handles private beta accept? | Both `AcceptInvitationHandler` (authenticated) and `InvitationRegistrationGatewayAdapter` (registration). Both delegate to `InvitationActivationCoordinator`. |
| Does SUPERSEDED exist? | No. DALLAY-565 defines resend with same InvitationId. |
| Raw token in events? | No. DALLAY-565/566 owns token handoff. |
