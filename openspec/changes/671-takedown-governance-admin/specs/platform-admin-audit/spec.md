# Delta for Platform Admin Audit

## ADDED Requirements

### Requirement: Takedown approve and reject audit outcomes

The platform-admin audit stream MUST support `TAKEDOWN_APPROVED` and `TAKEDOWN_REJECTED`. Each
attempted admin approve or reject MUST produce one matching event carrying operator, occurred time,
action, result, and target report id. Successful events MUST be emitted only after the governance
transition commits. Denied or failed attempts MUST carry a non-sensitive reason. Metadata MUST use
report, workspace, and asset ids only — never reporter email, tokens, or secrets. Workspace
`MEDIA_TAKEDOWN_APPROVED` / `MEDIA_TAKEDOWN_REJECTED` MUST still be recorded and MUST NOT be
renamed.

#### Scenario: Successful approve is audited

- GIVEN an authorized approve completes
- WHEN the admin audit stream is inspected
- THEN one `TAKEDOWN_APPROVED` event exists with result `SUCCEEDED`
- AND workspace `MEDIA_TAKEDOWN_APPROVED` is also recorded

#### Scenario: Successful reject is audited

- GIVEN an authorized reject completes
- WHEN the admin audit stream is inspected
- THEN one `TAKEDOWN_REJECTED` event exists with result `SUCCEEDED`
- AND workspace `MEDIA_TAKEDOWN_REJECTED` is also recorded

#### Scenario: Denied mutate is audited without PII

- GIVEN an AUDITOR or SUPPORT_AGENT attempts approve or reject
- WHEN the admin audit stream is inspected
- THEN one matching event exists with result `REJECTED`
- AND metadata contains ids only, not reporter email

#### Scenario: Registry includes both actions

- GIVEN the platform-admin audit action registry is loaded
- WHEN the system initializes
- THEN the registry contains `TAKEDOWN_APPROVED` and `TAKEDOWN_REJECTED`
