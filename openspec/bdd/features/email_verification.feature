Feature: Email Verification

  @integration @email-verification
  Scenario: Registration returns PENDING email status
    Given the user registers with a new email
    Then the API response should contain "emailStatus" with value "PENDING"

  @integration @email-verification
  Scenario: Unverified user can log in and receives PENDING session
    Given a user exists with email status PENDING
    When the user logs in with valid credentials
    Then the API response should contain "emailStatus" with value "PENDING"
    And the user should be redirected to "/"

  @integration @email-verification
  Scenario: Resend verification email returns 202
    When the user calls POST "/api/auth/resend-verification" with their email
    Then the API response should have status 202

  @integration @email-verification
  Scenario: Resend verification with non-existent email returns 202 (prevents enumeration)
    When the user calls POST "/api/auth/resend-verification" with "nonexistent@example.com"
    Then the API response should have status 202

  @integration @email-verification
  Scenario: Verify email with valid token returns VERIFIED session
    Given the user has a valid verification token
    When the user calls POST "/api/auth/verify-email" with the valid token
    Then the API response should have status 200
    And the response should contain "accessToken"
    And the response should contain "emailStatus" with value "VERIFIED"

  @integration @email-verification
  Scenario: Verify email with invalid token returns 400
    Given the user has an invalid verification token
    When the user calls POST "/api/auth/verify-email" with the invalid token
    Then the API response should have status 400
    And the response should contain "Invalid verification token"

  @integration @feature-gating
  Scenario: PENDING user can access dashboard
    Given a user is logged in with email status PENDING
    When the user navigates to "/"
    Then the dashboard should be visible

  @integration @feature-gating
  Scenario: PENDING user is blocked from publishing features
    Given a user is logged in with email status PENDING
    When the user calls GET "/api/publishing/channels/providers"
    Then the API response should have status 403
    And the response should contain "EMAIL_VERIFICATION_REQUIRED"
    And the response should contain "code" with value "EMAIL_VERIFICATION_REQUIRED"