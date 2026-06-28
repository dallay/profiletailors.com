# Delta for Publishing

## ADDED Requirements

### Requirement: Idempotent Connection Upsert Semantics

Repository methods for persisting `SocialConnection` and `SocialAccount` MUST use ON CONFLICT
UPDATE (upsert) semantics. Reconnecting the same LinkedIn profile to the same workspace MUST update
the existing record rather than violate uniqueness constraints.

#### Scenario: Reconnecting the same LinkedIn profile updates existing connection

- GIVEN a workspace already has an active LinkedIn personal profile connection with a specific
  provider account ID
- WHEN the OAuth flow is completed again for the same LinkedIn profile and workspace
- THEN the system MUST update the existing `SocialConnection` and `SocialAccount` records
- AND connection status MUST be `ACTIVE` with refreshed credential reference and `connectedAt`
  timestamp
- AND no duplicate records MUST be created

#### Scenario: Reconnecting after revocation restores the connection

- GIVEN a LinkedIn connection has status `REVOKED`
- WHEN the same LinkedIn profile is reconnected in the same workspace
- THEN the system MUST update the existing record status to `ACTIVE`
- AND the credential reference MUST be refreshed

### Requirement: OAuth State Validation on Connection Completion

The existing `POST /api/publishing/linkedin/connections/complete` endpoint MUST validate the `state`
parameter before processing the connection. If `state` is absent, tampered, or expired, the endpoint
MUST reject the request.

#### Scenario: Completion with valid state succeeds

- GIVEN a valid `authorizationCode` and a `state` value that matches the signed original from
  initiation
- WHEN the completion endpoint is called
- THEN the system MUST process the LinkedIn OAuth exchange and persist the connection

#### Scenario: Completion with invalid state is rejected

- GIVEN a `state` value that does not match the signed original from initiation
- WHEN the completion endpoint is called
- THEN the system MUST return 400 with a state-validation error
- AND it MUST NOT exchange the authorization code or persist any connection

### Requirement: Frontend Channel Data Source Migration

The publishing Pinia store MUST replace mock channel seeding with backend-loaded channels. The store
MUST initialize `channels` as an empty array for authenticated users and load real channels from
`GET /api/publishing/channels`. Actions `fetchChannels()`, `connectLinkedInPersonalProfile()`, and
`completeLinkedInConnectionFromCallback()` MUST be added.

#### Scenario: Authenticated user loads channels from backend

- GIVEN the user is authenticated and the publishing store initializes
- WHEN `fetchChannels()` is called
- THEN it MUST call `GET /api/publishing/channels` via `apiFetch` with `X-Workspace-Id`
- AND populate `channels` with the backend response
- AND no mock channel data MUST be present

#### Scenario: Scheduling uses real backend account ID

- GIVEN the user selects a connected LinkedIn personal profile for scheduling
- WHEN the publication is submitted
- THEN the `socialAccountId` MUST be the real backend `socialAccountId` value
- AND it MUST NOT use `account-linkedin-mock` or any mock identifier

#### Scenario: Empty channel state shows Connect LinkedIn CTA

- GIVEN the user is authenticated and no channels are connected
- WHEN the publishing store loads with an empty channel list
- THEN the UI MUST display an empty state with a "Connect LinkedIn profile" call-to-action
- AND it MUST NOT display mock channels

## MODIFIED Requirements

### Requirement: Workspace-Scoped Social Connections

The system MUST allow an authenticated workspace member to register and manage a social-provider
connection in workspace scope.

A social connection MUST be associated with exactly one workspace and one provider account identity.
The system MUST persist enough provider metadata to identify the connected account, provider type,
connection status, and credential freshness. Provider credential secrets MUST remain an
infrastructure concern and MUST NOT leak into public API responses. LinkedIn personal-profile
connection support MUST be implemented in this change. LinkedIn page support MAY be added later
without redefining the core connection model. Reconnecting the same provider account MUST use upsert
semantics to avoid uniqueness violations.

(Previously: Reconnect/upsert semantics were not specified; plain INSERT risked unique-constraint
violations.)

#### Scenario: User connects a LinkedIn personal profile to a workspace

- GIVEN an authenticated USER acts within an active workspace
- AND the user completes the supported LinkedIn OAuth flow successfully
- WHEN the backend finalizes the provider connection
- THEN the system MUST persist a workspace-scoped social connection for provider `LINKEDIN`
- AND the persisted connection MUST identify the connected personal profile account

#### Scenario: Provider credential details are not exposed through public read models

- GIVEN a workspace member retrieves connected social account details
- WHEN the system returns the connection read model
- THEN the response MUST include only safe provider metadata and connection status
- AND it MUST NOT expose provider access tokens, refresh tokens, or equivalent secrets

#### Scenario: Reconnecting the same LinkedIn profile is idempotent

- GIVEN a workspace already has an active LinkedIn personal profile connection
- WHEN the same LinkedIn profile is reconnected through OAuth
- THEN the system MUST update the existing connection and account records
- AND MUST NOT create duplicate records
- AND connection status MUST be `ACTIVE` with refreshed metadata
