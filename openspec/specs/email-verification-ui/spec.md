# Email Verification UI Specification

## Purpose

Define the SPA-owned email verification landing flow that consumes verification tokens, submits them
to the backend verification contract, and presents user-facing verification outcomes.

## Requirements

### Requirement: Verification Landing Route

The system MUST provide a frontend-owned verification landing route for email verification links.

#### Scenario: Route reads token from verification link

- GIVEN a user opens a verification email link
- WHEN the SPA route loads
- THEN the route MUST read the verification token from the link
- AND the user MUST remain on a frontend route during the flow

### Requirement: Verification Outcome Handling

The system MUST submit the token to `POST /api/auth/verify-email` and present clear outcome states.

#### Scenario: Successful verification shown

- GIVEN the route has a valid token
- WHEN backend verification succeeds
- THEN the UI MUST show a success state
- AND the UI MUST stop showing any loading state

#### Scenario: Invalid token shown

- GIVEN the route has a token that backend rejects as invalid or used
- WHEN verification completes with failure
- THEN the UI MUST show an invalid-link state
- AND the UI MUST NOT present the verification as successful

#### Scenario: Expired token shown

- GIVEN the route has a token that backend rejects as expired
- WHEN verification completes with failure
- THEN the UI MUST show an expired-link state
- AND the UI MUST distinguish it from success

### Requirement: Session Bootstrap After Verification

The system MUST reconcile verification success with authenticated session bootstrap.

#### Scenario: Existing authenticated session refreshed after verification

- GIVEN a signed-in user completes email verification successfully
- WHEN the SPA resolves the verification response
- THEN the client MUST refresh or re-read authoritative session/profile state
- AND verification-dependent UX MUST reflect `emailStatus = VERIFIED`

#### Scenario: Unauthenticated user remains in a coherent state after verification

- GIVEN a user is not signed in when verification succeeds
- WHEN the SPA shows the success state
- THEN the UI MUST NOT assume an authenticated session exists
- AND any next-step messaging MUST remain valid without a session
