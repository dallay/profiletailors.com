@publishing @threads @smoke @fast
Feature: Threads publishing provider behavior
  Threads remains provider-aware while external Meta calls are replaced by safe test fixtures.

  Scenario: Disabled Threads is omitted from the provider catalog
    When the client lists configured providers
    Then the publishing response status should be 200
    And the catalog should omit Threads
    And the catalog response should not contain provider secrets

  Scenario: Disabled Threads initiation fails without OAuth material
    Given the verified user has an active workspace membership
    When the client initiates a Threads connection
    Then the publishing response status should be 400
    And the OAuth response should not contain authorization material

  Scenario: LinkedIn initiation alias remains available
    Given the verified user has an active workspace membership
    When the client initiates a LinkedIn connection
    Then the publishing response status should be 200
    And the OAuth response should contain authorizationUrl and state

  Scenario: Threads channels remain workspace scoped
    Given a connected Threads social account exists
    When the client lists connected channels
    Then the publishing response status should be 200
    And the channels list should contain the existing Threads channel

  Scenario: Threads connection can be disconnected safely
    Given a connected Threads social account exists
    When the client disconnects the Threads connection
    Then the publishing response status should be 200
    And the connection response should be deleted for Threads
    When the client lists connected channels
    Then the publishing response status should be 200
    And the channels list should omit Threads

  Scenario: Threads text publication is queued without provider secrets
    Given a connected Threads social account exists
    When the client creates a publication with title "Threads Post" and body "Hello from Threads"
    Then the publishing response status should be 200
    And the response should contain a publicationId
    And the publication status should be "QUEUED"
    And the publication response should not contain provider secrets
