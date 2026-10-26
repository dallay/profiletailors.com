@tenancy @fast @postgres
Feature: Tenancy command dispatch
  Tenancy HTTP endpoints should dispatch ownership and membership commands with
  the expected request payloads.

  Scenario: Workspace ownership transfer dispatches the target principal command
    Given a stubbed workspace ownership response is configured
    When the client transfers workspace ownership to principal "6dd7b370-46f9-5f08-9a95-ba34f26bd8e7"
    Then the tenancy response status should be 200
    And the ownership response workspaceId should be "10000000-0000-4000-8000-000000000001"
    And the ownership response should include owner principal "6dd7b370-46f9-5f08-9a95-ba34f26bd8e7"

  Scenario: Workspace membership status update dispatches the target status command
    Given a stubbed workspace membership status response is configured
    When the client updates workspace membership status for principal "e6d7cb4c-50e4-52e7-9a54-76436681215a" to "SUSPENDED"
    Then the tenancy response status should be 200
    And the membership status response workspaceId should be "10000000-0000-4000-8000-000000000001"
    And the membership status response principalId should be "e6d7cb4c-50e4-52e7-9a54-76436681215a"
    And the membership status response status should be "SUSPENDED"
