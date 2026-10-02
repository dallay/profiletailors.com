# Delta for OAuth Callback UI

## MODIFIED Requirements

### Requirement: LinkedIn OAuth Callback Route

The SPA MUST register provider-aware callback handling for `/integrations/{provider}/callback` and retain `/integrations/linkedin/callback` as a compatibility alias. The view MUST accept the provider's OAuth redirect, validate required parameters before calling the backend, submit the authorization code and unchanged state to the matching provider completion endpoint, and offer retryable feedback without displaying secrets or raw provider errors.

(Previously: the callback route and completion call were LinkedIn-only.)

#### Scenario: Successful Threads connection callback

- GIVEN the SPA loads the Threads callback route with valid `code` and `state`
- WHEN the callback view processes the parameters
- THEN it MUST call `POST /api/publishing/threads/connections/complete`
- AND on success it MUST refresh channels and navigate with success feedback

#### Scenario: LinkedIn callback alias remains compatible

- GIVEN the SPA loads the existing LinkedIn callback route with valid parameters
- WHEN the callback view processes them
- THEN it MUST call the LinkedIn completion alias and preserve current success behavior

#### Scenario: OAuth denied by user

- GIVEN the provider redirect includes an OAuth error instead of a code
- WHEN the callback view processes the parameters
- THEN it MUST display a user-friendly denial message
- AND it MUST NOT call a completion endpoint

#### Scenario: Missing code or state parameters

- GIVEN the callback route is loaded without a required code or state
- WHEN the callback view processes the parameters
- THEN it MUST display a validation error and offer retry
- AND it MUST NOT call a completion endpoint

### Requirement: State Validation on Callback

The callback view MUST pass the received `state` value unchanged to the matching backend completion endpoint. The backend MUST perform cryptographic, provider, workspace, expiry, and replay validation; the frontend MUST NOT discard, modify, or attempt to replace that state.

(Previously: the frontend forwarded state for backend validation, but the requirement named only LinkedIn.)

#### Scenario: Frontend preserves provider state

- GIVEN a provider callback includes a state parameter
- WHEN the callback view calls completion
- THEN the exact state value MUST be forwarded
- AND the frontend MUST NOT perform cryptographic state validation

### Requirement: Channel Refresh After Successful Completion

After any successful provider connection completion, the publishing store MUST refresh the canonical channel list before navigating away from the callback view.

(Previously: successful LinkedIn completion refreshed channels.)

#### Scenario: Threads channel appears after completion

- GIVEN Threads completion returns success
- WHEN the callback view processes the result
- THEN the store MUST fetch the updated channel list
- AND the UI MUST reflect the connected Threads profile before navigation

### Requirement: Error Feedback on Completion Failure

If provider completion returns an error, the callback view MUST display a safe mapped error and offer retry without rendering authorization codes, tokens, signed media URLs, raw provider payloads, or stack traces.

(Previously: completion failure feedback was defined without the provider-neutral secret boundary.)

#### Scenario: Provider completion failure shows safe error

- GIVEN provider completion returns 400, 500, or a mapped reconnect-required error
- WHEN the callback view processes the response
- THEN it MUST show actionable retry feedback
- AND no secret or raw provider payload MUST be shown
