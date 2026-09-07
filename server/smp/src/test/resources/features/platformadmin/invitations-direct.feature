@smoke @platform-admin @fast @postgres
Feature: Direct invitation admin commands
  Platform owners and operators issue direct invitations without a waitlist entry.
  Creation rejects duplicates and revocation is optimistic-locked.

  Background:
    Given a platform operator with role "PLATFORM_OPERATOR" is authenticated

  Scenario: Operator creates a direct invitation
    When the platform operator creates a direct invitation for "direct-create@example.com"
    Then the admin response status should be 201
    And the direct invitation response should contain an id
    And the invitation response should not contain the token
    And the direct invitation status should be "ACTIVE"

  Scenario: Creating a duplicate direct invitation returns 409
    Given an active direct invitation exists for "direct-duplicate@example.com"
    When the platform operator creates a direct invitation for "direct-duplicate@example.com"
    Then the admin response status should be 409
    And the admin response code should be "INVITATION_ALREADY_ACTIVE"

  Scenario: Operator without permission cannot create a direct invitation
    Given the authenticated principal has the role "AUDITOR"
    When the platform operator creates a direct invitation for "direct-denied@example.com"
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"

  Scenario: Unauthenticated creation of a direct invitation returns 401
    When an unauthenticated principal creates a direct invitation for "direct-anon@example.com"
    Then the admin response status should be 401

  Scenario: Operator revokes a direct invitation
    Given an active direct invitation exists for "direct-revoke@example.com"
    When the platform operator revokes the direct invitation
    Then the admin response status should be 200
    And the direct invitation status should be "REVOKED"

  Scenario: Revoking a missing direct invitation returns 404
    When the platform operator revokes a missing direct invitation
    Then the admin response status should be 404
    And the admin response code should be "INVITATION_NOT_FOUND"

  Scenario: Operator without permission cannot revoke a direct invitation
    Given an active direct invitation exists for "direct-revoke-denied@example.com"
    And the authenticated principal has the role "AUDITOR"
    When the platform operator revokes the direct invitation
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"

  Scenario: Operator resends a direct invitation
    Given an active direct invitation exists for "direct-resend@example.com"
    When the platform operator resends the direct invitation
    Then the admin response status should be 200

  Scenario: Resending a consumed direct invitation returns 409
    Given a consumed direct invitation exists for "direct-consumed@example.com"
    When the platform operator resends the direct invitation
    Then the admin response status should be 409
    And the admin response code should be "INVITATION_NOT_RESENDABLE"

  Scenario: Operator without permission cannot resend a direct invitation
    Given an active direct invitation exists for "direct-resend-denied@example.com"
    And the authenticated principal has the role "AUDITOR"
    When the platform operator resends the direct invitation
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"
