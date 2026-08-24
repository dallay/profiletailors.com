# Delta for Registration

## MODIFIED Requirements

### Requirement: Registration UI Fails Closed

The SPA MUST show registration entry points only after the public capability resolves enabled.
Capability-read failure or malformed data MUST close registration UI and direct access, MUST NOT be
treated as security enforcement, and MUST NOT block login. Direct `/register` access while
unavailable MUST preserve the requested route, render “Registration is currently unavailable” with
named-route navigation to login, omit the form, and MUST NOT call registration.

(Previously: registration was unavailable on failure, without specifying in-route state, request
suppression, or delayed entry-point rendering.)

#### Scenario: Registration UI follows enabled capability

- GIVEN the capability resolves with registration enabled
- WHEN a guest views login or requests `/register`
- THEN the named registration entry and registration form MUST be available

#### Scenario: Capability failure closes registration only

- GIVEN the capability request fails or returns a malformed value
- WHEN a guest opens login or `/register`
- THEN registration MUST be unavailable without redirecting `/register`
- AND login MUST remain usable

#### Scenario: Disabled route suppresses registration

- GIVEN registration resolves disabled
- WHEN a guest directly requests `/register`
- THEN the unavailable state and named login navigation MUST render instead of the form
- AND no registration request MUST be sent

### Requirement: Backend-Authoritative Registration Gate

When registration is disabled, the backend MUST reject `POST /api/auth/register` before command
dispatch or mutation using the existing authoritative configuration. The response MUST be exact HTTP
503 Problem Details with `code: "REGISTRATION_DISABLED"`. No second registration flag or changed
enabled-registration protocol MAY be introduced.

#### Scenario: Disabled direct API registration

- GIVEN authoritative registration configuration is disabled
- WHEN a client posts directly to `/api/auth/register`
- THEN the response MUST be exact HTTP 503 with `code: "REGISTRATION_DISABLED"`
- AND no command, persistence, event, or session mutation MUST occur

#### Scenario: Enabled registration remains functional

- GIVEN registration is enabled
- WHEN a new user submits valid registration data
- THEN the response MUST be `201 Created`
- AND the existing atomic registration and authenticated-session behavior MUST occur
