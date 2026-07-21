@auth @fast @postgres
Feature: Token Refresh — failure modes
  Token refresh failure modes not covered by the existing local-auth.feature.

  Background:
    Given a previously registered local user session exists

  Scenario: Token refresh with invalid cookie returns 401
    Given the user has an expired session
    When the client refreshes the local user session
    Then the response status should be 401
