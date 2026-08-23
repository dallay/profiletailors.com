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
    And the admin response code should be "INVITATION_NOT_ACCEPTABLE"

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
    Then the admin response status should be 400
    And the admin response code should be "INVITATION_NOT_ACCEPTABLE"

  Scenario: Invitation acceptance is isolated from the request workspace
    Given an active direct invitation exists for "jwt-user@example.com"
    When the authenticated principal accepts the invitation
    Then the invitation acceptance workspace should be "invitation-workspace"

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
