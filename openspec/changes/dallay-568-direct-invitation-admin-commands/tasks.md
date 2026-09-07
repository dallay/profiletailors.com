# Tasks: DALLAY-568 — Direct Invitation Admin Commands

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~900–1,100 |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | 3 stacked PRs: permissions → handlers+controller → repository+tests |
| Delivery strategy | ask-on-risk |
| Chain strategy | github-stacked-prs |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Add `platform.invitations.create` and `platform.invitations.revoke` to `PlatformPermission` enum and role mapping | PR 1 → trunk | OWNER + OPERATOR only; also add `INVITATION_CREATED`/`INVITATION_REVOKED` audit event types if not yet present |
| 2 | `CreateInvitationHandler`, `RevokeInvitationHandler`, `AdminInvitationController`, `InvitationNotificationAdapter` | PR 2 → PR 1 | Core implementation; pure unit-testable handlers |
| 3 | `RdbcInvitationRepository` (hasActive query + optimistic-lock revoke), `Invitation.revoke(expectedVersion)`, BDD feature file, handler unit tests | PR 3 → PR 2 | Persistence layer and tests |

## Phase 1: Permission Registry (PR 1)

- [x] 1.1 Add `INVITATIONS_CREATE("platform.invitations.create")`, `INVITATIONS_REVOKE("platform.invitations.revoke")`, and `INVITATIONS_RESEND("platform.invitations.resend")` to `PlatformPermission.kt` enum
- [x] 1.2 Update role-permission mapping in `PlatformRolePermissions.kt` (or equivalent): grant all three to `PLATFORM_OWNER` and `PLATFORM_OPERATOR`; deny to `SUPPORT_AGENT` and `AUDITOR`
- [x] 1.3 Confirm `INVITATION_CREATED` and `INVITATION_REVOKED` audit event types exist in `AuditEventType.kt`; add if missing
- [x] 1.4 Add `platform.invitations.create`, `platform.invitations.revoke`, and `platform.invitations.resend` to `PLATFORM_OWNER` and `PLATFORM_OPERATOR` permission lists in `apps/web/admin/src/stores/auth.store.ts` — **IN SCOPE** per user confirmation

## Phase 2: Core Handlers and Controller (PR 2)

- [x] 2.1 Create `CreateInvitationCommand` data class in `platformadmin/application/contracts/`
- [x] 2.2 Create `RevokeInvitationCommand` data class in `platformadmin/application/contracts/`
- [x] 2.3 Create `CreateInvitationResult` and `RevokeInvitationResult` response DTOs
- [x] 2.4 Add `hasActiveInvitationFor(email: String, workspaceId: WorkspaceId): Boolean` to `InvitationRepository` port interface
- [x] 2.5 Add `revoke(expectedVersion: Long)` method to `Invitation` aggregate with optimistic lock check (throw `OptimisticLockingFailureException` on mismatch)
- [x] 2.6 Create `CreateInvitationHandler` in `platformadmin/application/handler/`: authorize via `OperatorAccessResolver.requirePlatformInvitationCreate()`, normalize email, call `hasActiveInvitationFor()`, generate token via `InvitationTokenCodec`, build `Invitation(source=DIRECT, target=EXISTING_WORKSPACE)`, persist, emit `InvitationCreated` domain event, schedule `InvitationNotificationRequested`, increment counter
- [x] 2.7 Create `RevokeInvitationHandler` in `platformadmin/application/handler/`: authorize via `OperatorAccessResolver.requirePlatformInvitationRevoke()`, load invitation, assert workspace ownership, call `invitation.revoke(expectedVersion)`, emit `InvitationRevoked` domain event, persist, increment counter
- [x] 2.8 Create `AdminInvitationController` in `platformadmin/infrastructure/http/`: `POST /api/admin/invitations` → `CreateInvitationHandler`, `POST /api/admin/invitations/{id}/revoke` → `RevokeInvitationHandler`; map exceptions to HTTP 201/200/400/403/404/409
- [x] 2.9 Create `InvitationNotificationAdapter` in `platformadmin/infrastructure/notification/`: subscribe to `InvitationCreated` and `InvitationRevoked` domain events; call `NotificationGateway` to schedule email delivery; failures are async-retried, do not propagate
- [x] 2.10 Wire `InvitationNotificationAdapter` in `PlatformAdminBootstrapConfiguration.kt` to the domain event publisher
- [x] 2.11 Load `$impeccable` skill — then design the Create Invitation screen: email input, role selector, workspace selector, submit, error states, loading state, success feedback. Apply Nothing-inspired dark theme per `.agents/DESIGN.md`.
- [x] 2.12 Design the Revoke Invitation flow: confirmation dialog, reason optional, confirm/cancel, error handling, success toast. Same design system.
- [x] 2.13 Wire new permissions (`platform.invitations.create`, `platform.invitations.revoke`, `platform.invitations.resend`) to UI: guard button visibility and form submission in the admin invitation panels.
- [x] 2.14 Create `ResendInvitationHandler` in `platformadmin/application/handler/`: authorize via `requirePlatformInvitationResend()`, load invitation, assert PENDING/EXPIRED status, generate new token, update hash, emit `InvitationResent` domain event, schedule notification, increment counter
- [x] 2.15 Add `POST /api/admin/invitations/{id}/resend` → `ResendInvitationHandler` in `AdminInvitationController`; map exceptions to HTTP 200/403/404

## Phase 3: Persistence and Concurrency Safety (PR 3)

- [x] 3.1 Implement `hasActiveInvitationFor()` in `RdbcInvitationRepository.kt`: query `SELECT 1 FROM invitations WHERE workspace_id = :ws AND normalized_email = :email AND status = 'ACTIVE' LIMIT 1`
- [x] 3.2 Add unique index migration: `CREATE UNIQUE INDEX idx_inv_active_email_ws ON invitations(workspace_id, normalized_email) WHERE status = 'ACTIVE'` (or partial index per DB dialect)
- [x] 3.3 Implement optimistic-lock revoke in `RdbcInvitationRepository.kt`: `UPDATE invitations SET status = 'REVOKED', revoked_at = :now, version = version + 1 WHERE id = :id AND version = :expectedVersion AND status = 'ACTIVE'`
- [x] 3.4 Map `OptimisticLockingFailureException` → HTTP 409 in the controller advice

## Phase 4: Testing

- [x] 4.1 Write `CreateInvitationHandlerTest.kt`: happy path, duplicate active invitation (409), workspace not found, missing permission (403), notification-scheduling-failure-does-not-rollback
- [x] 4.2 Write `RevokeInvitationHandlerTest.kt`: happy path, invitation not found (404), version mismatch (409), missing permission (403), already-revoked idempotency (200 or 404 per spec)
- [x] 4.3 Write `InvitationTest.kt` or inline test for `Invitation.revoke(expectedVersion)` optimistic lock behavior
- [x] 4.4 Write `RdbcInvitationRepositoryImplTest.kt` or integration test: `hasActiveInvitationFor` returns true/false correctly, optimistic-lock UPDATE affects 0 rows on version mismatch
- [x] 4.5 Create `invitations-direct.feature` BDD file under `server/smp/src/test/resources/features/platformadmin/`: scenarios for create success, create duplicate (409), create unauthorized (403), revoke success, revoke not found (404), revoke unauthorized (403), resend success, resend already-consumed (409), resend unauthorized (403) — tag with `@smoke @fast`
- [x] 4.6 Implement BDD step definitions in `PlatformAdminInvitationSteps.kt` using `BddDatabaseSupport`
- [x] 4.7 Write `ResendInvitationHandlerTest.kt`: happy path, already-consumed (409), missing permission (403)

## Phase 5: Verification and Cleanup

- [ ] 5.1 Run `just backend-check` (Detekt + tests, excludes BDD by design) — confirm clean
- [x] 5.2 Run `just backend-bdd-fast` — confirm all `@smoke @fast` BDD scenarios pass
- [ ] 5.3 Verify `POST /api/admin/invitations` returns 201 with `{id, status}` and no token field
- [ ] 5.4 Verify `POST /api/admin/invitations/{id}/revoke` returns 200 with `{id, status}`
- [ ] 5.5 Verify 401 for unauthenticated, 403 for missing permission, 409 for duplicate, 404 for missing invitation
- [ ] 5.6 Update `state.yaml`: set `current_phase: tasks`, add `tasks` to `completed`, set `next: apply`

## Dependency Order

```
PR 1 (trunk)        →  PlatformPermission + role mapping
PR 2 (→ PR 1)       →  Handlers + Controller + NotificationAdapter
PR 3 (→ PR 2)       →  Repository implementation + Tests
```

Phase 2 handlers depend on Phase 1 permissions existing in the enum. Phase 3 persistence tests depend on Phase 2 handler interfaces being stable. No circular dependencies.
