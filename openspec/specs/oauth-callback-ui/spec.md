# OAuth Callback UI Specification

## Purpose

Define the SPA route and view that handles the LinkedIn OAuth redirect, validates state, calls the
completion endpoint, refreshes channels, and navigates the user back with appropriate feedback.

## Requirements

### Requirement: LinkedIn OAuth Callback Route

The SPA MUST register a route at `/integrations/linkedin/callback` that receives the LinkedIn OAuth
redirect with `code` and `state` query parameters and processes the connection completion flow.

The callback view MUST validate that `code` and `state` are present before calling the backend. If
either is missing, the view MUST display an appropriate error message and offer a retry action.

#### Scenario: Successful LinkedIn connection callback

- GIVEN the SPA loads the callback route with valid `code` and `state` query parameters
- WHEN the callback view processes the parameters
- THEN it MUST call `POST /api/publishing/linkedin/connections/complete` with `authorizationCode`
  and `redirectUri`
- AND on success, it MUST refresh the channel list from the backend
- AND navigate the user to the channels or scheduler view with success feedback

#### Scenario: OAuth denied by user

- GIVEN the LinkedIn OAuth redirect includes an `error` parameter instead of `code`
- WHEN the callback view processes the parameters
- THEN it MUST display a user-friendly error indicating the connection was denied
- AND it MUST NOT call the completion endpoint
- AND it MUST navigate the user back with error feedback

#### Scenario: Missing code or state parameters

- GIVEN the callback route is loaded without `code` or `state` query parameters
- WHEN the callback view processes the parameters
- THEN it MUST display a validation error
- AND it MUST NOT call the completion endpoint

### Requirement: State Validation on Callback

The callback view MUST pass the received `state` value to the backend completion endpoint. The
backend is responsible for cryptographic validation. The frontend MUST NOT discard or modify the
`state` value.

#### Scenario: Frontend preserves state value for backend validation

- GIVEN the callback route receives a `state` parameter
- WHEN the callback view calls the completion endpoint
- THEN the `state` value MUST be forwarded unchanged to the backend
- AND the frontend MUST NOT perform its own cryptographic state validation

### Requirement: Channel Refresh After Successful Completion

After a successful connection completion, the publishing store MUST refresh the channel list from
the backend before navigating away from the callback view.

#### Scenario: Channel list is refreshed after connection

- GIVEN the completion endpoint returns success
- WHEN the callback view processes the result
- THEN the publishing store MUST call `fetchChannels()` to load the updated channel list from the
  backend
- AND the UI MUST reflect the new connected LinkedIn profile before navigating

### Requirement: Error Feedback on Completion Failure

If the completion endpoint returns an error (e.g., invalid state, OAuth exchange failure), the
callback view MUST display the error and offer the user a way to retry.

#### Scenario: Backend completion failure shows error

- GIVEN the completion endpoint returns 400 or 500
- WHEN the callback view processes the error response
- THEN it MUST display a user-friendly error message
- AND offer a "Try Again" action that navigates to the initiation flow
