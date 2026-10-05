# Delta for Admin Authorization

## ADDED Requirements

### Requirement: Platform configuration permission keys

The permission registry MUST add two keys governing the `platform-configuration` capability:
`platform.configuration.read` and `platform.configuration.manage`. `PLATFORM_OWNER` MUST hold both.
`PLATFORM_OPERATOR` MUST hold only `platform.configuration.read`. `AUDITOR` MUST hold only
`platform.configuration.read`, consistent with its existing `platform.operators.read` grant — both
are read-only governance/investigation visibility over operational state AUDITOR already receives.
`SUPPORT_AGENT` MUST hold neither key, consistent with it holding no `platform.operators.*`
permission today. No role other than `PLATFORM_OWNER` MUST hold `platform.configuration.manage`.

| Permission                      | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|---------------------------------|:-----:|:--------:|:-------------:|:-------:|
| `platform.configuration.read`   |  ✓   |    ✓    |       —       |   ✓    |
| `platform.configuration.manage` |  ✓   |    —     |       —       |    —    |

#### Scenario: Read permission is granted to OWNER, OPERATOR, and AUDITOR

- GIVEN an operator with an active `PLATFORM_OWNER`, `PLATFORM_OPERATOR`, or `AUDITOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.configuration.read` is present

#### Scenario: Manage permission is OWNER-only

- GIVEN an operator with an active `PLATFORM_OPERATOR`, `SUPPORT_AGENT`, or `AUDITOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.configuration.manage` is absent
- AND any attempt to change registration mode is denied with the established forbidden response

## MODIFIED Requirements

### Requirement: Frontend Mirror Matches Server

The frontend `ROLE_PERMISSIONS` mirror MUST equal the server `PLATFORM_ROLE_PERMISSIONS` for every
key, including `platform.publishing.stale.read` for OWNER and OPERATOR, and
`platform.configuration.read`/`platform.configuration.manage` per the mapping above. The system
MUST NOT imply permissions the API does not enforce.

- GIVEN OWNER or OPERATOR session permissions, WHEN the frontend evaluates
  `hasPermission('platform.publishing.stale.read')`, THEN it returns true, matching the server map.
- GIVEN a planned area with no backing admin API, WHEN its placeholder renders, THEN no permission
  beyond the registry entry is implied or checked. Planned placeholders reuse only existing
  server-enforced keys (overview/notifications → `platform.dashboard.read`; governance →
  `platform.operators.read`). (Previously: `configuration` was also listed as a planned placeholder
  reusing
  `platform.operators.read`. The `configuration` nav entry is now `live` and gated on its own
  `platform.configuration.read` permission, backed by a real admin API.)

#### Scenario: Configuration nav entry is gated on its own permission

- GIVEN an operator holds `platform.configuration.read` but not `platform.operators.read`
- WHEN the frontend evaluates visibility of the `configuration` nav entry
- THEN the entry is visible, gated solely on `platform.configuration.read`

#### Scenario: Forcing hidden nav still hits server enforcement

- GIVEN a principal lacking `platform.configuration.read` forces client-side navigation to the
  configuration view
- WHEN the corresponding `/api/admin/**` configuration endpoint is called
- THEN the server denies with `401`/`403` regardless of client-side navigation state
