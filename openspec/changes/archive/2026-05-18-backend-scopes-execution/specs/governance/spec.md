# Delta for governance

## MODIFIED Requirements

### Requirement: Deterministic and Explainable Authorization Governance

The system MUST support governance through deterministic and explainable authorization behavior.

Authorization outcomes MUST be attributable to explicit platform facts such as membership, role permissions, direct grants, denials, scopes, entitlements, and context.
The platform MUST favor explicit-over-implicit behavior so decisions can be understood and governed.
The system MUST NOT rely on undocumented fallback rules for protected access.
For `backend-scopes-execution`, deny outcomes on the new target-aware resource-preview proving capability MUST remain distinguishable when caused by scope reduction versus missing base permission.
This change MUST remain limited to runtime explainability for the new proving capability and MUST NOT require durable audit storage, governance dashboards, or broader reporting workflows.

#### Scenario: Scope-caused denial is explainable on the target-aware proving capability

- GIVEN a principal is denied the new resource-preview proving capability
- AND the principal has an otherwise valid allow path for `workspace:resource:read`
- AND the active workspace has persisted scope state whose allowed target set does not include the requested `targetResourceId`
- WHEN the authorization outcome is examined
- THEN the denial MUST be attributable to scope reduction on the requested target
- AND the result MUST remain distinguishable from a denial caused by missing base permission

#### Scenario: Missing base permission denial remains explainable on the target-aware proving capability

- GIVEN a principal is denied the new resource-preview proving capability
- AND the principal lacks any otherwise valid allow path for `workspace:resource:read`
- WHEN the authorization outcome is examined
- THEN the denial MUST be attributable to missing base permission or the lack of an explicit allow path
- AND the result MUST remain distinguishable from a denial caused by scope reduction
