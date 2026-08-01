@security @smoke @fast
Feature: Endpoint authorization security controls
  As the platform operator
  I want every API path to require authentication unless explicitly designed to be public
  So that unauthenticated clients cannot access protected functionality

  @sec-001
  Scenario: Unauthenticated request to media proxy path is rejected with 401
    When an unauthenticated client sends GET "/api/media/proxy"
    Then the security response status should be 401

  @sec-001
  Scenario: Unauthenticated request to a protected media asset endpoint is rejected with 401
    When an unauthenticated client sends GET "/api/media/assets"
    Then the security response status should be 401

  @sec-001
  Scenario: Unauthenticated request to a workspace endpoint is rejected with 401
    When an unauthenticated client sends GET "/api/tenancy/workspaces"
    Then the security response status should be 401

  @sec-001
  Scenario: Explicitly permitted health endpoint is reachable without authentication
    When an unauthenticated client sends GET "/actuator/health"
    Then the security response status should be 200

  @sec-001
  Scenario: Explicitly permitted public capabilities endpoint is reachable without authentication
    When an unauthenticated client sends GET "/api/capabilities/public"
    Then the security response status should be 200
