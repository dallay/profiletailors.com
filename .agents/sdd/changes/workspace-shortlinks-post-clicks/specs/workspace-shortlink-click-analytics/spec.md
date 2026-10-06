# Workspace Shortlink Click Analytics Specification

## Purpose

Define workspace-scoped recording and query of clicks on shortlinks, without asserting unverified accuracy, retention, privacy, or measurement technology.

## Requirements

### Requirement: Record Clicks for Workspace Shortlinks

The system MUST make clicks on shortlinks measurable and MUST provide the owning workspace with queryable click metrics. What constitutes a counted click, duplicate handling, delivery guarantees, metric granularity, retention, and any collected data remain open for design; design MUST verify the existing redirect and persistence infrastructure and make these decisions explicit before implementation.

#### Scenario: Workspace member consults click metrics

- GIVEN a workspace has shortlinks with recorded clicks
- WHEN an authorized member requests click metrics for that workspace
- THEN the response MUST contain click metrics for shortlinks owned by that workspace
- AND the metric meaning and time scope MUST be clear to the caller

#### Scenario: No recorded clicks

- GIVEN a workspace has a shortlink for which no clicks have been recorded
- WHEN an authorized member requests its metrics
- THEN the response MUST represent the absence of recorded clicks without implying unverified precision or retention guarantees

### Requirement: Isolate Shortlink Resolution and Analytics by Workspace

The system MUST enforce workspace ownership for protected shortlink management and analytics operations. A caller acting in one workspace MUST NOT read, infer, modify, or obtain analytics for a shortlink belonging to another workspace. Public resolution MUST NOT disclose protected management or analytics data; exact redirect behavior and whether ownership is required for resolution MUST be reconciled with Core V1 during design and ADR-0028 reconciliation.

#### Scenario: Cross-workspace shortlink management access is denied

- GIVEN a shortlink belongs to workspace A and the caller is authorized only for workspace B
- WHEN the caller attempts a protected operation on that shortlink
- THEN the request MUST be denied or return a non-disclosing not-found result
- AND MUST NOT reveal whether the shortlink exists in workspace A

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
