@publishing @channel @smoke @fast
Feature: Publishing channels and providers
  Publishing channels domain: list connected channels and configured providers.

  Scenario: List channels (empty)
    When the client lists connected channels
    Then the publishing response status should be 200
    And the channels list should be empty

  Scenario: List configured providers
    When the client lists configured providers
    Then the publishing response status should be 200
    And the providers list should contain "linkedin"
