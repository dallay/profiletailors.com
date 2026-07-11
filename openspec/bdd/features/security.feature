Feature: Security - Token and Credential Handling

  @security
  Scenario: Access token is never persisted to localStorage
    Given the user has successfully logged in
    Then the localStorage should not contain any key containing "token"
    And the localStorage should not contain any key containing "bearer"
    And the localStorage should not contain any key containing "jwt"
    And the localStorage should not contain any key containing "refresh"

  @security
  Scenario: Refresh token cookie has correct security attributes
    Given the user has successfully logged in
    Then a cookie named "pt_refresh" should exist
    And the cookie "pt_refresh" should have httpOnly set to true
    And the cookie "pt_refresh" should have secure enabled
    And the cookie "pt_refresh" should have sameSite set to an appropriate value
    And the cookie "pt_refresh" should have path "/api/auth"

  @security
  Scenario: Credentials are never exposed in URL
    Given the user has successfully logged in
    Then the browser URL should not contain "password"
    And no query parameters should contain credentials

  @security
  Scenario: Password is not returned in login/register response
    Given the user has successfully registered
    Then the API response should not contain a "password" field
    And the API response should not contain a "passwordHash" field
    And the API response should not contain a "passwordChanged" field

  @security
  Scenario: Email enumeration does not leak account information
    Given a user tries to register with an existing email
    Then the API response should not contain "accountCreatedAt"
    And the API response should not contain "lastLogin"
    And the API response should not contain "passwordChanged"

  @security
  Scenario: Login with redirect does not expose password in URL
    Given the user navigated to "/login?redirect=%2Fscheduler"
    When the user logs in with valid credentials
    Then the URL after redirect should not contain any password