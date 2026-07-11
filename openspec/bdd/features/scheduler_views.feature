Feature: Scheduler Views and Navigation

  Background:
    Given the user is authenticated and on the scheduler page "/scheduler"

  @e2e @scheduler @views
  Scenario: Default route redirects to week view
    When the user navigates to "/scheduler"
    Then the URL should be "/scheduler/calendar/week"
    And the week view should be visible
    And 24 hour slots should be displayed

  @e2e @scheduler @views
  Scenario: Week view displays 24-hour time slots
    When the user navigates to "/scheduler/calendar/week"
    Then "12 AM" should be visible
    And "11 PM" should be visible

  @e2e @scheduler @views
  Scenario: Month view displays 42 day cells
    When the user navigates to "/scheduler/calendar/month"
    Then exactly 42 day cells should be displayed

  @e2e @scheduler @views
  Scenario: List view shows posts without calendar cells
    When the user navigates to "/scheduler/list"
    Then no calendar day cells should be displayed

  @e2e @scheduler @navigation
  Scenario: Deep link with date param preserves the date
    When the user navigates to "/scheduler/calendar/month?date=2026-01-15"
    Then the URL should contain "date=2026-01-15"

  @e2e @scheduler @navigation
  Scenario: Switching to List view updates the URL
    Given the user is on "/scheduler/calendar/week"
    When the user clicks the "List" button
    Then the URL should change to "/scheduler/list"

  @e2e @scheduler @navigation
  Scenario: Switching from List back to Calendar returns to week view
    Given the user is on "/scheduler/list"
    When the user clicks the "Calendar" button
    Then the URL should change to "/scheduler/calendar/week"
    And "12 AM" should be visible

  @e2e @scheduler @navigation
  Scenario: Switching from Month to Week updates the URL
    Given the user is on "/scheduler/calendar/month"
    When the user clicks the "Week" toggle
    Then the URL should change to "/scheduler/calendar/week"

  @e2e @scheduler @navigation
  Scenario: Forward button updates date param in URL
    Given the user is on "/scheduler/calendar/week"
    When the user clicks the forward button
    Then the URL should contain a new date parameter
    And the URL should be different from the original

  @e2e @scheduler @navigation
  Scenario: Backward button updates date param in URL
    Given the user is on "/scheduler/calendar/week"
    When the user clicks the backward button
    Then the URL should contain a new date parameter
    And the URL should be different from the original

  @e2e @scheduler @navigation
  Scenario: Today button returns to canonical week view URL
    Given the user has navigated away from today
    When the user clicks the "Today" button
    Then the URL should change to "/scheduler/calendar/week"

  @e2e @scheduler @browser-history
  Scenario: Browser back restores previous scheduler state
    Given the user is on "/scheduler/calendar/week"
    When the user clicks the "List" button
    Then the URL should be "/scheduler/list"
    When the user clicks the browser back button
    Then the URL should be "/scheduler/calendar/week"

  @e2e @scheduler @browser-history
  Scenario: Browser forward restores forward scheduler state
    Given the user is on "/scheduler/calendar/week"
    And the user clicked "List" (URL is "/scheduler/list")
    And the user clicked browser back (URL is "/scheduler/calendar/week")
    When the user clicks the browser forward button
    Then the URL should be "/scheduler/list"