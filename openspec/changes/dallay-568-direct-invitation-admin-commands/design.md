# Design: Direct Invitation Admin Commands

## Technical Approach

Platform admin REST endpoints for the `platformadmin` bounded context — one for creating direct invitations and one for revoking them. Both live under `POST /api/admin/invitations`. Command handlers (`CreateInvitationHandler`, `RevokeInvitationHandler`) are pure application-layer orchestrators over the existing `Invitation` aggregate. Token generation, duplicate detection, and event-driven notification scheduling stay entirely within the domain layer. The API layer never sees a raw token — only the hash is persisted.

## Architecture Decisions

### Decision: Token never leaves the domain boundary

**Choice**: Invitation tokens are generated in domain logic, appear only in `InvitationIssued` / `DirectInvitationResent` domain events (consumed post-commit by `SendInvitationEmailConsumer` to build the accept URL), and only the bcrypt hash plus SHA-256 candidate key are persisted.
**Alternatives considered**: Return raw token from the controller — rejected because tokens crossing the API layer create audit, logging, and metrics exposure risk.
**Rationale**: The existing `InviteWaitlistEntryHandler` already follows this pattern with `InvitationTokenCodec`. Reusing it keeps the security boundary consistent and reduces the attack surface.

### Decision: Domain events drive notification scheduling

**Choice**: `InvitationIssued` (create) and `DirectInvitationResent` (resend) domain events are published after persistence. `SendInvitationEmailConsumer` handles them with `@TransactionalEventListener(AFTER_COMMIT)` to build the accept URL from the event's raw token. No `InvitationRevoked` event is published — revocation is terminal state, nothing downstream consumes it.
**Alternatives considered**: Call `NotificationGateway` directly from the handler — rejected because handler composition would then require the notification adapter at test time.
**Rationale**: Event-driven decoupling lets the handler remain pure in unit tests. The post-commit boundary guarantees no email is sent for a rolled-back invitation.

### Decision: Duplicate active invitation detection via repository query

**Choice**: `InvitationRepository.hasActiveInvitationFor(normalizedEmail, workspaceId, asOf)` — a targeted query for non-revoked, non-expired invitations for the same email+workspace pair (`status = 'ACTIVE' AND expires_at > :asOf`). A concurrent insert racing the check hits the pre-existing partial unique index and is mapped to `InvitationAlreadyActiveException` (→ 409).
**Alternatives considered**: Load all invitations for the workspace and filter in the handler — rejected because it fetches an unbounded collection. Scan by email only — rejected because workspaces are the isolation boundary.
**Rationale**: The query is a simple indexed look-up (email + workspace_id + status). The unique index from `005-harden-invitations.yaml` remains the exactly-once backstop.

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

1. Authorize caller (OWNER or OPERATOR via `platform.invitations.create`, resolved from command roles)
2. Require non-blank `workspaceId` for `EXISTING_WORKSPACE` targets (existence itself is validated at the acceptance gate, not here — no workspace read port exists in this context)
3. Normalize email once (`trim().lowercase()`); check `hasActiveInvitationFor(normalizedEmail, workspaceId, now)` — if true, throw `InvitationAlreadyActiveException`
4. Generate token via `InvitationTokenGenerator` (CSPRNG); persist bcrypt hash + SHA-256 candidate key
5. Build `Invitation(source=DIRECT, ...)` as `ACTIVE`; `save()` maps a unique-conflict to `InvitationAlreadyActiveException`
6. Publish `InvitationIssued` domain event (contains raw token for the post-commit email consumer)
7. Return `{invitationId, status, expiresAt, version}` — NO token field in the HTTP response

**`RevokeInvitationHandler`**

1. Authorize caller (OWNER or OPERATOR via `platform.invitations.revoke`)
2. Load `Invitation` by ID — throw `InvitationNotFoundException` if absent
3. Require `invitation.isActive(now)` (status `ACTIVE` AND `expiresAt` in the future) — else `InvitationNotRevocableException`
4. Call `invitation.revoke(expectedVersion)` — version check inside the aggregate, then `updateIfVersionMatches` for the persistence check
5. Persist updated aggregate; no revocation domain event is published
6. Increment `platform.invitations.revoked`

**`ResendInvitationHandler`**

1. Authorize caller via `platform.invitations.resend`
2. Load by ID — 404 if absent; require `source == DIRECT` (waitlist invitations keep their own resend path with limits)
3. Require `invitation.isActive(now)` — expired invitations are never revived
4. Rotate token material, extend expiry, bump version; publish `DirectInvitationResent`
5. Return `{invitationId, status, expiresAt, version}` — the caller MUST use the returned version for later revokes

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
POST /api/admin/invitations/direct
  → AdminInvitationController.createDirectInvitation(CreateInvitationCommand)
    → handler requires platform.invitations.create from command roles  # 403 if missing
    → InvitationRepository.hasActiveInvitationFor(email, workspaceId, now)  # 409 if duplicate
    → InvitationTokenGenerator.generate()                             # CSPRNG
    → InvitationRepository.save(invitation, candidateKey)              # hash persisted; DuplicateKey → 409
    → EventPublisher.publish(InvitationIssued(token, ...))            # post-commit email via SendInvitationEmailConsumer
    → 201 {invitationId, status, expiresAt, version}                   # NO token
```

### Revoke Invitation

```
POST /api/admin/invitations/{id}/direct-revoke {expectedVersion}
  → handler requires platform.invitations.revoke                       # 403 if missing
  → invitation.isActive(now) required                                  # 409 INVITATION_NOT_REVOCABLE if expired/consumed
  → invitation.revoke(expectedVersion) + updateIfVersionMatches        # 409 INVITATION_VERSION_CONFLICT on mismatch
  → 200 {invitationId}
```

### Resend Invitation

```
POST /api/admin/invitations/{id}/direct-resend
  → handler requires platform.invitations.resend                       # 403 if missing
  → source == DIRECT required                                          # 409 INVITATION_NOT_RESENDABLE for WAITLIST rows
  → invitation.isActive(now) required                                  # expired invitations are never revived
  → token rotation + expiry extension + version bump
  → EventPublisher.publish(DirectInvitationResent(token, ...))
  → 200 {invitationId, status, expiresAt, version}
```
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
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationController.kt` | Create | REST controller with `POST /api/admin/invitations/direct`, `POST /api/admin/invitations/{id}/direct-revoke`, `POST /api/admin/invitations/{id}/direct-resend` |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformPermission.kt` | Modify | Add `INVITATIONS_CREATE`, `INVITATIONS_REVOKE`, `INVITATIONS_RESEND` values |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/InvitationRepository.kt` | Modify | Add `hasActiveInvitationFor(email, workspaceId, asOf): Boolean` method |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt` | Modify | Implement `hasActiveInvitationFor` query (excludes expired), map unique-conflict to `InvitationAlreadyActiveException`, optimistic-lock UPDATE for revoke/resend |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/observability/InvitationObservability.kt` | Create | Micrometer adapter behind the `InvitationTelemetry` port (`platform.invitations.created` / `platform.invitations.revoked`) — delivery uses the existing `InvitationIssued` → `SendInvitationEmailConsumer` post-commit seam, no new adapter |
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

No database migration required. The `invitations` table and the partial unique index `uq_invitations_workspace_active_email` were established before this change (`005-harden-invitations.yaml`); this change only adds the `hasActiveInvitationFor` read query against that index.

Feature flags are not required — these are new endpoints under the existing `platformadmin` admin scope.

## Open Questions

- [x] Does `platform.invitations.create` also cover re-sending, or is resend separate? RESOLVED: resend is delivered in this change behind its own `platform.invitations.resend` permission (`POST /api/admin/invitations/{id}/direct-resend`, `DirectInvitationResent` event, handler + BDD coverage).
- [ ] Should the invitation token be single-use (one acceptance) or multi-use until expiry? The current aggregate design supports multi-use (no redemption counter). If single-use is required, `Invitation` needs a `redeemedAt` field and the accept endpoint must record redemption.
- [ ] The admin SPA (`apps/web/admin`) will need UI to call these endpoints — should the design document reference the frontend store/permission updates needed, or is that tracked separately?
