# Channel List API Specification

## Purpose

Define the backend query endpoint that returns safe connected social account summaries for the
active workspace, serving as the SPA's canonical source of channel truth.

## Requirements

### Requirement: Workspace-Scoped Connected Channel Listing

The system MUST expose `GET /api/publishing/channels` returning active connected social account
summaries for the requesting principal's active workspace.

Each channel summary MUST include: `socialAccountId`, `connectionId`, `provider`, `accountKind`,
`displayName`, `status`, and `connectedAt`. The endpoint MUST NOT expose provider credentials under
any circumstances. Only accounts with status `ACTIVE` MUST be included by default; the query MAY
support a `status` filter parameter.

#### Scenario: Authenticated user lists channels for active workspace

- GIVEN an authenticated principal and a valid `X-Workspace-Id` header
- WHEN `GET /api/publishing/channels` is called
- THEN the system MUST return 200 with an array of active connected social account summaries
- AND each summary MUST contain `socialAccountId`, `connectionId`, `provider`, `accountKind`,
  `displayName`, `status`, `connectedAt`
- AND provider credential secrets MUST NOT appear in any field

#### Scenario: Workspace with no connected channels returns empty array

- GIVEN an authenticated principal and a valid `X-Workspace-Id` header
- AND the workspace has no connected social accounts
- WHEN `GET /api/publishing/channels` is called
- THEN the system MUST return 200 with an empty array

#### Scenario: Unauthenticated request is rejected

- GIVEN no valid Bearer token is present
- WHEN `GET /api/publishing/channels` is called
- THEN the system MUST return 401

#### Scenario: Missing workspace context is rejected

- GIVEN an authenticated principal
- AND no `X-Workspace-Id` header is provided
- WHEN `GET /api/publishing/channels` is called
- THEN the system MUST return 400 with an error indicating workspace context is required

### Requirement: Channel Summary Model Preserves Future Provider Extensibility

The channel summary model MUST be provider-neutral. LinkedIn personal profiles MUST be the first
implemented provider kind. The contract MUST NOT block future addition of Twitter, Instagram,
LinkedIn organization pages, or other provider types.

#### Scenario: LinkedIn personal profile channel appears in listing

- GIVEN a workspace has an active LinkedIn personal profile connection
- WHEN channels are listed
- THEN the summary MUST include `provider: LINKEDIN` and `accountKind: PERSONAL_PROFILE`
- AND the display name MUST reflect the LinkedIn profile name

#### Scenario: Model shape does not prevent future provider addition

- GIVEN the channel summary model includes `provider` and `accountKind` as enum fields
- WHEN a future provider (e.g., Twitter) is connected
- THEN the same endpoint and model MUST be extensible without breaking existing consumers
