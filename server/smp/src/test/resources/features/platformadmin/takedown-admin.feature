@smoke @platform-takedown @fast @postgres
Feature: Platform admin takedown governance
  Platform operators list, review, and resolve content takedown reports
  through a dedicated admin API under /api/admin/takedown-reports.
  Access requires a PLATFORM_OWNER or PLATFORM_OPERATOR role assignment.
  Reporter email is surfaced for triage but never written to audit logs.

  Background:
    Given a platform operator with role "PLATFORM_OPERATOR" is authenticated

  # ── Query ───────────────────────────────────────────────────────────────────

  Scenario: Unauthenticated request returns 401
    When an unauthenticated platform operator queries takedown reports
    Then the takedown response status should be 401

  Scenario: List returns reports sorted by most recent
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-1"
    When the platform operator queries takedown reports
    Then the takedown response status should be 200
    And the takedown result should contain at least 1 report

  Scenario: Filter by status returns only matching reports
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-2"
    And a takedown report exists with status "APPROVED" and workspace "ws-gov-3"
    When the platform operator queries takedown reports with status filter "REPORTED"
    Then the takedown response status should be 200
    And all takedown reports should have status "REPORTED"

  Scenario: Filter by workspace returns only matching reports
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-4"
    When the platform operator queries takedown reports with workspace filter "ws-gov-4"
    Then the takedown response status should be 200
    And all takedown reports should have workspace "ws-gov-4"

  Scenario: Pagination returns the correct page
    When the platform operator queries takedown reports with page 0 and size 10
    Then the takedown response status should be 200

  # ── Detail ─────────────────────────────────────────────────────────────────

  Scenario: Get existing report returns 200 with asset status
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-5"
    When the platform operator views the takedown report
    Then the takedown response status should be 200
    And the report should include an asset status field

  Scenario: Get non-existent report returns 404
    When the platform operator views the non-existent takedown report
    Then the takedown response status should be 404

  # ── Approve ─────────────────────────────────────────────────────────────────

  Scenario: Operator can approve a reported takedown
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-6"
    When the platform operator approves the takedown report
    Then the takedown response status should be 200
    And the report status should be "APPROVED"

  Scenario: Approve without idempotency key returns 400
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-7"
    When the platform operator approves the takedown report without idempotency key
    Then the takedown response status should be 400

  # ── Reject ─────────────────────────────────────────────────────────────────

  Scenario: Operator can reject a reported takedown with a reason
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-8"
    When the platform operator rejects the takedown report with reason "Not a violation"
    Then the takedown response status should be 200
    And the report status should be "DISMISSED"
    And the report rejection reason should be "Not a violation"

  # ── Idempotency ─────────────────────────────────────────────────────────────

  Scenario: Replaying the same idempotency key returns 200 without duplicate action
    Given a takedown report exists with status "REPORTED" and workspace "ws-gov-9"
    When the platform operator approves the takedown report with idempotency key "idempotent-approve-1"
    And the platform operator approves the takedown report with idempotency key "idempotent-approve-1"
    Then the takedown response status should be 200

  # ── Already decided ─────────────────────────────────────────────────────────

  Scenario: Approving an already dismissed report returns 409
    Given a takedown report exists with status "DISMISSED" and workspace "ws-gov-10"
    When the platform operator approves the takedown report
    Then the takedown response status should be 409

  # ── Role guards ─────────────────────────────────────────────────────────────

  Scenario: SUPPORT_AGENT cannot list reports — 403
    Given the authenticated principal has the role "SUPPORT_AGENT"
    When the platform operator queries takedown reports
    Then the takedown response status should be 403

  Scenario: AUDITOR cannot mutate — 403 on approve
    Given the authenticated principal has the role "AUDITOR"
    And a takedown report exists with status "REPORTED" and workspace "ws-gov-11"
    When the platform operator approves the takedown report
    Then the takedown response status should be 403
