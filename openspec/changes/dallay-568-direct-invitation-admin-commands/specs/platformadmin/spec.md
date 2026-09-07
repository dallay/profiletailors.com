# Platform Admin — Invitation Commands

## Purpose

This spec covers the `CreateInvitationHandler` and `RevokeInvitationHandler` in the
`platformadmin` bounded context. These handlers own the admin-facing API surface for direct
invitations. They orchestrate the `Invitation` aggregate via `InvitationRepository`, enforce
authorization via `OperatorAccessResolver`, publish audit events, and schedule notifications.
Raw invitation tokens never cross API boundaries.

## Requirements

### Requirement: CreateInvitationHandler contract

`CreateInvitationHandler` MUST:

- Accept `CreateInvitationCommand(workspaceId, email, role)` as input
- Validate the operator has `platform.invitations.create` via `OperatorAccessResolver`
- Normalize the email to trimmed-lowercase form
- Generate CSPRNG token material and persist only its hash via `InvitationRepository`
- Construct `Invitation(source=DIRECT, target=EXISTING_WORKSPACE, workspaceId, ...)` as `ACTIVE`
- Use conditional INSERT to detect duplicate active invitations for same workspace+email
- Emit `INVITATION_CREATED` audit event with low-cardinality fields only
- Schedule `InvitationNotificationRequested` after successful persistence
- Increment platform invitation creation counter
- Return `CreateInvitationResult(invitationId, status)`

#### Scenario: CreateInvitationHandler — happy path

- GIVEN `OperatorAccessResolver` returns `OperatorAccess(principal, {PLATFORM_OPERATOR})`
- WHEN `CreateInvitationHandler.handle(CreateInvitationCommand(ws-123, "User@Example.com", MEMBER))` is called
- THEN the handler normalizes email to "user@example.com"
- AND generates CSPRNG token material
- AND persists token hash via `InvitationRepository.conditionalInsert()`
- AND `Invitation(source=DIRECT, target=EXISTING_WORKSPACE, workspaceId=ws-123, ...)` is stored as `ACTIVE`
- AND `INVITATION_CREATED` is published
- AND `InvitationNotificationRequested` is scheduled
- AND counter `platform.invitations.created` is incremented
- AND result `CreateInvitationResult(invitationId, ACTIVE)` is returned

#### Scenario: CreateInvitationHandler — duplicate active invitation

- GIVEN `OperatorAccessResolver` returns `OperatorAccess(principal, {PLATFORM_OPERATOR})`
- AND `InvitationRepository.conditionalInsert()` throws `InvitationAlreadyExistsException`
- WHEN `CreateInvitationHandler.handle()` is called
- THEN HTTP 409 is returned
- AND no audit event is emitted

### Requirement: RevokeInvitationHandler contract

`RevokeInvitationHandler` MUST:

- Accept `RevokeInvitationCommand(invitationId)` as input
- Validate the operator has `platform.invitations.revoke` via `OperatorAccessResolver`
- Call `InvitationRepository.conditionalRevoke(invitationId, ACTIVE)` expecting `ACTIVE` current state
- Emit `INVITATION_REVOKED` audit event with low-cardinality fields only
- Increment platform invitation revocation counter
- Return `RevokeInvitationResult(invitationId, REVOKED)`

#### Scenario: RevokeInvitationHandler — happy path

- GIVEN `OperatorAccessResolver` returns `OperatorAccess(principal, {PLATFORM_OPERATOR})`
- AND `InvitationRepository` contains an `ACTIVE` invitation with `id = invitationId`
- WHEN `RevokeInvitationHandler.handle(RevokeInvitationCommand(invitationId))` is called
- THEN the conditional update transitions invitation to `REVOKED`
- AND `INVITATION_REVOKED` is published
- AND counter `platform.invitations.revoked` is incremented
- AND result `RevokeInvitationResult(invitationId, REVOKED)` is returned

#### Scenario: RevokeInvitationHandler — invitation not active

- GIVEN `OperatorAccessResolver` returns `OperatorAccess(principal, {PLATFORM_OPERATOR})`
- AND `InvitationRepository` contains an invitation with `id = invitationId` that is not `ACTIVE`
- WHEN `RevokeInvitationHandler.handle()` is called
- THEN the conditional update affects zero rows
- AND `InvitationNotFoundException` or conflict is returned

### Requirement: Authorization enforcement

Controllers guarding `/api/admin/invitations` endpoints MUST enforce `PlatformAccessDeniedException`
(→ HTTP 403) when `OperatorAccessResolver.resolve().effectivePermissions()` does not contain the
required permission. Default-deny applies to unauthenticated requests (HTTP 401).

#### Scenario: Unauthenticated request

- GIVEN no authenticated principal
- WHEN POST /api/admin/invitations is received
- THEN HTTP 401 is returned

#### Scenario: Authenticated but missing permission

- GIVEN an authenticated principal with `SUPPORT_AGENT` role
- WHEN POST /api/admin/invitations is received
- THEN `PlatformAccessDeniedException` is thrown
- AND HTTP 403 is returned

### Requirement: Token never crosses API boundary

Raw invitation tokens MUST NOT appear in HTTP request or response bodies, audit events, logs,
or metrics. Only the hashed token material is persisted; token lookup and delivery follow
DALLAY-566.

#### Scenario: Response contains no raw token

- GIVEN a successful invitation creation
- WHEN the HTTP response is inspected
- THEN `{id, status}` is returned with no token field

#### Scenario: Audit event contains no raw token

- GIVEN a successful invitation creation
- WHEN `INVITATION_CREATED` event is consumed
- THEN it contains invitation ID, workspace ID, status, outcome, timestamps only

### Requirement: Notification scheduling after successful creation

`InvitationNotificationRequested` MUST be scheduled after the invitation is successfully persisted.
Failure to schedule is async and retried; it MUST NOT roll back the invitation.

#### Scenario: Notification scheduling failure does not roll back invitation

- GIVEN an invitation is successfully persisted as `ACTIVE`
- WHEN `InvitationNotificationRequested` scheduling throws an exception
- THEN the invitation remains `ACTIVE` in storage
- AND the scheduling failure is retried asynchronously
- AND HTTP 201 is still returned to the admin

### Requirement: Concurrency-safe duplicate prevention

A unique index on `(workspace_id, normalized_email, status = 'ACTIVE')` MUST prevent duplicate
active invitations. The handler MUST use conditional INSERT so that only one concurrent request
for the same email+workspace succeeds.

#### Scenario: Concurrent duplicate creation — exactly one succeeds

- GIVEN two concurrent POST /api/admin/invitations requests for the same workspace+email
- WHEN both transactions execute simultaneously
- THEN exactly one receives HTTP 201
- AND the other receives HTTP 409
