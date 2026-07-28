@identity @password-recovery @fast @postgres
Feature: Reset password using a recovery token
  As a user who has received a password reset link
  I want to choose a new password
  So that I can regain secure access to my account

  Background:
    Given password recovery is enabled
    And the password reset token lifetime is 30 minutes
    And password hashes are generated using the configured password hasher

  @happy-path
  Scenario: Reset the password using a valid token
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then the password recovery response status should be 204
    And the account password hash should be updated
    And the password reset token should be marked as used
    And all refresh sessions for the account should be revoked
    And no new authenticated session should be created
    And no refresh cookie should be issued

  @happy-path
  Scenario: User can log in with the new password after reset
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password to "NewSecurePassword123!"
    Then the password reset should succeed
    When the user logs in with "user@example.com" and "NewSecurePassword123!"
    Then authentication should succeed

  @security
  Scenario: User cannot log in with the old password after reset
    Given a local account exists with email "user@example.com"
    And the current password is "OldSecurePassword123!"
    And the account has an active password reset token
    When the user resets the password to "NewSecurePassword123!"
    And the user attempts to refresh the session
    Then authentication should fail with invalid credentials

  @security
  Scenario: Resetting the password revokes all active refresh sessions
    Given a local account has active refresh sessions on multiple devices
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then every refresh session for the account should be revoked
    And each previous refresh token should be rejected
    And existing devices should require a new login

  @security
  Scenario: Existing access tokens are not renewed after password reset
    Given a local account has an active access token and refresh token
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then the existing refresh token should be revoked
    When the client attempts to refresh the session
    Then the refresh request should be rejected with status 401

  @token
  Scenario: Reject an unknown password reset token
    Given no password reset token exists for "unknown-token"
    When the user submits "unknown-token" with a valid new password
    Then the password recovery response status should be 400
    And the response should use RFC 9457 Problem Details
    And the public error code should be "INVALID_PASSWORD_RESET_TOKEN"
    And no password should be changed
    And no session should be revoked

  @token
  Scenario: Reject an expired password reset token
    Given a password reset token expired one minute ago
    When the user submits the expired token with a valid new password
    Then the password recovery response status should be 400
    And the public response should state that the link is invalid or expired
    And no password should be changed
    And the token should remain unusable

  @token
  Scenario: Reject an already used password reset token
    Given a password reset token has already been used
    When the user submits the used token with a valid new password
    Then the password recovery response status should be 400
    And the public response should state that the link is invalid or expired
    And no password should be changed

  @token
  Scenario: Reject a token invalidated by a newer request
    Given a local account requested two password reset links
    And the first token was invalidated when the second token was created
    When the user submits the first token with a valid new password
    Then the password recovery response status should be 400
    And no password should be changed
    When the user submits the second token with a valid new password
    Then the password recovery response status should be 204

  @validation
  Scenario Outline: Reject an invalid new password
    Given a valid unused password reset token exists
    When the user resets the password using "<password>"
    Then the password recovery response status should be 400
    And the response should use RFC 9457 Problem Details
    And the response should contain code "INVALID_PASSWORD"
    And the account password should remain unchanged
    And the reset token should remain unused

    Examples:
      | password |
      |          |
      | short    |

  @validation
  Scenario: Accept a password with the maximum supported length
    Given the maximum password length is 128 characters
    And a valid unused password reset token exists
    When the user resets the password using a valid 128-character password
    Then the password recovery response status should be 204

  @validation
  Scenario: Reject a password exceeding the maximum supported length
    Given the maximum password length is 128 characters
    And a valid unused password reset token exists
    When the user resets the password using a 129-character password
    Then the password recovery response status should be 400
    And the password should remain unchanged
    And the reset token should remain unused

  @validation
  Scenario: Reject a reset request without a token
    When the user submits a reset request with a valid new password but no token
    Then the password recovery response status should be 400
    And no password should be changed

  @validation
  Scenario: Reject a reset request without a new password
    Given a valid unused password reset token exists
    When the user submits the reset token without a new password
    Then the password recovery response status should be 400
    And no password should be changed
    And the reset token should remain unused

  @validation
  Scenario: Reject a reset request with malformed JSON
    Given a valid unused password reset token exists
    When the user submits malformed JSON to the reset password endpoint
    Then the password recovery response status should be 400
    And no password should be changed
    And the reset token should remain unused

  @security
  Scenario: Store the new password only as a secure hash
    Given a valid unused password reset token exists
    When the user resets the password to "NewSecurePassword123!"
    Then the plaintext password should not be persisted
    And the stored credential should contain a password hash
    And the configured password hasher should verify the new password

  @security
  Scenario: Do not include sensitive values in the successful response
    Given a valid unused password reset token exists
    When the user resets the password using the token and a valid new password
    Then the password recovery response status should be 204
    And the response should not contain the password
    And the response should not contain the password hash
    And the response should not contain the reset token
    And the response should not contain an access token
    And the response should not contain a refresh token

  @concurrency
  Scenario: Only one concurrent reset request can consume a token
    Given a valid unused password reset token exists
    When two password reset requests using the same token are processed concurrently
    Then exactly one request should succeed with status 204
    And exactly one request should fail with status 400
    And the password should match only the successful request
    And the token should be marked as used exactly once

  @rate-limit
  Scenario: Rate limit repeated invalid reset attempts from the same IP
    Given the reset attempt limit is 10 requests per 15 minutes
    When the same IP submits 11 invalid reset tokens within 15 minutes
    Then the first 10 requests should return token validation responses
    And the eleventh response status should be 429
    And the response should contain code "AUTH_RATE_LIMIT_EXCEEDED"
