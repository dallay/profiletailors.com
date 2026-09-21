@smoke @platform-notifications @fast @postgres
Feature: Platform admin notification management
  Platform operators manage notifications through a dedicated admin API.
  Access requires an active platform role assignment; workspace roles are insufficient.
  Sensitive fields in payloads are redacted to protect credential data.

  Background:
    Given a platform operator with role "PLATFORM_OPERATOR" is authenticated

  # ── Query notifications ───────────────────────────────────────────────────────

  Scenario: Query returns empty result when no notifications exist
    When the platform operator queries notifications with no filters
    Then the notification response status should be 200
    And the notification result should be empty

  Scenario: Query returns notifications when they exist
    Given a notification exists with channel "EMAIL" and status "FAILED"
    When the platform operator queries notifications
    Then the notification response status should be 200
    And the notification result should contain notifications

  # ── Filter notifications ─────────────────────────────────────────────────────

  Scenario: Filter by status returns only matching notifications
    Given a notification exists with channel "EMAIL" and status "FAILED"
    And a notification exists with channel "EMAIL" and status "SENT"
    When the platform operator queries notifications with status filter "FAILED"
    Then the notification response status should be 200
    And all notifications should have status "FAILED"

  Scenario: Filter by channel returns only matching notifications
    Given a notification exists with channel "EMAIL" and status "FAILED"
    And a notification exists with channel "SMS" and status "FAILED"
    When the platform operator queries notifications with channel filter "EMAIL"
    Then the notification response status should be 200
    And all notifications should have channel "EMAIL"

  # ── Redaction ────────────────────────────────────────────────────────────────

  Scenario: Sensitive fields are redacted in notification payload
    Given a notification exists with channel "EMAIL" and status "FAILED"
    When the platform operator queries notifications
    Then the notification response status should be 200
    And no notification payload should contain "token"
    And no notification payload should contain "password"
    And no notification payload should contain "acceptUrl"

  # ── Retry notifications ─────────────────────────────────────────────────────

  Scenario: Retry eligible notification returns 200 and creates new notification
    Given a failed password-recovery notification exists
    When the platform operator retries the notification
    Then the notification response status should be 200
    And a new notification should be created

  Scenario: Retry invitation template notification returns 400
    Given a failed invitation notification exists
    When the platform operator retries the notification
    Then the notification response status should be 400
    And the response should indicate notification is not retryable

  # ── Access control ───────────────────────────────────────────────────────────

  Scenario: Retry with expired notification returns 400
    Given an expired notification exists
    When the platform operator retries the notification
    Then the notification response status should be 400
    And the response should indicate notification not found

  Scenario: Unauthenticated request returns 401
    When an unauthenticated principal retries a notification
    Then the notification response status should be 401

  # ── Idempotency ─────────────────────────────────────────────────────────────

  Scenario: Retry with same idempotency key returns 409
    Given a failed password-recovery notification exists
    And the idempotency key is set to "test-idempotency-key"
    When the platform operator retries the notification with the idempotency key
    Then the notification response status should be 200
    When the platform operator retries the notification with the idempotency key
    Then the notification response status should be 409
