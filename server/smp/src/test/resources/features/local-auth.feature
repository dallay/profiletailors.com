@smoke @identity @fast @postgres
Feature: Local authentication session lifecycle
  Local user authentication should issue in-memory access tokens and manage
  refresh-backed browser sessions through dedicated auth endpoints.

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
