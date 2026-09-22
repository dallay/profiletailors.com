# Delta for Admin Authorization

## ADDED Requirements

### Requirement: Platform governance permission keys

The permission registry MUST add `platform.governance.read` and `platform.governance.manage`.
`PLATFORM_OWNER` and `PLATFORM_OPERATOR` MUST hold both. `AUDITOR` MUST hold only
`platform.governance.read`. `SUPPORT_AGENT` MUST hold neither. Takedown governance MUST NOT reuse
`platform.operators.read`.

| Permission | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|------------|:-----:|:--------:|:-------------:|:-------:|
| `platform.governance.read` | ✓ | ✓ | — | ✓ |
| `platform.governance.manage` | ✓ | ✓ | — | — |

#### Scenario: Registry includes governance keys

- GIVEN the `PlatformPermission` registry is loaded
- WHEN the system initializes
- THEN the registry contains `platform.governance.read` and `platform.governance.manage`

#### Scenario: Owner and operator hold both keys

- GIVEN an active `PLATFORM_OWNER` or `PLATFORM_OPERATOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.governance.read` and `platform.governance.manage` are present

#### Scenario: Auditor is read-only

- GIVEN an active `AUDITOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.governance.read` is present and `platform.governance.manage` is absent

#### Scenario: Support agent holds neither key

- GIVEN an active `SUPPORT_AGENT` assignment
- WHEN effective permissions are evaluated
- THEN both governance keys are absent

## MODIFIED Requirements

### Requirement: Frontend Mirror Matches Server

The frontend `ROLE_PERMISSIONS` mirror MUST equal the server `PLATFORM_ROLE_PERMISSIONS` for every
key, including `platform.publishing.stale.read` for OWNER and OPERATOR,
`platform.configuration.read`/`platform.configuration.manage` per the mapping above (OWNER both;
OPERATOR and AUDITOR read-only; SUPPORT_AGENT neither),
`platform.notifications.read`/`platform.notifications.manage` per the mapping above (OWNER and
OPERATOR both; SUPPORT_AGENT and AUDITOR read-only), and
`platform.governance.read`/`platform.governance.manage` per the mapping above (OWNER and OPERATOR
both; AUDITOR read-only; SUPPORT_AGENT neither). The system MUST NOT imply permissions the API does
not enforce.
(Previously: governance planned placeholder reused `platform.operators.read`; no governance keys.)

- GIVEN OWNER or OPERATOR session permissions, WHEN the frontend evaluates
  `hasPermission('platform.publishing.stale.read')`, THEN it returns true, matching the server map.
- GIVEN a planned area with no backing admin API, WHEN its placeholder renders, THEN no permission
  beyond the registry entry is implied or checked. Planned placeholders reuse only existing
  server-enforced keys (overview → `platform.dashboard.read`). The `configuration` nav entry is
  `live`, gated on its own `platform.configuration.read`, backed by a real admin API
  (dallay/profiletailors.com#672). The `notifications` nav entry is `live`, gated on its own
  `platform.notifications.read`, backed by a real admin API (dallay/profiletailors.com#670). The
  `governance` nav entry is `live`, gated on its own `platform.governance.read`, backed by a real
  admin API (dallay/profiletailors.com#671).

#### Scenario: Governance nav entry is gated on its own permission

- GIVEN an operator holds `platform.governance.read` but not `platform.operators.read`
- WHEN the frontend evaluates visibility of the `governance` nav entry
- THEN the entry is visible, gated solely on `platform.governance.read`

#### Scenario: Forcing hidden nav still hits server enforcement

- GIVEN a principal lacking `platform.governance.read` forces client-side navigation to governance
- WHEN the corresponding `/api/admin/**` takedown endpoint is called
- THEN the server denies with `401`/`403` regardless of client-side navigation state
