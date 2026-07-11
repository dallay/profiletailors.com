Feature: Session Hydration

  @integration @hydration
  Scenario: Page renders during auth hydration
    Given the user has a valid session cookie
    When the user navigates to "/login"
    Then the login form should be visible within 3 seconds
    And the email input should be visible
    And the submit button should be visible

  @integration @hydration
  Scenario: Dashboard is visible during slow hydration
    Given the refresh API has a 2 second delay
    When the user navigates to "/"
    Then the dashboard should render without showing login page

  @integration @hydration
  Scenario: Slow hydration does not block form interaction
    Given the refresh API has a 2 second delay
    When the user navigates to "/login"
    Then the email input should be visible within 3 seconds
    And the user can interact with the form while hydration is in progress