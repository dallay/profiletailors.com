# Delta for Platform Admin Audit

## ADDED Requirements

### Requirement: User-control audit outcomes

The platform-admin audit stream MUST support `USER_DISABLED`, `USER_ENABLED`, and
`USER_SESSIONS_REVOKED` actions. Each attempted command MUST produce one audit event containing the
operator, target principal, occurred time, action, result, and correlation/request context when
available. Successful events MUST be emitted only after the corresponding state/session operation
completes; rejected and failed outcomes MUST identify the non-sensitive reason. Audit metadata MUST
continue to use the existing redaction enforcement.

#### Scenario: Successful control is audited

- GIVEN an authorized operator disables, enables, or revokes sessions for an existing user
- WHEN the command completes successfully
- THEN exactly one matching audit event is persisted with result `SUCCEEDED`
- AND the target identifier is the user principal
- AND no credential, token, password, or secret value is stored

#### Scenario: Rejected control is audited

- GIVEN an unauthenticated or unauthorized principal attempts a user-control command
- WHEN authorization rejects the request
- THEN an audit event records the matching action and result `REJECTED`
- AND the event contains no sensitive request data

#### Scenario: Failed control is audited

- GIVEN authorization succeeds but the target is missing or the operation cannot complete
- WHEN the command fails
- THEN an audit event records the matching action and result `FAILED`
- AND no success event is emitted
