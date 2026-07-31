@recurring @fast
Feature: Recurring publication schedules
  Recurring schedules create future publications from an existing scheduled post.

  Background:
    Given an authorized workspace member exists
    Given a scheduled publication exists

  Scenario: Create and pause a daily recurring schedule
    When the client creates a daily recurring schedule
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    And at least 3 recurring publications should be scheduled
    When the client pauses the recurring schedule
    Then the recurring response status should be "paused"
    When the client lists recurring schedules
    Then the recurring response status should be "paused"
