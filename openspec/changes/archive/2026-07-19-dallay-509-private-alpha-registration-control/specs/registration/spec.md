# Delta for Registration

## ADDED Requirements

### Requirement: Registration Availability Configuration

The backend MUST bind registration availability from typed, non-secret configuration, MUST default
it to `false`, and MUST require an explicit override to enable registration. Operator documentation
MUST describe the setting without secrets.

#### Scenario: Missing configuration fails closed

- GIVEN no registration availability override is configured
- WHEN the application starts
- THEN registration MUST be disabled

#### Scenario: Explicit override enables registration

- GIVEN the registration availability override is `true`
- WHEN the application starts
- THEN registration MUST be enabled

### Requirement: Backend-Authoritative Registration Gate

When registration is disabled, `POST /api/auth/register` MUST be rejected before command dispatch or
mutation with `403 application/problem+json`. The body MUST contain
`type: "/problems/registration-disabled"`, `title: "Registration disabled"`, `status: 403`,
non-sensitive `detail`, and `code: "registration_disabled"`. When enabled, existing registration
behavior and atomicity MUST remain unchanged.

#### Scenario: Direct registration is denied without side effects

- GIVEN registration is disabled
- WHEN a client posts valid registration data directly
- THEN the response MUST match the specified Problem Details contract
- AND no command, persistence, event, or session mutation MUST occur

#### Scenario: Enabled registration remains functional

- GIVEN registration is enabled
- WHEN a new user submits valid registration data
- THEN the response MUST be `201 Created`
- AND the existing atomic registration and authenticated-session behavior MUST occur

### Requirement: Registration UI Fails Closed

The SPA MUST show registration entry points only when the public capability reports enabled.
Capability-read failure MUST close registration UI and direct access, MUST NOT be treated as
security enforcement, and MUST NOT block login.

#### Scenario: Registration UI follows enabled capability

- GIVEN the capability reports registration enabled
- WHEN a guest views authentication UI
- THEN registration controls and the registration route MUST be available

#### Scenario: Capability failure closes registration only

- GIVEN the capability request fails
- WHEN a guest opens login or registration directly
- THEN registration MUST be unavailable
- AND login MUST remain usable
