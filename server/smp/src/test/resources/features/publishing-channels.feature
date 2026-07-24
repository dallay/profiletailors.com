@publishing @channel @smoke @fast
Feature: Publishing channels and providers
  Publishing channels domain: list connected channels and configured providers.

  Scenario: List channels (empty)
    When the client lists connected channels
    Then the publishing response status should be 200
    And the channels list should be empty

  Scenario: List resolved provider catalog
    When the client lists configured providers
    Then the publishing response status should be 200
    And the catalog should contain available LinkedIn personal profile without secrets or plans

  Scenario: Hidden providers are omitted from the catalog
    Given LinkedIn provider policy is hidden for the workspace
    When the client lists configured providers
    Then the publishing response status should be 200
    And the catalog should omit LinkedIn

  Scenario: Entitlement-locked providers remain visible with a typed reason
    Given LinkedIn provider policy is entitlement locked for the workspace
    When the client lists configured providers
    Then the publishing response status should be 200
    And the catalog should contain LinkedIn locked for "NOT_ENTITLED"

  Scenario: Capacity blocks a new connection but preserves an existing channel
    Given a connected LinkedIn social account exists
    And LinkedIn provider policy is capacity locked for the workspace
    When the client lists configured providers
    Then the publishing response status should be 200
    And the catalog should contain LinkedIn locked for "CAPACITY_REACHED" with 1 connected channel
    When the client lists connected channels
    Then the publishing response status should be 200
    And the channels list should contain the existing LinkedIn channel

  Scenario: Provider policy is isolated by workspace
    Given LinkedIn provider policy is available for workspace "workspace-1"
    And LinkedIn provider policy is entitlement locked for workspace "workspace-2"
    When the client lists configured providers for workspace "workspace-1"
    Then the publishing response status should be 200
    And the catalog should contain available LinkedIn personal profile without secrets or plans
    When the client lists configured providers for workspace "workspace-2"
    Then the publishing response status should be 200
    And the catalog should contain LinkedIn locked for "NOT_ENTITLED"

  Scenario: OAuth initiation is denied after capacity policy changes
    Given the verified user has an active workspace membership
    And LinkedIn provider policy is capacity locked for the workspace
    When the client initiates a LinkedIn connection
    Then the publishing response status should be 409
    And the OAuth denial should report "CAPACITY_REACHED" without authorization details

  Scenario: OAuth initiation rejects missing authentication
    When the unauthenticated client initiates a LinkedIn connection
    Then the publishing response status should be 401

  Scenario: OAuth initiation rejects missing workspace context
    Given the verified user has an active workspace membership
    When the client initiates a LinkedIn connection without workspace context
    Then the publishing response status should be 400
