@smoke @authorization @fast @postgres
Feature: Workspace access summary authorization
  The current workspace access summary endpoint should expose deterministic
  authorization outcomes for the proving slice.

  Scenario: Entitled authorized member can read the current workspace access summary
    Given an entitled workspace member with the required workspace access permission
    When the client requests the current workspace access summary with a valid JWT
    Then the response status should be 200
    And the response workspaceId should be "workspace-1"
    And the response principalId should be "principal-1"
    And the response should include role "member"
    And the response should include permission "workspace:access:read"
    And the latest authorization decision should be allow because "ROLE_PERMISSION"

  Scenario: Entitled member without the required permission is denied
    Given an entitled workspace member without the required workspace access permission
    When the client requests the current workspace access summary with a valid JWT
    Then the response status should be 403
    And the latest authorization decision should be deny because "MISSING_PERMISSION"

  Scenario: Request without active workspace header is rejected
    Given an entitled workspace member with the required workspace access permission
    When the client requests the current workspace access summary with a valid JWT but without workspace header
    Then the response status should be 400

  Scenario: Access is denied by default for authenticated principal without roles or grants
    Given an authenticated user principal exists without any workspace membership or roles
    When the client requests the current workspace access summary with a valid JWT
    Then the response status should be 403
    And the latest authorization decision should be deny because "MISSING_MEMBERSHIP"

  Scenario: Explicit denial overrides role-based allow
    Given an entitled workspace member with the required workspace access permission
    And an explicit direct "DENY" grant exists for the required permission
    When the client requests the current workspace access summary with a valid JWT
    Then the response status should be 403
    And the latest authorization decision should be deny because "DIRECT_DENY"
