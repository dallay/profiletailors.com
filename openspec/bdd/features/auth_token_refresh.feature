Feature: Token Refresh Mechanism

  @integration @token-refresh
  Scenario: API fetch retries on 401 with successful refresh
    Given the user is authenticated with a valid session
    And the dashboard is visible
    When an API call receives a 401 response
    Then the system should silently attempt token refresh
    And the failed API call should be retried
    And the user should remain on the dashboard

  @integration @token-refresh
  Scenario: Token refresh failure triggers logout
    Given the user is authenticated with a valid session
    And the dashboard is visible
    When an API call receives a 401 response
    And the token refresh also fails with 401
    Then the user should be logged out
    And the user should be redirected to "/login"

  @integration @token-refresh
  Scenario: Non-401 errors skip token refresh
    Given the user is authenticated with a valid session
    When an API call receives a 403 Forbidden response
    Then the system should NOT attempt token refresh
    And the error should be propagated to the UI