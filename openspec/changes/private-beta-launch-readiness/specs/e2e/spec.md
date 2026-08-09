# Delta for E2E

## ADDED Requirements

### Requirement: Invitee Journey Covers Activation Through First Publish (DALLAY-558)

The application E2E suite MUST cover the invitee journey from invitation acceptance through first
login, workspace loading, scheduler/composer access, and a publish or schedule attempt. Tests MUST
assert user-visible outcomes, requested effects, workspace context, and safe errors rather than relying
on snapshots alone. Backend behavior introduced by this change MUST also have required Cucumber BDD
coverage with `@smoke` and `@fast` tags.

#### Scenario: Invitee reaches the workspace

- GIVEN a valid invitation fixture for workspace A
- WHEN the invitee accepts it and completes first login
- THEN the dashboard MUST load workspace A
- AND the UI MUST NOT show another workspace’s data or controls

#### Scenario: Invitee schedules with an unavailable capability

- GIVEN the invitee can access the scheduler but the selected provider/capability is unavailable
- WHEN the invitee attempts to schedule
- THEN the UI MUST show an explicit unavailable or gated state and recovery action
- AND no unsupported provider request may be made

#### Scenario: Invitee sees a safe publishing failure

- GIVEN the publishing flow returns a typed retryable or terminal failure
- WHEN the invitee submits the first publish or schedule operation
- THEN the UI MUST show canonical, non-technical copy and the correct recovery action
- AND it MUST NOT display tokens, stack traces, raw provider payloads, or internal identifiers

### Requirement: E2E Evidence Has a Defined Boundary

Mocked provider tests, local browser runs, and CI results MUST be labeled code/test evidence. A browser
run against the managed VPS MAY be labeled operator-observed user journey evidence, with UTC timestamp,
deployment identity, and scope. E2E evidence MUST NOT be described as provider verification or
multi-user verification.

#### Scenario: Missing dependency is reported as a blocker

- GIVEN the invitee journey cannot reach first login, workspace access, or a safe failure state
- WHEN acceptance evidence is reviewed
- THEN DALLAY-558 MUST be marked blocked
- AND DALLAY-559 MUST NOT be eligible for GO
