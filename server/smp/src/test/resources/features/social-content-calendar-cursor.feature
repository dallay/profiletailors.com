@social-content-calendar @smoke @fast @postgres
Feature: Social content calendar keyset cursor continuation
  Imported LinkedIn Page posts support cursor-based continuation through keyset pagination.

  Scenario: Calendar returns paginated results with a next cursor
    Given the social-content calendar cursor BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T10:00:00Z"
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T11:00:00Z"
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T12:00:00Z"
    When the cursor client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-02T00:00:00Z" with limit 2
    Then the cursor social-content response status should be 200
    And the social-content calendar should contain 2 posts
    And the cursor social-content response should contain a nextCursor

  Scenario: Calendar continues from the provided cursor position
    Given the social-content calendar cursor BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T10:00:00Z"
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T11:00:00Z"
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T12:00:00Z"
    When the cursor client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-02T00:00:00Z" with limit 2
    Then the cursor social-content response status should be 200
    When the cursor client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-02T00:00:00Z" with the last received cursor
    Then the cursor social-content response status should be 200
    And the social-content calendar should contain 1 post

  Scenario: Calendar returns no next cursor on the final page
    Given the social-content calendar cursor BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T10:00:00Z"
    When the cursor client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-02T00:00:00Z" with limit 50
    Then the cursor social-content response status should be 200
    And the social-content calendar should contain 1 post
    And the cursor social-content response should not contain a nextCursor

  Scenario: A cursor from a foreign workspace is rejected
    Given the social-content calendar cursor BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T10:00:00Z"
    When the client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-02T00:00:00Z" with cursor "eyIwIjoid29ya3NwYWNlLTEiLCIyIjoiMjAyNi0wOC0wMVQxMDowMDowMFoiLCIzIjoiTElOS0VEIn0" for workspace "workspace-2"
    Then the cursor social-content response status should be 400
    And the cursor social-content problem should contain denial "INVALID_SOCIAL_CONTENT_CURSOR"

  Scenario: A malformed cursor is rejected
    Given the social-content calendar cursor BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1" via the production repository with publishedAt "2026-08-01T10:00:00Z"
    When the cursor client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-02T00:00:00Z" with cursor "not-valid-base64!!!"
    Then the cursor social-content response status should be 400
    And the cursor social-content problem should contain denial "INVALID_SOCIAL_CONTENT_CURSOR"
