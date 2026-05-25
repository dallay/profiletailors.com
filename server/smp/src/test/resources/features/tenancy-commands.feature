@tenancy @fast @postgres
Feature: Tenancy command dispatch
  Tenancy HTTP endpoints should dispatch ownership and membership commands with
  the expected request payloads.

  Scenario: Workspace ownership transfer dispatches the target principal command
    Given a stubbed workspace ownership response is configured
    When the client transfers workspace ownership to principal "owner-2"
    Then the tenancy response status should be 200
    And the ownership response workspaceId should be "workspace-1"
    And the ownership response should include owner principal "owner-2"

  Scenario: Workspace membership status update dispatches the target status command
    Given a stubbed workspace membership status response is configured
    When the client updates workspace membership status for principal "member-2" to "SUSPENDED"
    Then the tenancy response status should be 200
    And the membership status response workspaceId should be "workspace-1"
    And the membership status response principalId should be "member-2"
    And the membership status response status should be "SUSPENDED"
