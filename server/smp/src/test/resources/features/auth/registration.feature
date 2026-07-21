@auth @fast @postgres
Feature: User Registration — validation and edge cases
  Registration validation errors, duplicate email detection, and response
  contracts not covered by the existing local-auth.feature.

  Scenario: Duplicate email returns 409
    Given an existing user with email "existing@example.com"
    When the client registers with email "existing@example.com"
    Then the response status should be 409
    And the problem response should include code "USER_ALREADY_EXISTS"

  Scenario: Password too short returns 400
    When the client registers with password "Ab1"
    Then the response status should be 400
    And the problem response should include detail "Validation failure"

  Scenario: Registration response includes workspaceId
    Given a browser submits valid local registration details
    When the client registers a local user
    Then the response status should be 201
    And the response should contain a workspaceId

  Scenario: Registration normalizes email to lowercase
    When the client registers with email "Test@Example.COM"
    Then the response status should be 201
    And the email in the response should be normalized to lowercase
    And the auth response should include email "test@example.com"
