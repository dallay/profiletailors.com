@bulk @fast @smoke
Feature: Bulk scheduling

  Background:
    Given workspace "ws-bulk-1" exists
    And user "bulk-user" with emailStatus VERIFIED in workspace "ws-bulk-1" with token "valid-token"

  Scenario: Gherkin 1 — per-row errors, no persistence
    When POST /bulk/validate with csvText "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello 1,2099-06-15T10:00:00Z,UTC,,\n,not-a-date,UTC,,\nHello 3,2099-06-15T11:00:00Z,UTC,,"
    Then validate MUST list 3 rows with 1 INVALID and no DB writes

  Scenario: Retry is side-effect free
    When POST /bulk/validate with csvText "bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2099-06-15T10:00:00Z,UTC,,"
    And POST /bulk/validate with same csvText
    Then responses MUST match and neither MUST persist

  Scenario: Gherkin 2 — chunked atomic, partial success
    Given validate flagged 2 VALID 1 INVALID
    When POST /bulk/schedule with csvText "bodyText,scheduledFor,timezone,media_urls,hashtags\nA,2099-06-15T10:00:00Z,UTC,,\nB,2099-06-15T11:00:00Z,UTC,,\n,not-a-date,UTC,,"
    Then it MUST return scheduledCount 2 failedCount 1 with 2 SCHEDULED publications

  Scenario: 1000-row batch chunked
    Given csv with 1000 rows
    When schedule is called
    Then system MUST persist in 10-20 transactions and report via job status

  Scenario: Gherkin 3 — owner sees counts
    Given workspace A job is PARTIAL with total 3 scheduled 2 failed 1
    When GET /bulk/jobs/{jobId} in A is called
    Then it MUST return 200 with counts and row errors

  Scenario: Cross-workspace blocked
    Given job in workspace A "ws-bulk-1" with id "job-cross"
    When workspace B "ws-bulk-2" requests same jobId
    Then it MUST return 404

  Scenario: Gherkin 4 — catalog and CSV correct
    When GET /bulk/templates is called
    And GET /bulk/templates/linkedin-calendar/csv is called
    Then list MUST be non-empty and CSV header MUST match canonical order

  Scenario: Gherkin 5 — blank lines skipped
    Given CSV with 2 rows plus 1 blank line
    When validate is called
    Then result MUST contain 2 rows

  Scenario: Gherkin 5 — invalid date and missing content
    Given row with scheduledFor not-a-date empty body and media
    When validate is called
    Then row MUST be INVALID with INVALID_DATE and MISSING_CONTENT

  Scenario: Duplicate warning and invalid media
    Given duplicate rows and row with blocked url "http://10.0.0.1/evil.jpg"
    When validate or schedule is called
    Then second duplicate MUST warn DUPLICATE media row MUST be INVALID_MEDIA

  Scenario: Workspace mismatch rejected
    Given user in A calls bulk for B
    When request is evaluated
    Then it MUST return 403 or 404 and not process

  Scenario: Unverified blocked
    Given user with emailStatus UNVERIFIED calls schedule
    Then it MUST return 403

  Scenario: Duplicate CSV returns 409
    Given same principal resubmits identical csvHash
    When schedule is called
    Then it MUST return 409 with existing jobId

  Scenario: Capability violation PDF
    Given LinkedIn row with media APPLICATION/PDF url "https://example.com/a.pdf"
    When validate is called
    Then row MUST be INVALID with CAPABILITY_VIOLATION
