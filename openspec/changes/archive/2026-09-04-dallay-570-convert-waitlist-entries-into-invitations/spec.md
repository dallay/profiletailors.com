# Delta Spec: DALLAY-570 — Convert Waitlist Entries into Invitations

> Corrected spec after team review. Supersedes previous version.
> Changes: lifecycle-aware invariants, enum not sealed class, coordinator not direct handler branching,
> no SUPERSEDED, no raw token in events, correct resend semantics.

---

## 1. Added Requirements

### Req 1: InvitationTarget models two distinct onboarding paths

Every `Invitation` has a `target: InvitationTarget` field:

```kotlin
enum class InvitationTarget {
    EXISTING_WORKSPACE   // invitee joins an existing workspace
    NEW_WORKSPACE        // invitee provisions a new workspace on acceptance
}
```

**Lifecycle-aware invariants enforced in aggregate init:**

| target | status | workspaceId |
|--------|--------|------------|
| `EXISTING_WORKSPACE` | any | `!= null` (always required) |
| `NEW_WORKSPACE` | `ACTIVE`, `EXPIRED`, `REVOKED` | `== null` |
| `NEW_WORKSPACE` | `ACCEPTED` | `!= null` (set by `accept()`) |

The aggregate init raises `IllegalStateException` when invariants are violated.

**Accept transition is single-method with workspace parameter:**

```kotlin
fun accept(at: Instant, principalId: String, resolvedWorkspaceId: String? = null): Invitation
```

For `NEW_WORKSPACE`, `resolvedWorkspaceId` is mandatory. For `EXISTING_WORKSPACE`,
it is unused and `workspaceId` is already set.

---

### Req 2: Waitlist invitation targets NEW_WORKSPACE

When an admin creates an invitation from an eligible waitlist entry, the system MUST create
`Invitation` with:
- `source = InvitationSource.WAITLIST`
- `sourceReferenceId` = waitlist entry ID (non-blank)
- `target = InvitationTarget.NEW_WORKSPACE`
- `workspaceId = null`

**Scenario: Admin creates invitation from eligible waitlist entry**

```
GIVEN a waitlist entry with status PENDING and no active invitation
WHEN  admin with WAITLIST_INVITE permission executes InviteWaitlistEntryCommand
THEN  the handler creates Invitation(
         source = WAITLIST,
         sourceReferenceId = waitlistEntryId,
         target = NEW_WORKSPACE,
         workspaceId = null
       )
AND   persists it via InvitationRepository
AND   calls WaitlistEntry.invite(now)  [PENDING → INVITED]
AND   publishes InvitationIssued (audit event — no raw token)
```

**Scenario: PENDING entry transitions to INVITED on invitation creation**

```
GIVEN a waitlist entry with status PENDING
WHEN  InviteWaitlistEntryHandler creates an Invitation for that entry
THEN  WaitlistEntry.invite(now) is called and the entry transitions to INVITED
```

---

### Req 3: InvitationActivationCoordinator orchestrates all acceptance paths

Both acceptance entry points delegate to `InvitationActivationCoordinator`:

| Entry point | Triggered by |
|---|---|
| `AcceptInvitationHandler` | Authenticated user clicks email link |
| `InvitationRegistrationGatewayAdapter` | New user completes registration form |

Coordinator returns `InvitationActivationResult`:
```kotlin
data class InvitationActivationResult(
    val invitation: Invitation,
    val membershipStatus: WorkspaceMembershipStatus,
)
```

`ProvisionedWorkspace` (from `WorkspaceProvisioningService`) MUST expose `membershipStatus`:

```kotlin
data class ProvisionedWorkspace(
    val workspaceId: String,
    val name: String,
    val membershipStatus: WorkspaceMembershipStatus,  // must be exposed
)
```

Coordinator has no transaction of its own. Transaction is owned by the caller
(`AtomicTransactionRunner`). Coordinator returns `InvitationActivationResult`, which
handlers map to `InvitationAcceptanceResult(workspaceId, membershipStatus)` for the HTTP contract.

**Scenario: User accepts a waitlist invitation (NEW_WORKSPACE)**

```
GIVEN an active Invitation with source=WAITLIST, sourceReferenceId=entry-456,
      target=NEW_WORKSPACE, workspaceId=null
WHEN  user with matching identity and email presents valid token
THEN  InvitationActivationCoordinator.activate(invitation, principalId, displayName)
        → WorkspaceProvisioningService.provisionDefaultWorkspace(principalId, displayName)
        → WaitlistEntry.convert(now)  [INVITED → CONVERTED]
        → Invitation.accept(now, principalId, provisioned.workspaceId)
        → InvitationRepository.updateIfVersionMatches
AND   returns InvitationActivationResult(invitation, membershipStatus)
AND  handler maps to InvitationAcceptanceResult(workspaceId, membershipStatus.name)
```

**Scenario: User accepts invitation to existing workspace (EXISTING_WORKSPACE)**

```
GIVEN an active Invitation with source=DIRECT,
      target=EXISTING_WORKSPACE, workspaceId=ws-789
WHEN  user with matching email presents valid token
THEN  InvitationActivationCoordinator.activate(invitation, principalId, displayName)
        → WorkspaceMembershipProvisioner.reconcile(ws-789, principalId)
        → Invitation.accept(now, principalId)
        → InvitationRepository.updateIfVersionMatches
AND   returns InvitationActivationResult(invitation, membershipStatus)
AND  handler maps to InvitationAcceptanceResult(workspaceId, membershipStatus.name)
```

---

### Req 4: Waitlist entry reflects conversion on acceptance

`WaitlistEntry.convert()` MUST be called by `InvitationActivationCoordinator` when a
`source=WAITLIST` invitation is accepted, within the same logical flow as workspace provisioning.

**Scenario: INVITED entry transitions to CONVERTED when workspace is provisioned**

```
GIVEN a waitlist entry with status INVITED
       and an active Invitation with target=NEW_WORKSPACE
WHEN  InvitationActivationCoordinator activates the invitation for NEW_WORKSPACE
THEN  WorkspaceProvisioningService.provisionDefaultWorkspace(principalId, displayName)
AND   WaitlistEntry.convert(now)  [INVITED → CONVERTED]
AND   Invitation.accept(now, principalId, provisionedWorkspaceId)  [ACTIVE → ACCEPTED]
```

---

### Req 5: WAITLIST source enforces sourceReferenceId

`Invitation` with `source = InvitationSource.WAITLIST` MUST have non-blank `sourceReferenceId`.
Init block enforces: `require(source != WAITLIST || !sourceReferenceId.isNullOrBlank())`.

---

### Req 6: No raw token in InvitationIssued event

`InvitationIssued` published by `InviteWaitlistEntryHandler` MUST NOT carry the raw token.
Token handoff for notification delivery follows DALLAY-565/566 contract:
`InvitationNotificationRequested(invitationId, commandId, kind)` — no raw token,
no recipient, no workspace context in the event payload.

---

### Req 7: No SUPERSEDED status

Canonical `Invitation` status is NOT modified. `SUPERSEDED` is not a valid status.
PostgreSQL CHECK constraint enforces: `status IN ('ACTIVE', 'ACCEPTED', 'EXPIRED', 'REVOKED')`.

Resend follows DALLAY-565 contract: same `InvitationId`, new delivery command/notification record.
DALLAY-570 does NOT create a new `Invitation` on re-invite.

---

## 2. Modified Requirements

### WaitlistInvitation is legacy-only

`WaitlistInvitation` and `WaitlistInvitationRepository` are **legacy compatibility models only**.
New waitlist invitation flows MUST NOT create or update `WaitlistInvitation` rows.
Existing records created before this change remain readable via the legacy repository.

**Scenario: Legacy WaitlistInvitation records remain readable**

```
GIVEN a WaitlistInvitation created before this change
WHEN  an operator queries or resends that invitation
THEN  WaitlistInvitationRepository continues to function without error
AND   the WaitlistInvitation aggregate behaves as before
```

---

## 3. Removed Requirements

### Req (removed): Re-invite supersedes prior invitation

The scenario "admin re-invites → existing Invitation marked SUPERSEDED" is REMOVED.
DALLAY-565 defines resend with same `InvitationId` and new delivery notification.
If an entry already has an active `Invitation`, re-invite creation MUST either:
- Throw `InvitationAlreadyActiveException`, OR
- Route through explicit resend command (handled by DALLAY-565 contract)

DALLAY-570 does NOT create a replacement `Invitation` on re-invite.

---

## 4. Data Invariants

### Invitation lifecycle table

| source | target | workspaceId | sourceReferenceId | Notes |
|--------|--------|-------------|-------------------|-------|
| `DIRECT` | `EXISTING_WORKSPACE` | non-null | `null` | Normal invite |
| `DIRECT` | `NEW_WORKSPACE` | null → non-null on accept | `null` | Platform invite to new workspace |
| `WAITLIST` | `NEW_WORKSPACE` | null → non-null on accept | non-null | Waitlist conversion |

### Init block rules (enforced at construction and on state transitions)

```
1. source = WAITLIST → sourceReferenceId != null
2. target = EXISTING_WORKSPACE → workspaceId != null  (always)
3. target = NEW_WORKSPACE and status in {ACTIVE, EXPIRED, REVOKED} → workspaceId == null
4. target = NEW_WORKSPACE and status = ACCEPTED → workspaceId != null
5. accept() for NEW_WORKSPACE requires non-null resolvedWorkspaceId
```

---

## 5. Error Scenarios

### CONVERTED entry cannot be invited

```
GIVEN waitlist entry with status CONVERTED
WHEN  admin executes InviteWaitlistEntryCommand
THEN  WaitlistEntryAlreadyConvertedException
AND   no Invitation created
```

### CANCELLED entry cannot be invited

```
GIVEN waitlist entry with status CANCELLED
WHEN  admin executes InviteWaitlistEntryCommand
THEN  WaitlistEntryNotInvitableException with message "Entry is cancelled"
AND   no Invitation created
```

### Duplicate active invitation is rejected

```
GIVEN waitlist entry with status PENDING and an existing active Invitation
WHEN  admin executes InviteWaitlistEntryCommand
THEN  InvitationAlreadyActiveException
AND   no second Invitation created
```

---

## 6. Happy Path

```
Admin → InviteWaitlistEntryHandler
  → Creates Invitation(WAITLIST, sourceReferenceId=entryId,
                       target=NEW_WORKSPACE, workspaceId=null)
  → InvitationRepository.save()
  → WaitlistEntry.invite()   [PENDING → INVITED]
  → InvitationIssued (audit, no raw token)

User clicks email link
  → AcceptInvitationHandler
  → InvitationRepository.findByCandidateKeyForUpdate()
  → InvitationActivationCoordinator.activate(NEW_WORKSPACE)
    → WorkspaceProvisioningService.provisionDefaultWorkspace()
    → WaitlistEntry.convert()   [INVITED → CONVERTED]
    → Invitation.accept(resolvedWorkspaceId)  [ACTIVE → ACCEPTED]
    → InvitationRepository.updateIfVersionMatches()
  → InvitationAcceptanceResult

New user registration
  → RegisterUserHandler (creates identity + credential)
  → InvitationRegistrationGatewayAdapter.acceptForRegistration()
  → InvitationActivationCoordinator.activate(NEW_WORKSPACE)
    → [same as above]
  → Registration result with workspaceId
```

---

## 7. Acceptance Criteria

| AC | Description | Scenario |
|----|-------------|----------|
| AC1 | Admin creates invitation from eligible waitlist entry | "Admin creates invitation from eligible waitlist entry" |
| AC2 | Resulting invitation has WAITLIST source and NEW_WORKSPACE target | Table in §4 |
| AC3 | Waitlist entry reflects INVITED state after invitation creation | "PENDING entry transitions to INVITED" |
| AC4 | Waitlist entry reflects CONVERTED state after acceptance | "INVITED entry transitions to CONVERTED" |
| AC5 | Workspace provisioned and linked to invitation on acceptance | Coordinator scenario for NEW_WORKSPACE |
| AC6 | Both accept entry points (authenticated + registration) use same coordinator | §1 Req 3 |
| AC7 | No raw token in InvitationIssued event | §1 Req 6 |
| AC8 | SUPERSEDED not in canonical status | §1 Req 7 |
