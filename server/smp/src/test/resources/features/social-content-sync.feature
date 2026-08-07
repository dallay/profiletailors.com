@social-content @smoke @fast @linkedin-social-content
Feature: LinkedIn social-content sync contracts
  Company Page imports remain workspace-scoped and disabled by default.

  Scenario: Missing contract headers are rejected before sync
    Given the social-content BDD state is reset
    When the client requests social-content sync for actor "page-1" without authorization
    Then the social-content response status should be 401
    And the social-content provider should have received 0 calls

  Scenario: Missing workspace context is rejected before sync
    Given the social-content BDD state is reset
    When the client requests social-content sync for actor "page-1" without workspace context
    Then the social-content response status should be 400
    And the social-content provider should have received 0 calls

  Scenario: Default Community Management gate denies sync without provider access
    Given the social-content BDD state is reset
    And the default social-content feature gates are disabled
    When the client requests social-content sync for actor "page-1"
    Then the social-content response status should be 403
    And the social-content problem should contain denial "OPERATION_DISABLED"
    And the social-content provider should have received 0 calls

  Scenario: Personal profile cannot satisfy a Company Page sync
    Given the social-content BDD state is reset
    And a personal LinkedIn social account is the only social-content account
    When the client requests social-content sync for actor "personal-profile-1"
    Then the social-content response status should be 403
    And the social-content problem should contain denial "ORGANIZATION_PAGE_REQUIRED"
    And the social-content provider should have received 0 calls

  Scenario: Valid sync returns an imported count when the read wiring is available
    Given the social-content BDD state is reset
    And an approved LinkedIn organization page actor exists for social-content sync
    When the client requests social-content sync for actor "page-1"
    Then the social-content response status should be 200
    And the social-content response should contain actorId "page-1"
    And the social-content response should contain status "COMPLETED"
    And the social-content provider should have received 1 calls
