# Delta for Registration — Auth Security Hardening

## MODIFIED Requirements

### Requirement: Registration Creates Authenticated Session

(Previously: Registration emitted domain event without issuing tokens; response was 201 with
verification instructions)

Registration SHALL create an authenticated session immediately upon successful completion.

The registration handler SHALL issue JWT and refresh tokens.
The response SHALL be HTTP 201 Created with AuthTokens payload.
The response SHALL set an HttpOnly refresh token cookie.
The registration payload SHALL no longer return only RegistrationResult (breaking change from prior
behavior).

#### Scenario: Registration creates session and returns tokens (MODIFIED)

- GIVEN a new user submits valid registration payload with email and password
- WHEN the registration handler processes the request successfully
- THEN the response SHALL be HTTP 201 Created
- AND the response SHALL include an access token in the body
- AND the response SHALL set an HttpOnly refresh token cookie
- AND the user SHALL be immediately authenticated

#### Scenario: Registration response matches AuthTokens payload (MODIFIED)

- GIVEN a user completes registration
- WHEN the response is returned
- THEN the payload SHALL conform to the AuthTokens schema
- AND SHALL NOT conform to the legacy RegistrationResult schema
