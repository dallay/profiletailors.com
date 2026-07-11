Feature: Post Creation and Composition

  Background:
    Given the user is authenticated with a verified email
    And the user is on the compose modal

  @e2e @compose
  Scenario: Compose modal opens from calendar cell
    Given the user is on the scheduler
    When the user clicks the "+" add button in a future calendar cell
    Then the compose modal should be visible

  @e2e @compose
  Scenario: Submit button is disabled by default
    Given the compose modal is open
    Then the submit button should be disabled

  @e2e @compose
  Scenario: Submit button is enabled when content and channel are provided
    Given the compose modal is open
    When the user fills the content with "Test post content"
    And the user selects a channel
    Then the submit button should be enabled

  @e2e @compose
  Scenario: LinkedIn preview shows truncated content
    Given the compose modal is open
    When the user fills the content with a long text
    Then the LinkedIn preview should show truncated content (max 140 chars)

  @e2e @compose
  Scenario: Cancel button closes modal without creating post
    Given the compose modal is open
    When the user clicks the cancel button
    Then the compose modal should be closed
    And no new post should be created

  @e2e @compose
  Scenario: Successfully created post appears in scheduler as QUEUED
    Given the compose modal is open
    And the user fills the content with "New scheduled post"
    And the user selects a channel
    And the user sets a future date and time
    When the user clicks the submit button
    Then the compose modal should be closed
    And the new post should appear in the scheduler
    And the post should have status "QUEUED"

  @e2e @compose @validation
  Scenario: Cannot create post without content
    Given the compose modal is open
    And the user selects a channel
    When the user tries to submit
    Then the submit button should remain disabled

  @e2e @compose @validation
  Scenario: Cannot create post without channel selected
    Given the compose modal is open
    And the user fills the content with "Some content"
    When the user tries to submit
    Then the submit button should remain disabled

  @e2e @compose @validation
  Scenario: Cannot create post without date/time
    Given the compose modal is open
    And the user fills the content with "Some content"
    And the user selects a channel
    When the user tries to submit without date/time
    Then the validation should prevent submission