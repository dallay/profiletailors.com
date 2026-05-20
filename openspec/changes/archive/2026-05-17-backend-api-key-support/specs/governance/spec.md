# Delta for Governance

## MODIFIED Requirements

### Requirement: Auditability of Security-Relevant Platform Actions

The system MUST provide auditability for security-relevant platform actions and decisions as a
platform concern.

At minimum, the platform MUST preserve a seam for recording security-relevant events involving
authentication, credential use, workspace membership changes, role or grant changes, and protected
authorization outcomes.
For the existing proving slice, the platform MUST produce runtime audit-ready proof for allow and
deny outcomes of `/api/authorization/workspace-access/current` for authenticated USER, authenticated
SERVICE_ACCOUNT, and authenticated API_KEY requests.
That proof MUST be attributable to explicit decision facts for the evaluated request, including the
protected capability and enough authorization or credential-state context to distinguish allow,
authorization-controlled deny, and revoked-or-inactive API-key deny on the active workspace request.
This change MUST remain limited to runtime proof for the existing slice and MUST NOT require audit
persistence, compliance reporting, credential-governance dashboards, issuance reporting, inventory
reporting, or broader governance workflows.
Comprehensive compliance reporting, retention operations, organization-wide governance workflows,
and governance expansion beyond this slice remain deferred.
(Previously: Runtime audit-ready proof for this slice was required only for authenticated USER and
authenticated SERVICE_ACCOUNT requests, including revoked service-account credential denial.)

#### Scenario: Allowed API-key workspace access outcome is audit-ready at runtime

- GIVEN an authenticated API_KEY principal is allowed to access
  `/api/authorization/workspace-access/current`
- WHEN the protected request completes successfully
- THEN the platform MUST surface runtime audit-ready proof that the authorization outcome was
  allowed
- AND the proof MUST be attributable to the existing protected workspace-access slice

#### Scenario: Authorization-controlled API-key denial is audit-ready at runtime

- GIVEN an authenticated API_KEY principal is denied access to
  `/api/authorization/workspace-access/current` by current workspace authorization facts
- WHEN the protected request is evaluated
- THEN the platform MUST surface runtime audit-ready proof that the outcome was denied by
  authorization evaluation
- AND the proof MUST be attributable to explicit decision facts for that protected slice

#### Scenario: Revoked or inactive API-key denial is audit-ready at runtime

- GIVEN a persisted API_KEY principal presents a credential for
  `/api/authorization/workspace-access/current`
- AND authoritative backend credential state marks that credential as revoked or inactive
- WHEN the request is evaluated
- THEN the platform MUST surface runtime audit-ready proof that the outcome was denied because the
  credential was revoked or inactive
- AND the proof MUST remain limited to runtime observability for the existing slice

### Requirement: Current-Slice Governance Deferral Boundary

The system MUST keep governance scope for `backend-api-key-support` limited to runtime proof for the
existing workspace-access slice.

This change MUST NOT require durable audit storage, audit query APIs, compliance dashboards, policy
administration, credential-inventory reporting, issuance workflows, or governance coverage for
endpoints beyond `/api/authorization/workspace-access/current`.
If broader governance capabilities are needed, they MUST be specified in a later change.
(Previously: The boundary referred to `backend-credentials-expansion` and deferred broader
credential governance for service-account breadth expansion.)

#### Scenario: Broader API-key governance feature is deferred

- GIVEN a requested governance capability does not directly provide runtime audit-ready allow or
  deny proof for `/api/authorization/workspace-access/current`
- WHEN the scope for `backend-api-key-support` is reviewed
- THEN that capability MUST be considered deferred
- AND the current change MUST proceed without broadening into API-key governance platform features
