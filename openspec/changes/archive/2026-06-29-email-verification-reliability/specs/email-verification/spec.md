# Delta for Email Verification

## ADDED Requirements

### Requirement: Verification Email Dispatch Reliability

The system MUST reliably dispatch verification emails for both registration and resend flows.

Registration and resend flows MUST trigger the same verification-email delivery path, and a successful API response MUST leave the account in a state where a verification email can be delivered without additional manual operator action.

#### Scenario: Registration triggers deliverable verification email

- GIVEN a new user completes registration successfully
- WHEN registration returns success
- THEN the system MUST create a valid verification token
- AND the system MUST trigger verification email dispatch for that token

#### Scenario: Resend replaces the active verification email

- GIVEN an existing unverified user requests resend
- WHEN the resend request is accepted
- THEN the system MUST invalidate prior unused tokens
- AND the system MUST trigger dispatch for the newly issued token

### Requirement: Current User Profile Exposes Authoritative Email Status

The system MUST expose the current user's authoritative email verification status through `GET /api/auth/me`.

The profile response MUST include `emailStatus`, and clients MUST treat that field as the source of truth for verification-dependent UX and capability gating.

#### Scenario: Unverified profile returns authoritative status

- GIVEN an authenticated user whose email is not verified
- WHEN the client requests `GET /api/auth/me`
- THEN the response MUST include `emailStatus = UNVERIFIED`
- AND the response MUST remain successful if the session itself is otherwise valid

#### Scenario: Verified profile returns authoritative status

- GIVEN an authenticated user whose email is verified
- WHEN the client requests `GET /api/auth/me`
- THEN the response MUST include `emailStatus = VERIFIED`
- AND clients MUST be able to use that value without token heuristics
