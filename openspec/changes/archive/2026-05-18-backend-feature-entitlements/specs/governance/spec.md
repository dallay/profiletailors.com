# Delta for governance

## MODIFIED Requirements

### Requirement: Deterministic and Explainable Authorization Governance

The system MUST support governance through deterministic and explainable authorization behavior.

Authorization outcomes MUST be attributable to explicit platform facts such as membership, role
permissions, direct grants, denials, scopes, entitlements, and context.
The platform MUST favor explicit-over-implicit behavior so decisions can be understood and governed.
The system MUST NOT rely on undocumented fallback rules for protected access.
For `backend-feature-entitlements`, deny outcomes on `/api/authorization/workspace-access/current`
MUST remain distinguishable when caused by missing workspace entitlement versus missing principal
permission.
This change MUST remain limited to runtime explainability for the existing proving slice and MUST
NOT require durable audit storage, governance dashboards, or broader reporting workflows.
(Previously: Authorization outcomes had to be attributable to explicit facts, including
entitlements, but the spec did not require the existing proving slice to distinguish
missing-entitlement deny from missing-permission deny.)

#### Scenario: Missing entitlement denial is explainable on the proving slice

- GIVEN a principal is denied `/api/authorization/workspace-access/current`
- AND the principal has an effective allow path for the required explicit workspace-scoped
  permission
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the authorization outcome is examined
- THEN the denial MUST be attributable to missing workspace entitlement
- AND the result MUST remain distinguishable from a denial caused by missing principal permission

#### Scenario: Missing permission denial remains explainable on the proving slice

- GIVEN a principal is denied `/api/authorization/workspace-access/current`
- AND the active workspace is entitled to the proving-slice feature
- AND the principal lacks an effective allow path for the required explicit workspace-scoped
  permission
- WHEN the authorization outcome is examined
- THEN the denial MUST be attributable to missing principal permission or the lack of an explicit
  allow path
- AND the result MUST remain distinguishable from a denial caused by missing workspace entitlement
