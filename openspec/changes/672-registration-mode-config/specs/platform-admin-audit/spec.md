# Delta for Platform Admin Audit

## ADDED Requirements

### Requirement: Configuration-change audit outcomes

The platform-admin audit stream MUST support a `CONFIGURATION_CHANGED` action. Each attempted
registration-mode change MUST produce one audit event carrying the operator, occurred time, action,
result, and metadata `previousMode`/`newMode` (successful attempts) or a non-sensitive rejection
reason (denied/failed attempts). Successful events MUST be emitted only after the persisted mode
change commits; the previous/new pair MUST be captured atomically with the same statement that
performs the update, so it always reflects the actual transition. `previousMode` and `newMode`
values (`OPEN`, `INVITE_ONLY`, `CLOSED`) MUST pass through the existing redaction enforcement
unredacted — they are operational state, not sensitive material.

#### Scenario: Successful configuration change is audited unredacted

- GIVEN an authorized owner changes the registration mode from `OPEN` to `CLOSED`
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED` event exists with result `SUCCEEDED`
- AND its metadata contains `previousMode: "OPEN"` and `newMode: "CLOSED"`, both stored unredacted

#### Scenario: Rejected control is audited without disclosing prior state

- GIVEN an unauthorized (non-owner) operator attempts to change the registration mode
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED` event exists with result `REJECTED`
- AND the persisted registration mode did not change

#### Scenario: Invalid value attempt is audited as failed

- GIVEN an authorized owner submits a value outside `OPEN`, `INVITE_ONLY`, `CLOSED`
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED` event exists with result `FAILED`
- AND no success event is emitted
- AND the persisted registration mode did not change
