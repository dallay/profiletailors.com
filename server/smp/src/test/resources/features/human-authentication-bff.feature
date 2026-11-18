@identity @smoke @fast
Feature: Human authentication BFF boundaries
  The BFF must distinguish protected API failures from browser OIDC navigation and expose its CSRF contract.

  Scenario: Unauthenticated protected API request returns unauthorized without an OIDC redirect
    When an unauthenticated client requests the protected BFF profile endpoint
    Then the BFF response status should be 401
    And the BFF response should not redirect to OIDC
    And the BFF response should not be an HTML login page

  Scenario: CSRF endpoint returns a token and its required request header name
    When an unauthenticated client requests the BFF CSRF token
    Then the BFF response status should be 200
    And the BFF CSRF response should include a non-empty token
    And the BFF CSRF response header name should be "X-CSRF-TOKEN"
