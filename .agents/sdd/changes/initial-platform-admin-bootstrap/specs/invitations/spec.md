# Delta for Invitations

## ADDED Requirements

### Requirement: Bootstrap invitations remain distinct from workspace invitations

Bootstrap invitations MUST be explicitly identified and MUST preserve the existing workspace invitation lifecycle, workspace-scoped uniqueness, and acceptance behavior. Bootstrap invitations MUST NOT be accepted through workspace provisioning or create a workspace or membership. They MUST use one-use hashed token material and a fixed 30-minute expiry for invitation claim. A successful claim MUST bind the invitation to the created principal in a non-reassignable pending-verification state; the existing email-verification token expiry governs the remaining verification period. Replacement MUST require explicit operator confirmation and MUST be atomic under concurrent issuance.

#### Scenario: Bootstrap invitation is not workspace-provisioned

- GIVEN a valid bootstrap invitation
- WHEN its recipient completes the bootstrap flow
- THEN the workspace invitation acceptance and provisioning flow is not used
- AND no workspace or membership is created

#### Scenario: Bootstrap replacement is serialized

- GIVEN a pending bootstrap invitation for the target email
- WHEN concurrent confirmed replacement attempts occur
- THEN at most one replacement is committed
- AND no more than one valid pending bootstrap invitation remains

#### Scenario: Workspace invitation behavior remains unchanged

- GIVEN a normal workspace invitation
- WHEN it is issued or accepted
- THEN its established workspace-scoped lifecycle and provisioning behavior remain unchanged
