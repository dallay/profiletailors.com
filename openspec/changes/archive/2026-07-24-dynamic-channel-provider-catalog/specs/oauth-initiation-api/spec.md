# OAuth Initiation API Delta

## MODIFIED Requirements

### Requirement: LinkedIn OAuth Initiation Endpoint

`POST /api/publishing/linkedin/connections/initiate` MUST return signed `authorizationUrl` and `state` to an authenticated workspace caller. Before generation, the server MUST re-evaluate LinkedIn personal-profile policy. It MUST deny initiation unless `AVAILABLE`; the client catalog MUST NOT authorize. State MUST remain tamper-evident and validatable at completion.

(Previously: Initiation required authentication and workspace context but did not require current provider-policy revalidation.)

#### Scenario: Available provider initiates connection
- GIVEN an authenticated caller, workspace context, and `AVAILABLE` policy
- WHEN initiation is called
- THEN it MUST return 200 with `authorizationUrl` and signed `state`
- AND the URL MUST include required parameters

#### Scenario: Policy changed after catalog load
- GIVEN the SPA previously received `AVAILABLE`
- AND current server policy is `LOCKED` or `HIDDEN`
- WHEN initiation is called
- THEN it MUST reject the request without an authorization URL or state

#### Scenario: Missing workspace or authentication is rejected
- GIVEN workspace context or a valid Bearer token is absent
- WHEN initiation is called
- THEN the system MUST reject the request with the existing 400 or 401 behavior
