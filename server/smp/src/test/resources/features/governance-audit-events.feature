@governance @fast @postgres
Feature: Workspace audit events query
  Governance consumers should be able to query workspace audit events with filters
  and pagination metadata through the audit-events endpoint.

  Scenario: Audit events query dispatches filters and pagination parameters
    Given a workspace member with the audit events read permission
    And a stubbed audit events response is configured
    When the client queries workspace audit events with filters and pagination
    Then the response status should be 200
    And the audit events response workspaceId should be "10000000-0000-4000-8000-000000000001"
    And the audit events response returned count should be 1
