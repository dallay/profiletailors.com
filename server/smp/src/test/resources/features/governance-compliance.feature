@governance @fast
Feature: Compliance release gate
  Governance consumers should be able to query the release gate status
  for a given release.

  Scenario: Release gate returns not applicable when no controls exist
    Given an authenticated user exists
    When the client queries the release gate for the release "0.1.0"
    Then the release gate response status should be 200
    And the release gate status should be "NOT_APPLICABLE"
