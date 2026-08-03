@publication @smoke @fast
Feature: Publications management
  Publications domain: create, edit, cancel, delete, and list publications.

  Background:
    Given an authorized workspace member exists
    And a connected LinkedIn social account exists

  Scenario: Create queued publication
    When the client creates a publication with title "Draft Post" and body "Hello"
    Then the publishing response status should be 200
    And the response should contain a publicationId
    And the publication status should be "QUEUED"

  Scenario: Create scheduled publication
    When the client creates a scheduled publication for "+7days" with title "Scheduled Post" and body "Scheduled post"
    Then the publishing response status should be 200
    And the response should contain a publicationId
    And the publication status should be "SCHEDULED"

  Scenario: Edit a draft publication
    Given a draft publication exists
    When the client edits the publication with new title "Updated Title"
    Then the publishing response status should be 200
    And the response title should be "Updated Title"

  Scenario: Cancel a scheduled publication
    Given a scheduled publication exists
    When the client cancels the publication
    Then the publishing response status should be 200
    And the publication status should be "CANCELLED"

  Scenario: Delete a draft publication
    Given a draft publication exists
    When the client deletes the publication
    Then the publishing response status should be 200
    When the client lists publications
    Then the publishing response status should be 200
    And the response should contain 0 publications

  Scenario: Quick-create a scheduled publication
    When the client quick-creates a publication for "+7days" with title "Quick Post" and body "Quick body"
    Then the publishing response status should be 200
    And the publication status should be "SCHEDULED"

  Scenario: List publications with no publications
    When the client lists publications
    Then the publishing response status should be 200
    And the response should contain 0 publications

  Scenario: List publications with existing ones
    Given a draft and a scheduled publication exist
    When the client lists publications
    Then the publishing response status should be 200
    And the response should contain 2 publications
