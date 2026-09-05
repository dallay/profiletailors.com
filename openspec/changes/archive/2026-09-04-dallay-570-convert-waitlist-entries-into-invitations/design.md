# Design: DALLAY-570 — Convert Waitlist Entries into Invitations

> **Corrected design after team review. Supersedes previous version.**

## Summary

Unify waitlist invitation flow on the canonical `Invitation` aggregate using
`InvitationTarget.EXISTING_WORKSPACE` / `NEW_WORKSPACE`. Both acceptance entry points
(`AcceptInvitationHandler` and `InvitationRegistrationGatewayAdapter`) delegate to a shared
`InvitationActivationCoordinator`. `WaitlistInvitation` is legacy-only.

## Architecture Decisions

### Decision: InvitationTarget enum with lifecycle-aware invariant

**Choice**: `InvitationTarget` is a plain enum (`EXISTING_WORKSPACE`, `NEW_WORKSPACE`).
`workspaceId` is nullable. Invariant is lifecycle-aware — `NEW_WORKSPACE` allows
`workspaceId == null` in non-ACCEPTED states, requires non-null after ACCEPTED.

**Alternatives rejected**:
- Sealed interface with nested data class — overkill; enum suffices.
- Null-is-missing without target context — ambiguous semantics.
- Immutable `workspaceId` on `Invitation` — would require `withWorkspaceId()` which
  creates two aggregate transitions and two version increments.

**Why lifecycle-aware invariant:**

```
NEW_WORKSPACE + ACTIVE       → workspaceId == null   (workspace not yet created)
                         accept + provision
                                 ↓
NEW_WORKSPACE + ACCEPTED    → workspaceId == ws-123 (provisioned)
```

A single `accept(at, principalId, resolvedWorkspaceId)` call produces exactly ONE
version increment and one status transition.

### Decision: Shared InvitationActivationCoordinator

**Choice**: Extract a plain application class wired explicitly from
`PlatformAdminBootstrapConfiguration` via `@Bean`. No `@Service` annotation.
Both `AcceptInvitationHandler` (authenticated accept via email link) and
`InvitationRegistrationGatewayAdapter` (registration flow) delegate to.

**Alternatives rejected**:
- Branching `when(target)` in both handlers — diverges over time.
- Put orchestration in one handler only — registration doesn't go through that handler.

**Result types**:

```kotlin
data class InvitationActivationResult(
    val invitation: Invitation,
    val membershipStatus: WorkspaceMembershipStatus,
)

data class ProvisionedWorkspace(
    val workspaceId: String,
    val name: String,
    val membershipStatus: WorkspaceMembershipStatus,
)
```

`ProvisionedWorkspace.membershipStatus` exposes the status of the membership created during provisioning,
so the coordinator can propagate it without hardcoding.

**Coordinator**:

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
    ): InvitationActivationResult {
        val accepted: Invitation
        val membershipStatus: WorkspaceMembershipStatus

        when (invitation.target) {
            InvitationTarget.EXISTING_WORKSPACE -> {
                val membership = membershipProvisioner.reconcile(
                    requireNotNull(invitation.workspaceId),
                    principalId,
                )
                accepted = invitation.accept(clock.instant(), principalId)
                membershipStatus = membership.status
            }
            InvitationTarget.NEW_WORKSPACE -> {
                val provisioned = workspaceProvisioningService.provisionDefaultWorkspace(
                    principalId,
                    displayName ?: principalId,
                )
                if (invitation.source == InvitationSource.WAITLIST) {
                    val entry = waitlistEntryAdmin.findById(
                        requireNotNull(invitation.sourceReferenceId),
                    )
                        ?: throw IllegalStateException("Waitlist entry not found")
                    entry.convert(clock.instant())
                    waitlistEntryAdmin.save(entry)
                }
                accepted = invitation.accept(
                    clock.instant(),
                    principalId,
                    provisioned.workspaceId,
                )
                membershipStatus = provisioned.membershipStatus
            }
        }
        if (!invitationRepository.updateIfVersionMatches(accepted)) {
            throw OptimisticLockException("Invitation was modified concurrently")
        }
        return InvitationActivationResult(accepted, membershipStatus)
    }
}
```

**Transaction**: The atomic boundary is managed by the caller. Both `AcceptInvitationHandler`
and `InvitationRegistrationGatewayAdapter` run within `AtomicTransactionRunner`. The
coordinator itself is stateless and has no transaction.

### Decision: WaitlistEntry conversion call site

**Choice**: `InvitationActivationCoordinator` calls `WaitlistEntry.convert()` for
`source = WAITLIST` invitations, within the same logical flow as workspace provisioning.

**Not in `WorkspaceMembershipProvisioner`**: Tenancy context must not import waitlist domain.

**Not in `InviteWaitlistEntryHandler`**: Conversion at invite time would mark entry
`CONVERTED` before the user actually accepts — if the invite expires unused, the entry
is stuck in `CONVERTED`.

### Decision: Token/notification ownership

**Choice**: `InviteWaitlistEntryHandler` publishes `InvitationIssued` (audit-only event
without raw token). Notification delivery follows the DALLAY-565/566 contract:
`InvitationNotificationRequested` (no raw token) → DALLAY-566 handles token handoff.

**Rejected**: Publishing raw token in any domain/integration event. DALLAY-565 explicitly
rejects this.

### Decision: No SUPERSEDED in canonical Invitation

**Choice**: Canonical `Invitation` status enum is NOT modified. Resend follows DALLAY-565
contract: same `InvitationId`, new delivery command/notification record.

**Rejected**: Adding `SUPERSEDED` status. ADR-0020 constrains canonical statuses to
`ACTIVE`, `ACCEPTED`, `EXPIRED`, `REVOKED`. PostgreSQL CHECK enforces this.
DALLAY-565 explicitly defines resend semantics with ID reuse.

**Implication for re-invite**: If an entry is `INVITED` with an active `Invitation`,
the re-invite either:
- Rejects duplicate creation (throws `InvitationAlreadyActiveException`), OR
- Routes through explicit resend command (DALLAY-565 notification contract)

DALLAY-570 does NOT create a new `Invitation` on re-invite.

## Data Flow

### Invite flow

```
Admin → InviteWaitlistEntryHandler
    → Checks entry status (PENDING/INVITED/CONVERTED/CANCELLED)
    → Gets WaitlistInvitationContext (email, waitlist name, locale)
    → Creates Invitation(
          source = InvitationSource.WAITLIST,
          sourceReferenceId = waitlistEntryId,
          target = InvitationTarget.NEW_WORKSPACE,
          workspaceId = null,
          invitedEmailNormalized = context.recipientEmail,
          ...
        )
    → invitationRepository.save(invitation, candidateKey)
    → entry.invite(now); waitlistEntryAdmin.save(entry)
    → Publishes InvitationIssued (audit only — no raw token)
    → Publishes AdminAuditEvent.WAITLIST_ENTRY_INVITED

[Invitation record in `invitations` table with source=WAITLIST, target=NEW_WORKSPACE]
```

### Acceptance flow (authenticated user, existing account)

```
User clicks link → POST /api/invitations/accept
    → AcceptInvitationHandler
    → invitationRepository.findByCandidateKeyForUpdate(candidateKey)
    → Validates token, email, status
    → InvitationActivationCoordinator.activate(invitation, principalId, displayName)
        → NEW_WORKSPACE branch
          → provisionDefaultWorkspace(principalId, displayName) → ws-xyz
          → waitlistEntryAdmin.findById(sourceReferenceId); entry.convert(now); save
          → invitation.accept(now, principalId, ws-xyz)
          → updateIfVersionMatches
    → Maps InvitationActivationResult to InvitationAcceptanceResult(workspaceId, membershipStatus)
    → Returns InvitationAcceptanceResult
```

### Registration flow (new user, private beta)

```
New user submits registration form
    → LocalAuthHandlers / RegisterUserHandler
    → identity + credential creation (within AtomicTransactionRunner)
    → InvitationRegistrationGatewayAdapter.acceptForRegistration(rawToken, email, principalId)
        → validates token + email
        → invitationRepository.findById(invitationId)
    → InvitationActivationCoordinator.activate(invitation, principalId, displayName)
        → Same NEW_WORKSPACE branch as above
    → Maps InvitationActivationResult to InvitationAcceptanceResult(workspaceId, membershipStatus)
    → Returns registration result
```

Both flows share the same coordinator. The transaction boundary is the caller's
`AtomicTransactionRunner`.

## Database Migration

### Current state
- `invitations.workspace_id` is `NOT NULL` with FK to `workspaces`
- No `target` column

### Required changes (additive, backwards-compatible)

```sql
-- 1. Allow workspace_id to be nullable (required for NEW_WORKSPACE + ACTIVE)
ALTER TABLE invitations ALTER COLUMN workspace_id DROP NOT NULL;

-- 2. Add target column with safe default (existing rows → EXISTING_WORKSPACE)
ALTER TABLE invitations ADD COLUMN target VARCHAR(32) NOT NULL DEFAULT 'EXISTING_WORKSPACE';

-- 3. Protect against duplicate ACTIVE NEW_WORKSPACE invitations per waitlist entry
--    (the existing unique index on (workspace_id, email) doesn't protect NULLs)
CREATE UNIQUE INDEX uq_invitations_waitlist_active_source
    ON invitations (source_reference_id)
    WHERE status = 'ACTIVE' AND source = 'WAITLIST';

-- 4. Enforce lifecycle-aware target/workspace consistency at DB level
ALTER TABLE invitations ADD CONSTRAINT chk_invitation_target_workspace
CHECK (
    (target = 'EXISTING_WORKSPACE' AND workspace_id IS NOT NULL)
    OR
    (target = 'NEW_WORKSPACE'
     AND ((status <> 'ACCEPTED' AND workspace_id IS NULL)
          OR (status = 'ACCEPTED' AND workspace_id IS NOT NULL)))
);
```

**Rollback**: `ALTER TABLE invitations DROP COLUMN target`, `ALTER TABLE invitations
ALTER COLUMN workspace_id SET NOT NULL`. Safe only before any `NEW_WORKSPACE` invitations
with `workspace_id = NULL` exist. After rollout, active `NEW_WORKSPACE` invitations
with `NULL` workspace_id must first be revoked/deleted before restoring NOT NULL.

**Risk**: Low. All changes are additive. Existing rows are unaffected (default values).

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/.../domain/Invitation.kt` | Modify | Add `InvitationTarget` enum; make `workspaceId` nullable; add lifecycle-aware invariant; update `accept()` signature |
| `db/changelog/.../xxx-add-invitation-target.yaml` | Add | Migration with DROP NOT NULL, target column, check constraint, index |
| `server/smp/src/main/kotlin/.../persistence/R2dbcInvitationRepository.kt` | Modify | Handle nullable `workspaceId`; read/write `target` column |
| `server/smp/src/main/kotlin/.../handler/InviteWaitlistEntryHandler.kt` | Modify | Create `Invitation(WAITLIST, NEW_WORKSPACE)` via `InvitationRepository` |
| `server/smp/src/main/kotlin/.../InvitationActivationCoordinator.kt` | Add | Shared orchestration for both accept entry points |
| `server/smp/src/main/kotlin/.../AcceptInvitationHandler.kt` | Modify | Delegate to `InvitationActivationCoordinator` |
| `server/smp/src/main/kotlin/.../InvitationRegistrationGatewayAdapter.kt` | Modify | Delegate to `InvitationActivationCoordinator` |
| `server/smp/src/main/kotlin/.../PlatformAdminBootstrapConfiguration.kt` | Modify | Wire `InvitationActivationCoordinator`; wire `InvitationRepository` to `InviteWaitlistEntryHandler` |
| `server/smp/src/main/kotlin/.../contracts/WaitlistEntryAdmin.kt` | Audit | Confirm `WaitlistEntry.convert()` and `save()` available |

## Resolved Open Questions

| Question | Answer |
|---|---|
| Where does workspaceId for waitlist come from? | It doesn't exist yet. `NEW_WORKSPACE` provisions it on acceptance. |
| Does `SUPERSEDED` exist in `Invitation`? | No. DALLAY-565 defines resend with same InvitationId. |
| Raw token in events? | No. DALLAY-565/566 owns token handoff. `InvitationIssued` is audit-only. |
| Which handler for private beta accept? | Both `AcceptInvitationHandler` and `InvitationRegistrationGatewayAdapter` — both delegate to coordinator. |
| `WaitlistInvitation` status? | Legacy compatibility only. New flows MUST NOT create or update it. |
