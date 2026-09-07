# Delta: DALLAY-568 — Direct Invitation Admin Commands

## ADDED Requirements

### Requirement: Admin creates a direct invitation

A platform administrator with `platform.invitations.create` MAY issue a direct invitation for a
normalized email within a workspace. The invitation MUST be constructed with `source = DIRECT`,
`target = EXISTING_WORKSPACE`, and `workspaceId` from the request. CSPRNG token material MUST be
generated and its hash persisted; the raw token MUST NOT cross the API boundary.

The handler MUST emit `INVITATION_CREATED` audit event containing only low-cardinality fields
(invitation ID, workspace ID, status, outcome, timestamps, correlation data). Raw tokens and
full target emails MUST NOT cross audit or metric boundaries.

The handler MUST schedule `InvitationNotificationRequested` after successful persistence.
Notification failure is async and retried; it MUST NOT roll back the invitation.

#### Scenario: Admin creates invitation — success

- GIVEN an authenticated platform operator with `platform.invitations.create` permission
- WHEN POST /api/admin/invitations with `{workspaceId, email, role}` is received
- THEN the handler generates CSPRNG token material and persists its hash only
- AND the invitation is stored as `ACTIVE` with `source=DIRECT`, `target=EXISTING_WORKSPACE`
- AND `INVITATION_CREATED` audit event is published (no raw token, no full email)
- AND `InvitationNotificationRequested` is scheduled
- AND platform invitation creation counter is incremented
- AND HTTP 201 is returned with `{id, status}`

#### Scenario: Admin creates invitation — duplicate active invitation

- GIVEN an authenticated platform operator with `platform.invitations.create` permission
- AND an existing `ACTIVE` invitation for the same normalized email in the same workspace
- WHEN POST /api/admin/invitations with `{workspaceId, email, role}` is received
- THEN the conditional INSERT fails due to unique index on `(workspace, normalized_email, status='ACTIVE')`
- AND HTTP 409 Conflict is returned with a machine-safe error
- AND no audit event is emitted

#### Scenario: Admin creates invitation — missing permission

- GIVEN an authenticated principal without `platform.invitations.create`
- WHEN POST /api/admin/invitations is received
- THEN `PlatformAccessDeniedException` is thrown
- AND HTTP 403 is returned

### Requirement: Admin revokes an active invitation

A platform administrator with `platform.invitations.revoke` MAY revoke an active invitation.
The handler MUST transition the invitation from `ACTIVE` to `REVOKED` using the canonical
conditional transition. The raw token MUST be invalidated atomically — no further acceptance is
possible after revocation.

The handler MUST emit `INVITATION_REVOKED` audit event. Raw tokens MUST NOT cross audit or
metric boundaries. Platform invitation revocation counter MUST be incremented.

#### Scenario: Admin revokes invitation — success

- GIVEN an authenticated platform operator with `platform.invitations.revoke` permission
- AND an `ACTIVE` invitation with `id = invitationId`
- WHEN DELETE /api/admin/invitations/{invitationId} is received
- THEN the handler transitions invitation to `REVOKED` via conditional update
- AND `INVITATION_REVOKED` audit event is published (no raw token)
- AND platform invitation revocation counter is incremented
- AND HTTP 200 is returned with `{id, status}`

#### Scenario: Admin revokes invitation — already revoked

- GIVEN an authenticated platform operator with `platform.invitations.revoke` permission
- AND an invitation with `id = invitationId` that is not `ACTIVE`
- WHEN DELETE /api/admin/invitations/{invitationId} is received
- THEN the conditional update affects zero rows
- AND HTTP 404 or 409 is returned

#### Scenario: Admin revokes invitation — missing permission

- GIVEN an authenticated principal without `platform.invitations.revoke`
- WHEN DELETE /api/admin/invitations/{invitationId} is received
- THEN `PlatformAccessDeniedException` is thrown
- AND HTTP 403 is returned

## REMOVED Requirements

(None — this change adds net new behavior only.)
