@mcp @smoke @fast
Feature: MCP tool invocations via JSON-RPC over HTTP
  The MCP server exposes social-media management tools to AI clients.
  All requests go through POST /api/mcp with a Bearer JWT that carries
  workspace_id and audience claims.

  Background:
    Given the MCP workspace is seeded

  # ── list_channels ──────────────────────────────────────────────────────────

  Scenario: list_channels happy path
    Given a connected LinkedIn social account exists for MCP
    When the MCP client calls tool "list_channels" with no arguments
    Then the MCP response status should be 200
    And the MCP result should be successful

  Scenario: list_channels without authentication
    When the unauthenticated MCP client calls tool "list_channels"
    Then the MCP response status should be 401

  Scenario: list_channels with invalid workspace
    When the MCP client with wrong workspace calls tool "list_channels"
    Then the MCP response status should be 200
    And the MCP result should contain error code "workspace_mismatch"

  # ── list_publications ──────────────────────────────────────────────────────

  Scenario: list_publications happy path
    When the MCP client calls tool "list_publications" with date range
    Then the MCP response status should be 200
    And the MCP result should be successful

  Scenario: list_publications without authentication
    When the unauthenticated MCP client calls tool "list_publications"
    Then the MCP response status should be 401

  Scenario: list_publications with invalid date format
    When the MCP client calls tool "list_publications" with invalid dates
    Then the MCP response status should be 200
    And the MCP result should contain error code "invalid_date_range"

  # ── get_calendar ───────────────────────────────────────────────────────────

  Scenario: get_calendar happy path
    When the MCP client calls tool "get_calendar" with date range
    Then the MCP response status should be 200
    And the MCP result should be successful

  Scenario: get_calendar without authentication
    When the unauthenticated MCP client calls tool "get_calendar"
    Then the MCP response status should be 401

  # ── list_providers ─────────────────────────────────────────────────────────

  Scenario: list_providers happy path
    When the MCP client calls tool "list_providers" with no arguments
    Then the MCP response status should be 200
    And the MCP result should be successful

  Scenario: list_providers without authentication
    When the unauthenticated MCP client calls tool "list_providers"
    Then the MCP response status should be 401

  # ── Workspace isolation ────────────────────────────────────────────────────

  Scenario: Workspace isolation prevents cross-workspace access
    Given a connected LinkedIn social account exists for MCP
    When the MCP client bound to workspace "workspace-other" calls tool "list_channels"
    Then the MCP response status should be 200
    And the MCP result channels should not contain workspace-1 data

  # ── OAuth discovery ────────────────────────────────────────────────────────

  Scenario: RFC 9728 protected resource metadata is publicly accessible
    When any client requests the OAuth protected resource metadata
    Then the MCP response status should be 200
    And the metadata should contain the MCP resource URI and supported scopes
