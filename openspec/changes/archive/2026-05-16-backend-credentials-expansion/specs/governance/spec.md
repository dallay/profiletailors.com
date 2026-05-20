# Delta for Governance

## MODIFIED Requirements

### Requirement: Auditability of Security-Relevant Platform Actions

The system MUST provide auditability for security-relevant platform actions and decisions as a
platform concern.

At minimum, the platform MUST preserve a seam for recording security-relevant events involving
authentication, credential use, workspace membership changes, role or grant changes, and protected
authorization outcomes.
For the existing proving slice, the platform MUST produce runtime audit-ready proof for allow and
deny outcomes of `/api/authorization/workspace-access/current` for authenticated USER and
authenticated SERVICE_ACCOUNT requests.
That proof MUST be attributable to explicit decision facts for the evaluated request, including the
protected capability and enough authorization or credential-state context to distinguish allow,
authorization-controlled deny, and revoked-credential deny on the active workspace request.
This change MUST remain limited to runtime proof for the existing slice and MUST NOT require audit
persistence, compliance reporting, credential-governance dashboards, or broader governance
workflows.
Comprehensive compliance reporting, retention operations, organization-wide governance workflows,
and governance expansion beyond this slice remain deferred.

(Previously: The existing proving slice required runtime audit-ready proof for allow and deny
outcomes of `/api/authorization/workspace-access/current`, but the executable proof focused on
authenticated USER requests and did not yet require distinction of service-account revocation denial
on that slice.)

#### Scenario: Allowed service-account workspace access outcome is audit-ready at runtime

- GIVEN an authenticated SERVICE_ACCOUNT principal is allowed to access
  `/api/authorization/workspace-access/current`
- WHEN the protected request completes successfully
- THEN the platform MUST surface runtime audit-ready proof that the authorization outcome was
  allowed
- AND the proof MUST be attributable to the existing protected workspace-access slice

#### Scenario: Authorization-controlled service-account denial is audit-ready at runtime

- GIVEN an authenticated SERVICE_ACCOUNT principal is denied access to
  `/api/authorization/workspace-access/current` by current workspace authorization facts
- WHEN the protected request is evaluated
- THEN the platform MUST surface runtime audit-ready proof that the outcome was denied by
  authorization evaluation
- AND the proof MUST be attributable to explicit decision facts for that protected slice

#### Scenario: Revoked service-account credential denial is audit-ready at runtime

- GIVEN a persisted SERVICE_ACCOUNT principal presents a bearer credential for
  `/api/authorization/workspace-access/current`
- AND authoritative backend credential state marks that credential as revoked
- WHEN the request is evaluated
- THEN the platform MUST surface runtime audit-ready proof that the outcome was denied because the
  credential was revoked
- AND the proof MUST remain limited to runtime observability for the existing slice

### Requirement: Current-Slice Governance Deferral Boundary

The system MUST keep governance scope for `backend-credentials-expansion` limited to runtime proof
for the existing workspace-access slice.

This change MUST NOT require durable audit storage, audit query APIs, compliance dashboards, policy
administration, credential-inventory reporting, or governance coverage for endpoints beyond
`/api/authorization/workspace-access/current`.
If broader governance capabilities are needed, they MUST be specified in a later change.

(Previously: The deferral boundary was scoped to `backend-auth-hardening` and its existing
workspace-access proof, without the new service-account revocation-specific proving requirements.)

#### Scenario: Broader credential governance feature is deferred

- GIVEN a requested governance capability does not directly provide runtime audit-ready allow or
  deny proof for `/api/authorization/workspace-access/current`
- WHEN the scope for `backend-credentials-expansion` is reviewed
- THEN that capability MUST be considered deferred
- AND the current change MUST proceed without broadening into credential-governance platform
  features
