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

The handler MUST publish `InvitationIssued` after successful persistence so the existing
post-commit email consumer delivers the accept URL. A publish failure propagates without
rolling back the saved invitation (pinned by unit test); it MUST NOT be silently swallowed.

#### Scenario: Admin creates invitation — success

- GIVEN an authenticated platform operator with `platform.invitations.create` permission
- WHEN POST /api/admin/invitations/direct with `{email, target, workspaceId}` is received
- THEN the handler normalizes the email once (`trim().lowercase()`) and persists its hash only
- AND the invitation is stored as `ACTIVE` with `source=DIRECT`
- AND `INVITATION_CREATED` audit event is published (no raw token, no full email)
- AND `InvitationIssued` is published for post-commit delivery
- AND platform invitation creation counter is incremented
- AND HTTP 201 is returned with `{invitationId, status, expiresAt, version}` and no token field

#### Scenario: Admin creates invitation — duplicate active invitation

- GIVEN an authenticated platform operator with `platform.invitations.create` permission
- AND an existing live `ACTIVE` invitation (not expired) for the same normalized email in the same workspace
- WHEN POST /api/admin/invitations/direct with `{email, target, workspaceId}` is received
- THEN the pre-check query — or the unique index via `save()` on a race — raises `InvitationAlreadyActiveException`
- AND HTTP 409 Conflict is returned with a machine-safe error
- AND no audit event is emitted

#### Scenario: Admin creates invitation — missing permission

- GIVEN an authenticated principal without `platform.invitations.create`
- WHEN POST /api/admin/invitations/direct is received
- THEN `PlatformAccessDeniedException` is thrown
- AND HTTP 403 is returned

### Requirement: Admin resends a direct invitation

A platform administrator with `platform.invitations.resend` MAY rotate the token material of a
DIRECT invitation that is still valid (`isActive(now)`). WAITLIST invitations MUST be rejected —
they keep their own resend path with limits. Expired invitations MUST NOT be revived. The
response MUST carry the bumped version for later revokes.

#### Scenario: Admin resends invitation — success

- GIVEN an operator with `platform.invitations.resend` and a live DIRECT invitation
- WHEN POST /api/admin/invitations/{invitationId}/direct-resend is received
- THEN token material is rotated, expiry extended, version bumped
- AND `DirectInvitationResent` is published for post-commit delivery
- AND HTTP 200 is returned with `{invitationId, status, expiresAt, version}`

### Requirement: Admin revokes an active invitation

A platform administrator with `platform.invitations.revoke` MAY revoke an active invitation.
The handler MUST transition the invitation from `ACTIVE` to `REVOKED` using the canonical
conditional transition. The raw token MUST be invalidated atomically — no further acceptance is
possible after revocation.

The handler MUST emit `INVITATION_REVOKED` audit event. Raw tokens MUST NOT cross audit or
metric boundaries. Platform invitation revocation counter MUST be incremented.

#### Scenario: Admin revokes invitation — success

- GIVEN an authenticated platform operator with `platform.invitations.revoke` permission
- AND a live `ACTIVE` invitation with `id = invitationId`
- WHEN POST /api/admin/invitations/{invitationId}/direct-revoke with the current version is received
- THEN the handler transitions invitation to `REVOKED` via version-checked update
- AND `INVITATION_REVOKED` audit event is published (no raw token)
- AND platform invitation revocation counter is incremented
- AND HTTP 200 is returned with `{invitationId}`

#### Scenario: Admin revokes invitation — already revoked

- GIVEN an authenticated platform operator with `platform.invitations.revoke` permission
- AND an invitation with `id = invitationId` that is not live `ACTIVE`
- WHEN POST /api/admin/invitations/{invitationId}/direct-revoke is received
- THEN HTTP 404 or 409 is returned

#### Scenario: Admin revokes invitation — missing permission

- GIVEN an authenticated principal without `platform.invitations.revoke`
- WHEN POST /api/admin/invitations/{invitationId}/direct-revoke is received
- THEN `PlatformAccessDeniedException` is thrown
- AND HTTP 403 is returned

## REMOVED Requirements

(None — this change adds net new behavior only.)
