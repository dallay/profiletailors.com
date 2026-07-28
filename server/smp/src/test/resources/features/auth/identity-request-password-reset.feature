@identity @password-recovery @fast @postgres
Feature: Request password reset
  As a user who cannot remember the account password
  I want to request a secure password reset link
  So that I can regain access to my account

  Background:
    Given password recovery is enabled
    And the password reset token lifetime is 30 minutes
    And authentication rate limiting is enabled

  @happy-path
  Scenario: Request a password reset for an existing local account
    Given a local account exists with email "user@example.com"
    And the account has a password credential
    When the visitor requests a password reset for "user@example.com"
    Then the password recovery response status should be 202
    And the response should not indicate whether the account exists
    And a password reset token should be created for the account
    And only the token hash should be persisted
    And a password reset notification should be scheduled
    And the notification should be sent to "user@example.com"

  @security @enumeration
  Scenario: Request a password reset for an unknown email
    Given no account exists with email "unknown@example.com"
    When the visitor requests a password reset for "unknown@example.com"
    Then the password recovery response status should be 202
    And the response body should be empty
    And no password reset token should be created
    And no password reset notification should be scheduled

  @security @enumeration
  Scenario: Existing and unknown accounts return the same public response
    Given a local account exists with email "existing@example.com"
    And no account exists with email "missing@example.com"
    When the visitor requests a password reset for "existing@example.com"
    And the visitor requests a password reset for "missing@example.com" again
    Then both responses should have status 202
    And both responses should have the same response body
    And neither response should expose account existence

  @oauth
  Scenario: Request a password reset for an OAuth-only account
    Given an account exists with email "oauth@example.com"
    And the account has no local password credential
    And the account authenticates only through an external provider
    When the visitor requests a password reset for "oauth@example.com"
    Then the password recovery response status should be 202
    And the response should not contain the authentication provider
    And no password reset token should be created
    And no password reset notification should be scheduled

  @normalization
  Scenario: Normalize the email before account lookup
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "  USER@EXAMPLE.COM  "
    Then the password recovery response status should be 202
    And the account should be resolved using "user@example.com"
    And a password reset token should be created for the account

  @validation
  Scenario Outline: Reject an invalid password reset request
    When the visitor requests a password reset using email "<email>"
    Then the password recovery response status should be 400
    And the response should use RFC 9457 Problem Details
    And the response should contain validation code "<code>"
    And no password reset token should be created
    And no password reset notification should be scheduled

    Examples:
      | email                | code              |
      |                      | VALIDATION_ERROR  |
      | invalid-email        | VALIDATION_ERROR  |
      | user@                | VALIDATION_ERROR  |
      | @example.com         | VALIDATION_ERROR  |
      | user example.com     | VALIDATION_ERROR  |

  @validation
  Scenario: Reject a request without a JSON body
    When the visitor sends a password reset request without a request body
    Then the password recovery response status should be 400
    And the response should use RFC 9457 Problem Details
    And no password reset token should be created

  @validation
  Scenario: Reject a request with malformed JSON
    When the visitor sends malformed JSON to the password reset request endpoint
    Then the password recovery response status should be 400
    And the response should use RFC 9457 Problem Details
    And no password reset token should be created

  @token-lifecycle
  Scenario: Invalidate an existing active token when a new reset is requested
    Given a local account exists with email "user@example.com"
    And the account has an active password reset token
    When the visitor requests a password reset for "user@example.com"
    Then the previous password reset token should be invalidated
    And a new password reset token should be created
    And only the new token should remain usable

  @token-lifecycle
  Scenario: Multiple sequential requests leave only the latest token active
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    And the visitor requests a password reset for "user@example.com" again
    And the visitor requests a password reset a third time for "user@example.com"
    Then three requests should have been accepted
    And only the latest password reset token should be active
    And two previous password reset tokens should be unusable

  @concurrency
  Scenario: Concurrent password reset requests result in one active token
    Given a local account exists with email "user@example.com"
    When two password reset requests for "user@example.com" are processed concurrently
    Then both public responses should have status 202
    And the account should have exactly one active password reset token
    And all superseded tokens should be unusable

  @notification
  Scenario: Password reset email contains the expected reset link
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the password reset email should contain a link to the reset password page
    And the link should include the raw reset token
    And the email should state that the link expires in 30 minutes
    And the email should state that the request can be ignored
    And the email should not contain the current password
    And the email should not contain a temporary password

  @security
  Scenario: Never persist the raw password reset token
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the database should contain only the token hash
    And the raw token should not be present in persisted data
    And the raw token should not be present in application logs
    And the raw token should not be present in metrics

  @security
  Scenario: Do not expose account data in the password reset request response
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the response should not contain the principal identifier
    And the response should not contain the normalized email
    And the response should not contain the authentication provider
    And the response should not contain token metadata

  @rate-limit
  Scenario: Rate limit repeated requests for the same normalized email
    Given the email password reset limit is 3 requests per 30 minutes
    When password reset is requested 4 times for variants of "user@example.com" within 30 minutes
    Then the first 3 requests should be accepted
    And the fourth response status should be 429
    And all email variants should count toward the same normalized email bucket
