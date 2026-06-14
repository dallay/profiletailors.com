@smoke @identity @fast @postgres
Feature: Local authentication session lifecycle
  Local user authentication should issue in-memory access tokens and manage
  refresh-backed browser sessions through dedicated auth endpoints.

  Scenario: Registration creates an unverified user without tokens
    Given a browser submits valid local registration details
    When the client registers a local user
    Then the response status should be 201
    And the auth response should include email "yuniel@example.com"
    And the auth response should include emailStatus "UNVERIFIED"
    And the response should not set a refresh cookie

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
