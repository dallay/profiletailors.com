Feature: User Authentication - Login

  Background:
    Given the user is on the login page "/login"

  @frontend @login
  Scenario: Login page renders with all required elements
    Then the page title should be "Welcome back"
    And the subtitle should contain "Sign in to continue"
    And the email input field should be visible
    And the password input field should be visible
    And the submit button should say "Sign in"
    And the "Register" link should point to "/register"

  @frontend @login
  Scenario: Login form has correct input attributes
    Then the email input should have type "email"
    And the email input should have autocomplete "email"
    And the email input should have placeholder "you@example.com"
    And the email input should be required
    And the password input should have type "password"
    And the password input should have autocomplete "current-password"
    And the password input should be required

  @frontend @login @validation
  Scenario: Empty form submission is blocked by HTML5 validation
    When the user clicks the submit button without filling fields
    Then no request should be sent to "/api/auth/login"

  @frontend @login @validation
  Scenario: Invalid email format is blocked by browser validation
    When the user fills the email input with "not-an-email"
    And the user fills the password input with "password123"
    And the user clicks the submit button
    Then no request should be sent to "/api/auth/login"

  @integration @login
  Scenario: Successful login with valid credentials
    When the user fills the email input with "dev@profiletailors.com"
    And the user fills the password input with the correct password
    And the user clicks the submit button
    Then the API should receive a POST to "/api/auth/login"
    And the API response should have status 200
    And the response should contain "accessToken"
    And the response should contain "tokenType" with value "Bearer"
    And the response should contain "expiresIn"
    And the response should contain "principalId"
    And the response should contain "emailStatus"
    And the user should be redirected to "/"
    And the dashboard should be visible

  @integration @login
  Scenario: Login is case-insensitive for email
    When the user fills the email input with "TEST@EXAMPLE.COM"
    And the user fills the password input with the correct password
    And the user clicks the submit button
    Then the user should be redirected to "/"
    And the dashboard should be visible

  @integration @login
  Scenario: Login with redirect preserves destination
    Given the user navigated to "/login?redirect=%2Fscheduler"
    When the user fills the email input with "dev@profiletailors.com"
    And the user fills the password input with the correct password
    And the user clicks the submit button
    Then the user should be redirected to "/scheduler"

  @integration @login @error
  Scenario: Login with invalid credentials shows error banner
    When the user fills the email input with "wrong@email.com"
    And the user fills the password input with "incorrect"
    And the user clicks the submit button
    Then the API should receive a POST to "/api/auth/login"
    And the API response should have status 401
    And an error banner should be visible
    And the error banner should contain "Invalid email or password."
    And the error banner should have error styling

  @integration @login @error
  Scenario: Login with non-existent account shows same error (prevents enumeration)
    When the user fills the email input with "nonexistent-12345@example.com"
    And the user fills the password input with "SomePass123!"
    And the user clicks the submit button
    Then the API response should have status 401
    And an error banner should be visible with "Invalid email or password."

  @integration @login @error
  Scenario: Server error on login shows error message
    When the user fills the email input with "dev@profiletailors.com"
    And the user fills the password input with the correct password
    And the user clicks the submit button
    And the API response is 500 "Internal Server Error"
    Then an error banner should be visible

  @integration @login @error
  Scenario: Network failure on login shows error state
    When the user fills the email input with "dev@profiletailors.com"
    And the user fills the password input with the correct password
    And the user clicks the submit button
    And the network request fails
    Then an error state should be visible

  @integration @login
  Scenario: Error banner clears on successful re-submission
    Given the user has triggered a login error
    And the error banner is visible
    When the user fills the email input with "dev@profiletailors.com"
    And the user fills the password input with the correct password
    And the user clicks the submit button
    Then the user should be redirected to "/"
    And the error banner should not be visible

  @integration @login
  Scenario: Error banner clears on navigation away
    Given the user has triggered a login error
    And the error banner is visible
    When the user clicks the "Register" link
    Then the user should be on "/register"
    And the error banner should not be visible

  @frontend @login @error
  Scenario: Error banner is hidden by default on page load
    Then no error banner should be visible

  @integration @login @session
  Scenario: Session persists across page reload
    Given the user is logged in with valid credentials
    And the dashboard is visible
    When the user reloads the page
    Then the dashboard should still be visible
    And the user should be authenticated

  @integration @login @session
  Scenario: Expired session redirects to login on reload
    Given the user is logged in with valid credentials
    And the session has expired
    When the user reloads the page
    Then the user should be redirected to "/login"

  @integration @login @session
  Scenario: Fresh browser without session redirects to login
    Given the browser has no session
    When the user navigates to "/"
    Then the user should be redirected to "/login"