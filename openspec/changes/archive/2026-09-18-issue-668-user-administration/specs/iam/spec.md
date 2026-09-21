# Delta for IAM

## MODIFIED Requirements

### Requirement: User account lifecycle and credential enforcement

The Identity context MUST model a user account state of `ACTIVE` or `DISABLED`, defaulting new users
to `ACTIVE`. Administrative disable MUST persist `DISABLED` and revoke all active refresh sessions
before reporting success. Login and refresh MUST reject a disabled user; refresh MUST NOT rotate or
issue a replacement session. Existing bearer tokens MAY expire normally and MUST NOT require
per-request identity-state lookup. (Previously: users had no administrative account state; login and
refresh issued credentials without checking disabled state.)

#### Scenario: Disabled user cannot log in

- GIVEN a user is `DISABLED` and submits otherwise valid credentials
- WHEN local login is attempted
- THEN authentication is rejected
- AND no access token or refresh session is issued

#### Scenario: Disabled user cannot refresh

- GIVEN a user is `DISABLED` with a refresh token that was active before disable
- WHEN refresh is attempted
- THEN refresh is rejected
- AND no replacement access token or refresh session is issued

#### Scenario: Disable revokes active sessions

- GIVEN an `ACTIVE` user has active refresh sessions
- WHEN administrative disable completes
- THEN the user is `DISABLED`
- AND every active refresh session for that principal is `REVOKED`

#### Scenario: Enable permits new authentication

- GIVEN a user is `DISABLED`
- WHEN an authorized administrator enables the user
- AND the user submits valid credentials
- THEN login MAY issue a new session
- AND previously revoked refresh sessions remain unusable

#### Scenario: Disable cannot report partial success

- GIVEN account-state persistence or refresh-session revocation cannot be confirmed
- WHEN disable is requested
- THEN the command reports failure
- AND it MUST NOT claim that the user is safely disabled
