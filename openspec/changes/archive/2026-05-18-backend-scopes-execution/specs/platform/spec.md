# Delta for platform

## MODIFIED Requirements

### Requirement: Resource Context Taxonomy

The system MUST define the following resource context taxonomy: GLOBAL, USER, WORKSPACE, and SYSTEM.

Authorization decisions MUST be evaluated relative to an explicit resource context.
Permissions, grants, scopes, and policies MUST NOT rely on implicit resource-context inference.
Phase one MUST fully support WORKSPACE context for the proving slice.
For `backend-scopes-execution`, the new target-aware proving capability MUST evaluate authorization
in WORKSPACE context and MUST use explicit `targetResourceId` input as part of the protected request
context.
Support for GLOBAL, USER, and SYSTEM contexts is platform-required and MAY be deferred in
implementation beyond the contracts required to keep the model stable.
(Previously: WORKSPACE context was required for the proving slice, but no executable target-aware
capability was required to carry explicit target resource context for scope reduction.)

#### Scenario: Target-aware workspace request evaluates with explicit target context

- GIVEN the new resource-preview proving capability is defined for workspace data
- AND the request includes an active workspace identifier and explicit `targetResourceId`
- WHEN authorization is evaluated for that capability
- THEN the platform MUST evaluate the request in WORKSPACE resource context
- AND it MUST treat the supplied `targetResourceId` as explicit protected target context rather than
  as implicit or derived state
