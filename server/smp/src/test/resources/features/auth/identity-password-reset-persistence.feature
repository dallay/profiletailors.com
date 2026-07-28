@identity @password-recovery @smoke @fast
Feature: Persist password reset tokens securely
  As the authentication subsystem
  I want password reset tokens to be persisted safely
  So that token lifecycle rules remain enforceable

  @schema @postgres
  Scenario: Persist a password reset token
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then a password reset token should be created for the account
    And the raw token should not be present in persisted data

  @lookup @postgres
  Scenario: Find a token by its hash
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    Then the stored credential should contain a password hash

  @lookup @postgres
  Scenario: Do not find a token using the raw token
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    Then the raw token should not be present in persisted data

  @lifecycle @postgres
  Scenario: Invalidate all active tokens for a principal
    Given a local account exists with email "user@example.com"
    And the account has an active password reset token
    When the visitor requests a password reset for "user@example.com"
    Then the account should have exactly one active password reset token
