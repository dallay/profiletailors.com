Feature: Responsive Design

  @responsive @login
  Scenario: Login page renders correctly on mobile viewport
    Given the viewport is set to 390x844 (iPhone 12)
    When the user navigates to "/login"
    Then all form elements should be visible
    And no horizontal scroll should be required
    And the page width should not exceed 400px

  @responsive @login
  Scenario: Login page renders correctly on tablet viewport
    Given the viewport is set to 768x1024 (iPad)
    When the user navigates to "/login"
    Then all form elements should be visible
    And the hero section should be visible
    And no horizontal scroll should be required

  @responsive @registration
  Scenario: Registration page renders correctly on mobile
    Given the viewport is set to 390x844 (iPhone 12)
    When the user navigates to "/register"
    Then all form elements should be visible
    And no horizontal scroll should be required

  @responsive @registration
  Scenario: Registration page renders correctly on tablet
    Given the viewport is set to 768x1024 (iPad)
    When the user navigates to "/register"
    Then all form elements should be visible
    And the hero section should be visible