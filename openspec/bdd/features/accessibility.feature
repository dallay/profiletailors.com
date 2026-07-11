Feature: Accessibility and Internationalization

  @a11y @registration
  Scenario: Register form has proper labels
    Given the user is on the registration page
    Then the email input should have a visible label
    And the password input should have a visible label
    And both labels should have "for" attributes

  @a11y @registration
  Scenario: Keyboard navigation follows logical order
    Given the user is on the registration page
    When the user presses Tab
    Then the focus should be on the email input
    When the user presses Tab
    Then the focus should be on the password input
    When the user presses Tab
    Then the focus should be on the submit button

  @a11y @registration
  Scenario: Focus is visible and styled
    Given the user is on the registration page
    When the email input receives focus
    Then a visible focus indicator should be present

  @a11y @scheduler
  Scenario: Past calendar cells have correct accessibility attributes
    Given the user is in month view on a past month
    Then past cells should have aria-disabled="true"

  @i18n @login
  Scenario: Login page displays in English by default
    Given the user locale is "en"
    When the user navigates to "/login"
    Then the page title should be "Welcome back"
    And the submit button should say "Sign in"

  @i18n @login
  Scenario: Login page displays in Spanish when locale is "es"
    Given the user locale is "es"
    When the user navigates to "/login"
    Then the page title should be "Bienvenido de nuevo"
    And the submit button should say "Iniciar sesión"

  @i18n @registration
  Scenario: Registration page displays in Spanish when locale is "es"
    Given the user locale is "es"
    When the user navigates to "/register"
    Then the page title should be "Crear cuenta"