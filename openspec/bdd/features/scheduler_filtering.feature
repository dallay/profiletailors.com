Feature: Scheduler Filtering

  Background:
    Given the user is authenticated and on the scheduler page "/scheduler/calendar/week"

  @e2e @scheduler @filtering
  Scenario: Initial view shows All Channels
    Then the header should show "All Channels"
    And the URL should not contain "channels"

  @e2e @scheduler @filtering
  Scenario: Filtering by LinkedIn channel adds channels param to URL
    When the user clicks on a LinkedIn channel filter button
    Then the URL should contain "channels[]"

  @e2e @scheduler @filtering
  Scenario: Clicking All Channels removes channel filter
    Given a channel filter is active
    When the user clicks the "All Channels" button
    Then the URL should not contain "channels[]"
    And the header should show "All Channels"

  @e2e @scheduler @filtering
  Scenario: Filtering by post type "Queued"
    Given the user is in list view
    When the user selects "Queued" from the post type filter
    Then only queued posts should be displayed

  @e2e @scheduler @filtering
  Scenario: Filtering by post type "Published"
    Given the user is in list view
    When the user selects "Published" from the post type filter
    Then only published posts should be displayed

  @e2e @scheduler @filtering
  Scenario: Resetting post type filter shows all posts
    Given a post type filter is active
    When the user selects "All Posts" from the filter
    Then all posts should be displayed