# Delta for App Shell

## ADDED Requirements

### Requirement: Global Unverified Email Guidance

The authenticated app shell MUST surface a persistent verification warning for users whose authoritative profile status is not `VERIFIED`.

The warning MUST be visible across authenticated routes until verification completes, MUST explain that verification is required for restricted actions, and MUST provide visible resend and verify-account entry points.

#### Scenario: Unverified user sees global banner

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the app shell renders
- THEN a global warning banner MUST be shown
- AND it MUST state that publish, social connect, and media upload require email verification

#### Scenario: Verified user does not see banner

- GIVEN an authenticated user with `emailStatus = VERIFIED`
- WHEN the app shell renders
- THEN the global warning banner MUST NOT be shown

#### Scenario: Resend action is visible from the banner

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user views the global warning banner
- THEN a resend verification action MUST be visible
- AND the user MUST also have a visible path to the verification screen or instructions
