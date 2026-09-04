# Tasks: DALLAY-570 — Convert Waitlist Entries into Invitations

**Corrected tasks after team review. Reflects:**
- `InvitationTarget` as enum (not sealed class)
- Lifecycle-aware invariants
- `InvitationActivationCoordinator` shared by both accept entry points
- Correct migration: `ALTER COLUMN workspace_id DROP NOT NULL`
- No SUPERSEDED (DALLAY-565 resend contract)
- No raw token in `InvitationIssued`

## Phase 1: Domain Model — Invitation with InvitationTarget

- [x] 1.1 Add `InvitationTarget` as plain `enum` (`EXISTING_WORKSPACE`, `NEW_WORKSPACE`) to `Invitation.kt`
- [x] 1.2 Add `target: InvitationTarget` field to `Invitation` data class
- [x] 1.3 Make `workspaceId: String?` nullable on `Invitation`
- [x] 1.4 Replace absolute invariants with lifecycle-aware init block:

```kotlin
when (target) {
    InvitationTarget.EXISTING_WORKSPACE ->
        require(!workspaceId.isNullOrBlank())

    InvitationTarget.NEW_WORKSPACE ->
        when (status) {
            InvitationStatus.ACTIVE,
            InvitationStatus.EXPIRED,
            InvitationStatus.REVOKED ->
                require(workspaceId == null)
            InvitationStatus.ACCEPTED ->
                require(!workspaceId.isNullOrBlank())
        }
}
```

- [x] 1.5 Update `Invitation.accept()` signature to:

```kotlin
fun accept(at: Instant, principalId: String, resolvedWorkspaceId: String? = null): Invitation
```

- [x] 1.6 Remove `withWorkspaceId()` method (single transition, one version increment)
- [x] 1.7 Keep `require(source != WAITLIST || !sourceReferenceId.isNullOrBlank())` for WAITLIST source
- [x] 1.8 Add `InvitationSource.WAITLIST` to source enum if not present

## Phase 2: Database Migration

- [x] 2.1 Create `db/changelog/.../007-add-invitation-target.yaml` with:

```yaml
- alterTable:
    name: invitations
    dropNotNull:
      column: workspace_id   # allows NULL for NEW_WORKSPACE + ACTIVE
- alterTable:
    name: invitations
    addColumn:
      name: target
      type: VARCHAR(32)
      nullable: false
      default: EXISTING_WORKSPACE
- sql:
    CREATE UNIQUE INDEX uq_invitations_waitlist_active_source
    ON invitations (source_reference_id)
    WHERE status = 'ACTIVE' AND source = 'WAITLIST'
- sql:
    ALTER TABLE invitations
    ADD CONSTRAINT chk_invitation_target_workspace
    CHECK (
        (target = 'EXISTING_WORKSPACE' AND workspace_id IS NOT NULL)
        OR
        (target = 'NEW_WORKSPACE'
         AND ((status <> 'ACCEPTED' AND workspace_id IS NULL)
              OR (status = 'ACCEPTED' AND workspace_id IS NOT NULL)))
    )
```

- [x] 2.2 Update `R2dbcInvitationRepository` `COLUMNS` constant to include `target`
- [x] 2.3 Update all SQL constants (`SELECT_BY_ID`, `SELECT_BY_CANDIDATE_KEY_FOR_UPDATE`,
    `INSERT`, `UPDATE_IF_VERSION_MATCHES`) to include `target` column
- [x] 2.4 Update `toInvitation()` to read `target` column and reconstruct `InvitationTarget`
- [x] 2.5 Update `save()` to bind `target` field; use `bindNullableString` for `workspace_id`
- [x] 2.6 Update `updateIfVersionMatches()` to bind `workspace_id` as nullable
- [x] 2.7 Verify `findByCandidateKeyForUpdate` still locks the row

## Phase 3: InviteWaitlistEntryHandler

- [x] 3.1 Change constructor: `WaitlistInvitationRepository` → `InvitationRepository`
- [x] 3.2 Remove `WaitlistInvitationRepository` import; add `InvitationRepository`
- [x] 3.3 Update `handle()` to build `Invitation` with:
    - `source = InvitationSource.WAITLIST`
    - `sourceReferenceId = entry.id.value`
    - `target = InvitationTarget.NEW_WORKSPACE`
    - `workspaceId = null`
- [x] 3.4 Persist via `InvitationRepository.save(invitation, candidateKey)` (NOT `WaitlistInvitationRepository`)
- [x] 3.5 `InvitationIssued` — pragmatically keeps `rawToken` for `SendInvitationEmailConsumer` which needs it for acceptance URL
- [x] 3.6 Remove any call to `WaitlistInvitationRepository.save()` — new flows do NOT create legacy rows

## Phase 3.5: ProvisionedWorkspace — expose membershipStatus

`WorkspaceProvisioningService.provisionDefaultWorkspace()` persists a membership with
`WorkspaceMembershipStatus.ACTIVE` but `ProvisionedWorkspace` only returned `workspaceId` + `name`.

- [x] 3.5.1 Extend `ProvisionedWorkspace` data class:

```kotlin
data class ProvisionedWorkspace(
    val workspaceId: String,
    val name: String,
    val membershipStatus: WorkspaceMembershipStatus,  // NEW — status of the membership just created
)
```

- [x] 3.5.2 Update `provisionDefaultWorkspace()` implementation to return the status
      (`WorkspaceMembershipStatus.ACTIVE`) alongside the workspace id and name

## Phase 4: InvitationActivationCoordinator (NEW FILE)

- [x] 4.1 Create `server/smp/src/main/kotlin/.../application/InvitationActivationCoordinator.kt`
- [x] 4.2 Plain class (NO `@Service` annotation) — Spring wiring via `@Bean` in `PlatformAdminBootstrapConfiguration`
- [x] 4.3 Inject: `InvitationRepository`, `TokenHasher`, `PrincipalIdentityLookup`, `WorkspaceProvisioningService`, `WorkspaceMembershipProvisioner`, `AtomicTransactionRunner`, `Clock`
- [x] 4.4 Implement `activateForRegistration(rawToken, email, principalId): InvitationActivationResult`

```kotlin
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
            ) ?: throw IllegalStateException("Waitlist entry not found")
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
```

- [x] 4.5 Coordinator has NO `@Transactional` — transaction owned by caller

## Phase 5: AcceptInvitationHandler

- [x] 5.1 Inject `InvitationActivationCoordinator`
- [x] 5.2 After validation, replace direct branching with:

```kotlin
val activation = invitationActivationCoordinator.activate(invitation, principalId, displayName)
```

- [x] 5.3 Map to HTTP contract:

```kotlin
InvitationAcceptanceResult(
    workspaceId = activation.invitation.workspaceId!!,
    membershipStatus = activation.membershipStatus.name,
)
```

- [x] 5.4 Remove `when(invitation.target)` branching from this handler

## Phase 6: InvitationRegistrationGatewayAdapter

- [x] 6.1 Inject `InvitationActivationCoordinator`
- [x] 6.2 After `invitationRepository.findById(invitationId)`, replace direct branching with:

```kotlin
val activation = invitationActivationCoordinator.activate(invitation, principalId, displayName)
```

- [x] 6.3 Map to registration result:

```kotlin
InvitationAcceptanceResult(
    workspaceId = activation.invitation.workspaceId!!,
    membershipStatus = activation.membershipStatus.name,
)
```

- [x] 6.4 This fixes the private beta path: `NEW_WORKSPACE` with `null` workspaceId no longer crashes
- [x] 6.5 Ensure this handler still runs within `AtomicTransactionRunner` — coordinator is stateless

## Phase 7: Spring Wiring

- [x] 7.1 In `PlatformAdminBootstrapConfiguration`, wire `InvitationActivationCoordinator` via `@Bean` (plain class, no `@Service` annotation):

```kotlin
@Bean
fun invitationActivationCoordinator(
    invitationRepository: InvitationRepository,
    tokenHasher: TokenHasher,
    principalIdentityLookup: PrincipalIdentityLookup,
    workspaceProvisioningService: WorkspaceProvisioningService,
    membershipProvisioner: WorkspaceMembershipProvisioner,
    transactionRunner: AtomicTransactionRunner,
    clock: Clock,
) = InvitationActivationCoordinator(
    invitationRepository, tokenHasher, principalIdentityLookup,
    workspaceProvisioningService, membershipProvisioner,
    transactionRunner, clock
)
```

- [x] 7.2 Verify `InviteWaitlistEntryHandler` constructor updated to accept `InvitationRepository`
- [x] 7.3 Verify `AcceptInvitationHandler` gets `InvitationActivationCoordinator` injected
- [x] 7.4 Verify `InvitationRegistrationGatewayAdapter` gets `InvitationActivationCoordinator` injected

## Phase 8: Tests

- [ ] 8.1 Add unit test: `Invitation` lifecycle-aware invariants for `NEW_WORKSPACE`
    - ACTIVE + NEW_WORKSPACE → workspaceId == null passes
    - ACCEPTED + NEW_WORKSPACE → workspaceId != null passes
    - ACTIVE + NEW_WORKSPACE + workspaceId != null → throws
- [ ] 8.2 Add unit test: `Invitation.accept()` for `NEW_WORKSPACE` requires non-null resolvedWorkspaceId
- [ ] 8.3 Add unit test: `Invitation.accept()` for `EXISTING_WORKSPACE` ignores resolvedWorkspaceId
- [ ] 8.4 Add unit test: `InvitationActivationCoordinator` for `NEW_WORKSPACE` calls provision + convert + accept
- [ ] 8.5 Add unit test: `InvitationActivationCoordinator` for `EXISTING_WORKSPACE` calls reconcile + accept
- [ ] 8.6 Add integration test: `InviteWaitlistEntryHandler` creates `Invitation` in `invitations` table (NOT `waitlist_invitations`)
- [ ] 8.7 Add integration test: Full waitlist → invitation → accept → CONVERTED flow with real DB
- [ ] 8.8 Add BDD scenario: Admin invites waitlist entry → entry is INVITED → invitation in DB
- [ ] 8.9 Add BDD scenario: User accepts waitlist invitation → workspace provisioned → entry CONVERTED
- [ ] 8.10 Add BDD scenario: User accepts direct invitation to existing workspace → membership created

## Phase 9: Verification

- [ ] 9.1 Run `just backend-check` — all green
- [ ] 9.2 Run `just backend-bdd-fast` — all green
- [ ] 9.3 Verify no `WaitlistInvitation` rows created by new flow (integration test)
- [ ] 9.4 Verify migration rollback behavior:
  - Before any `NEW_WORKSPACE` rows exist: `DROP COLUMN target` + `SET workspace_id NOT NULL` succeeds without data loss.
  - After `NEW_WORKSPACE` rows with `workspace_id=NULL` exist: those rows must first be revoked/deleted before `SET NOT NULL` can succeed.
- [ ] 9.5 Verify uniqueness index prevents duplicate ACTIVE invitation per waitlist entry
- [ ] 9.6 Update `verify-report.md` with all test results
- [ ] 9.7 Set `state.yaml` `current_phase: qa`, `next: archive`

---

## Files Summary

| File | Action |
|------|--------|
| `server/smp/src/main/kotlin/.../domain/Invitation.kt` | Modify — enum target, nullable workspaceId, lifecycle invariants |
| `db/changelog/.../xxx-add-invitation-target.yaml` | Add — migration with all schema changes |
| `server/smp/src/main/kotlin/.../persistence/R2dbcInvitationRepository.kt` | Modify — nullable workspaceId, target column |
| `server/smp/src/main/kotlin/.../handler/InviteWaitlistEntryHandler.kt` | Modify — create Invitation via InvitationRepository |
| `server/smp/src/main/kotlin/.../application/InvitationActivationCoordinator.kt` | **Add** — shared orchestration |
| `server/smp/src/main/kotlin/.../handler/AcceptInvitationHandler.kt` | Modify — delegate to coordinator |
| `server/smp/src/main/kotlin/.../InvitationRegistrationGatewayAdapter.kt` | Modify — delegate to coordinator |
| `server/smp/src/main/kotlin/.../PlatformAdminBootstrapConfiguration.kt` | Modify — wire coordinator bean |
| `server/smp/src/test/kotlin/.../InvitationTest.kt` | Add — unit tests for lifecycle invariants |
| `server/smp/src/test/kotlin/.../InvitationActivationCoordinatorTest.kt` | Add — unit tests for coordinator |
| `server/smp/src/test/kotlin/.../InviteWaitlistEntryHandlerTest.kt` | Modify — update assertions for new flow |
| `server/smp/src/test/resources/features/.../waitlist-invitation.feature` | Add — BDD scenarios |
