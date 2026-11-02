@platform-admin @bootstrap @smoke @fast
Feature: Initial platform admin bootstrap
  The first platform owner is invited through terminal output, accepts current Terms, and verifies email.

  Scenario: Recipient completes bootstrap and existing verification grants the system-attributed owner role
    Given a terminal-issued bootstrap invitation exists for "bootstrap-owner@example.test"
    When the recipient sets a password, accepts current Terms, and verifies email
    Then the invitation verification email uses the existing app route and is sent once
    And the verified recipient has platform consent and no tenant data
    And the owner grant audit identifies only the system bootstrap actor

  Scenario: Invalid bootstrap credentials are unavailable and do not mutate state
    When an unauthenticated recipient submits an invalid bootstrap credential
    Then the bootstrap response code is "BOOTSTRAP_UNAVAILABLE"
    And no bootstrap owner or tenant data is created

  Scenario: Bootstrap issuance and status remain private
    When an unauthenticated client requests bootstrap status and issuance
    Then both requests are unauthorized
