# Delta for Tenancy

## ADDED Requirements

### Requirement: SPA Workspace Context Header Injection

The frontend `apiFetch` helper MUST inject the `X-Workspace-Id` header from the active workspace context on all workspace-scoped API requests. If no active workspace is available, the frontend MUST NOT send the request and MUST surface a workspace-context error to the user.

#### Scenario: Authenticated SPA request includes workspace header

- GIVEN the user is authenticated and has an active workspace context
- WHEN any workspace-scoped API call is made through `apiFetch`
- THEN the request MUST include `X-Workspace-Id` with the active workspace ID

#### Scenario: Missing workspace context prevents API call

- GIVEN the user is authenticated but no active workspace context is available
- WHEN the SPA attempts a workspace-scoped API call
- THEN the SPA MUST NOT send the request
- AND it MUST surface an error indicating workspace context is required

#### Scenario: Unauthenticated state shows appropriate UI

- GIVEN no user is authenticated
- WHEN the publishing store initializes
- THEN the store MUST NOT attempt workspace-scoped API calls
- AND the UI MUST display a sign-in prompt rather than channel data
