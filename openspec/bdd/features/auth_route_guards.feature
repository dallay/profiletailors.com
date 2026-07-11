Feature: Route Guards - Protected Routes

  @integration @route-guards
  Scenario: Unauthenticated user accessing /scheduler is redirected to login
    Given the user is not authenticated
    When the user navigates to "/scheduler"
    Then the user should be redirected to "/login?redirect=/scheduler"

  @integration @route-guards
  Scenario: Unauthenticated user accessing /analytics is redirected to login
    Given the user is not authenticated
    When the user navigates to "/analytics"
    Then the user should be redirected to "/login?redirect=/analytics"

  @integration @route-guards
  Scenario: Unauthenticated user accessing /settings is redirected to login
    Given the user is not authenticated
    When the user navigates to "/settings"
    Then the user should be redirected to "/login?redirect=/settings"

  @integration @route-guards
  Scenario: Authenticated user accessing /login is redirected to dashboard
    Given the user is authenticated
    When the user navigates to "/login"
    Then the user should be redirected to "/"

  @integration @route-guards
  Scenario: Authenticated user accessing /register is redirected to dashboard
    Given the user is authenticated
    When the user navigates to "/register"
    Then the user should be redirected to "/"

  @integration @route-guards
  Scenario: Redirect parameter propagates through login flow
    Given the user is not authenticated
    When the user navigates to "/settings"
    And the user logs in with valid credentials
    Then the user should be redirected to "/settings"