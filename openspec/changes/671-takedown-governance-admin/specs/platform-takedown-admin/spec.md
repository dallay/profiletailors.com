# Platform Takedown Admin Specification

## Purpose

Expose existing workspace takedown review across workspaces on `/api/admin/**` without duplicating
governance domain or changing workspace handlers.

## Requirements

### Requirement: Cross-workspace list, filter, and pagination

An operator with `platform.governance.read` MUST list takedown reports across workspaces as a
`PagedResult`. Filters MUST include status and workspace identity. Omitted filters MUST NOT restrict
that dimension.

#### Scenario: Authorized list with filters

- GIVEN OWNER, OPERATOR, or AUDITOR holds `platform.governance.read` and reports exist in two
  workspaces
- WHEN they request a page filtered by `REPORTED` and one workspace id
- THEN the response is 200 with only matching reports and pagination metadata

#### Scenario: Unauthenticated list is denied

- GIVEN no authentication
- WHEN the list is requested
- THEN the response is 401 and no report data is disclosed

### Requirement: Detail includes asset status

An operator with `platform.governance.read` MUST retrieve one report by id including current media
asset status from a media status reader. The admin surface MUST NOT duplicate media domain.

#### Scenario: Detail returns report and asset status

- GIVEN a report exists and the linked asset is `READY` or `SUSPENDED`
- WHEN an authorized operator requests detail
- THEN the response is 200 with report fields and the current asset status

#### Scenario: Unknown report is not found

- GIVEN no report exists for the requested id
- WHEN detail is requested by an authorized operator
- THEN the response is 404 and no other report is disclosed

### Requirement: Approve and reject reuse existing lifecycle

An operator with `platform.governance.manage` MUST approve or reject a `REPORTED` report through
governance ports that return DTOs. Approve MUST suspend the asset and set the report `APPROVED`.
Reject MUST dismiss the report and leave the asset unchanged. Workspace emails and
`MEDIA_TAKEDOWN_APPROVED` / `MEDIA_TAKEDOWN_REJECTED` MUST still fire. Mutations MUST require
`Idempotency-Key` as in #672. `platformadmin` MUST NOT import `TakedownReport`. Workspace
`/api/governance/takedown` handlers MUST remain unchanged.

#### Scenario: Owner or operator approves

- GIVEN a `REPORTED` report and a valid `Idempotency-Key`
- WHEN OWNER or OPERATOR approves
- THEN the report is `APPROVED`, the asset is `SUSPENDED`, emails fire, and workspace
  `MEDIA_TAKEDOWN_APPROVED` is recorded

#### Scenario: Owner or operator rejects

- GIVEN a `REPORTED` report and a valid `Idempotency-Key`
- WHEN OWNER or OPERATOR rejects
- THEN the report is `DISMISSED`, the asset status is unchanged, emails fire, and workspace
  `MEDIA_TAKEDOWN_REJECTED` is recorded

#### Scenario: Replay of the same idempotency key

- GIVEN a successful approve or reject already stored for that operator, command, target, and key
- WHEN the same mutation is repeated
- THEN no second state transition occurs and the original outcome is returned

### Requirement: Forbidden and conflict responses

`SUPPORT_AGENT` MUST receive 403 on every admin takedown operation. AUDITOR MUST receive 403 on
approve and reject. A mutation against a report not in `REPORTED` MUST return 409 with no further
transition.

#### Scenario: Support agent cannot read

- GIVEN an active `SUPPORT_AGENT` assignment
- WHEN list or detail is requested
- THEN the response is 403 and reporter email is not disclosed

#### Scenario: Auditor cannot mutate

- GIVEN an active `AUDITOR` assignment and a `REPORTED` report
- WHEN approve or reject is attempted
- THEN the response is 403 and report and asset states are unchanged

#### Scenario: Already decided report conflicts

- GIVEN the report is `APPROVED` or `DISMISSED`
- WHEN approve or reject is attempted with a new `Idempotency-Key`
- THEN the response is 409 and no further transition occurs

### Requirement: Minimal PII with reporter email for read roles

Admin list and detail MUST include reporter email for principals with `platform.governance.read`.
Responses MUST NOT include tokens, secrets, credentials, or other denylisted material.

#### Scenario: Read roles see reporter email

- GIVEN OWNER, OPERATOR, or AUDITOR lists or opens detail
- WHEN the response is returned
- THEN reporter email is present and no token or secret fields are present
