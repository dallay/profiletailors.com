Feature: User Settings and Preferences

  Background:
    Given the user is authenticated and on the settings page "/settings"

  @e2e @settings @theme
  Scenario: Change theme from dark to light
    Given the current theme is "dark"
    When the user selects "light" theme
    Then "pt_settings_v1" in localStorage should contain "theme: light"

  @e2e @settings @theme
  Scenario: Theme persists across page reload
    Given the user has changed the theme to "light"
    When the user reloads the page
    Then the UI should still display light theme
    And "pt_settings_v1" in localStorage should still contain "theme: light"

  @e2e @settings @locale
  Scenario: Change locale from English to Spanish
    Given the current locale is "en"
    When the user clicks the language option for "es"
    Then "pt_settings_v1" in localStorage should contain "locale: es"

  @e2e @settings @locale
  Scenario: Locale persists across page reload
    Given the user has changed the locale to "es"
    When the user reloads the page
    Then the UI should display in Spanish
    And the settings page should still be visible

  @e2e @settings @locale
  Scenario: Changing language updates UI text immediately
    Given the user is on the settings page
    When the user clicks the language option for "es"
    Then the UI text should update to Spanish