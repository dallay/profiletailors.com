@analytics @smoke @fast
Feature: Analytics Dashboard
  Analytics bounded context: overview metrics, per-post analytics, best times, and CSV export.

  Background:
    Given an authorized workspace member exists
    And a connected LinkedIn social account exists

  Scenario: Get analytics overview for default date range
    When the client requests analytics overview
    Then the analytics response status should be 200
    And the overview contains totalImpressions
    And the overview contains totalEngagements
    And the overview contains engagementRate
    And the overview contains totalClicks
    And the overview contains dailyMetrics

  Scenario: Get analytics overview for custom date range
    When the client requests analytics overview from "2026-01-01" to "2026-01-31"
    Then the analytics response status should be 200
    And the overview period start is "2026-01-01"
    And the overview period end is "2026-01-31"

  Scenario: Get post analytics list
    Given a published publication exists
    When the client requests post analytics
    Then the analytics response status should be 200
    And the post analytics list contains at least 1 post
    And each post has postId and publishedAt

  Scenario: Get best posting times
    When the client requests best posting times
    Then the analytics response status should be 200
    And the best times response contains a slots array

  Scenario: Export analytics as CSV
    When the client exports analytics as CSV
    Then the analytics response status should be 200
    And the response content type is "text/csv"
    And the CSV contains the header row

  Scenario: Analytics overview returns empty metrics for workspace with no published posts
    When the client requests analytics overview
    Then the analytics response status should be 200
    And the overview totalImpressions is 0
    And the overview totalEngagements is 0
