Feature: User Logout

  @integration @logout
  Scenario: Logout clears session and redirects to login
    Given the user is authenticated and on the dashboard
    When the user clicks the logout button
    Then the session should be cleared
    And the user should be redirected to "/login"

  @integration @logout
  Scenario: Protected routes redirect to login after logout
    Given the user is authenticated
    When the user logs out
    And the user tries to access "/scheduler"
    Then the user should be redirected to "/login?redirect=/scheduler"

  @integration @logout
  Scenario: Logout is idempotent from login page
    Given the user is on the login page with no session
    When the user calls the logout API
    Then no error should occur
    And the user should still be on "/login"

  @integration @logout
  Scenario: User cannot access protected routes after logout
    Given the user is authenticated
    When the user logs out
    And the session refresh fails
    And the user tries to access "/scheduler"
    Then the user should be redirected to "/login"