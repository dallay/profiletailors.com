# Delta for Observability

## ADDED Requirements

### Requirement: User-administration metrics

The platform MUST expose counters for user disable, enable, and session-revoke command outcomes, plus failed authorization attempts for user administration. Counters MUST distinguish operation and outcome, and MUST NOT include email addresses, tokens, passwords, raw user-agent values, or other sensitive identifiers. Metric names and ownership MUST be finalized in design while preserving these semantic dimensions.

#### Scenario: Successful control increments a counter

- GIVEN an authorized disable, enable, or sessions/revoke command completes successfully
- WHEN observability is recorded
- THEN the counter for that operation and successful outcome increments once
- AND no sensitive user data is used as a metric label

#### Scenario: Failed or rejected control is observable

- GIVEN a user-administration command is rejected or fails
- WHEN observability is recorded
- THEN the corresponding operation/outcome counter increments once
- AND an authorization rejection is distinguishable from an authorized operation failure

#### Scenario: Repeated idempotent command remains measurable

- GIVEN an idempotent command is submitted more than once
- WHEN each request completes
- THEN each request contributes one outcome observation
- AND counters remain bounded to declared low-cardinality labels
