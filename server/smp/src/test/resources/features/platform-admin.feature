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
    Then the admin response status should be 204
    And the waitlist entry status should become "CANCELLED"
    And no active invitation should remain for the entry

  Scenario: Cancelling a converted entry returns 409
    Given a converted waitlist entry exists for "converted-cancel@example.com"
    When the platform operator cancels the waitlist entry with reason "test"
    Then the admin response status should be 409
    And the admin response code should be "WAITLIST_ENTRY_ALREADY_CONVERTED"

  # ── Invitation operations ─────────────────────────────────────────────────

  Scenario: Revoking an active invitation marks it as revoked
    Given an invited waitlist entry with an active invitation exists for "revoke-test@example.com"
    When the platform operator revokes the active invitation
    Then the admin response status should be 204
    And the invitation status should be "REVOKED"
    And the waitlist entry status should remain "INVITED"

  # ── Admin audit trail ─────────────────────────────────────────────────────

  Scenario: Inviting a waitlist entry creates an audit event
    Given a pending waitlist entry exists for "audit-invite@example.com"
    When the platform operator invites the waitlist entry
    Then an audit event with action "WAITLIST_ENTRY_INVITED" should be recorded
    And the audit event should not contain a raw invitation token
