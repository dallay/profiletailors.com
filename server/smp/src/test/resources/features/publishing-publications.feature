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
    When the client creates a scheduled publication for "future" with title "Scheduled Post" and body "Scheduled post"
    Then the publishing response status should be 200
    And the response should contain a publicationId
    And the publication status should be "SCHEDULED"

  Scenario: Batch creation groupId is returned by the calendar API
    When the client batch-creates publications for valid targets
    Then the batch publication response status should be 201
    And the batch publication status should be "ALL_CREATED"
    And the batch response should contain 2 created targets
    And the batch publication response should not contain provider data
    When the client requests the calendar for the batch publications around now
    Then the calendar should return the batch publications with the same non-empty groupId

  Scenario: Batch create publication with one invalid target
    When the client batch-creates publications with one valid and one invalid target
    Then the batch publication response status should be 207
    And the batch publication status should be "PARTIAL"
    And the batch response should contain one created and one failed target
    And the batch publication response should not contain provider data

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
    When the client quick-creates a publication for "future" with title "Quick Post" and body "Quick body"
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
