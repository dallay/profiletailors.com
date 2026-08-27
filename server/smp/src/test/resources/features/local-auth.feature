@smoke @identity @fast @postgres
Feature: Local authentication session lifecycle
  Local user authentication should issue in-memory access tokens and manage
  refresh-backed browser sessions through dedicated auth endpoints.

  Scenario: Public capabilities expose exactly authoritative authentication availability
    Given public registration and password recovery are enabled
    When the visitor requests public application capabilities
    Then the public capabilities response status should be 200
    And the public capabilities response should equal the exact allow-listed contract

  Scenario: Public capabilities expose disabled authoritative authentication availability
    Given public registration and password recovery are disabled
    When the visitor requests public application capabilities
    Then the public capabilities response status should be 200
    And the public capabilities response should equal the exact disabled allow-listed contract

  Scenario: Disabled registration rejects before mutation
    Given public registration is disabled
    When the visitor submits valid disabled registration details
    Then the disabled registration response status should be 503
    And the disabled registration response code should be "REGISTRATION_DISABLED"
    And no local account, credential, workspace, consent, event, or session should be created

  Scenario: Invite-only registration rejects before mutation
    Given public registration is invite-only
    When the visitor submits valid disabled registration details
    Then the disabled registration response status should be 403
    And the invite-only registration response code should be "REGISTRATION_INVITATION_REQUIRED"
    And no local account, credential, workspace, consent, event, or session should be created

  Scenario: Disabled password recovery request rejects before token creation
    Given password recovery is disabled
    When the visitor requests a password reset for "user@example.com"
    Then the password recovery response status should be 503
    And no password reset token should be created
    And no password reset notification should be scheduled

  Scenario: Disabled password reset rejects without consuming token or changing credentials
    Given password recovery is disabled
    And a previously issued reset token exists
    When the user submits the reset token
    Then the password recovery response status should be 503
    And the password should remain unchanged
    And the reset token should remain unused

  Scenario: Registration creates an unverified user with session tokens
    Given a browser submits valid local registration details
    When the client registers a local user
    Then the response status should be 201
    And the auth response should include email "yuniel@example.com"
    And the auth response should include emailStatus "PENDING"
    And the auth response should include an access token
    And the response should set a refresh cookie

  Scenario: Registration yields PENDING and current user profile reports authoritative PENDING
    Given a browser submits valid local registration details
    When the client registers a local user
    And the pending user has an active workspace membership
    And the client requests the current authenticated user profile
    Then the response status should be 200
    And the current user profile should include emailStatus "PENDING"

  Scenario: Registration dispatches verification email through configured sender
    Given a browser submits valid local registration details
    When the client registers a local user
    Then the verification email sender should have received 1 message for "yuniel@example.com"

  Scenario: Pending user media upload attempt is denied by email verification gate
    Given a previously registered local user session exists without verified email
    And the pending user has an active workspace membership
    When the client attempts to register a media asset
    Then the response status should be 403
    And the problem response should include code "EMAIL_VERIFICATION_REQUIRED"
    And no media asset should be persisted

  Scenario: Verified user media upload path is evaluated by normal media rules
    Given a previously registered local user session exists
    And the verified user has an active workspace membership
    When the client attempts to register a media asset
    Then the response status should be 201
    And one media asset should be persisted

  Scenario: Refresh returns a new access token for an active session cookie
    Given a previously registered local user session exists
    When the client refreshes the local user session
    Then the response status should be 200
    And the auth response should include an access token
    And the auth response should include email "owner@example.com"
    And the response should set a refresh cookie

  Scenario: Logout invalidates the refresh-backed session
    Given a previously registered local user session exists
    When the client logs out the local user session
    Then the response status should be 204
    And the response should clear the refresh cookie
    When the client refreshes the local user session
    Then the response status should be 401

  Scenario: Resend verification smoke check
    Given a previously registered local user session exists
    When the client resends the verification email for "owner@example.com"
    Then the response status should be 202
    And the verification email sender should have received 1 message for "owner@example.com"

  Scenario: Refresh rejects cookie-authenticated request without origin
    Given a previously registered local user session exists
    When the client refreshes the local user session without an origin
    Then the response status should be 403

  Scenario: Refresh rejects cookie-authenticated request with untrusted origin
    Given a previously registered local user session exists
    When the client refreshes the local user session with an untrusted origin
    Then the response status should be 403

  Scenario: Logout rejects cookie-authenticated request without origin
    Given a previously registered local user session exists
    When the client logs out the local user session without an origin
    Then the response status should be 403

  Scenario: Logout rejects cookie-authenticated request with untrusted origin
    Given a previously registered local user session exists
    When the client logs out the local user session with an untrusted origin
    Then the response status should be 403
