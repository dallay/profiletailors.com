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

  Scenario: Create a weekly recurring schedule for Monday and Wednesday
    When the client creates a weekly recurring schedule on Monday and Wednesday
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    And the recurring response status should be "active"

  Scenario: Create a monthly recurring schedule on day 15
    When the client creates a monthly recurring schedule on day 15
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    And the recurring response status should be "active"

  Scenario: Create and list multiple recurring schedules
    When the client creates a daily recurring schedule
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    When the client creates a weekly recurring schedule on Monday and Wednesday
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    When the client lists recurring schedules
    Then the recurring schedules list should contain 2 schedule

  Scenario: Create and delete a recurring schedule
    When the client creates a daily recurring schedule
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    When the client deletes the recurring schedule
    Then the publishing response status should be 200
    When the client lists recurring schedules
    Then the recurring schedules list should contain 0 schedule

  Scenario: Create, pause, and resume a recurring schedule
    When the client creates a daily recurring schedule
    Then the publishing response status should be 200
    And the recurring response should contain a schedule id
    And the recurring response status should be "active"
    When the client pauses the recurring schedule
    Then the recurring response status should be "paused"
    When the client resumes the recurring schedule
    Then the recurring response status should be "active"
