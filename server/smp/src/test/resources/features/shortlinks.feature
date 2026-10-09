@shortlinks @smoke @fast
Feature: Short link management
  Scenario: Reject unauthenticated link creation
    When a client creates a link without authentication
    Then the short links response status should be 401

  Scenario: Authenticated user manages a link lifecycle
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    Then the short links response status should be 201
    When a client resolves the created active short link
    Then the short links response status should be 302
    And the short links redirect destination should be "https://example.com/start"
    When the authenticated user gets the created short link
    Then the short links response status should be 200
    When the authenticated user updates the created short link
    Then the short links response status should be 200
    When the authenticated user disables the created short link
    Then the short links response status should be 200
    When the authenticated user enables the created short link
    Then the short links response status should be 200
    When the authenticated user deletes the created short link
    Then the short links response status should be 200

  Scenario: A user cannot access a link owned by another workspace
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And an authenticated user requests the created short link from another workspace
    Then the short links response status should be 404

  Scenario: Workspace member reads recorded metrics for an owned link
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And the authenticated user requests metrics for the created short link
    Then the short links response status should be 200
    And the recorded redirect count should be 0

  Scenario: Workspace shortlink metrics collection includes owned links and click counts
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And a client resolves the created active short link
    And an authenticated user creates a short link
    And an authenticated user creates a short link in another workspace
    And the authenticated user lists workspace link metrics with limit 10
    Then the short links response status should be 200
    And the workspace metrics page should contain exactly the created workspace link ids
    And the workspace metrics page should report 1 recorded redirect for the clicked link
    And the workspace metrics page should report 0 recorded redirects for the unclicked link

  Scenario: Reject unauthenticated metrics request
    When a client requests metrics without authentication
    Then the short links response status should be 401

  Scenario: A user cannot read metrics for a link owned by another workspace
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And an authenticated user requests metrics for the created short link from another workspace
    Then the short links response status should be 404

  Scenario: Unknown public short codes do not redirect
    When a client resolves an unknown public short code
    Then the short links response status should be 404

  Scenario: Replaying a create request with the same idempotency key returns the same link
    Given the short links workspace is prepared
    When an authenticated user creates a short link with idempotency key "shortlinks-replay" and destination "https://example.com/replay"
    And the authenticated user repeats the short link creation with idempotency key "shortlinks-replay" and destination "https://example.com/replay"
    Then the short links response status should be 201
    And the repeated short link should have the same resource ID

  Scenario: Reusing an idempotency key with a different payload conflicts
    Given the short links workspace is prepared
    When an authenticated user creates a short link with idempotency key "shortlinks-conflict" and destination "https://example.com/first"
    And the authenticated user repeats the short link creation with idempotency key "shortlinks-conflict" and destination "https://example.com/second"
    Then the short links response status should be 409

  Scenario: Creating a link invalidates a cached public miss
    Given the short links workspace is prepared
    When a client resolves an unknown public short code
    Then the short links response status should be 404
    When an authenticated user creates a short link for the resolved cache miss
    Then the short links response status should be 201
    When a client resolves the previously unknown public short code
    Then the short links response status should be 302
    And the short links redirect destination should be "https://example.com/cache-miss"

  Scenario: Expired short links do not redirect after an update
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And the authenticated user expires the created short link
    Then the short links response status should be 200
    When a client resolves the created short link
    Then the short links response status should be 410

  Scenario: Disabled short links do not redirect
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And the authenticated user disables the created short link
    And a client resolves the created short link
    Then the short links response status should be 404

  Scenario: Expired short links do not redirect
    Given the short links workspace is prepared
    When an authenticated user creates a short link
    And the authenticated user expires the created short link
    Then the short links response status should be 200
    When a client resolves the created active short link
    Then the short links response status should be 410

  Scenario: A cached unknown code continues to return not found
    When a client resolves the unknown alias "CacheAlias"
    Then the short links response status should be 404
