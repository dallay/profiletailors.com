@ideas @smoke @fast
Feature: Ideas canvas lifecycle
  Ideas domain: capture, organize and convert ideas.

  Background:
    Given an authorized workspace member exists
    And a connected LinkedIn social account exists

  Scenario: Create and list ideas
    When the client creates an idea with title "Q3 launch angle"
    Then the ideas response status should be 200
    And the ideas response should contain an ideaId
    When the client lists ideas
    Then the ideas response status should be 200
    And the ideas response should contain at least 1 idea

  Scenario: Move and update idea
    Given an existing idea in raw column
    When the client moves the idea to column "done" at position 0
    Then the ideas response status should be 200
    And the idea column should be "done"
    When the client updates the idea title to "Q3 launch final"
    Then the ideas response status should be 200
    And the idea title should be "Q3 launch final"

  Scenario: Configure board columns
    When the client updates idea columns to "Raw,In Progress,Done"
    Then the ideas response status should be 200
    And the columns response should contain 3 columns

  Scenario: Convert idea to publication
    Given an existing idea in raw column
    When the client converts the idea to a publication
    Then the ideas response status should be 200
    And the convert response should contain a publicationId
