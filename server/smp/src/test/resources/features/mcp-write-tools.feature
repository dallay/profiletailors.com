@mcp @smoke @fast
Feature: MCP write tool catalog and publication recovery flow
  Validates the end-to-end contract documented in ADR-0019 once the read and
  write tools are exposed through the Spring AI transport:
  `tools/list` advertises the catalog, agents can recover a missed write via
  `list_publications(status=FAILED|BLOCKED|CANCELLED)`, and the agent-facing
  write tool contract (cancel/retry + scope + idempotency) behaves as
  advertised.

  Background:
    Given the MCP workspace is seeded
    And a connected LinkedIn social account exists for MCP

  # ── Catalog ────────────────────────────────────────────────────────────────

  Scenario: tools list advertises the read + write MCP catalog
    When the MCP client requests tools list
    Then the MCP catalog should contain exactly:
      | name                  |
      | mcp_ping              |
      | list_channels         |
      | list_publications     |
      | get_calendar          |
      | list_providers        |
      | create_publication    |
      | edit_publication      |
      | delete_publication    |
      | cancel_publication    |
      | retry_publication     |

  # ── Recovery via list_publications ──────────────────────────────────────────

  Scenario: Agent recovers a missed write through list_publications
    Given a publication exists in FAILED status for MCP
    When the MCP client calls tool "list_publications" with status filter "FAILED"
    Then the MCP response status should be 200
    And the MCP result publications should include the FAILED publication id

  Scenario: Agent recovers a cancelled publication through list_publications
    Given a publication exists in CANCELLED status for MCP
    When the MCP client calls tool "list_publications" with status filter "CANCELLED"
    Then the MCP response status should be 200
    And the MCP result publications should include the CANCELLED publication id

  # ── Cancel/Retry tool behaviour (adapter-level smoke) ───────────────────────

  Scenario: cancel_publication returns the cancel acknowledgement
    Given a publication exists in SCHEDULED status for MCP
    When the MCP client invokes cancel_publication for that publication
    Then the MCP response status should be 200
    And the MCP result publication status should be CANCELLED

  Scenario: retry_publication returns the retry acknowledgement
    Given a publication exists in FAILED status for MCP
    When the MCP client invokes retry_publication for that publication with scheduleMode "NOW"
    Then the MCP response status should be 200
    And the MCP result publication status should be QUEUED
