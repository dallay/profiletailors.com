@credentials @authorization @smoke @fast @postgres
Feature: Workspace access summary credentials enforcement
  The current workspace access summary endpoint should enforce credential state
  and support credential-specific proving flows.

  Scenario: Entitled authorized service account can read the current workspace access summary
    Given an entitled authorized service-account principal exists
    When the client requests the current workspace access summary with a service-account bearer token
    Then the response status should be 200
    And the response workspaceId should be "workspace-1"
    And the response principalId should be "service-principal-1"
    And the latest authorization decision should be allow because "ROLE_PERMISSION"

  Scenario: Revoked service-account credential is rejected before authorization succeeds
    Given an entitled authorized service-account principal exists with revoked credential state
    When the client requests the current workspace access summary with a service-account bearer token
    Then the response status should be 401
    And the latest authorization decision should be deny because "REVOKED_CREDENTIAL"

  Scenario: Completed API-key replacement rejects the predecessor and accepts the successor
    Given an entitled authorized API-key principal exists
    When the client requests the current workspace access summary with the predecessor API key
    Then the response status should be 200
    When the active API key is replaced
    And the client requests the current workspace access summary with the predecessor API key
    Then the response status should be 401
    When the client requests the current workspace access summary with the successor API key
    Then the response status should be 200
