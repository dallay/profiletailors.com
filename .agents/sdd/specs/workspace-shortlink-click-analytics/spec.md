# Workspace Shortlink Click Analytics Specification

## Purpose

Define workspace-scoped recording and query of clicks on shortlinks, without asserting unverified accuracy, retention, privacy, or measurement technology.

## Requirements

### Requirement: Record Clicks for Workspace Shortlinks

The system MUST make clicks on shortlinks measurable and provide the owning workspace with queryable click metrics. The metrics capability MUST provide a workspace-scoped collection query that returns the workspace's shortlinks together with each link's count of currently stored click records, including links with zero records. What constitutes a counted click, duplicate handling, delivery guarantees, metric granularity, retention, and any collected data remain open for design; design MUST verify the existing redirect and persistence infrastructure and make these decisions explicit before implementation.

#### Scenario: Workspace member consults click metrics

- GIVEN a workspace has shortlinks with recorded clicks
- WHEN an authorized member requests click metrics for that workspace
- THEN the response MUST contain click metrics for shortlinks owned by that workspace
- AND the metric meaning and time scope MUST be clear to the caller

#### Scenario: No recorded clicks

- GIVEN a workspace has a shortlink for which no clicks have been recorded
- WHEN an authorized member requests its metrics or lists workspace shortlinks with metrics
- THEN the response MUST represent its count as zero
- AND the metric MUST be the count of all currently stored click records with no time-range parameter

#### Scenario: List workspace shortlinks with recorded and zero-click counts

- GIVEN the current workspace owns shortlinks with recorded clicks and shortlinks with no click records
- WHEN an authorized member requests the workspace shortlink metrics collection
- THEN the response MUST list only shortlinks owned by the current workspace
- AND each listed shortlink MUST include its exact currently stored click-record count, including zero

#### Scenario: Paginate workspace shortlink metrics

- GIVEN the current workspace has more shortlinks than fit in one response
- WHEN an authorized member requests successive pages using `limit` and an opaque cursor
- THEN the response MUST continue exclusively after the cursor's composite `(created_at, id)` position
- AND the list MUST be ordered by `created_at DESC, id DESC`
- AND cursor payload encoding MUST follow existing repository cursor conventions where compatible with the composite position, without requiring a particular payload format or signing mechanism
- AND pagination MUST NOT guarantee a stable snapshot when shortlinks are created or otherwise change between page requests

### Requirement: Isolate Shortlink Resolution and Analytics by Workspace

The system MUST enforce workspace ownership for protected shortlink management and analytics operations. Protected requests MUST require authentication and the active workspace context. Collection queries MUST return only links owned by that workspace. Access by link ID MUST NOT distinguish foreign-owned links from unknown links. Public resolution MUST NOT disclose protected management or analytics data; exact redirect behavior and whether ownership is required for resolution MUST be reconciled with Core V1 during design and ADR-0028 reconciliation. The workspace metrics collection MUST use the versioned `application/vnd.api.v1+json` response media type.

#### Scenario: Cross-workspace shortlink management access is denied

- GIVEN a shortlink belongs to workspace A and the caller is authorized only for workspace B
- WHEN the caller attempts a protected operation on that shortlink by ID
- THEN the response MUST match the response for an unknown link ID
- AND MUST NOT reveal whether the shortlink exists in workspace A

#### Scenario: Workspace context is required for metrics collection

- GIVEN a request is unauthenticated or has no valid active workspace context
- WHEN it requests the workspace shortlink metrics collection
- THEN the request MUST be rejected according to the API's authentication or workspace-context error contract
- AND MUST NOT return shortlink or click-count data

#### Scenario: Metrics collection requires the versioned media type

- GIVEN an authenticated caller has a valid active workspace context
- WHEN the caller requests the metrics collection with `Accept: application/vnd.api.v1+json`
- THEN the response MUST use that versioned media type
- AND a request with an incompatible explicit version MUST be rejected according to the API versioning contract

#### Scenario: Cross-workspace analytics access is denied

- GIVEN a shortlink and its click metrics belong to workspace A
- WHEN a member of workspace B requests those metrics, directly or through a collection query
- THEN the response MUST NOT expose the shortlink or its metrics
- AND MUST NOT reveal whether the foreign shortlink exists

#### Scenario: Public resolution does not expose protected data

- GIVEN a shortlink is used for public redirection
- WHEN a visitor resolves it
- THEN the response MUST NOT expose workspace identity or analytics data
- AND workspace-scoped management and analytics access MUST remain protected
