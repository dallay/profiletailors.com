# Spec: Password Recovery

Delta spec for the `password-recovery` change. It preserves the complete
product contract while assigning implementation and executable acceptance
ownership to three delivery slices: **PR 1 core backend**, **PR 2 frontend**,
and **PR 3 hardening**. A later slice does not weaken or remove its requirements;
it only defers their production behavior and acceptance execution.

## Purpose

Allow a user who has lost access to their account to regain it through a
self-service, time-limited email link — without the system leaking whether
an email corresponds to an existing account.

## Capabilities

### Modified

- **`iam`** — Document the rule that a successful password reset MUST revoke
  every refresh session for the principal and MUST NOT issue a new session
  token. Implementation detail (`RefreshSessionLifecycleService.revokeAllForPrincipal`)
  is introduced to support this rule. No spec text changes inside the
  `iam` capability itself beyond this addendum.

- **`email-verification`** — Architectural reference only. The new password
  reset capability mirrors the *pattern* (hash-only token storage, atomic
  consume, async event-driven email dispatch, single-use enforcement) but
  does NOT reuse the `email_verification_tokens` table or the
  `EmailVerificationTokenHasher` symbol. Independent lifecycle.

## Delivery Classification

| Requirement group                                                                               | Delivery slice    | Executable acceptance owner                                                          |
|-------------------------------------------------------------------------------------------------|-------------------|--------------------------------------------------------------------------------------|
| REQ-PR, REQ-RP, REQ-TOK-01..05                                                                  | PR 1 core backend | PR 1 backend unit, HTTP, BDD, and real-Postgres tests where database semantics apply |
| REQ-NOT-01..05                                                                                  | PR 1 core backend | PR 1 notification/template acceptance                                                |
| REQ-NOT-06                                                                                      | PR 3 hardening    | PR 3 telemetry acceptance                                                            |
| REQ-UI-01..16                                                                                   | PR 2 frontend     | PR 2 Vitest and Playwright acceptance                                                |
| Audit, retention cleanup, notification retry/final failure, and operational telemetry scenarios | PR 3 hardening    | PR 3 backend acceptance                                                              |

Feature-level delivery tags classify PR 1 backend scenarios, frontend coverage is
owned by PR 2, and scenario-level `[PR 3]` headings plus `@pr-3` tags identify
hardening overrides. PR 1 MUST NOT be considered responsible for implementing or
executing PR 3 production behavior.

## Requirements

### REQ-PR — Request Password Reset

**Delivery: PR 1 core backend.**

The capability MUST satisfy these requirements when the system exposes
`POST /api/auth/forgot-password`.

- **REQ-PR-01** Request body MUST contain a single `email` field of type
  string.
- **REQ-PR-02** Email MUST be normalized (trimmed, lowercased) before
  account lookup. The same normalization rule applied by the
  `identity` bounded context for login and registration MUST be reused.
- **REQ-PR-03** When the normalized email resolves to a local principal
  identity AND a `LocalPasswordCredential` exists for that principal, the
  capability MUST: (a) invalidate every active password reset token for the
  principal, (b) generate a new token, (c) persist ONLY the token hash,
  (d) dispatch a `PasswordResetRequested` domain event after the transaction
  commits.
- **REQ-PR-04** When the normalized email resolves to a principal identity
  that has NO local password credential (OAuth-only identity), the
  capability MUST do nothing silently — no token created, no event
  published.
- **REQ-PR-05** When no principal identity matches the normalized email,
  the capability MUST do nothing silently — no token created, no event
  published.
- **REQ-PR-06** The HTTP response MUST be `202 Accepted` with NO body for
  every case (existing local, OAuth-only, unknown). The response MUST NOT
  reveal whether the account exists.
- **REQ-PR-07** Generated tokens MUST contain at least 256 bits of entropy
  from a CSPRNG. Tokens MUST be URL-safe Base64 (no padding).
- **REQ-PR-08** Tokens MUST expire no later than 30 minutes after issuance.
- **REQ-PR-09** Issuing a new token MUST invalidate all previously-active
  (unused, unexpired) tokens for the same principal in the same
  transaction. Only one active token per principal is permitted at any
  time.
- **REQ-PR-10** The raw token MUST NEVER be persisted in any database
  table, audit record, log line, metric label, or exception message.
- **REQ-PR-11** The `PasswordResetRequested` event MUST be dispatched
  asynchronously AFTER the token persistence transaction commits. No email
  may be sent before the token row is durably persisted.
- **REQ-PR-12** Invalid request bodies (missing email, malformed email,
  missing JSON body, malformed JSON) MUST return `400` with RFC 9457
  Problem Details. Validation error code: `VALIDATION_ERROR`.
- **REQ-PR-13** Rate limit per source IP: 5 requests per 15 minutes.
- **REQ-PR-14** Rate limit per normalized email: 3 requests per 30
  minutes. The email bucket MUST increment even when the email does not
  resolve to an account.
- **REQ-PR-15** Exceeding a rate limit MUST return `429` with RFC 9457
  Problem Details and code `AUTH_RATE_LIMIT_EXCEEDED`. The 429 response
  MUST NOT reveal whether the email exists.
- **REQ-PR-16** If password recovery is disabled by configuration, the
  endpoint MUST return `503` with RFC 9457 Problem Details and no token
  must be created.

### REQ-RP — Reset Password

**Delivery: PR 1 core backend.**

The capability MUST satisfy these requirements when the system exposes
`POST /api/auth/reset-password`.

- **REQ-RP-01** Request body MUST contain `token` (string) and
  `newPassword` (string).
- **REQ-RP-02** The raw token received in the request MUST be hashed with
  the same SHA-256 algorithm used by `EmailVerificationTokenHasher` (and
  its `PasswordResetTokenHasher` counterpart) before any lookup. The raw
  token MUST NOT be persisted, logged, or emitted in any response or
  metric.
- **REQ-RP-03** Look up the token by hash. If no row matches the hash, the
  response MUST be `400` with code `INVALID_PASSWORD_RESET_TOKEN`. No
  password MUST be changed and no session MUST be revoked.
- **REQ-RP-04** If the matched token has `used_at IS NOT NULL`, the
  response MUST be `400` with code `USED_PASSWORD_RESET_TOKEN`. No
  password MUST be changed and no session MUST be revoked.
- **REQ-RP-05** If the matched token has `expires_at <= now`, the
  response MUST be `400` with code `EXPIRED_PASSWORD_RESET_TOKEN`. No
  password MUST be changed and no session MUST be revoked.
- **REQ-RP-06** The public error detail for codes
  `INVALID_PASSWORD_RESET_TOKEN`, `EXPIRED_PASSWORD_RESET_TOKEN`, and
  `USED_PASSWORD_RESET_TOKEN` MUST be IDENTICAL: "This password reset
  link is invalid or has expired. Request a new one."
- **REQ-RP-07** Token consumption and password update MUST be atomic in a
  single database transaction. The atomic operation MUST be equivalent to:
  ```sql
  UPDATE password_reset_tokens
     SET used_at = :now
   WHERE token_hash = :hash
     AND used_at IS NULL
     AND expires_at > :now;
  ```
  If this update affects exactly one row, the password credential row
  for the principal MUST be updated in the same transaction. If zero
  rows are affected, no other write MUST occur.
- **REQ-RP-08** The new password MUST be hashed using the configured
  `PasswordHasher`. Plain text MUST NEVER be persisted.
- **REQ-RP-09** After the atomic operation succeeds, ALL refresh sessions
  for the principal MUST be revoked in the same transaction. Any
  subsequent refresh request MUST be rejected with `401`.
- **REQ-RP-10** After a successful reset, NO access token or refresh
  token MUST be issued in the response. The response MUST be
  `204 No Content` with no body and no `Set-Cookie` header.
- **REQ-RP-11** `newPassword` MUST satisfy the same password policy
  applied at registration: minimum 12 characters, maximum 128 characters,
  not blank. Failing validation MUST return `400` with code
  `INVALID_PASSWORD` and MUST NOT consume the token.
- **REQ-RP-12** Rate limit per source IP: 10 requests per 15 minutes.
- **REQ-RP-13** Exceeding a rate limit MUST return `429` with code
  `AUTH_RATE_LIMIT_EXCEEDED`.
- **REQ-RP-14** If password recovery is disabled by configuration, the
  endpoint MUST return `503` and MUST NOT consume the token.

### REQ-TOK — Token Storage

**Delivery: PR 1 core backend, except retention cleanup acceptance is PR 3 hardening.**

- **REQ-TOK-01** Tokens MUST be persisted in a dedicated table named
  `password_reset_tokens` with the schema described below.
- **REQ-TOK-02** The table MUST have a unique constraint on `token_hash`.
- **REQ-TOK-03** A foreign key from `principal_id` to
  `user_identities.principal_id` MUST exist with `ON DELETE CASCADE`. The
  column MUST use `VARCHAR(64)`, matching the existing identity schema's
  `principals.id` and `user_identities.principal_id` types.
- **REQ-TOK-04** A partial index on `(principal_id, expires_at)` filtered
  to `used_at IS NULL` MUST exist to support the active-token lookup.
- **REQ-TOK-05** The table MUST NOT store the raw token, the email
  address, the IP address, or the user agent in plain text. Optional
  `request_ip_hash` and `user_agent_hash` columns MAY store SHA-256
  digests of those values for forensic correlation.

Schema contract:

```sql
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    principal_id VARCHAR(64) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    request_ip_hash VARCHAR(128) NULL,
    user_agent_hash VARCHAR(128) NULL,
    CONSTRAINT fk_password_reset_principal
        FOREIGN KEY (principal_id)
        REFERENCES user_identities(principal_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_principal_active
    ON password_reset_tokens (principal_id, expires_at)
    WHERE used_at IS NULL;
```

### REQ-NOT — Notifications

**Delivery: REQ-NOT-01..05 and primary dispatch are PR 1 core backend;
REQ-NOT-06, retries, and terminal-failure recording are PR 3 hardening.**

- **REQ-NOT-01** The `PasswordResetRequested` event MUST carry:
  `principalId: String`, `email: String`, `rawResetToken: String`.
- **REQ-NOT-02** The notification consumer MUST render the email with:
    - Reason header (password reset request).
    - A primary CTA "Reset password" linking to
      `<appBaseUrl>/reset-password?token=<rawToken>`.
    - A statement that the link expires in 30 minutes.
    - A statement that the request can be ignored if not initiated by the
      recipient.
- **REQ-NOT-03** The email MUST NOT contain a temporary password or the
  current password.
- **REQ-NOT-04** The reset URL MUST use the configured `publicAppUrl`
  property (see `EmailProperties`).
- **REQ-NOT-05** The email template MUST escape user-controlled values
  to prevent HTML injection.
- **REQ-NOT-06** [PR 3] Notification telemetry MUST include the notification
  type and delivery status, but MUST NOT include the raw token, the
  recipient's password, or the reset URL query string.
- **REQ-NOT-07** [PR 3] Temporary delivery failures MUST be retried according
  to the notification policy without exposing sensitive values in retry records.
- **REQ-NOT-08** [PR 3] Exhausted delivery retries MUST produce a terminal
  failure record and operational signal without storing the raw token or reset URL.

### REQ-UI — Frontend Flows

**Delivery: PR 2 frontend. All REQ-UI requirements and scenarios are classified `@pr-2`.**

- **REQ-UI-01** A "Forgot password?" link MUST be present and keyboard-reachable on the login page
  and MUST navigate to `/forgot-password`.
- **REQ-UI-02** `/forgot-password` MUST remain guest-only under the same guard rule as `/login` and
  `/register`.
- **REQ-UI-03** `ForgotPasswordView` MUST render an RFC 5322 email field and submit control,
  validate before API submission, expose pending state, and prevent duplicate submissions.
- **REQ-UI-04** A successful forgot-password request MUST show the same localized generic
  confirmation regardless of account existence: "If an account exists for this email, you'll receive
  a password reset link shortly."
- **REQ-UI-05** A forgot-password `429` response MUST show a localized rate-limit error; a disabled
  or unknown failure MUST show a localized unavailable or generic error without account disclosure.
- **REQ-UI-06** `/reset-password?token=...` MUST be accessible to authenticated and unauthenticated
  visitors. The recovery token is the authorization capability; an existing session MUST NOT
  redirect away from or block the reset form.
- **REQ-UI-07** `ResetPasswordView` MUST read the `token` query parameter. A missing or blank token
  MUST show an invalid-link state linking to `/forgot-password` and MUST NOT render the form.
- **REQ-UI-08** The reset form MUST provide new-password and confirmation fields and enforce
  required, 12..128 characters, and equality before submission.
- **REQ-UI-09** Invalid client input MUST NOT be submitted and MUST show localized policy or
  mismatch feedback. Pending submission MUST disable repeat submission.
- **REQ-UI-10** Invalid, expired, and used token responses MUST produce one identical localized
  generic invalid-link state linking to `/forgot-password`; backend detail MUST NOT distinguish
  token state.
- **REQ-UI-11** After backend `204`, the frontend MUST show that the password changed and MUST
  direct the visitor to `/login` to authenticate again. It MUST NOT preserve, restore, or create an
  authenticated frontend session, because the backend revokes refresh sessions and issues no
  replacement session.
- **REQ-UI-12** All recovery strings MUST have EN and ES parity, wrap without truncation, and remain
  usable at supported responsive widths without horizontal overflow.
- **REQ-UI-13** Both forms MUST use native submission, programmatic labels, suitable autocomplete
  values, associated validation errors, announced async/error states, visible keyboard focus, and
  practical touch targets.
- **REQ-UI-14** API functions MUST reside in
  `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` and preserve empty `202`/`204`
  responses and RFC 9457 error status/code:
  ```ts
  requestPasswordReset(email: string): Promise<void>
  resetPassword(payload: { token: string; newPassword: string }): Promise<void>
  ```
- **REQ-UI-15** The raw token and new password MUST NOT enter localStorage, sessionStorage,
  analytics, logs, rendered error text, or test diagnostics.
- **REQ-UI-16** Recovery routes MUST render outside the authenticated application shell.

#### PR 2 Scenarios

```gherkin
@pr-2 @route-guard
Scenario: Guest-only forgot-password route redirects an authenticated visitor
  Given the visitor has an authenticated session
  When the visitor opens "/forgot-password"
  Then the guest-only guard should redirect the visitor away from the recovery request form

@pr-2 @route-guard @happy-path
Scenario: Authenticated visitor opens a valid reset link
  Given the visitor has an authenticated session
  And the URL contains a valid password recovery token
  When the visitor opens "/reset-password?token=valid-token"
  Then the visitor should reach the reset password form
  And the existing session should not block or redirect the visitor

@pr-2 @happy-path
Scenario: Successful reset directs the visitor to login again
  Given an authenticated or unauthenticated visitor submits a valid token and matching valid passwords
  When the backend accepts the reset with status 204 and revokes refresh sessions
  Then the frontend should show the password-changed success state
  And the visitor should be directed to "/login" without automatic authentication

@pr-2 @token-error
Scenario: Reset link is missing, invalid, expired, or used
  Given the reset link has no usable token or the backend returns a token error
  When the reset view handles the link or response
  Then one localized generic invalid-link state should be shown
  And no token-state distinction or password submission should be exposed

@pr-2 @rate-limit @disabled
Scenario: Recovery API is throttled or unavailable
  Given the backend returns 429, 503, or an unknown failure
  When either recovery view handles the response
  Then the view should show the corresponding localized safe error
  And pending controls should prevent duplicate submission

@pr-2 @i18n @accessibility @responsive
Scenario Outline: Recovery views remain usable across supported presentation contexts
  Given the recovery flow uses locale "<locale>" and viewport "<viewport>"
  When the visitor completes the flow using only the keyboard
  Then labels, focus, announcements, touch targets, wrapping, and overflow should satisfy REQ-UI-12 and REQ-UI-13
  Examples:
    | locale | viewport |
    | en     | desktop  |
    | es     | mobile   |

@pr-2 @privacy
Scenario: Recovery secrets are not retained or observed
  Given the visitor opens a reset URL and submits a new password
  When the frontend processes success or failure
  Then the raw token and password should not enter storage, analytics, logs, errors, or test diagnostics
```

### REQ-HARD — Operational Hardening

**Delivery: PR 3 hardening.**

- **REQ-HARD-01** Successful resets MUST emit `PASSWORD_RESET_COMPLETED` with
  principal identifier and occurrence time, and MUST exclude tokens and passwords.
- **REQ-HARD-02** Suspicious repeated failures MAY emit a security event containing
  hashed network identifiers and counts, and MUST exclude tokens and passwords.
- **REQ-HARD-03** A scheduled, idempotent cleanup MUST delete expired tokens older
  than the retention threshold while preserving active tokens and records inside the
  audit-retention window.
- **REQ-HARD-04** Operational telemetry MUST expose safe delivery and reset outcomes
  without email, raw IP, token, password, or reset-URL query values.

## Behavior Coverage

Scenario delivery is determined by its inherited feature tag. Every backend feature
is `@pr-1` by default; an explicit scenario-level `@pr-3` tag overrides that default.
Frontend E2E scenarios are `@pr-2`. Therefore every listed scenario has exactly one
acceptance owner even when its product requirement remains globally mandatory.

| Artifact                                                 | Default owner | Override                                             |
|----------------------------------------------------------|---------------|------------------------------------------------------|
| `identity-request-password-reset.feature`                | PR 1          | None                                                 |
| `identity-reset-password.feature`                        | PR 1          | None                                                 |
| `identity-password-reset-persistence.feature`            | PR 1          | `@pr-3` retention cleanup                            |
| `identity-password-reset-notifications.feature`          | PR 1          | `@pr-3` retry, terminal failure, telemetry           |
| `identity-password-reset-security.feature`               | PR 1          | `@pr-3` completed-reset and suspicious-attempt audit |
| `apps/web/app/e2e/specs/password-reset-frontend.spec.ts` | PR 2          | None                                                 |

---

## Feature: Request Password Reset — `identity-request-password-reset.feature`

```gherkin
@identity @password-recovery @pr-1
Feature: Request password reset
  As a user who cannot remember the account password
  I want to request a secure password reset link
  So that I can regain access to my account

  Background:
    Given password recovery is enabled
    And the password reset token lifetime is 30 minutes
    And authentication rate limiting is enabled

  @happy-path
  Scenario: Request a password reset for an existing local account
    Given a local account exists with email "user@example.com"
    And the account has a password credential
    When the visitor requests a password reset for "user@example.com"
    Then the response status should be 202
    And the response should not indicate whether the account exists
    And a password reset token should be created for the account
    And only the token hash should be persisted
    And a password reset notification should be scheduled
    And the notification should be sent to "user@example.com"

  @security @enumeration
  Scenario: Request a password reset for an unknown email
    Given no account exists with email "unknown@example.com"
    When the visitor requests a password reset for "unknown@example.com"
    Then the response status should be 202
    And the response should be indistinguishable from the response for an existing account
    And no password reset token should be created
    And no password reset notification should be scheduled

  @security @enumeration
  Scenario: Existing and unknown accounts return the same public response
    Given a local account exists with email "existing@example.com"
    And no account exists with email "missing@example.com"
    When the visitor requests a password reset for "existing@example.com"
    And the visitor requests a password reset for "missing@example.com"
    Then both responses should have status 202
    And both responses should have the same response body
    And neither response should expose account existence

  @oauth
  Scenario: Request a password reset for an OAuth-only account
    Given an account exists with email "oauth@example.com"
    And the account has no local password credential
    And the account authenticates only through an external provider
    When the visitor requests a password reset for "oauth@example.com"
    Then the response status should be 202
    And the response should not expose the authentication provider
    And no password reset token should be created
    And no password reset notification should be scheduled

  @normalization
  Scenario: Normalize the email before account lookup
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "  USER@EXAMPLE.COM  "
    Then the response status should be 202
    And the account should be resolved using "user@example.com"
    And a password reset token should be created for the account

  @validation
  Scenario Outline: Reject an invalid password reset request
    When the visitor requests a password reset using email "<email>"
    Then the response status should be 400
    And the response should use RFC 9457 Problem Details
    And the response should contain validation code "<code>"
    And no password reset token should be created
    And no password reset notification should be scheduled

    Examples:
      | email                | code                  |
      |                      | VALIDATION_ERROR      |
      | invalid-email        | VALIDATION_ERROR      |
      | user@                | VALIDATION_ERROR      |
      | @example.com         | VALIDATION_ERROR      |
      | user example.com     | VALIDATION_ERROR      |

  @validation
  Scenario: Reject a request without a JSON body
    When the visitor sends a password reset request without a request body
    Then the response status should be 400
    And the response should use RFC 9457 Problem Details
    And no password reset token should be created

  @validation
  Scenario: Reject a request with malformed JSON
    When the visitor sends malformed JSON to the password reset request endpoint
    Then the response status should be 400
    And the response should use RFC 9457 Problem Details
    And no password reset token should be created

  @token-lifecycle
  Scenario: Invalidate an existing active token when a new reset is requested
    Given a local account exists with email "user@example.com"
    And the account has an active password reset token
    When the visitor requests another password reset for "user@example.com"
    Then the previous password reset token should be invalidated
    And a new password reset token should be created
    And only the new token should remain usable

  @token-lifecycle
  Scenario: Multiple sequential requests leave only the latest token active
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    And the visitor requests a password reset again for "user@example.com"
    And the visitor requests a password reset a third time for "user@example.com"
    Then three requests should have been accepted
    And only the latest password reset token should be active
    And the two previous password reset tokens should be unusable

  @concurrency
  Scenario: Concurrent password reset requests result in one active token
    Given a local account exists with email "user@example.com"
    When two password reset requests for "user@example.com" are processed concurrently
    Then both public responses should have status 202
    And the account should have exactly one active password reset token
    And all superseded tokens should be unusable

  @transaction
  Scenario: [PR 1] Do not publish a notification when token persistence fails
    Given a local account exists with email "user@example.com"
    And password reset token persistence will fail
    When the visitor requests a password reset for "user@example.com"
    Then no password reset notification should be published
    And no partial password reset token record should remain
    And the response should not expose sensitive data

  @notification
  Scenario: Password reset email contains the expected reset link
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the password reset email should contain a link to the reset password page
    And the link should include the raw reset token
    And the email should state that the link expires in 30 minutes
    And the email should state that the request can be ignored
    And the email should not contain the current password
    And the email should not contain a temporary password

  @notification @i18n
  Scenario Outline: Send the password reset email using the supported locale
    Given a local account exists with email "user@example.com"
    And the preferred locale is "<locale>"
    When the visitor requests a password reset for "user@example.com"
    Then the password reset email should be rendered in "<locale>"

    Examples:
      | locale |
      | en     |
      | es     |

  @security
  Scenario: Never persist the raw password reset token
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the database should contain only the token hash
    And the raw token should not be present in persisted data
    And the raw token should not be present in audit records
    And the raw token should not be present in application logs
    And the raw token should not be present in metrics

  @security
  Scenario: Do not expose account data in the password reset request response
    Given a local account exists with email "user@example.com"
    When the visitor requests a password reset for "user@example.com"
    Then the response should not contain the principal identifier
    And the response should not contain the normalized email
    And the response should not contain the authentication provider
    And the response should not contain token metadata

  @rate-limit
  Scenario: Rate limit repeated requests from the same IP address
    Given the IP password reset limit is 5 requests per 15 minutes
    When the same IP address submits 6 password reset requests within 15 minutes
    Then the first 5 requests should be accepted
    And the sixth response status should be 429
    And the sixth response should use RFC 9457 Problem Details
    And the sixth response should contain code "AUTH_RATE_LIMIT_EXCEEDED"

  @rate-limit
  Scenario: Rate limit repeated requests for the same normalized email
    Given the email password reset limit is 3 requests per 30 minutes
    When password reset is requested 4 times for variants of "user@example.com" within 30 minutes
    Then the first 3 requests should be accepted
    And the fourth response status should be 429
    And all email variants should count toward the same normalized email bucket

  @rate-limit @enumeration
  Scenario: Apply email rate limiting equally to existing and unknown accounts
    Given a local account exists with email "existing@example.com"
    And no account exists with email "missing@example.com"
    When the email request limit is exceeded for both addresses
    Then both addresses should receive equivalent rate limit responses
    And the rate limit response should not reveal account existence

  @rate-limit
  Scenario: Allow a new request after the rate limit window expires
    Given the password reset request limit has been exceeded
    And the rate limit window has expired
    When the visitor requests another password reset
    Then the response status should be 202

  @disabled
  Scenario: Password recovery is disabled
    Given password recovery is disabled
    When the visitor requests a password reset for "user@example.com"
    Then the response status should be 503
    And the response should use RFC 9457 Problem Details
    And no token should be created
    And no notification should be scheduled
```

---

## Feature: Reset Password — `identity-reset-password.feature`

```gherkin
@identity @password-recovery @pr-1
Feature: Reset password using a recovery token
  As a user who has received a password reset link
  I want to choose a new password
  So that I can regain secure access to my account

  Background:
    Given password recovery is enabled
    And the password reset token lifetime is 30 minutes
    And password hashes are generated using the configured password hasher

  @happy-path
  Scenario: Reset the password using a valid token
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password using the token and a valid new password
    Then the response status should be 204
    And the account password hash should be updated
    And the password reset token should be marked as used
    And all refresh sessions for the account should be revoked
    And no new authenticated session should be created
    And no refresh cookie should be issued

  @happy-path
  Scenario: User can log in with the new password after reset
    Given a local account exists with email "user@example.com"
    And a valid unused password reset token exists for the account
    When the user resets the password to "NewSecurePassword123!"
    Then the password reset should succeed
    When the user logs in with "user@example.com" and "NewSecurePassword123!"
    Then authentication should succeed

  @security
  Scenario: User cannot log in with the old password after reset
    Given a local account exists with email "user@example.com"
    And the current password is "OldSecurePassword123!"
    And a valid unused password reset token exists for the account
    When the user resets the password to "NewSecurePassword123!"
    And the user attempts to log in with "OldSecurePassword123!"
    Then authentication should fail with invalid credentials

  @security
  Scenario: Resetting the password revokes all active refresh sessions
    Given a local account has active refresh sessions on multiple devices
    And a valid unused password reset token exists for the account
    When the user resets the password using the token
    Then every refresh session for the account should be revoked
    And each previous refresh token should be rejected
    And existing devices should require a new login

  @security
  Scenario: Existing access tokens are not renewed after password reset
    Given a local account has an active access token and refresh token
    And a valid unused password reset token exists for the account
    When the user resets the password using the token
    Then the existing refresh token should be revoked
    When the client attempts to refresh the session
    Then the refresh request should be rejected with status 401

  @token
  Scenario: Reject an unknown password reset token
    Given no password reset token exists for "unknown-token"
    When the user submits "unknown-token" with a valid new password
    Then the response status should be 400
    And the response should use RFC 9457 Problem Details
    And the public error code should be "INVALID_PASSWORD_RESET_TOKEN"
    And no password should be changed
    And no session should be revoked

  @token
  Scenario: Reject an expired password reset token
    Given a password reset token expired one minute ago
    When the user submits the expired token with a valid new password
    Then the response status should be 400
    And the public response should state that the link is invalid or expired
    And no password should be changed
    And the token should remain unusable

  @token
  Scenario: Reject an already used password reset token
    Given a password reset token has already been used
    When the user submits the used token with a valid new password
    Then the response status should be 400
    And the public response should state that the link is invalid or expired
    And no password should be changed

  @token
  Scenario: Reject a token invalidated by a newer request
    Given a local account requested two password reset links
    And the first token was invalidated when the second token was created
    When the user submits the first token with a valid new password
    Then the response status should be 400
    And no password should be changed
    When the user submits the second token with a valid new password
    Then the response status should be 204

  @token
  Scenario: Accept a token immediately before expiration
    Given a password reset token expires at "2026-07-27T12:30:00Z"
    And the current time is "2026-07-27T12:29:59Z"
    When the user submits the token with a valid new password
    Then the response status should be 204

  @token
  Scenario: Reject a token exactly at its expiration time
    Given a password reset token expires at "2026-07-27T12:30:00Z"
    And the current time is "2026-07-27T12:30:00Z"
    When the user submits the token with a valid new password
    Then the response status should be 400
    And no password should be changed

  @token
  Scenario: Reject a token with modified characters
    Given a valid unused password reset token exists
    And one character of the token has been changed
    When the user submits the modified token with a valid new password
    Then the response status should be 400
    And no password should be changed

  @validation
  Scenario Outline: Reject an invalid new password
    Given a valid unused password reset token exists
    When the user resets the password using "<password>"
    Then the response status should be 400
    And the response should use RFC 9457 Problem Details
    And the response should contain code "INVALID_PASSWORD"
    And the account password should remain unchanged
    And the reset token should remain unused

    Examples:
      | password |
      |          |
      | short    |

  @validation
  Scenario: Accept a password with the maximum supported length
    Given the maximum password length is 128 characters
    And a valid unused password reset token exists
    When the user resets the password using a valid 128-character password
    Then the response status should be 204

  @validation
  Scenario: Reject a password exceeding the maximum supported length
    Given the maximum password length is 128 characters
    And a valid unused password reset token exists
    When the user resets the password using a 129-character password
    Then the response status should be 400
    And the password should remain unchanged
    And the reset token should remain unused

  @validation
  Scenario: Reject a reset request without a token
    When the user submits a reset request with a valid new password but no token
    Then the response status should be 400
    And no password should be changed

  @validation
  Scenario: Reject a reset request without a new password
    Given a valid unused password reset token exists
    When the user submits the reset token without a new password
    Then the response status should be 400
    And no password should be changed
    And the reset token should remain unused

  @validation
  Scenario: Reject a reset request with malformed JSON
    Given a valid unused password reset token exists
    When the user submits malformed JSON to the reset password endpoint
    Then the response status should be 400
    And no password should be changed
    And the reset token should remain unused

  @security
  Scenario: Store the new password only as a secure hash
    Given a valid unused password reset token exists
    When the user resets the password to "NewSecurePassword123!"
    Then the plaintext password should not be persisted
    And the stored credential should contain a password hash
    And the configured password hasher should verify the new password

  @security
  Scenario: Do not include sensitive values in the successful response
    Given a valid unused password reset token exists
    When the user resets the password using the token
    Then the response status should be 204
    And the response should not contain the password
    And the response should not contain the password hash
    And the response should not contain the reset token
    And the response should not contain an access token
    And the response should not contain a refresh token

  @security
  Scenario: Do not log the raw reset token or new password
    Given a valid unused password reset token exists
    When the user resets the password using the token
    Then application logs should not contain the raw reset token
    And application logs should not contain the new password
    And audit records should not contain the raw reset token
    And metrics should not contain the raw reset token

  @transaction
  Scenario: Roll back token consumption when password update fails
    Given a valid unused password reset token exists
    And the password credential update will fail
    When the user attempts to reset the password
    Then the reset operation should fail
    And the original password hash should remain unchanged
    And the reset token should remain unused
    And existing refresh sessions should remain active

  @transaction
  Scenario: Roll back password update when token consumption fails
    Given a valid unused password reset token exists
    And token consumption will fail
    When the user attempts to reset the password
    Then the reset operation should fail
    And the original password hash should remain unchanged
    And existing refresh sessions should remain active

  @transaction
  Scenario: Roll back password reset when session revocation fails
    Given a valid unused password reset token exists
    And refresh session revocation will fail
    When the user attempts to reset the password
    Then the reset operation should fail
    And the original password hash should remain unchanged
    And the reset token should remain unused

  @concurrency
  Scenario: Only one concurrent reset request can consume a token
    Given a valid unused password reset token exists
    When two password reset requests using the same token are processed concurrently
    Then exactly one request should succeed with status 204
    And exactly one request should fail with status 400
    And the password should match only the successful request
    And the token should be marked as used exactly once

  @concurrency
  Scenario: Concurrent reset attempts with different passwords are atomic
    Given a valid unused password reset token exists
    When one request attempts to set password "Password-A-123!"
    And another concurrent request attempts to set password "Password-B-123!"
    Then exactly one password should be persisted
    And the other request should fail
    And the token should be consumed once

  @rate-limit
  Scenario: Rate limit repeated invalid reset attempts from the same IP
    Given the reset attempt limit is 10 requests per 15 minutes
    When the same IP submits 11 invalid reset tokens within 15 minutes
    Then the first 10 requests should return token validation responses
    And the eleventh response status should be 429
    And the response should contain code "AUTH_RATE_LIMIT_EXCEEDED"

  @rate-limit
  Scenario: Allow reset attempts after the rate limit window expires
    Given the reset attempt rate limit has been exceeded
    And the rate limit window has expired
    When the user submits a valid unused reset token
    Then the reset request should be processed normally

  @disabled
  Scenario: Password recovery is disabled during reset
    Given password recovery is disabled
    And a previously issued reset token exists
    When the user submits the reset token
    Then the response status should be 503
    And the password should remain unchanged
    And the token should remain unused
```

---

## Feature: Persist Password Reset Tokens — `identity-password-reset-persistence.feature`

```gherkin
@backend @persistence @password-recovery @pr-1
Feature: Persist password reset tokens securely
  As the authentication subsystem
  I want password reset tokens to be persisted safely
  So that token lifecycle rules remain enforceable

  @schema
  Scenario: Persist a password reset token
    Given a principal with identifier "user-123" exists
    When a password reset token is persisted
    Then the token record should reference principal "user-123"
    And the token hash should be persisted
    And the request timestamp should be persisted
    And the expiration timestamp should be persisted
    And the used timestamp should be null

  @schema
  Scenario: Enforce uniqueness of token hashes
    Given a password reset token hash already exists
    When another token is persisted with the same hash
    Then persistence should fail with a uniqueness violation

  @schema
  Scenario: Delete reset tokens when the principal is deleted
    Given a principal has password reset tokens
    When the principal is permanently deleted
    Then all password reset tokens for the principal should be deleted

  @lookup
  Scenario: Find a token by its hash
    Given a password reset token exists with a known hash
    When the repository searches using that hash
    Then the matching token should be returned

  @lookup
  Scenario: Do not find a token using the raw token
    Given a password reset token exists
    When the repository searches using the raw token instead of its hash
    Then no token should be returned

  @lifecycle
  Scenario: Invalidate all active tokens for a principal
    Given a principal has multiple active password reset tokens
    And the principal has one already used token
    When all active tokens are invalidated
    Then every active token should become unusable
    And the already used token should remain unchanged

  @atomicity
  Scenario: Consume an unused unexpired token atomically
    Given an unused password reset token has not expired
    When the repository consumes the token
    Then exactly one row should be updated
    And the used timestamp should be set

  @atomicity
  Scenario: Do not consume an expired token
    Given an unused password reset token has expired
    When the repository attempts to consume the token
    Then zero rows should be updated
    And the used timestamp should remain null

  @atomicity
  Scenario: Do not consume an already used token
    Given a password reset token has already been used
    When the repository attempts to consume the token
    Then zero rows should be updated
    And the original used timestamp should remain unchanged

  @concurrency
  Scenario: Database locking prevents double consumption
    Given an unused unexpired password reset token exists
    When two transactions attempt to consume the token concurrently
    Then exactly one transaction should update the token
    And exactly one transaction should report no token consumed
    And the token should have one final used timestamp

  @cleanup @pr-3
  Scenario: [PR 3] Remove expired password reset tokens
    Given expired and active password reset tokens exist
    When the expired-token cleanup job runs
    Then expired tokens older than the retention threshold should be deleted
    And active unexpired tokens should remain
    And recently used tokens within the audit retention period should remain
```

---

## Feature: Deliver Password Reset Notifications — `identity-password-reset-notifications.feature`

```gherkin
@notifications @password-recovery @pr-1
Feature: Deliver password reset notifications
  As the platform
  I want password reset emails to be dispatched securely
  So that account owners can complete password recovery

  @dispatch
  Scenario: Dispatch a password reset notification after token creation
    Given a password reset token has been persisted
    When a PasswordResetRequested event is published
    Then one password reset email should be queued
    And the recipient should be the account email
    And the email should contain the raw token reset URL

  @dispatch
  Scenario: Do not dispatch before the transaction commits
    Given password reset token creation occurs inside a transaction
    When the transaction has not yet committed
    Then no password reset email should be dispatched
    When the transaction commits successfully
    Then the password reset email may be dispatched

  @dispatch
  Scenario: Do not dispatch after a transaction rollback
    Given password reset token creation occurs inside a transaction
    When the transaction rolls back
    Then no password reset email should be dispatched

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
    And operational metrics should record the failure
    And the raw token should not be included in the failure record

  @template
  Scenario: Render the reset URL using the configured application base URL
    Given the application base URL is "https://app.profiletailors.com"
    And the raw token is "secure-token"
    When the password reset email is rendered
    Then the reset link should be:
      """
      https://app.profiletailors.com/reset-password?token=secure-token
      """

  @template
  Scenario: Escape user-controlled values in the email template
    Given the recipient account contains user-controlled profile fields
    When the password reset email is rendered
    Then user-controlled values should be safely escaped
    And no HTML injection should be possible

  @privacy @pr-3
  Scenario: [PR 3] Notification telemetry excludes sensitive values
    Given a password reset notification is dispatched
    Then telemetry may include the notification type
    And telemetry may include delivery status
    But telemetry should not include the raw token
    And telemetry should not include the new password
    And telemetry should not include the reset URL query string
```

---

## Feature: Password Recovery Security Controls — `identity-password-reset-security.feature`

```gherkin
@security @password-recovery @pr-1
Feature: Password recovery security controls
  As the platform operator
  I want password recovery to resist common attacks
  So that account ownership cannot be compromised

  @enumeration
  Scenario: Prevent account enumeration through response status
    Given an existing local account
    And an unknown email address
    When password reset is requested for both
    Then both requests should return status 202

  @enumeration
  Scenario: Prevent account enumeration through response body
    Given an existing local account
    And an unknown email address
    When password reset is requested for both
    Then both responses should contain equivalent public content

  @enumeration
  Scenario: Prevent account enumeration through provider disclosure
    Given an OAuth-only account exists
    When password reset is requested for its email
    Then the response should not reveal that the account uses OAuth

  @token-strength
  Scenario: Generate a cryptographically secure token
    When a password reset token is generated
    Then the token should contain at least 256 bits of entropy
    And the token should be generated using a cryptographically secure random generator
    And the token should be URL-safe

  @token-strength
  Scenario: Generated tokens are unique
    When 10000 password reset tokens are generated
    Then all generated raw tokens should be unique
    And all generated token hashes should be unique

  @token-storage
  Scenario: A database leak does not reveal usable reset tokens
    Given the password reset token table is exposed
    Then only hashed tokens should be available
    And the hashes should not be directly usable as reset tokens

  @csrf
  Scenario: Reset password endpoint does not rely on ambient authentication
    Given the reset request contains no session cookie
    And the reset request contains a valid reset token
    When the reset request is submitted
    Then the request should be evaluated using the reset token
    And no authenticated browser session should be required

  @cors
  Scenario: Reject reset requests from a disallowed origin
    Given the request origin is not allowed by platform policy
    When a password reset request is submitted
    Then the request should be rejected according to origin policy
    And no password should be changed

  @replay
  Scenario: Prevent replay after a successful reset
    Given a reset token has been used successfully
    When an attacker replays the same token
    Then the request should be rejected
    And the password should remain unchanged

  @brute-force
  Scenario: Limit brute-force token attempts
    Given an attacker submits many random reset tokens
    When the configured attempt threshold is exceeded
    Then subsequent requests should be rate limited
    And no information about valid token prefixes should be exposed

  @timing
  Scenario: Unknown account requests avoid obvious timing disclosure
    Given email delivery is asynchronous
    When password reset is requested for an existing account
    And password reset is requested for an unknown account
    Then neither HTTP response should wait for email delivery
    And both accepted paths should complete account-dependent work before a bounded minimum-duration equalization boundary
    And response processing should avoid an obvious account-dependent delay

  @audit @pr-3
  Scenario: [PR 3] Audit a successful password change
    Given a valid password reset succeeds
    Then an audit event should record the principal identifier
    And the event should record the occurrence timestamp
    And the event should record the action "PASSWORD_RESET_COMPLETED"
    And the event should not contain the raw token
    And the event should not contain the password
    And the event should not contain the password hash

  @audit @pr-3
  Scenario: [PR 3] Audit repeated suspicious reset failures without storing secrets
    Given repeated invalid reset attempts are detected
    Then a security event may record a hashed network identifier
    And the event may record attempt counts
    And the event should not contain raw tokens
    And the event should not contain passwords
```