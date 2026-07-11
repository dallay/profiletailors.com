Feature: User Registration

  Background:
    Given the user is on the registration page "/register"

  @frontend @registration
  Scenario: Registration page renders with all required elements
    Then the page title should be "Create account"
    And the subtitle should contain "start managing your channels"
    And the email input field should be visible
    And the password input field should be visible
    And the submit button should say "Create account"
    And the "Sign in" link should point to "/login"

  @frontend @registration
  Scenario: Registration form has correct input attributes
    Then the email input should have type "email"
    And the email input should have autocomplete "email"
    And the email input should have placeholder "you@example.com"
    And the email input should be required
    And the password input should have type "password"
    And the password input should have autocomplete "new-password"
    And the password input should have placeholder "at least 8 characters"
    And the password input should be required

  @frontend @registration @validation
  Scenario: Empty fields trigger HTML5 validation
    When the user clicks the submit button without filling fields
    Then no request should be sent to "/api/auth/register"

  @frontend @registration @validation
  Scenario: Invalid email format is blocked by browser
    When the user fills the email input with "not-an-email"
    And the user fills the password input with "password123"
    And the user clicks the submit button
    Then no request should be sent to "/api/auth/register"

  @frontend @registration @validation
  Scenario: Empty password is blocked by HTML5
    When the user fills the email input with "valid@email.com"
    And the user clicks the submit button without password
    Then no request should be sent to "/api/auth/register"

  @frontend @registration @validation
  Scenario: Short password is not blocked client-side (server validation)
    When the user fills the email input with "valid@email.com"
    And the user fills the password input with "Ab1"
    And the user clicks the submit button
    Then a request should be sent to "/api/auth/register"
    And the server should return 400 with "at least 8 characters"

  @frontend @registration
  Scenario: Form is cleared when switching to login
    Given the user fills the email input with "test@example.com"
    And the user fills the password input with "password123"
    When the user clicks the "Sign in" link
    Then the email input should be empty
    And the password input should be empty

  @frontend @registration @responsive
  Scenario: Registration page renders on mobile viewport
    Given the viewport is set to 390x844 (mobile)
    Then the page should render without horizontal scroll

  @frontend @registration @responsive
  Scenario: Registration page renders on tablet viewport
    Given the viewport is set to 768x1024 (tablet)
    Then the page should render with hero section visible

  @integration @registration
  Scenario: Successful registration creates authenticated session
    When the user fills the email input with a new unique email
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API should receive a POST to "/api/auth/register"
    And the API response should have status 201
    And the response should contain "accessToken"
    And the response should contain "tokenType" with value "Bearer"
    And the response should contain "emailStatus" with value "PENDING"
    And the response should contain "workspaceId"
    And the user should be redirected to "/"
    And the dashboard should be visible

  @integration @registration
  Scenario: Registration normalizes email to lowercase
    When the user fills the email input with "Test@Example.COM"
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API request should contain email normalized to lowercase

  @integration @registration
  Scenario: Registration trims whitespace from email
    When the user fills the email input with "  newuser@example.com  "
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API request should contain email without surrounding whitespace

  @integration @registration
  Scenario: Registration returns username derived from email
    When the user fills the email input with "john.doe@example.com"
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API response should contain "username" with value "john.doe"

  @integration @registration
  Scenario: Workspace is available immediately after registration
    Given the user has successfully registered
    When the user navigates to "/scheduler"
    Then the scheduler should be visible

  @integration @registration
  Scenario: Registration with redirect preserves destination
    Given the user navigated to "/register?redirect=%2Fscheduler"
    When the user fills the email input with a new unique email
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the user should be redirected to "/scheduler"

  @integration @registration @error
  Scenario: Duplicate email returns 409 with specific error
    When the user fills the email input with "existing@profiletailors.com"
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API response should have status 409
    And an error banner should be visible
    And the error banner should contain "already exists"
    And the user should remain on "/register"
    And the email input should retain the email value

  @integration @registration @error
  Scenario: Case-insensitive duplicate email detection
    Given the email "test@example.com" already exists
    When the user fills the email input with "TEST@EXAMPLE.COM"
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API response should have status 409
    And an error banner should be visible with "already exists"

  @integration @registration @error
  Scenario: Password too short returns 400
    When the user fills the email input with "newuser@example.com"
    And the user fills the password input with "Ab1"
    And the user clicks the submit button
    Then the API response should have status 400
    And an error banner should be visible with "at least 8 characters"

  @integration @registration @error
  Scenario: Password too long returns 400
    When the user fills the email input with "newuser@example.com"
    And the user fills the password input with a 129-character password
    And the user clicks the submit button
    Then the API response should have status 400
    And an error banner should be visible with "128 characters"

  @integration @registration @error
  Scenario: Server error shows generic error message
    When the user fills the email input with a new unique email
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    And the API response is 500 "Internal Server Error"
    Then an error banner should be visible
    And the user should remain on "/register"

  @integration @registration @error
  Scenario: Network error shows error state
    When the user fills the email input with a new unique email
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    And the network request fails
    Then an error state should be visible

  @integration @registration @error
  Scenario: Error banner clears on navigation away
    Given the user has triggered a registration error
    And the error banner is visible
    When the user clicks the "Sign in" link
    Then the user should be on "/login"
    And the error banner should not be visible

  @security @registration
  Scenario: Access token is never persisted to localStorage
    Given the user has successfully registered
    Then the localStorage should not contain "access_token"
    And the localStorage should not contain "bearer"
    And the localStorage should not contain "jwt"
    And the localStorage should not contain "refresh"

  @security @registration
  Scenario: Password is sent in request but never returned in response
    Given the user fills the email input with "test@example.com"
    And the user fills the password input with "SecretPass123!"
    And the user clicks the submit button
    Then the API request should contain the password
    But the API response should not contain a "password" field
    And the API response should not contain a "passwordHash" field

  @security @registration
  Scenario: Email enumeration does not reveal extra account details
    When the user fills the email input with "existing@profiletailors.com"
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API response should not contain "accountCreatedAt"
    And the API response should not contain "lastLogin"
    And the API response should not contain "passwordChanged"

  @security @registration
  Scenario: Credentials are never exposed in the URL
    Given the user has successfully registered
    Then the browser URL should not contain the password
    And the browser URL should not contain query parameters with credentials

  @security @registration
  Scenario: Auth endpoints require correct Content-Type and Accept headers
    When the user fills the email input with a new unique email
    And the user fills the password input with "SecurePass123!"
    And the user clicks the submit button
    Then the API request should have "Content-Type: application/json"
    And the API request should have "Accept: application/vnd.api.v1+json"