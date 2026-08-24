# Publishing Delta

## ADDED Requirements

### Requirement: Workspace-Resolved Provider Catalog API

`GET /api/publishing/channels/providers` MUST return workspace catalog. Entries MUST include
provider, account kinds, `state` (`AVAILABLE`, `LOCKED`, `HIDDEN`), typed `reason` (or `null`),
`channelLimit: null`, `connectedChannelCount`, and `canConnectMore: true`. The server MUST resolve
implementation, configuration/enabling, entitlement, and capacity; it MUST NOT infer plans or
limits. `HIDDEN` MUST cover unavailable implementation/configuration. Capacity MUST restrict new
connections. BDD MUST cover headers, states/reasons, hidden omission, capacity, and no secrets or
plans.

#### Scenario: Available LinkedIn personal-profile entry

- GIVEN LinkedIn personal-profile connection is available
- WHEN the workspace requests the catalog
- THEN it MUST be `AVAILABLE` with reason `null` and account kind `PERSONAL_PROFILE`
- AND `channelLimit` MUST be `null` and `canConnectMore` MUST be `true`

#### Scenario: Policy and hidden results are distinguishable

- GIVEN a provider is denied by entitlement or capacity
- WHEN the catalog is resolved
- THEN it MUST be `LOCKED` with a typed reason such as `NOT_ENTITLED` or `CAPACITY_REACHED`
- AND a provider lacking implementation, enablement, or valid credentials MUST be `HIDDEN`

#### Scenario: Capacity preserves channels

- GIVEN a connected channel and unavailable new-connection capacity
- WHEN catalog and channel data are requested
- THEN only another connection MUST be blocked
- AND the existing channel MUST remain manageable
