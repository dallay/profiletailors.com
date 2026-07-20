@auth @fast @postgres
Feature: User Authentication — Login error states
  Login API error states, email case-insensitivity, and non-existent account
  behaviour not covered by the existing local-auth.feature.

  Background:
    Given a previously registered local user exists

  Scenario: Login with invalid credentials returns 401
    When the user submits invalid credentials
    Then the response status should be 401
    And the problem response should include detail "Invalid email or password."

  Scenario: Login with non-existent account returns same 401 error
    When the user submits credentials for "nonexistent@example.com"
    Then the response status should be 401
    And the problem response should include detail "Invalid email or password."

  Scenario: Login is case-insensitive for email
    Given a previously registered local user exists with email "Case@Test.Com"
    When the user submits credentials with email "case@test.com"
    Then the response status should be 200
    And the auth response should include email "case@test.com"
