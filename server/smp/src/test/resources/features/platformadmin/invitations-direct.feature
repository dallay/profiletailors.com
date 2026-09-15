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

  Scenario: Operator revokes a resent direct invitation with the returned version
    Given an active direct invitation exists for "direct-resend-revoke@example.com"
    When the platform operator resends the direct invitation
    Then the admin response status should be 200
    When the platform operator revokes the direct invitation with the returned version
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

  Scenario: Operator lists direct invitations without token material
    Given an active direct invitation exists for "direct-list-alpha@example.com"
    And an active direct invitation exists for "direct-list-beta@example.com"
    When the platform operator lists the direct invitations
    Then the admin response status should be 200
    And the direct invitation list should contain 2 invitations
    And the direct invitation list should contain an invitation with email "direct-list-alpha@example.com"
    And the direct invitation list should not expose token material

  Scenario: Direct invitation list is ordered newest first
    Given an expired direct invitation exists for "direct-list-old@example.com"
    And an active direct invitation exists for "direct-list-new@example.com"
    When the platform operator lists the direct invitations
    Then the admin response status should be 200
    And the first direct invitation in the list should have email "direct-list-new@example.com"

  Scenario: Direct invitation list honors pagination
    Given an active direct invitation exists for "direct-page-1@example.com"
    And an active direct invitation exists for "direct-page-2@example.com"
    And an active direct invitation exists for "direct-page-3@example.com"
    When the platform operator lists the direct invitations with "page=1&size=2"
    Then the admin response status should be 200
    And the direct invitation list should contain 1 invitation
    And the direct invitation list total should be 3

  Scenario: Direct invitation list combines status and email filters
    Given an active direct invitation exists for "ops-combined@example.com"
    And an active direct invitation exists for "other-combined@example.com"
    And a revoked direct invitation exists for "ops-revoked@example.com"
    When the platform operator lists the direct invitations with "status=ACTIVE&email=ops-"
    Then the admin response status should be 200
    And the direct invitation list should contain 1 invitation
    And the direct invitation list should contain an invitation with email "ops-combined@example.com"

  Scenario: Unauthenticated list of direct invitations returns 401
    When an unauthenticated principal lists the direct invitations
    Then the admin response status should be 401

  Scenario: Operator without permission cannot list direct invitations
    Given the authenticated principal has the role "AUDITOR"
    When the platform operator lists the direct invitations
    Then the admin response status should be 403
    And the admin response code should be "PLATFORM_ACCESS_DENIED"

  Scenario: Empty direct invitation list returns an empty page
    When the platform operator lists the direct invitations with "status=REVOKED"
    Then the admin response status should be 200
    And the direct invitation list should contain 0 invitations
    And the direct invitation list total should be 0
