# Platform Admin — Invitation Commands

## Purpose

This spec covers the `CreateInvitationHandler`, `RevokeInvitationHandler`, and
`ResendInvitationHandler` in the `platformadmin` bounded context. These handlers own the
admin-facing API surface for direct invitations. They orchestrate the `Invitation` aggregate via
`InvitationRepository`, enforce authorization from the command's operator roles (controllers stay
thin: they only resolve the authenticated operator), publish audit events, and publish domain
events for post-commit email delivery. Raw invitation tokens never cross API boundaries.

## Requirements

### Requirement: CreateInvitationHandler contract

`CreateInvitationHandler` MUST:

- Accept `CreateInvitationCommand(operatorPrincipalId, operatorRoles, email, target, workspaceId)` as input
- Validate the operator has `platform.invitations.create` from the command roles (no framework resolver inside the handler)
- Normalize the email once to trimmed-lowercase form and use it for lookup, persistence, and the notification event
- Generate CSPRNG token material and persist only its hash plus candidate key via `InvitationRepository.save()`
- Construct `Invitation(source=DIRECT, ...)` as `ACTIVE`
- Detect duplicates via `hasActiveInvitationFor(email, workspaceId, asOf)`; map a unique-conflict on `save()` to `InvitationAlreadyActiveException` (→ 409)
- Emit `INVITATION_CREATED` audit event with low-cardinality fields only
- Publish `InvitationIssued` after successful persistence for post-commit delivery
- Increment platform invitation creation counter
- Return `CreateInvitationResult(invitationId, status, expiresAt, version)`

#### Scenario: CreateInvitationHandler — happy path

- GIVEN an operator with `platform.invitations.create`
- WHEN `CreateInvitationHandler.handle()` is called with `email = "  User@Example.com  "`
- THEN the handler normalizes email to "user@example.com"
- AND generates CSPRNG token material
- AND persists token hash via `InvitationRepository.save()`
- AND `Invitation(source=DIRECT, ...)` is stored as `ACTIVE`
- AND `INVITATION_CREATED` is published
- AND `InvitationIssued` is published for post-commit delivery
- AND counter `platform.invitations.created` is incremented
- AND result `CreateInvitationResult(invitationId, ACTIVE, expiresAt, version)` is returned

#### Scenario: CreateInvitationHandler — duplicate active invitation

- GIVEN an operator with `platform.invitations.create`
- AND a live `ACTIVE` invitation for the same normalized email and workspace
- WHEN `CreateInvitationHandler.handle()` is called
- THEN `InvitationAlreadyActiveException` is raised (pre-check or unique-conflict mapping)
- AND HTTP 409 is returned
- AND no audit event is emitted

### Requirement: RevokeInvitationHandler contract

`RevokeInvitationHandler` MUST:

- Accept `RevokeInvitationCommand(operatorPrincipalId, operatorRoles, invitationId, expectedVersion)` as input
- Validate the operator has `platform.invitations.revoke` from the command roles
- Load via `findById`; require `invitation.isActive(now)` (status AND expiry) else `InvitationNotRevocableException`
- Call `invitation.revoke(expectedVersion)` and persist via `updateIfVersionMatches`; a lost update raises `InvitationVersionConflictException` (→ 409)
- Emit `INVITATION_REVOKED` audit event with low-cardinality fields only (no revocation domain event is published)
- Increment platform invitation revocation counter
- Return `RevokeInvitationResult(invitationId)`

#### Scenario: RevokeInvitationHandler — happy path

- GIVEN an operator with `platform.invitations.revoke`
- AND a live `ACTIVE` invitation with `id = invitationId`
- WHEN `RevokeInvitationHandler.handle()` is called with the current version
- THEN the version-checked update transitions the invitation to `REVOKED`
- AND `INVITATION_REVOKED` is published
- AND counter `platform.invitations.revoked` is incremented
- AND result `RevokeInvitationResult(invitationId)` is returned

#### Scenario: RevokeInvitationHandler — invitation not active

- GIVEN an operator with `platform.invitations.revoke`
- AND an invitation with `id = invitationId` that is expired, consumed, or revoked
- WHEN `RevokeInvitationHandler.handle()` is called
- THEN `InvitationNotRevocableException` is raised (404 or 409 at the API)

### Requirement: ResendInvitationHandler contract

`ResendInvitationHandler` MUST:

- Accept `ResendInvitationCommand(operatorPrincipalId, operatorRoles, invitationId)` as input
- Validate the operator has `platform.invitations.resend` from the command roles
- Load via `findById` (404 if absent); reject `source != DIRECT` with `InvitationNotResendableException`
- Require `invitation.isActive(now)` — expired invitations are never revived
- Rotate token material, extend expiry, bump the version, persist via `updateIfVersionMatches`
- Publish `DirectInvitationResent` after persistence for post-commit delivery
- Return `ResendInvitationResult(invitationId, status, expiresAt, version)`

### Requirement: Authorization enforcement

Controllers guarding `/api/admin/invitations/direct`, `/direct-revoke`, and `/direct-resend`
endpoints MUST resolve the authenticated operator (401 when absent) and pass its roles into the
command. Handlers MUST enforce `PlatformAccessDeniedException` (→ HTTP 403) from the command
roles. Default-deny applies to unauthenticated requests (HTTP 401).

#### Scenario: Unauthenticated request

- GIVEN no authenticated principal
- WHEN POST /api/admin/invitations/direct is received
- THEN HTTP 401 is returned

#### Scenario: Authenticated but missing permission

- GIVEN an authenticated principal with `SUPPORT_AGENT` role
- WHEN POST /api/admin/invitations/direct is received
- THEN `PlatformAccessDeniedException` is thrown
- AND HTTP 403 is returned

### Requirement: Token never crosses API boundary

Raw invitation tokens MUST NOT appear in HTTP request or response bodies, audit events, logs,
or metrics. Only the hashed token material is persisted; token lookup and delivery follow
DALLAY-566.

#### Scenario: Response contains no raw token

- GIVEN a successful invitation creation or resend
- WHEN the HTTP response is inspected
- THEN `{invitationId, status, expiresAt, version}` is returned with no token field

#### Scenario: Audit event contains no raw token

- GIVEN a successful invitation creation
- WHEN `INVITATION_CREATED` event is consumed
- THEN it contains invitation ID, workspace ID, status, outcome, timestamps only

### Requirement: Notification delivery after successful persistence

`InvitationIssued` (create) and `DirectInvitationResent` (resend) MUST be published after the
invitation is successfully persisted, for post-commit email delivery by `SendInvitationEmailConsumer`.
A publish failure propagates without rolling back the saved invitation (pinned by unit test); it
MUST NOT be silently swallowed.

#### Scenario: Event publish failure does not roll back the invitation

- GIVEN an invitation is successfully persisted as `ACTIVE`
- WHEN the domain-event publish throws an exception
- THEN the invitation remains `ACTIVE` in storage
- AND the failure propagates to the caller

### Requirement: Concurrency-safe duplicate prevention

The pre-existing partial unique index on live workspace+email invitations MUST back the
`hasActiveInvitationFor(email, workspaceId, asOf)` pre-check. `save()` MUST map a unique
conflict to `InvitationAlreadyActiveException` so that only one concurrent request for the
same email+workspace succeeds.

#### Scenario: Concurrent duplicate creation — exactly one succeeds

- GIVEN two concurrent POST /api/admin/invitations/direct requests for the same workspace+email
- WHEN both transactions execute simultaneously
- THEN exactly one receives HTTP 201
- AND the other receives HTTP 409
