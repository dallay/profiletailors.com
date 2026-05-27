# Delta for Platform

## MODIFIED Requirements

### Requirement: Platform Bounded Contexts

The system MUST define the following bounded contexts for the platform architecture: Identity,
Tenancy, Authorization, Credentials, Governance, Publishing, and Platform.

The Platform context MUST own cross-cutting seams required by all other contexts, including
mediator-style dispatch, context propagation, and adapter-facing shared contracts. The Identity
context MUST own principal identity semantics. The Tenancy context MUST own workspace lifecycle,
ownership, and membership semantics. The Authorization context MUST own permissions, roles, grants,
scopes, policies, and effective authorization evaluation semantics. The Credentials context MUST own
authentication credential and token semantics. The Governance context MUST own auditing and
governance semantics. The Publishing context MUST own workspace-scoped outbound social publishing
semantics, including provider-neutral publication lifecycle rules and provider delivery ports. Phase
one MUST implement only the minimum contracts and behaviors from these contexts required by the
proving slice.

(Previously: The system MUST define the following bounded contexts for the platform architecture:
Identity, Tenancy, Authorization, Credentials, Governance, and Platform.)

#### Scenario: Cross-context behavior remains bounded with publishing

- GIVEN a workspace member requests outbound social publishing behavior
- WHEN the platform resolves authentication, active workspace context, authorization, publishing
  lifecycle, and provider delivery preparation
- THEN each behavior MUST be attributable to the appropriate bounded context
- AND the Publishing context MUST NOT absorb unrelated identity or tenancy responsibilities merely
  for convenience
