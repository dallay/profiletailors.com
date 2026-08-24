@smoke @fast @publishing-stale @postgres
Feature: Stale publication-job visibility for platform operators (DALLAY-555)
  Platform operators need a global, audit-safe view of publication-job claims
  whose lease has expired past the configured stale threshold. The endpoint
  surfaces publication, workspace, age, and a safe canonical next action without
  leaking raw exceptions, tokens, URLs, or storage paths.

  Background:
    Given a platform operator with role "PLATFORM_OPERATOR" is authenticated
    And an authorized workspace member exists
    And a connected LinkedIn social account exists

  # ── Access and validation ───────────────────────────────────────────────

  Scenario: Unauthenticated request to stale jobs returns 401
    When an unauthenticated principal requests the stale publication jobs endpoint
    Then the stale jobs response status should be 401

  Scenario: Auditor cannot read stale publication jobs
    Given the authenticated principal has the role "AUDITOR"
    When the platform operator requests the stale publication jobs endpoint
    Then the stale jobs response status should be 403
     And the stale jobs admin response code should be "PLATFORM_ACCESS_DENIED"

  Scenario: Invalid stale threshold returns a validation error
    When the platform operator requests stale publication jobs with threshold "not-a-duration"
    Then the stale jobs response status should be 400
     And the stale jobs admin response code should be "VALIDATION_ERROR"

  Scenario: Out-of-range stale job limit returns a validation error
    When the platform operator requests stale publication jobs with limit "101"
    Then the stale jobs response status should be 400
     And the stale jobs admin response code should be "VALIDATION_ERROR"

  # ── Happy path ──────────────────────────────────────────────────────────

  Scenario: Operator lists stale claims and sees publication, workspace, age and suggested action
    Given a queued publication exists for "stale-test-1"
    And a publication job has been claimed by worker "worker-stuck-bdd" with a stale lease
    When the platform operator requests the stale publication jobs endpoint
    Then the stale jobs response status should be 200
    And the stale jobs response should contain a jobId for "stale-test-1"
    And the stale jobs response should contain the workspaceId for the publication
    And the stale jobs entry should expose ageSeconds greater than or equal to 0
    And the stale jobs entry should expose suggestedAction "RELEASE_AND_RETRY"
    And the publication status should remain "QUEUED"

  # ── No silent publication ────────────────────────────────────────────────

  Scenario: Stale claims cannot be silently treated as published
    Given a queued publication exists for "stale-test-2"
    And a publication job has been claimed by worker "worker-stuck-bdd-2" with a stale lease
    When the platform operator requests the stale publication jobs endpoint
    Then the stale jobs response status should be 200
    And the publication status should remain "QUEUED"
    And the publication published_at column should be null

  # ── Redaction contract ───────────────────────────────────────────────────

  Scenario: Stale jobs response is safe-shaped and contains no tokens, URLs, exceptions or storage paths
    Given a queued publication exists for "stale-test-3"
    And a publication job has been claimed by worker "worker-stuck-bdd-3" with a stale lease
    When the platform operator requests the stale publication jobs endpoint
    Then the stale jobs response status should be 200
     And the stale jobs response body should be safe-shaped

  # ── Empty state ───────────────────────────────────────────────────────────

  Scenario: No stale claims returns an empty list with total 0
    When the platform operator requests the stale publication jobs endpoint
    Then the stale jobs response status should be 200
    And the stale jobs response total should be 0
    And the stale jobs response staleJobs list should be empty
