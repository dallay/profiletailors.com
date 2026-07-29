@identity @password-recovery @smoke @fast
Feature: Password recovery security controls
  As the platform operator
  I want password recovery to resist common attacks
  So that account ownership cannot be compromised

  @enumeration
  Scenario: Prevent account enumeration through response status
    Given a local account exists with email "existing@example.com"
    And no account exists with email "missing@example.com"
    When the visitor requests a password reset for "existing@example.com"
    And the visitor requests a password reset for "missing@example.com" again
    Then both responses should have status 202

  @enumeration
  Scenario: Prevent account enumeration through response body
    Given a local account exists with email "existing@example.com"
    And no account exists with email "missing@example.com"
    When the visitor requests a password reset for "existing@example.com"
    And the visitor requests a password reset for "missing@example.com" again
    Then both responses should have the same response body

  @enumeration
  Scenario: Prevent account enumeration through provider disclosure
    Given an account exists with email "oauth@example.com"
    And the account has no local password credential
    When the visitor requests a password reset for "oauth@example.com"
    Then the password recovery response status should be 202
    And the response should not contain the authentication provider

  @token-strength
  Scenario: Do not expose raw tokens in the database
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the database should contain only the token hash
    And the raw token should not be present in persisted data

  @csrf
  Scenario: Reset password endpoint does not rely on ambient authentication
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then the password recovery response status should be 204
    And no new authenticated session should be created

  @cors
  Scenario: Reject reset requests from a disallowed origin
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the password reset request is submitted from disallowed origin "https://evil.example"
    Then the password recovery response status should be 403
    And no password should be changed

  @replay
  Scenario: Prevent replay after a successful reset
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then the password recovery response status should be 204
    And the password reset token should be marked as used
    When the user submits the modified token with a valid new password
    Then the password recovery response status should be 400
    And the password should remain unchanged

  @audit @pr-3
  Scenario: [PR 3] Audit a successful password change
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then the password recovery response status should be 204
    And an audit event should record the principal identifier
    And the event should record the occurrence timestamp
    And the event should record the action "PASSWORD_RESET_COMPLETED"
    And the event should not contain the raw token
    And the event should not contain the password
    And the event should not contain the password hash
    And the event should not contain the email or raw IP
