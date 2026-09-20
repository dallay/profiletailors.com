@smoke @platform-admin @fast @postgres
Feature: Platform administration access control and waitlist management
  Platform operators manage waitlist candidates through a dedicated admin API.
  Access requires an active platform role assignment; workspace roles are insufficient.

  Background:
    Given a platform operator with role "PLATFORM_OPERATOR" is authenticated

  # ── Access control ─────────────────────────────────────────────────────────

  Scenario: Unauthenticated request to admin endpoint returns 401
    When an unauthenticated principal requests the admin waitlist endpoint
    Then the admin response status should be 401

  Scenario: Principal with no platform role cannot access admin waitlist endpoint
    Given the authenticated principal has no active platform role
    When the principal requests the admin waitlist endpoint
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"

  Scenario: Platform operator can list waitlist entries
    Given a pending waitlist entry exists for "operator-test@example.com"
    When the platform operator requests the admin waitlist endpoint
    Then the admin response status should be 200
    And the waitlist result should be paginated

  Scenario: Platform operator can search waitlist entries by email
    Given a pending waitlist entry exists for "search-test@example.com"
    When the platform operator searches the waitlist for "Search-Test@example.com"
    Then the admin response status should be 200
    And the waitlist result should contain 1 entries
    And the waitlist result should contain an entry with email "search-test@example.com"

  Scenario: Searching the waitlist for an email with no match returns no entries
    Given a pending waitlist entry exists for "no-match-test@example.com"
    When the platform operator searches the waitlist for "unknown-search@example.com"
    Then the admin response status should be 200
    And the waitlist result should contain 0 entries

  Scenario: Platform operator can filter waitlist entries by status
    Given a pending waitlist entry exists for "pending-filter@example.com"
    And an invited waitlist entry with an active invitation exists for "invited-filter@example.com"
    When the platform operator filters the waitlist by status "PENDING"
    Then the admin response status should be 200
    And the waitlist result should contain 1 entries
    And the waitlist result should contain an entry with email "pending-filter@example.com"

  Scenario: AUDITOR cannot invite a candidate
    Given the authenticated principal has the role "AUDITOR"
    And a pending waitlist entry exists for "auditor-test@example.com"
    When the auditor attempts to invite the waitlist entry
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"
    And the waitlist entry status should remain "PENDING"

  # ── Waitlist invitation ────────────────────────────────────────────────────

  Scenario: Operator invites a pending waitlist entry
    Given a pending waitlist entry exists for "invite-test@example.com"
    When the platform operator invites the waitlist entry
    Then the admin response status should be 201
    And one active invitation should be created for the entry
    And the waitlist entry status should become "INVITED"

  Scenario: Inviting a converted entry returns 409
    Given a converted waitlist entry exists for "converted-test@example.com"
    When the platform operator invites the waitlist entry
    Then the admin response status should be 409
    And the admin response code should be "WAITLIST_ENTRY_ALREADY_CONVERTED"

  Scenario: Cancelling an invited entry revokes the active invitation
    Given an invited waitlist entry with an active invitation exists for "cancel-test@example.com"
    When the platform operator cancels the waitlist entry with reason "spam account"
    Then the admin response status should be 200
    And the waitlist entry status should become "CANCELLED"
    And no active invitation should remain for the entry

  Scenario: Cancelling a converted entry returns 409
    Given a converted waitlist entry exists for "converted-cancel@example.com"
    When the platform operator cancels the waitlist entry with reason "test"
    Then the admin response status should be 409
    And the admin response code should be "WAITLIST_ENTRY_ALREADY_CONVERTED"

  # ── Invitation acceptance ─────────────────────────────────────────────────

  Scenario: Unauthenticated principal cannot accept an invitation
    Given an active direct invitation exists for "jwt-user@example.com"
    When an unauthenticated principal accepts the invitation
    Then the admin response status should be 401

  Scenario: Authenticated principal must provide an invitation token
    When the authenticated principal accepts the invitation with an empty token
    Then the admin response status should be 400

  Scenario: Authenticated principal receives a safe error for an unavailable invitation
    Given an active direct invitation exists for "jwt-user@example.com"
    When the authenticated principal accepts the invitation with an unavailable token
    Then the admin response status should be 400
    And the admin response code should be "INVITATION_INVALID"

  Scenario: Authenticated principal accepts a direct invitation and receives a safe result
    Given an active direct invitation exists for "jwt-user@example.com"
    When the authenticated principal accepts the invitation
    Then the admin response status should be 200
    And the invitation acceptance workspace should be "invitation-workspace"
    And the invitation acceptance membership status should be "ACTIVE"
    And the invitation response should not contain the token
    And the invitation status should become "ACCEPTED"

  Scenario: Replaying an accepted invitation is denied
    Given an active direct invitation exists for "jwt-user@example.com"
    When the authenticated principal accepts the invitation
    And the authenticated principal accepts the invitation again
    Then the admin response status should be 409
    And the admin response code should be "INVITATION_ALREADY_CONSUMED"

  Scenario: Invitation acceptance is isolated from the request workspace
    Given an active direct invitation exists for "jwt-user@example.com"
    When the authenticated principal accepts the invitation
    Then the invitation acceptance workspace should be "invitation-workspace"

  Scenario: Authenticated existing identity accepts without duplicate records
    Given an active direct invitation exists for "jwt-user@example.com"
    And the authenticated principal has an existing credential and workspace membership
    When the authenticated principal accepts the invitation
    Then the admin response status should be 200
    And the invitation acceptance workspace should be "invitation-workspace"
    And the invitation acceptance membership status should be "ACTIVE"
    And the invitation response should not contain the token
    And the invitation response should not contain "jwt-user@example.com"
    And the invitation status should become "ACCEPTED"
    And the authenticated principal should have exactly one identity and credential
    And the invitation workspace should have exactly one workspace and membership for the authenticated principal

  # ── Invitation operations ─────────────────────────────────────────────────

  Scenario: Revoking an active invitation marks it as revoked
    Given an invited waitlist entry with an active invitation exists for "revoke-test@example.com"
    When the platform operator revokes the active invitation
    Then the admin response status should be 200
    And the invitation status should be "REVOKED"
    And the waitlist entry status should remain "INVITED"

  # ── Admin audit trail ─────────────────────────────────────────────────────

  Scenario: Inviting a waitlist entry creates an audit event
    Given a pending waitlist entry exists for "audit-invite@example.com"
    When the platform operator invites the waitlist entry
    Then an audit event with action "WAITLIST_ENTRY_INVITED" should be recorded
    And the audit event should not contain a raw invitation token

  # ── Bulk waitlist invitation ─────────────────────────────────────────────

  Scenario: Operator bulk invites a mixed batch with partial success
    Given a pending waitlist entry exists for "bulk-ok-a@example.com"
    And a pending waitlist entry exists for "bulk-ok-b@example.com"
    And a pending waitlist entry exists for "bulk-ok-c@example.com"
    And an invited waitlist entry with an active invitation exists for "bulk-skipped@example.com"
    And a converted waitlist entry exists for "bulk-failed@example.com"
    When the platform operator bulk invites the tracked waitlist entries
    Then the admin response status should be 200
    And the bulk invite summary should be 3 invited, 1 skipped and 1 failed
    And the bulk invite results should not contain sensitive values

  Scenario: Retrying a bulk invite yields skips without duplicates
    Given a pending waitlist entry exists for "bulk-retry-a@example.com"
    And a pending waitlist entry exists for "bulk-retry-b@example.com"
    When the platform operator bulk invites the tracked waitlist entries
    And the platform operator bulk invites the tracked waitlist entries
    Then the admin response status should be 200
    And the bulk invite summary should be 0 invited, 2 skipped and 0 failed

  Scenario: Bulk invite after a single invite keeps exactly one invitation
    Given a pending waitlist entry exists for "bulk-race@example.com"
    When the platform operator invites the waitlist entry
    And the platform operator bulk invites the tracked waitlist entries
    Then the admin response status should be 200
    And the bulk invite summary should be 0 invited, 1 skipped and 0 failed
    And one active invitation should be created for the entry

  Scenario: AUDITOR cannot bulk invite candidates
    Given the authenticated principal has the role "AUDITOR"
    And a pending waitlist entry exists for "bulk-denied@example.com"
    When the auditor bulk invites the tracked waitlist entries
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"
    And the waitlist entry status should remain "PENDING"

  @user-administration
  Scenario: Support agent cannot disable a user
    Given a registered user exists for "control-test@example.com"
    And the authenticated principal has the role "SUPPORT_AGENT"
    When the operator disables the registered user
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"

  @user-administration
  Scenario: Platform operator disables a user and replays the command
    Given a registered user exists for "control-test@example.com"
    When the platform operator disables the registered user
    Then the admin response status should be 200
    And the registered user account state should be "DISABLED"
    When the platform operator repeats the disable command with the same idempotency key
    Then the admin response status should be 200
    And the disable operation should have been executed once

  @user-administration
  Scenario: User detail exposes workspace membership data
    Given a registered user with a workspace membership exists for "detail-test@example.com"
    When the platform operator requests the registered user detail
    Then the admin response status should be 200
    And the user detail should include one workspace membership

  @user-administration
  Scenario: Workspace membership lookup requires base user read permission
    Given a registered user exists for "workspace-permission@example.com"
    And the authenticated principal has the role "AUDITOR"
    When the platform operator requests the registered user workspaces
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"

  # ── Platform configuration: registration mode ──────────────────────────────

  @platform-configuration
  Scenario: Authorized read returns the current registration mode
    Given the registration mode is currently "OPEN"
    When the platform operator requests the current registration mode
    Then the admin response status should be 200
    And the registration mode response should be "OPEN"

  @platform-configuration
  Scenario: Unauthorized read of the registration mode is denied without disclosure
    Given the authenticated principal has no active platform role
    When the principal requests the current registration mode
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"
    And the registration mode response should not disclose a mode value

  @platform-configuration
  Scenario: Owner changes the registration mode and the change is observable on the next read
    Given the authenticated principal has the role "PLATFORM_OWNER"
    And the registration mode is currently "OPEN"
    When the owner changes the registration mode to "INVITE_ONLY"
    Then the admin response status should be 200
    And the registration mode response should be "INVITE_ONLY"
    When the platform operator requests the current registration mode
    Then the registration mode response should be "INVITE_ONLY"

  @platform-configuration
  Scenario: Non-owner write is denied and audited without changing the persisted mode
    Given the registration mode is currently "OPEN"
    When the platform operator attempts to change the registration mode to "CLOSED"
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"
    And the persisted registration mode should be "OPEN"
    And a "CONFIGURATION_CHANGED" "REJECTED" audit event should be recorded

  @platform-configuration
  Scenario: Invalid registration mode value from an owner is rejected without changing the persisted mode
    Given the authenticated principal has the role "PLATFORM_OWNER"
    And the registration mode is currently "OPEN"
    When the owner attempts to change the registration mode to "BOGUS"
    Then the admin response status should be 400
    And the persisted registration mode should be "OPEN"
    And a "CONFIGURATION_CHANGED" "FAILED" audit event should be recorded

  @platform-configuration
  Scenario: Concurrent writes never lose an update
    Given the authenticated principal has the role "PLATFORM_OWNER"
    And the registration mode is currently "OPEN"
    When two owners concurrently change the registration mode to "INVITE_ONLY" and "CLOSED"
    Then both concurrent registration mode changes should return 200
    And the persisted registration mode should be "INVITE_ONLY" or "CLOSED"
    And each concurrent write should be audited with a consistent previous and new mode

  @platform-configuration
  Scenario: Admin-set mode survives a restart-equivalent fresh request
    Given the authenticated principal has the role "PLATFORM_OWNER"
    And the registration mode is currently "OPEN"
    When the owner changes the registration mode to "CLOSED"
    Then the admin response status should be 200
    When a new request reads the current registration mode
    Then the registration mode response should be "CLOSED"
