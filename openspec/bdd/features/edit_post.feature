Feature: Post Editing

  Background:
    Given the user is authenticated with a queued post
    And the user is on the scheduler

  @e2e @edit-post
  Scenario: Edit button is visible for queued posts
    When the user clicks on a queued post card
    Then the edit button should be visible

  @e2e @edit-post
  Scenario: Edit opens composer with precached data
    When the user clicks on the post card
    And the user clicks the edit button
    Then the compose modal should open
    And the content should be pre-filled
    And the channel should be pre-selected

  @e2e @edit-post
  Scenario: Edited post saves changes
    Given the user clicks on the post card
    And the user clicks the edit button
    When the user modifies the content
    And the user clicks the submit button
    Then the post should be updated in the scheduler
    And the changes should be reflected

  @e2e @edit-post
  Scenario: Changing date moves post in calendar
    Given the user clicks on the post card
    And the user clicks the edit button
    When the user changes the date to a different day
    And the user clicks the submit button
    Then the post should appear on the new date in the calendar

  @e2e @edit-post
  Scenario: Changing channel updates post channel
    Given the user clicks on the post card
    And the user clicks the edit button
    When the user changes the channel
    And the user clicks the submit button
    Then the post should show the new channel