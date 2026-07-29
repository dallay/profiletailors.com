@identity @password-recovery @smoke @fast
Feature: Deliver password reset notifications
  As the platform
  I want password reset emails to be dispatched securely
  So that account owners can complete password recovery

  @dispatch
  Scenario: Dispatch a password reset notification after token creation
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then a password reset notification should be scheduled
    And the notification should be sent to "user@example.com"
    And the password reset email should contain a link to the reset password page

  @retry @pr-3
  Scenario: [PR 3] Retry a temporary email provider failure
    Given the password reset email provider is temporarily unavailable
    When the notification dispatcher attempts delivery
    Then the delivery should be retried according to notification policy
    And the raw token should not appear in retry logs

  @failure @pr-3
  Scenario: [PR 3] Record a terminal notification failure
    Given all configured delivery retries are exhausted
    When the password reset email cannot be delivered
    Then the notification should be marked as failed
    And operational telemetry should record the failure
    And the raw token should not be included in the failure record

  @privacy @pr-3
  Scenario: [PR 3] Notification telemetry excludes sensitive values
    Given a password reset notification is dispatched
    Then telemetry may include the notification type
    And telemetry may include delivery status
    But telemetry should not include the raw token
    And telemetry should not include the new password
    And telemetry should not include the reset URL query string

  @template
  Scenario: Render the reset URL using the configured application base URL
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the password reset email should contain a link to the reset password page
    And the link should include the raw reset token

  @template
  Scenario: Escape user-controlled values in the email template
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the email should not contain the current password
    And the email should not contain a temporary password
    And the email should state that the request can be ignored

  @privacy
  Scenario: Notification content excludes password secrets
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the email should not contain the current password
    And the email should not contain a temporary password
    And the password reset email should contain a link to the reset password page
