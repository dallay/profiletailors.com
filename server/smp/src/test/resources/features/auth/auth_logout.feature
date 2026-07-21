@auth @fast @postgres
Feature: User Logout — idempotency
  Logout is idempotent when called without an active session cookie.

  Background:
    Given a previously registered local user session exists

  Scenario: Logout is idempotent without a valid session cookie
    Given the user has no active session
    When the client logs out without a valid session
    Then the response status should be 204
