@community-inbox @smoke @fast @linkedin-social-content
Feature: LinkedIn Company Page community inbox read contracts
  Imported Company Page posts are informational read models only.

  Scenario: Calendar returns an imported Page post as immutable
    Given the social-content BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1"
    When the client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-08T00:00:00Z"
    Then the social-content response status should be 200
    And the social-content calendar should contain post "post-1"
    And the social-content calendar item "post-1" should have mutationAllowed false
    And the social-content calendar item "post-1" should have origin "EXTERNAL_OR_UNKNOWN"

  Scenario: Post detail returns an imported Page post as immutable
    Given the social-content BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1"
    When the client requests social-content post detail for "post-1"
    Then the social-content response status should be 200
    And the social-content response should contain externalPostId "post-1"
    And the social-content response should contain mutationAllowed false

  Scenario: A foreign workspace post is not returned
    Given the social-content BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-2"
    When the client requests social-content post detail for "post-1" in workspace "workspace-1"
    Then the social-content response status should be 404

  Scenario Outline: Invalid calendar input is rejected before the reader
    Given the social-content BDD state is reset
    When the client requests social-content calendar from "<from>" to "<to>" with limit <limit>
    Then the social-content response status should be 400
    And the social-content reader should have received 0 calls

    Examples:
      | from                 | to                   | limit |
      | 2026-08-08T00:00:00Z | 2026-08-01T00:00:00Z | 50    |
      | 2026-08-01T00:00:00Z | 2026-08-08T00:00:00Z | 0     |
      | 2026-08-01T00:00:00Z | 2026-08-08T00:00:00Z | 101   |

  Scenario: Calendar preserves an opaque continuation cursor
    Given the social-content BDD state is reset
    And an imported LinkedIn Page post exists in workspace "workspace-1"
    When the client requests social-content calendar from "2026-08-01T00:00:00Z" to "2026-08-08T00:00:00Z" with cursor "opaque.next"
    Then the social-content response status should be 200
    And the social-content reader should receive cursor "opaque.next"
