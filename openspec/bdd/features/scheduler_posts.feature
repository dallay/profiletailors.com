Feature: Scheduler Post Management

  Background:
    Given the user is authenticated and on the scheduler page

  @e2e @scheduler @posts
  Scenario: Clicking a post card opens the detail modal
    Given there is at least one post in the scheduler
    When the user clicks on a post card
    Then the post detail modal should be visible
    And the post title should be visible
    And the post content should be visible

  @e2e @scheduler @posts
  Scenario: View Post button opens LinkedIn URL in new tab
    Given there is a published post with a LinkedIn URL
    When the user clicks on the post card
    And the user clicks the "View Post" button
    Then a new tab should open
    And the URL should contain "linkedin.com/feed/update"

  @e2e @scheduler @posts
  Scenario: Delete post from scheduler list view
    Given there is a queued post in the scheduler
    When the user clicks the delete button on the post card
    Then the post should be removed from the scheduler
    And the post card should no longer be visible

  @e2e @scheduler @posts
  Scenario: Month view chip opens detail modal
    Given the user is in month view
    And there is a post in a month cell
    When the user clicks on the post chip
    Then the post detail modal should be visible
    And the post title should match

  @e2e @scheduler @posts
  Scenario: Clicking add button in calendar cell opens composer
    Given the user is in month view
    When the user clicks the "+" add button in a calendar cell
    Then the compose modal should be visible

  @e2e @scheduler @posts @read-only
  Scenario: Published posts are read-only in detail modal
    Given there is a published post
    When the user clicks on the post card
    Then a "Read Only" badge should be visible
    And no edit button should be visible
    And no delete button should be visible

  @e2e @scheduler @posts
  Scenario: Past slots show posts as read-only
    Given there is a published post in a past slot
    When the user clicks on the post card
    Then a "Read Only" badge should be visible

  @e2e @scheduler @posts
  Scenario: Past slots cannot create or edit posts
    Given the user is in month view on a past month
    Then past cells should have aria-disabled="true"
    And the "+" add button should not appear on past cells

  @e2e @scheduler @posts
  Scenario: Past posts cannot be deleted
    Given there is a published post
    When the user clicks on the post card
    Then the delete button should not be visible