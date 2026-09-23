# Delta for Backoffice Admin Shell

## ADDED Requirements

### Requirement: GovernanceView for live takedown operations

The `governance` nav entry MUST be `live` and routed to `GovernanceView`. The view MUST list,
filter, paginate, show detail with asset status, and approve or reject with confirmation. List and
detail fetch MUST require `platform.governance.read`. Approve and reject MUST require
`platform.governance.manage`, send `Idempotency-Key`, and MUST NOT run until confirmed. Labels MUST
have EN+ES keys. Without read permission the view MUST show access-denied and issue zero takedown
requests.

#### Scenario: Permitted principal sees live governance

- GIVEN a principal with `platform.governance.read`
- WHEN the shell renders nav
- THEN `governance` is visible, `live`, and routes to `GovernanceView`

#### Scenario: Table, filters, and detail render

- GIVEN a permitted operator and a populated list response
- WHEN the view loads and a row is opened
- THEN rows show workspace, status, reporter email, and asset identity
- AND detail shows current asset status

#### Scenario: Confirm before approve or reject

- GIVEN a rendered `REPORTED` row and `platform.governance.manage`
- WHEN approve or reject is chosen
- THEN a confirmation step is required before the mutation runs
- AND the request includes `Idempotency-Key`

#### Scenario: Auditor cannot mutate from the view

- GIVEN an AUDITOR session with read and without manage
- WHEN `GovernanceView` renders
- THEN approve and reject controls are unavailable
- AND no mutate request is issued

#### Scenario: Access denied without read permission

- GIVEN a principal lacking `platform.governance.read`
- WHEN the shell renders nav or the route is opened directly
- THEN the nav entry is hidden and the view shows access-denied with zero takedown requests

## MODIFIED Requirements

### Requirement: Inert Planned-Area Placeholders

The system MUST render planned areas (`overview`) via a shared static view that is explicit
"planned", permission-gated, and performs zero fetch. (Previously: planned list included
`governance` gated on `platform.operators.read`; `governance` is
now live.)

#### Scenario: Planned area shows planned state

- GIVEN a permitted principal opening a planned area
- WHEN the route renders
- THEN an explicit planned message shows and no API request is issued

#### Scenario: Unpermitted planned area stays hidden

- GIVEN a principal lacking the area's permission
- WHEN the shell renders nav and routes
- THEN the entry is hidden and direct navigation is denied
