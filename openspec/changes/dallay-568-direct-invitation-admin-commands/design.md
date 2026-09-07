# Design: Direct Invitation Admin Commands

## Technical Approach

Platform admin REST endpoints for the `platformadmin` bounded context — one for creating direct invitations and one for revoking them. Both live under `POST /api/admin/invitations`. Command handlers (`CreateInvitationHandler`, `RevokeInvitationHandler`) are pure application-layer orchestrators over the existing `Invitation` aggregate. Token generation, duplicate detection, and event-driven notification scheduling stay entirely within the domain layer. The API layer never sees a raw token — only the hash is persisted.

## Architecture Decisions

### Decision: Token never leaves the domain boundary

**Choice**: Invitation tokens are generated in domain logic, appear only in `InvitationCreated` domain events, and only the bcrypt hash is persisted.
**Alternatives considered**: Return raw token from the controller — rejected because tokens crossing the API layer create audit, logging, and metrics exposure risk.
**Rationale**: The existing `InviteWaitlistEntryHandler` already follows this pattern with `InvitationTokenCodec`. Reusing it keeps the security boundary consistent and reduces the attack surface.

### Decision: Domain events drive notification scheduling

**Choice**: `InvitationCreated` and `InvitationRevoked` domain events are published via `DomainEventPublisher`. Downstream notification adapters subscribe asynchronously.
**Alternatives considered**: Call `NotificationGateway` directly from the handler — rejected because handler composition would then require the notification adapter at test time.
**Rationale**: Event-driven decoupling lets the handler remain pure in unit tests. The `InvitationActivationCoordinator` already uses this pattern for waitlist invitations.

### Decision: Duplicate active invitation detection via repository query

**Choice**: `InvitationRepository.hasActiveInvitationFor(email, workspaceId)` — a targeted query that checks for non-revoked, non-expired invitations for the same email+workspace pair.
**Alternatives considered**: Load all invitations for the workspace and filter in the handler — rejected because it fetches an unbounded collection. Scan by email only — rejected because workspaces are the isolation boundary.
**Rationale**: The query is a simple indexed look-up (email + workspace_id + status). Existing `InvitationRepository` already has `findByWorkspaceAndEmail` — extend it with a `hasActive` variant.

### Decision: New platform permissions `platform.invitations.create` and `platform.invitations.revoke`

**Choice**: Two distinct permissions — create and revoke — scoped to OWNER and OPERATOR roles only. No grant to SUPPORT_AGENT or AUDITOR.
**Alternatives considered**: Single `platform.invitations.manage` permission — rejected because revoke and create have different risk profiles and audit requirements.
**Rationale**: Aligns with the existing pattern of fine-grained permissions in `PlatformPermission` (e.g., `WAITLIST_INVITE` vs `WAITLIST_CANCEL`). The `specs/admin-authorization/spec.md` defines the exact matrix.

### Decision: Optimistic locking via `Invitation.version`

**Choice**: Use the existing `Invitation.version` field (already present from DALLAY-564) for optimistic concurrency control on revoke.
**Alternatives considered**: Pessimistic locking via `SELECT FOR UPDATE` — rejected because it creates cross-request blocking and complicates connection handling in R2DBC.
**Rationale**: The `Invitation` aggregate already carries a version. `RevokeInvitationCommand` includes the expected version; the repository implementation throws `OptimisticLockingFailureException` on mismatch, which the infrastructure layer translates to HTTP 409.

## Component Design

```
AdminInvitationController          CreateInvitationHandler / RevokeInvitationHandler
         │                                     │
         │  CreateInvitationCommand           │  RevokeInvitationCommand
         │  RevokeInvitationCommand           │
         ▼                                     ▼
  OperatorAccessResolver                  InvitationRepository
  (authorize platform.invitations.create)  NotificationGateway (port)
  (authorize platform.invitations.revoke)   AuditEventPublisher (port)
                                              │
                                              ▼
                                    InvitationCreated / InvitationRevoked
                                              │
                                              ▼
                                  InvitationNotificationAdapter
                                  (schedule email delivery)
```

### Handlers

**`CreateInvitationHandler`**

1. Authorize caller (OWNER or OPERATOR via `platform.invitations.create`)
2. Validate `workspaceId` exists
3. Check `hasActiveInvitationFor(email, workspaceId)` — if true, throw `InvitationAlreadyExistsException`
4. Generate token via `InvitationTokenCodec.generate()`
5. Build `Invitation` aggregate, publish `InvitationCreated` domain event (contains raw token)
6. Persist aggregate (hash stored, token never returned past the domain boundary)
7. Return `InvitationCreated` response (token included — one chance to display to admin)

**`RevokeInvitationHandler`**

1. Authorize caller (OWNER or OPERATOR via `platform.invitations.revoke`)
2. Load `Invitation` by ID — throw `InvitationNotFoundException` if absent
3. Assert workspace ownership matches caller's context
4. Call `invitation.revoke(version)` — optimistic lock check inside aggregate
5. Publish `InvitationRevoked` domain event
6. Persist updated aggregate

### Ports (application-layer interfaces)

| Port | Direction | Purpose |
|------|-----------|---------|
| `InvitationRepository` | Outbound | Persist and query `Invitation` aggregates |
| `NotificationGateway` | Outbound (event-driven) | Schedule/send email notifications — implemented by `InvitationNotificationAdapter` |
| `AuditEventPublisher` | Outbound | Emit `PlatformAuditEvent` for admin actions |
| `WorkspaceExistenceChecker` | Inbound | Verify `workspaceId` is valid before invitation creation |

### Concurrency Safety

The `revoke()` method on `Invitation` accepts an optional `expectedVersion`. The repository implementation performs:

```sql
UPDATE invitations SET status = 'REVOKED', revoked_at = :now, version = version + 1
WHERE id = :id AND version = :expectedVersion
```

If `rowsUpdated == 0`, `OptimisticLockingFailureException` is thrown and translated to HTTP 409 Conflict.

## Data Flow

### Create Invitation

```
POST /api/admin/invitations
  → AdminInvitationController.create(CreateInvitationCommand)
    → OperatorAccessResolver.requirePlatformInvitationCreate()   # 403 if missing
    → WorkspaceExistenceChecker.exists(workspaceId)             # 400 if missing
    → InvitationRepository.hasActiveInvitationFor(email, workspaceId)  # 409 if duplicate
    → Invitation.create(...)                                    # generates token
    → DomainEventPublisher.publish(InvitationCreated(token, ...))
    → InvitationRepository.save(invitation)                    # hash persisted
    → InvitationNotificationAdapter.onInvitationCreated(...)     # async email scheduling
    → return InvitationCreatedResponse(invitationId, token, expiresAt)
```

### Revoke Invitation

```
POST /api/admin/invitations/revoke
  → AdminInvitationController.revoke(RevokeInvitationCommand)
    → OperatorAccessResolver.requirePlatformInvitationRevoke()  # 403 if missing
    → InvitationRepository.findById(id)                        # 404 if missing
    → Invitation.revoke(expectedVersion)                      # 409 on version mismatch
    → DomainEventPublisher.publish(InvitationRevoked(...))
    → InvitationRepository.save(invitation)
    → InvitationNotificationAdapter.onInvitationRevoked(...)   # async notification
    → return InvitationRevokedResponse(invitationId)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandler.kt` | Create | Command handler for `CreateInvitationCommand` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/RevokeInvitationHandler.kt` | Create | Command handler for `RevokeInvitationCommand` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationController.kt` | Create | REST controller with `POST /api/admin/invitations` and `POST /api/admin/invitations/revoke` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt` | Modify | Add `INVITATIONS_CREATE` and `INVITATIONS_REVOKE` values |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/InvitationRepository.kt` | Modify | Add `hasActiveInvitationFor(email, workspaceId): Boolean` method |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt` | Modify | Implement `hasActiveInvitationFor` query and optimistic-lock UPDATE for revoke |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/notification/InvitationNotificationAdapter.kt` | Create | Subscribes to `InvitationCreated` / `InvitationRevoked` events and calls `NotificationGateway` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/PlatformAdminBootstrapConfiguration.kt` | Modify | Wire new adapter to domain event publisher |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/Invitation.kt` | Modify | Add `revoke(expectedVersion: Long)` method with optimistic lock check |
| `apps/web/admin/src/stores/auth.store.ts` | Modify | Add `platform.invitations.create` and `platform.invitations.revoke` to `PLATFORM_OWNER` and `PLATFORM_OPERATOR` permission lists |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt` | Create | Unit tests for handler, including duplicate, workspace not found, and permission scenarios |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/RevokeInvitationHandlerTest.kt` | Create | Unit tests including version mismatch (409), not found (404), and permission scenarios |
| `server/smp/src/test/resources/features/platformadmin/invitations-direct.feature` | Create | BDD scenarios for create and revoke flows |

## Interfaces and Key Types

### CreateInvitationCommand

```kotlin
data class CreateInvitationCommand(
    val email: EmailAddress,
    val workspaceId: WorkspaceId,
    val role: PlatformRole,
    val createdBy: OperatorId,
    val invitedAt: Instant = Instant.now(),
    val expiresAt: Instant,
)
```

### RevokeInvitationCommand

```kotlin
data class RevokeInvitationCommand(
    val invitationId: InvitationId,
    val workspaceId: WorkspaceId,
    val revokedBy: OperatorId,
    val expectedVersion: Long, // optimistic lock
)
```

### InvitationCreated Domain Event (internal — never serialized to HTTP)

```kotlin
data class InvitationCreated(
    val invitationId: InvitationId,
    val email: EmailAddress,
    val workspaceId: WorkspaceId,
    val role: PlatformRole,
    val token: InvitationToken,       // raw token — domain event only
    val invitedAt: Instant,
    val expiresAt: Instant,
    val createdBy: OperatorId,
) : DomainEvent
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Handler orchestration, permission checks, duplicate detection, aggregate revoke | Mock `InvitationRepository`, `NotificationGateway`, `AuditEventPublisher` |
| Unit | `Invitation.revoke()` optimistic lock | Inline test of aggregate method |
| Integration | Repository `hasActiveInvitationFor` query, optimistic-lock UPDATE | `R2dbcInvitationRepositoryTest` with H2 or Testcontainers |
| Integration | Controller endpoint — auth, validation, 409, 404 | `WebTestClient` with mocked auth |
| BDD | Full create and revoke flows including duplicate and unauthorized | `invitations-direct.feature` with `@smoke @fast` |

## Migration / Rollout

No database migration required. The `Invitation` aggregate and `invitations` table schema were established in DALLAY-564. The `hasActiveInvitationFor` query uses the existing index on `(workspace_id, email, status)`.

Feature flags are not required — this is a new endpoint under the existing `platformadmin` admin scope.

## Open Questions

- [ ] Does `platform.invitations.create` also cover re-sending an existing invitation, or is that a separate `platform.invitations.resend` permission? The current spec only covers creating and revoking — resend behavior for direct admin invitations is out of scope for this change.
- [ ] Should the invitation token be single-use (one acceptance) or multi-use until expiry? The current aggregate design supports multi-use (no redemption counter). If single-use is required, `Invitation` needs a `redeemedAt` field and the accept endpoint must record redemption.
- [ ] The admin SPA (`apps/web/admin`) will need UI to call these endpoints — should the design document reference the frontend store/permission updates needed, or is that tracked separately?
